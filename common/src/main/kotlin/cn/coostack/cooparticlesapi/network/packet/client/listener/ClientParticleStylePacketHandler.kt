package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import java.util.UUID

object ClientParticleStylePacketHandler {
    fun receive(packet: PacketParticleStyleS2C, context: ClientContext) {
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

    /** 处理客户端同步修改 */
    private fun handleChange(
        uuid: UUID,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        // 确保他在 clientView
        val style = ParticleStyleManager.clientViewStyles[uuid] ?: return
        if (args.containsKey("pos")) {
            val pos = args["pos"]!!.loadedValue as Vec3
            style.teleportTo(pos)
        }
        if (args.containsKey("teleport")) {
            val pos = args["teleport"]!!.loadedValue as Vec3
            style.teleportTo(pos)
        }
        if (args.containsKey("rotate_to") && args.containsKey("rotate_angle")) {
            style.rotateToWithAngle(
                args["rotate_to"]!!.loadedValue as RelativeLocation,
                args["rotate_angle"]!!.loadedValue as Double
            )
        } else {
            if (args.containsKey("rotate_angle")) {
                style.rotateAsAxis(args["rotate_angle"]!!.loadedValue as Double)
            }
            if (args.containsKey("rotate_to")) {
                style.rotateToPoint(args["rotate_to"]!!.loadedValue as RelativeLocation)
            }
        }
        if (args.containsKey("axis")) {
            style.axis = RelativeLocation.of(args["axis"]!!.loadedValue as Vec3)
        }
        if (args.containsKey("scale")) {
            style.scale = args["scale"]!!.loadedValue as Double
        }
        if (args.containsKey("lastUpdatedGameTime")) {
            style.lastUpdatedGameTime = args["lastUpdatedGameTime"]!!.loadedValue as Long
        }
        if (args.containsKey("displayedTime")) {
            style.displayedTime = args["displayedTime"]!!.loadedValue as Long
        }
        style.readPacketArgs(args)
    }

    /** 处理客户端同步创建 */
    private fun handleCreate(
        uuid: UUID,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        val clazz = args["style_type"]!!.loadedValue as String
        val builderType = Class.forName(clazz)
        val builder = ParticleStyleManager.getBuilder(builderType as Class<out ParticleGroupStyle>) ?: return
        val style = builder.createStyle(uuid, args)
        val pos = args["pos"]!!.loadedValue as Vec3
        style.rotate = args["rotate"]!!.loadedValue as Double
        style.axis = RelativeLocation.of(args["axis"]!!.loadedValue as Vec3)
        style.scale = args["scale"]!!.loadedValue as Double
        style.lastUpdatedGameTime = args["lastUpdatedGameTime"]!!.loadedValue as Long
        style.displayedTime = args["displayedTime"]!!.loadedValue as Long
        val world = Minecraft.getInstance().level
        style.readPacketArgs(args)
        ParticleStyleManager.spawnStyle(world!!, pos, style)
    }

}