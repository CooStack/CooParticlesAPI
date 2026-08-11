package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * `RenderEntity` 是一条“服务端权威状态 + 客户端镜像渲染”的基础 API。
 *
 * 它负责三件事：
 * 1. 在服务端维护实体状态，并通过 `CREATE / TOGGLE / REMOVE` 包同步给客户端。
 * 2. 在客户端保存对应镜像，并把同步状态更新到本地实例。
 * 3. 提供 `tracked(...)`、`createCodec(...)`、`loadProfileFromEntity(...)` 等通用同步工具。
 *
 * 使用子类时通常需要完成以下几个点：
 * - 用 `createCodec(...)` 为自定义字段建立编解码。
 * - 覆盖 `getRenderID()`，并在客户端完成注册。
 * - 如果实体有额外同步字段，需要覆盖 `loadProfileFromEntity(...)` 把这些字段回写到客户端镜像。
 *
 * 客户端 renderer 只声明不可变 pipeline 并绘制几何：
 * ```kotlin
 * class FooRenderer : RenderEntityRenderer<FooEntity> {
 *     override val pipeline = CooPipelines.MASK_BLOOM
 *         .bloomMipLevels(6)
 *         .intensity { entity: FooEntity -> 2.8F * entity.bright.coerceAtLeast(0F) }
 *
 *     override fun render(input: RenderInput<FooEntity>) {
 *         drawFoo(input.entity, input.viewMatrix, input.projMatrix, input.modelMatrix)
 *     }
 * }
 * ```
 * `CooPipelines.DEFAULT` 用于不需要后处理的实体。pipeline compiler 根据节点和连线推导
 * stage、scene 资源、mask 与临时 target；renderer 不再声明单独的能力接口。
 *
 * 一般业务请优先继承 [AutoRenderEntity]：它能基于 `@CodecField` 注解自动生成 codec
 * 并自动回写字段。直接继承 `RenderEntity` 适用于需要完全自定义同步格式的少数场景。
 */
abstract class RenderEntity(var world: Level?, var pos: Vec3 = Vec3.ZERO) : ServerControler<RenderEntity>,
    Tickable<RenderEntity> {
    /**
     * 渲染可视距离
     * 给服务器设置则是设置进行传输生成的最小范围
     * 给客户端设置这是设置渲染的范围
     */
    var renderRange = 256.0

    /**
     * 当前实例是否运行在客户端世界。
     */
    val client: Boolean
        get() = world?.isClientSide ?: false

    /**
     * 为 `true` 时，服务端每 tick 都会尝试发送 `TOGGLE`，即使本帧没有 dirty。
     */
    var alwaysToggle = false
    private var syncOnce = false

    companion object {
        /**
         * 解码 RenderEntity 的基础同步字段。
         *
         * 这些字段是所有 RenderEntity 共有的同步基线：
         * `uuid / pos / canceled / age`
         */
        fun decodeBase(buf: FriendlyByteBuf, instance: RenderEntity) {
            instance.uuid = buf.readUUID()
            instance.pos = buf.readVec3()
            instance.canceled = buf.readBoolean()
            instance.age = buf.readInt()
            instance.dirty = false
        }

        /**
         * 编码 RenderEntity 的基础同步字段。
         */
        fun encodeBase(buf: FriendlyByteBuf, entity: RenderEntity) {
            buf.writeUUID(entity.uuid)
            buf.writeVec3(entity.pos)
            buf.writeBoolean(entity.canceled)
            buf.writeInt(entity.age)
        }

        /**
         * 构建一个始终包含基础字段的 codec。
         *
         * 绝大部分子类都应优先使用这个方法，而不是手写完整 codec。
         * 你只需要处理自己的额外字段，基础字段会自动编码和解码。
         */
        fun <T : RenderEntity> createCodec(
            factory: () -> T,
            encodeExtra: (FriendlyByteBuf, T) -> Unit = { _, _ -> },
            decodeExtra: (FriendlyByteBuf, T) -> Unit = { _, _ -> }
        ): StreamCodec<FriendlyByteBuf, RenderEntity> {
            return StreamCodec.of(
                { buf, entity ->
                    encodeBase(buf, entity)
                    @Suppress("UNCHECKED_CAST")
                    val typed = entity as T
                    encodeExtra(buf, typed)
                },
                { buf ->
                    val instance = factory()
                    decodeBase(buf, instance)
                    decodeExtra(buf, instance)
                    instance
                }
            )
        }
    }

    /**
     * 最近一次实际参与渲染时记录的位置。
     * 可用于做插值、拖尾或和当前 `pos` 做对比。
     */
    var lastRenderPos = pos
        internal set

    /**
     * 实体已经经过的逻辑 tick 数。
     * `getTime(delta)` 会把它转换成适合 shader 使用的秒制时间。
     */
    var age = 0

    /**
     * 同步和客户端查找用的稳定标识。
     */
    var uuid: UUID = UUID.randomUUID()

    /**
     * 当前实例是否需要发送 `TOGGLE` 同步。
     */
    var dirty = false

    /**
     * 为 `true` 时，服务端和客户端管理器都会在后续 tick 中移除该实体。
     */
    var canceled = false
    private val preTickActions = ArrayList<RenderEntity.() -> Unit>()
    private val postTickActions = ArrayList<RenderEntity.() -> Unit>()

    /**
     * 统一 tick 入口。
     *
     * - 服务端走 `serverTick()`
     * - 客户端走 `clientTick()`
     */
    override fun tick() {
        if (canceled) return
        age++
        val stableSize = preTickActions.size
        var index = 0
        while (index < stableSize) {
            preTickActions[index](this)
            index++
        }
        if (client) {
            clientTick()
        } else {
            serverTick()
        }
        postTickActions.forEach { it(this) }
    }


    final override fun addPreTickAction(action: RenderEntity.() -> Unit): Tickable<RenderEntity> {
        preTickActions.add(action)
        return this
    }

    final override fun addPreTickActionPost(action: RenderEntity.() -> Unit): Tickable<RenderEntity> {
        postTickActions.add(action)
        return this
    }

    /**
     * 客户端逻辑更新钩子。
     *
     * 适合：
     * - 纯视觉状态推进
     * - 客户端缓存刷新
     * - 不需要回写到服务端的动画逻辑
     */
    open fun clientTick() {
    }

    /**
     * 服务端逻辑更新钩子。
     *
     * 适合：
     * - 推进权威状态
     * - 修改同步字段
     * - 触发 `markDirty()` / `requestSync()`
     */
    open fun serverTick() {
    }

    /**
     * 获取标准 `TOGGLE` 包。
     * 当 `dirty == false` 且未强制发送时会返回 `null`。
     */
    fun getTogglePacket(): PacketRenderEntityS2C? {
        return getTogglePacket(false)
    }

    /**
     * 获取 `TOGGLE` 包。
     *
     * @param force 为 `true` 时，即使未 dirty 也会强制生成包
     */
    fun getTogglePacket(force: Boolean): PacketRenderEntityS2C? {
        return getPacket(PacketRenderEntityS2C.Method.TOGGLE, force)
    }

    /**
     * 获取指定方法的数据包，默认遵循 `TOGGLE` 的 dirty 检查规则。
     */
    fun getPacket(method: PacketRenderEntityS2C.Method): PacketRenderEntityS2C? {
        return getPacket(method, false)
    }

    /**
     * 构建一个同步包。
     *
     * 除 `TOGGLE` 会根据 `dirty/force` 决定是否跳过外，
     * `CREATE` 和 `REMOVE` 通常总是会返回有效包。
     */
    fun getPacket(method: PacketRenderEntityS2C.Method, force: Boolean): PacketRenderEntityS2C? {
        // 判断数据有没有发生改变
        if (!force && !dirty && method == PacketRenderEntityS2C.Method.TOGGLE) {
            return null
        }
        val buf = FriendlyByteBuf(Unpooled.buffer())
        getCodec().encode(buf, this)
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        // 发包
        val packet = PacketRenderEntityS2C(uuid, bytes, getRenderID(), method)
        return packet
    }

    /**
     * 修改世界位置并自动标记 dirty。
     *
     * 如果只是局部插值或客户端内部移动，不一定要调用这个方法；
     * 如果希望服务端位置同步到客户端，应优先使用它。
     */
    fun setPosition(pos: Vec3) {
        this.pos = pos
        markDirty()
    }

    /**
     * 把当前实体标记为“需要在下一次同步时发送 TOGGLE”。
     *
     * 当你直接修改自定义字段，而不是通过 `setPosition(...)` 这类自带同步语义的方法更新时，
     * 应该主动调用它，否则客户端镜像不会收到这次状态变化。
     */
    fun markDirty() {
        dirty = true
    }

    /**
     * 请求一次性同步。
     *
     * 与 `markDirty()` 的区别是：
     * 这次 `TOGGLE` 成功发送后会自动清掉 dirty。
     */
    fun requestSync() {
        dirty = true
        syncOnce = true
    }

    /**
     * 手动清空 dirty / syncOnce 状态。
     */
    fun clearDirty() {
        dirty = false
        syncOnce = false
    }

    /**
     * 服务端在同步成功后调用的内部钩子。
     */
    internal fun onSynced() {
        dirty = false
        syncOnce = false
    }

    /**
     * 决定服务端这一 tick 是否需要发送 `TOGGLE`。
     *
     * 默认规则是：
     * - `alwaysToggle == true`
     * - 或 `dirty == true`
     *
     * 子类可覆盖它来自定义同步频率。
     */
    open fun shouldSync(): Boolean {
        return alwaysToggle || dirty
    }

    /**
     * 一个常用的属性委托：字段值变化时自动触发同步标记。
     *
     * 用法：
     * `var radius by tracked(1.0f)`
     *
     * 注意：
     * - 它只负责把服务端状态标记为 dirty
     * - 不会自动帮你实现客户端镜像字段回写
     * - 如果子类有额外同步字段，仍需要覆盖 `loadProfileFromEntity(...)`
     *
     * @param syncOnce 为 `true` 时改用 `requestSync()`，只同步一次
     */
    protected fun <T> tracked(initial: T, syncOnce: Boolean = false): ReadWriteProperty<Any?, T> {
        return object : ReadWriteProperty<Any?, T> {
            private var value = initial

            /**
             * 从 `当前组件` 当前维护的状态中读取 `getValue` 结果，不创建新的渲染资源。
             *
             * 示例：`getValue(thisRef = thisRef, property = property)`。
             *
             * @param thisRef 当前操作需要的输入值；其语义由方法名和所属组件共同限定
             *
             * @param property 当前操作需要的输入值；其语义由方法名和所属组件共同限定
             *
             * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
             */
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return value
            }

            /**
             * 更新 `当前组件` 的 `setValue` 状态；修改会影响后续查询、构建或当前帧绘制。
             *
             * 示例：`setValue(thisRef = thisRef, property = property, value = value)`。
             *
             * @param thisRef 当前操作需要的输入值；其语义由方法名和所属组件共同限定
             *
             * @param property 当前操作需要的输入值；其语义由方法名和所属组件共同限定
             *
             * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
             */
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                if (this.value == value) return
                this.value = value
                if (syncOnce) {
                    requestSync()
                } else {
                    markDirty()
                }
            }
        }
    }

    /**
     * 把逻辑 tick 时间转换为以秒为单位的连续时间。
     *
     * @param delta tickDelta 在渲染阶段提供，用作着色器的 time 参数
     */
    fun getTime(delta: Float): Float {
        return (age + delta) / 20
    }

    /**
     * 把一个新解码出来的实体数据回写到当前客户端镜像。
     *
     * 基类只复制通用字段：
     * `age / canceled / pos / uuid / world`
     *
     * 如果子类还有额外同步字段，例如半径、颜色、强度等，
     * 必须覆盖这个方法并显式把这些字段写回去。
     */
    open fun loadProfileFromEntity(another: RenderEntity) {
        this.age = another.age
        this.canceled = another.canceled
        this.pos = another.pos
        this.uuid = another.uuid
        this.world = another.world
    }

    /**
     * 返回该类型的同步 codec。
     *
     * 这个 codec 同时用于：
     * - 服务端发送 `CREATE / TOGGLE / REMOVE`
     * - 客户端收到包后重建临时实体
     *
     * 子类自己的字段必须全部纳入 codec，否则无法正确同步。
     */
    abstract fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity>

    /**
     * 返回当前 RenderEntity 类型的唯一注册 id。
     *
     * 这个 id 会直接写进同步包，并用于客户端查找：
     * - codec
     * - rendererFactory
     *
     * 因此同一种实体的服务端和客户端必须保持完全一致。
     */
    abstract fun getRenderID(): ResourceLocation

    /**
     * 直接把实体移动到指定坐标。
     *
     * 这个接口来自 `ServerControler`，语义上更接近“瞬移”而不是平滑同步。
     * 它会同步更新 `lastRenderPos`，方便客户端做插值或拖尾过渡。
     */
    override fun teleportTo(to: Vec3) {
        this.lastRenderPos = this.pos
        this.pos = to
    }

    /**
     * `teleportTo(Vec3)` 的坐标拆分重载。
     */
    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    /**
     * 旋转到目标相对位置。
     *
     * 当前 `RenderEntity` 基类未内置朝向状态，因此默认留空。
     * 如果你的实体有朝向、法线、切线或局部旋转语义，需要在子类自行实现。
     */
    override fun rotateToPoint(to: RelativeLocation) {

    }

    /**
     * 判断当前实体是否仍然有效。
     *
     * 管理器会以这个状态决定是否继续保留该实例。
     */
    override fun isValid(): Boolean {
        return !canceled
    }

    /**
     * 以给定弧度朝向目标方向。
     *
     * 基类不维护旋转参数，默认是空实现，供带有自定义姿态系统的子类覆盖。
     */
    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {

    }

    /**
     * 绕自身轴进行旋转。
     *
     * 同样由于基类不直接提供旋转存储，默认留给子类按自身数据结构决定行为。
     */
    override fun rotateAsAxis(radian: Double) {

    }

    /**
     * 标记实体已移除。
     *
     * 调用后不会立刻销毁对象，但后续 tick / 管理器清理阶段会把它移出运行时。
     */
    override fun remove() {
        this.canceled = true
    }

    /**
     * 返回当前控制器承载的实体值本身。
     */
    override fun getValue(): RenderEntity {
        return this
    }

    /**
     * 服务端生成入口。
     *
     * 只有 `ServerLevel` 上的实例才会真正进入 `ServerRenderEntityManager`。
     * 如果传入客户端世界，该调用会直接返回。
     */
    override fun spawn(world: Level, pos: Vec3) {
        if (world !is ServerLevel) return
        this.world = world
        this.pos = pos
        ServerRenderEntityManager.spawn(this)
    }
}
