package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
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
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 为了方便设置
 */
abstract class RenderEntity(var world: Level?, var pos: Vec3 = Vec3.ZERO) : ServerControler<RenderEntity>,
    Tickable<RenderEntity> {
    /**
     * 渲染可视距离
     * 给服务器设置则是设置进行传输生成的最小范围
     * 给客户端设置这是设置渲染的范围
     */
    var renderRange = 256.0
    val client: Boolean
        get() = world?.isClientSide ?: false

    var init = false
    var alwaysToggle = false
    private var syncOnce = false

    companion object {
        fun decodeBase(buf: FriendlyByteBuf, instance: RenderEntity) {
            instance.uuid = buf.readUUID()
            instance.pos = buf.readVec3()
            instance.canceled = buf.readBoolean()
            instance.age = buf.readInt()
            instance.dirty = false
        }

        fun encodeBase(buf: FriendlyByteBuf, entity: RenderEntity) {
            buf.writeUUID(entity.uuid)
            buf.writeVec3(entity.pos)
            buf.writeBoolean(entity.canceled)
            buf.writeInt(entity.age)
        }

        /**
         * Helper to build a codec that always includes base fields.
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

    var lastRenderPos = pos
        private set
    var age = 0
    var uuid: UUID = UUID.randomUUID()
    var dirty = false
    var canceled = false
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
     * Client-only update hook. Override for visual-only logic.
     */
    open fun clientTick() {
    }

    /**
     * Server-only update hook. Override for sync or gameplay logic.
     */
    open fun serverTick() {
    }

    fun getTogglePacket(): PacketRenderEntityS2C? {
        return getTogglePacket(false)
    }

    fun getTogglePacket(force: Boolean): PacketRenderEntityS2C? {
        return getPacket(PacketRenderEntityS2C.Method.TOGGLE, force)
    }

    fun getPacket(method: PacketRenderEntityS2C.Method): PacketRenderEntityS2C? {
        return getPacket(method, false)
    }

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
     * One-shot sync request. Clears after a successful toggle send.
     */
    fun requestSync() {
        dirty = true
        syncOnce = true
    }

    /**
     * Manually clear dirty state.
     */
    fun clearDirty() {
        dirty = false
        syncOnce = false
    }

    /**
     * Server hook to clear a one-shot sync.
     */
    internal fun onSynced() {
        if (syncOnce) {
            dirty = false
            syncOnce = false
        }
    }

    /**
     * Override to control when the server should sync this entity.
     */
    open fun shouldSync(): Boolean {
        return alwaysToggle || dirty
    }

    /**
     * Delegate that marks dirty when the value changes.
     * Set syncOnce to true for one-shot syncing.
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

    open fun loadProfileFromEntity(another: RenderEntity) {
        this.age = another.age
        this.canceled = another.canceled
        this.pos = another.pos
        this.uuid = another.uuid
        this.world = another.world
    }

    /**
     * 此初始化只会在客户端调用
     */
    internal fun init() {
        if (init) {
            return
        }
        init = true
        initialize()
    }

    abstract fun initialize()

    /**
     * render entity 用于服务器通讯的类 同时也是更新用的
     * 自定义的参数也一定要写入codec
     */
    abstract fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity>

    /**
     * 获取标识符 (用于在客户端注册)
     */
    abstract fun getRenderID(): ResourceLocation

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

    override fun spawn(world: Level, pos: Vec3) {
        if (world !is ServerLevel) return
        this.world = world
        this.pos = pos
        ServerRenderEntityManager.spawn(this)
    }

    fun renderOnWorld(
        matrices: Matrix4fStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    ) {
        lastRenderPos = pos
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        render(matrices, viewMatrix, projMatrix, tickDelta)
    }

    abstract fun render(
        matrices: Matrix4fStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    )
}
