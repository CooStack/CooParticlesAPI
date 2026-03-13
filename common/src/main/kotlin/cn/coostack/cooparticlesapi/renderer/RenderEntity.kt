package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.mojang.blaze3d.systems.RenderSystem
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.lwjgl.opengl.GL33.GL_ONE
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * `RenderEntity` 是一条“服务端权威状态 + 客户端镜像渲染”的基础 API。
 *
 * 它负责三件事：
 * 1. 在服务端维护实体状态，并通过 `CREATE / TOGGLE / REMOVE` 包同步给客户端。
 * 2. 在客户端保存对应镜像，进入 `WORLD` 或 `POST_PROCESS` 渲染管线。
 * 3. 提供 `tracked(...)`、`createCodec(...)`、`loadProfileFromEntity(...)` 等通用同步工具。
 *
 * 使用子类时通常需要完成以下几个点：
 * - 用 `createCodec(...)` 为自定义字段建立编解码。
 * - 覆盖 `getRenderID()`，并在客户端完成注册。
 * - 实现 `initialize()` / `render()` / `release()`。
 * - 如果实体有额外同步字段，需要覆盖 `loadProfileFromEntity(...)` 把这些字段回写到客户端镜像。
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
     * 客户端初始化标记。
     * `ClientRenderEntityManager.add(...)` 会调用 `init()`，确保 `initialize()` 只执行一次。
     */
    var init = false

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
        private set

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

    /**
     * 统一 tick 入口。
     *
     * - 服务端走 `serverTick()`
     * - 客户端走 `clientTick()`
     */
    override fun tick() {
        if (canceled) return
        age++
        if (client) {
            clientTick()
        } else {
            serverTick()
        }
    }


    final override fun addPreTickAction(action: RenderEntity.() -> Unit): Tickable<RenderEntity> {
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
     * 如果想要通过 togglePacket 同步到客户端状态
     * 修改entity的属性必须要执行该方法
     * (调用setPos无需)
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
     * 服务端在一次性同步成功后调用的内部钩子。
     */
    internal fun onSynced() {
        if (syncOnce) {
            dirty = false
            syncOnce = false
        }
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

            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return value
            }

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
     * @param delta tickDelta 在render方法中提供 用作着色器的 time参数
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
     * 客户端初始化入口。
     *
     * `ClientRenderEntityManager.add(...)` 会调用此方法，
     * 并确保 `initialize()` 对每个客户端镜像只执行一次。
     */
    internal fun init() {
        if (init) {
            return
        }
        init = true
        initialize()
    }

    /**
     * 客户端首次创建镜像时调用。
     *
     * 适合初始化：
     * - shader program
     * - vertex buffer
     * - texture / framebuffer 句柄
     */
    abstract fun initialize()

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
     * 获取标识符 (用于在客户端注册)
     */
    abstract fun getRenderID(): ResourceLocation

    /**
     * 返回当前实体所属的渲染 pass。
     *
     * 默认是 `WORLD`。
     * 如果需要帧尾 post-process、scene copy、persistent bloom 等效果，
     * 通常要覆盖成 `POST_PROCESS`。
     */
    open fun getRenderPass(): RenderEntityRenderPass {
        return RenderEntityRenderPass.WORLD
    }

    /**
     * 控制当前实体写入共享 pipe 输入目标时的混合模式。
     *
     * 默认使用 `REPLACE`，保持旧行为。
     * 如果多个实体共享同一个 glow / bloom pipe，并且希望输入 mask 可以叠加，
     * 则应覆盖为 `ADDITIVE`，同时避免在 `render(...)` 内再次强制关闭 blend。
     */
    open fun getInputBlendMode(): RenderEntityInputBlendMode {
        return RenderEntityInputBlendMode.REPLACE
    }

    /**
     * 客户端镜像被移除时的资源释放钩子。
     */
    abstract fun release()

    override fun teleportTo(to: Vec3) {
        this.lastRenderPos = this.pos
        this.pos = to
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    override fun rotateToPoint(to: RelativeLocation) {

    }

    override fun isValid(): Boolean {
        return !canceled
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {

    }

    override fun rotateAsAxis(radian: Double) {

    }

    override fun remove() {
        this.canceled = true
    }

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

    /**
     * 管理器使用的统一绘制入口。
     *
     * 它会记录 `lastRenderPos`，并设置基础渲染状态，
     * 然后再调用子类真正实现的 `render(...)`。
     */
    fun renderOnWorld(
        matrices: Matrix4fStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    ) {
        lastRenderPos = pos
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        applyInputBlendMode()
        runCatching {
            render(matrices, viewMatrix, projMatrix, tickDelta)
        }
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.disableCull()
    }

    private fun applyInputBlendMode() {
        when (getInputBlendMode()) {
            RenderEntityInputBlendMode.REPLACE -> {
                RenderSystem.defaultBlendFunc()
                RenderSystem.disableBlend()
            }

            RenderEntityInputBlendMode.ADDITIVE -> {
                RenderSystem.enableBlend()
                RenderSystem.blendFunc(GL_ONE, GL_ONE)
            }
        }
    }

    /**
     * 子类的实际绘制逻辑。
     *
     * 这里应只关注“如何把自己画出来”，而不要重复处理：
     * - 客户端注册
     * - 网络同步
     * - 管理器分类
     * - 帧生命周期调度
     */
    abstract fun render(
        matrices: Matrix4fStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    )
}
