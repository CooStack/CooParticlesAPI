package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.packet.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.SequencedParticleGroup
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import java.util.UUID


object ClientParticleGroupPacketHandler {
    fun receive(packet: PacketParticleGroupS2C, context: ClientContext) {
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


    private fun handleCreate(
        groupUUID: UUID,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        val pos = args[PacketParticleGroupS2C.PacketArgsType.POS.ofArgs]?.loadedValue!! as Vec3
        val world = Minecraft.getInstance().level!!
        val type = args[PacketParticleGroupS2C.PacketArgsType.GROUP_TYPE.ofArgs]!!.loadedValue as String
        val currentTick = args[PacketParticleGroupS2C.PacketArgsType.CURRENT_TICK.ofArgs]!!.loadedValue as Int
        val maxTick = args[PacketParticleGroupS2C.PacketArgsType.MAX_TICK.ofArgs]!!.loadedValue as Int
        val scale = args[PacketParticleGroupS2C.PacketArgsType.SCALE.ofArgs]!!.loadedValue as Double
        val groupClass = Class.forName(type)
        val builder = ClientParticleGroupManager.getBuilder(groupClass as Class<out ControlableParticleGroup>)!!
        val group = builder.createGroup(groupUUID, args)
        if (group is SequencedParticleGroup) {
            handleSequencedGroupArgs(group, args)
        }
        group.tick = currentTick
        group.maxTick = maxTick
        group.scale(scale)
        group.display(pos, world)
        ClientParticleGroupManager.addVisibleGroup(group)
    }

    private fun handleChange(
        groupUUID: UUID,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        val targetGroup = ClientParticleGroupManager.getControlGroup(groupUUID) ?: return
        val argKeys = args.keys
        if (PacketParticleGroupS2C.PacketArgsType.POS.ofArgs in argKeys) {
            val pos = args[PacketParticleGroupS2C.PacketArgsType.POS.ofArgs]!!.loadedValue!! as Vec3
            targetGroup.teleportGroupTo(pos)
        }
        if (PacketParticleGroupS2C.PacketArgsType.ROTATE_TO.ofArgs in argKeys) {
            val to = args[PacketParticleGroupS2C.PacketArgsType.ROTATE_TO.ofArgs]!!.loadedValue!! as Vec3
            targetGroup.rotateToPoint(RelativeLocation.of(to))
        }
        if (PacketParticleGroupS2C.PacketArgsType.ROTATE_AXIS.ofArgs in argKeys) {
            val angle = args[PacketParticleGroupS2C.PacketArgsType.ROTATE_AXIS.ofArgs]!!.loadedValue as Double
            targetGroup.rotateAsAxis(angle)
        }
        if (PacketParticleGroupS2C.PacketArgsType.AXIS.ofArgs in argKeys) {
            val axis = args[PacketParticleGroupS2C.PacketArgsType.AXIS.ofArgs]!!.loadedValue!! as Vec3
            targetGroup.axis = RelativeLocation.of(axis)
        }
        if (PacketParticleGroupS2C.PacketArgsType.CURRENT_TICK.ofArgs in argKeys) {
            val tick = args[PacketParticleGroupS2C.PacketArgsType.CURRENT_TICK.ofArgs]!!.loadedValue!! as Int
            targetGroup.tick = tick
        }
        if (PacketParticleGroupS2C.PacketArgsType.MAX_TICK.ofArgs in argKeys) {
            val tick = args[PacketParticleGroupS2C.PacketArgsType.MAX_TICK.ofArgs]!!.loadedValue!! as Int
            targetGroup.maxTick = tick
        }
        if (PacketParticleGroupS2C.PacketArgsType.INVOKE.ofArgs in argKeys) {
            val targetMethodName =
                args[PacketParticleGroupS2C.PacketArgsType.INVOKE.ofArgs]!!.loadedValue!! as String
            targetGroup::class.java.getDeclaredMethod(targetMethodName).apply {
                isAccessible = true
            }.invoke(targetGroup)
        }

        if (PacketParticleGroupS2C.PacketArgsType.SCALE.ofArgs in argKeys) {
            targetGroup.scale(
                args[PacketParticleGroupS2C.PacketArgsType.SCALE.ofArgs]!!.loadedValue!! as Double
            )
        }
        if (targetGroup is SequencedParticleGroup) {
            handleSequencedGroupArgs(targetGroup, args)
        }

        val builder = ClientParticleGroupManager.getBuilder(targetGroup::class.java) ?: return
        builder.changeGroup(targetGroup, args)
    }

    private fun handleRemove(groupUUID: UUID) {
        ClientParticleGroupManager.removeVisible(groupUUID)
    }

    private fun handleSequencedGroupArgs(
        group: SequencedParticleGroup,
        args: Map<String, ParticleControlerDataBuffer<*>>
    ) {
        if (args.containsKey("toggle")) {
            group.toggle(args["toggle"]!!.loadedValue as Int)
        }
        if (args.containsKey("addCount")) {
            group.addMultiple(args["addCount"]!!.loadedValue as Int)
        }
        if (args.containsKey("removeCount")) {
            group.removeMultiple(args["removeCount"]!!.loadedValue as Int)
        }

        if (args.containsKey("toggle_status")) {
            group.toggleStatus(args["toggle_status"]!!.loadedValue as LongArray)
        }
    }
}