package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class RenderEntity(var world: Level?, var pos: Vec3 = Vec3.ZERO) : ServerControler<RenderEntity>,
    Tickable<RenderEntity> {
    var renderRange = 256.0

    val client: Boolean
        get() = world?.isClientSide ?: false

    var alwaysToggle = false
    private var syncOnce = false

    companion object {
        fun decodeBase(buf: PacketByteBuf, instance: RenderEntity) {
            instance.uuid = buf.readUUID()
            instance.pos = buf.readVec3()
            instance.canceled = buf.readBoolean()
            instance.age = buf.readInt()
            instance.dirty = false
        }

        fun encodeBase(buf: PacketByteBuf, entity: RenderEntity) {
            buf.writeUUID(entity.uuid)
            buf.writeVec3(entity.pos)
            buf.writeBoolean(entity.canceled)
            buf.writeInt(entity.age)
        }

        fun <T : RenderEntity> createCodec(
            factory: () -> T,
            encodeExtra: (PacketByteBuf, T) -> Unit = { _, _ -> },
            decodeExtra: (PacketByteBuf, T) -> Unit = { _, _ -> }
        ): ForgeStreamCodec<PacketByteBuf, RenderEntity> {
            return ForgeStreamCodec.of(
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
        internal set

    var age = 0

    var uuid: UUID = UUID.randomUUID()

    var dirty = false

    var canceled = false
    private val preTickActions = ArrayList<RenderEntity.() -> Unit>()
    private val postTickActions = ArrayList<RenderEntity.() -> Unit>()

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

    open fun clientTick() {
    }

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
        if (!force && !dirty && method == PacketRenderEntityS2C.Method.TOGGLE) {
            return null
        }
        val buf = PacketByteBuf(Unpooled.buffer())
        getCodec().encode(buf, this)
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        val packet = PacketRenderEntityS2C(uuid, bytes, getRenderID(), method)
        return packet
    }

    fun setPosition(pos: Vec3) {
        this.pos = pos
        markDirty()
    }

    fun markDirty() {
        dirty = true
    }

    fun requestSync() {
        dirty = true
        syncOnce = true
    }

    fun clearDirty() {
        dirty = false
        syncOnce = false
    }

    internal fun onSynced() {
        dirty = false
        syncOnce = false
    }

    open fun shouldSync(): Boolean {
        return alwaysToggle || dirty
    }

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

    abstract fun getCodec(): ForgeStreamCodec<PacketByteBuf, RenderEntity>

    abstract fun getRenderID(): ResourceLocation

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
}
