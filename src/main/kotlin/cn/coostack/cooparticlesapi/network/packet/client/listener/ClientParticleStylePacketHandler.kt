package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d
import org.joml.Vector3d
import java.util.UUID

@Environment(EnvType.CLIENT)
object ClientParticleStylePacketHandler : ClientPlayNetworking.PlayPayloadHandler<PacketParticleStyleS2C> {
    override fun receive(packet: PacketParticleStyleS2C, context: ClientPlayNetworking.Context) {
        // Group UUID
        val uuid = packet.uuid
        // 修改类型
        val type = packet.type
        when (type) {
            ControlType.CREATE -> handleCreate(uuid, packet.args)
            ControlType.CHANGE -> handleChange(uuid, packet.args)
            ControlType.REMOVE -> handleRemove(uuid)
        }
    }

    private fun handleRemove(uuid: UUID) {
        ParticleStyleManager.clientViewStyles[uuid]?.remove()
    }

    /**
     * 处理客户端同步修改
     */
    private fun handleChange(
        uuid: UUID,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        // 确保他在 clientView
        val style = ParticleStyleManager.clientViewStyles[uuid] ?: return
        if (args.containsKey("pos")) {
            val pos = args["pos"]!!.loadedValue as Vec3d
            style.teleportTo(pos)
        }
        if (args.containsKey("teleport")) {
            val pos = args["teleport"]!!.loadedValue as Vec3d
            style.teleportTo(pos)
        }
        if (args.containsKey("rotate_to") && args.containsKey("rotate_angle")) {
            style.rotateToWithAngle(
                args["rotate_to"]!!.loadedValue as RelativeLocation,
                args["rotate_angle"]!!.loadedValue as Double
            )
        } else {
            if (args.containsKey("rotate_angle")) {
                style.rotateParticlesAsAxis(args["rotate_angle"]!!.loadedValue as Double)
            }
            if (args.containsKey("rotate_to")) {
                style.rotateParticlesToPoint(args["rotate_to"]!!.loadedValue as RelativeLocation)
            }
        }
        if (args.containsKey("axis")) {
            style.axis = RelativeLocation.of(args["axis"]!!.loadedValue as Vec3d)
        }
        if (args.containsKey("scale")) {
            style.scale = args["scale"]!!.loadedValue as Double
        }
        style.readPacketArgs(args)
    }

    /**
     * 处理客户端同步创建
     */
    private fun handleCreate(
        uuid: UUID,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        val clazz = args["style_type"]!!.loadedValue as String
        val builderType = Class.forName(clazz)
        val builder = ParticleStyleManager.getBuilder(builderType as Class<out ParticleGroupStyle>) ?: return
        val style = builder.createStyle(uuid, args)
        val pos = args["pos"]!!.loadedValue as Vec3d
        style.rotate = args["rotate"]!!.loadedValue as Double
        style.axis = RelativeLocation.of(args["axis"]!!.loadedValue as Vec3d)
        style.scale = args["scale"]!!.loadedValue as Double
        val world = MinecraftClient.getInstance().world
        style.readPacketArgs(args)
        ParticleStyleManager.spawnStyle(world!!, pos, style)
    }

}