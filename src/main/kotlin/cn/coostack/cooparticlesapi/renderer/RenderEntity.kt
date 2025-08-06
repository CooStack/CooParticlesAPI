package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import io.netty.buffer.Unpooled
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.joml.Matrix4f
import java.util.UUID

/**
 * 为了方便设置
 */
abstract class RenderEntity(var world: World?, var pos: Vec3d = Vec3d.ZERO) {
    /**
     * 渲染可视距离
     * 给服务器设置则是设置进行传输生成的最小范围
     * 给客户端设置这是设置渲染的范围
     */
    var renderRange = 256.0
    val client: Boolean
        get() = world?.isClient ?: false

    var init = false
    var alwaysToggle = false

    companion object {
        fun decodeBase(buf: PacketByteBuf, instance: RenderEntity) {
            instance.uuid = buf.readUuid()
            instance.pos = buf.readVec3d()
            instance.canceled = buf.readBoolean()
            instance.age = buf.readInt()
            instance.dirty = false
        }

        fun encodeBase(buf: PacketByteBuf, entity: RenderEntity) {
            buf.writeUuid(entity.uuid)
            buf.writeVec3d(entity.pos)
            buf.writeBoolean(entity.canceled)
            buf.writeInt(entity.age)
        }
    }

    var lastRenderPos = pos
        private set
    var age = 0
    var uuid: UUID = UUID.randomUUID()
    var dirty = false
    var canceled = false
    open fun tick() {
        if (canceled) return
        age++
    }

    fun getTogglePacket(): PacketRenderEntityS2C? {
        return getPacket(PacketRenderEntityS2C.Method.TOGGLE)
    }

    fun getPacket(method: PacketRenderEntityS2C.Method): PacketRenderEntityS2C? {
        // 判断数据有没有发生改变
        if (!dirty) {
            return null
        }
        val buf = PacketByteBuf(Unpooled.buffer())
        getCodec().encode(buf, this)
        // 发包
        val packet = PacketRenderEntityS2C(uuid, buf, getRenderID(), method)
        return packet
    }

    fun setPosition(pos: Vec3d) {
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
    abstract fun getCodec(): PacketCodec<PacketByteBuf, RenderEntity>

    /**
     * 获取标识符 (用于在客户端注册)
     */
    abstract fun getRenderID(): Identifier

    abstract fun release()

    @Environment(EnvType.CLIENT)
    fun renderOnWorld(
        matrices: MatrixStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    ) {
        lastRenderPos = pos
        render(matrices, viewMatrix, projMatrix, tickDelta)
    }

    @Environment(EnvType.CLIENT)
    abstract fun render(
        matrices: MatrixStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    )
}