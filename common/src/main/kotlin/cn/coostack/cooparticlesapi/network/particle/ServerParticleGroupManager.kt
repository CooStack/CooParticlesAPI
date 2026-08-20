package cn.coostack.cooparticlesapi.network.particle

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleGroupS2C.PacketArgsType
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * 控制所有的ServerParticleGroup
 */
@Deprecated("使用ParticleComposition")

object ServerParticleGroupManager {
    private val serverGroups = ConcurrentHashMap<UUID, ServerParticleGroup>()

    /**
     * key playerUUID
     * value player can see
     */
    internal val visible = ConcurrentHashMap<UUID, MutableSet<ServerParticleGroup>>()

    fun addParticleGroup(
        group: ServerParticleGroup,
        pos: Vec3,
        world: ServerLevel
    ) {
        serverGroups[group.uuid] = group
        group.initServerGroup(pos, world)
        world.players().filter { it.position().distanceTo(pos) <= group.visibleRange }
            .forEach { receiver ->
                addGroupPlayerView(receiver, group)
            }
        group.onGroupDisplay(pos, world)
    }

    fun getParticleGroup(group: UUID): ServerParticleGroup? {
        return serverGroups[group]
    }

    fun getGroups(): Map<UUID, ServerParticleGroup> {
        return Collections.unmodifiableMap(serverGroups)
    }

    /** 返回服务端当前旧 ParticleGroup 实例数。 */
    fun groupCount(): Int = serverGroups.size

    fun clearServer() {
        serverGroups.onEach { it.value.canceled = true }.clear()
        visible.clear()
    }

    fun upgrade() {
        upgradeGroups()
        clearOfflineVisible()
    }

    fun filterVisiblePlayer(group: ServerParticleGroup): Set<UUID> {
        val set = HashSet<UUID>()
        visible.forEach {
            if (group in it.value) {
                set.add(it.key)
            }
        }
        return set
    }

    private fun upgradeGroups() {
        val iterator = serverGroups.iterator()
        while (iterator.hasNext()) {
            val value = iterator.next().value
            // 更新状态
            if (value.canceled || !value.valid) {
                val server = value.world?.server ?: continue
                visible.forEach { (t, u) ->
                    val player = server.playerList.getPlayer(t) ?: return@forEach
                    if (value in u) {
                        removeGroupPlayerView(player, value)
                        u.remove(value)
                    }
                }
                iterator.remove()
                continue
            }
            // 更新可见性
            value.world!!.server!!.playerList.players.forEach { p ->
                val visibleSet = visible.getOrPut(p.uuid) { HashSet() }
                if (p.level().dimension() != value.world?.dimension()) {
                    // 世界转换
                    if (value in visibleSet) {
                        removeGroupPlayerView(p, value)
                    }
                    visibleSet!!.remove(value)
                    return@forEach
                }
                if (p.isDeadOrDying) {
                    if (value in visibleSet) {
                        removeGroupPlayerView(p, value)
                        visibleSet!!.remove(value)
                    }
                    return@forEach
                }
                if (value.pos.distanceTo(p.position()) <= value.visibleRange) {
                    // 防止重复添加(发包)
                    if (value in visibleSet) {
                        return@forEach
                    }
                    addGroupPlayerView(p, value)
                    // 同步数据包
                    togglePacketView(p, value)

                } else {
                    if (value in visibleSet) {
                        removeGroupPlayerView(p, value)
                    }
                    // 超过范围
                    visibleSet.remove(value)
                }
            }
            value.tick()
        }
    }

    private fun clearOfflineVisible() {
        val server = CooParticlesAPI.serverOrNull ?: return
        // 清空所有离线玩家
        val visibleIterator = visible.iterator()
        while (visibleIterator.hasNext()) {
            val entry = visibleIterator.next()
            val player = server.playerList.getPlayer(entry.key)
            if (player == null || player.hasDisconnected()) {
                visibleIterator.remove()
            }
        }
    }

    private fun togglePacketView(target: ServerPlayer, group: ServerParticleGroup) {
        val packet = PacketParticleGroupS2C(
            group.uuid, ControlType.CHANGE,
            mutableMapOf(
                PacketArgsType.POS.ofArgs to ParticleControlerDataBuffers.vec3d(group.pos),
                PacketArgsType.AXIS.ofArgs to ParticleControlerDataBuffers.vec3d(group.axis.toVector()),
                PacketArgsType.CURRENT_TICK.ofArgs to ParticleControlerDataBuffers.int(group.clientTick),
                PacketArgsType.MAX_TICK.ofArgs to ParticleControlerDataBuffers.int(group.clientMaxTick),
            )
        )

        CooParticlesServices.SERVER_NETWORK.send(packet, target)
    }

    /**
     * 在该玩家的视角中移除这些粒子
     * @param target
     * @param targetGroup
     */
    private fun removeGroupPlayerView(target: ServerPlayer, targetGroup: ServerParticleGroup) {
        // 发包给玩家
        val packet = PacketParticleGroupS2C(
            targetGroup.uuid,
            ControlType.REMOVE, mapOf()
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, target)
    }

    /*
    * @param target
    * @param targetGroup
    */
    private fun addGroupPlayerView(
        target: ServerPlayer,
        targetGroup: ServerParticleGroup,
    ) {
        // 修复一个包发送给一个玩家两次
        val visibleSet = visible.getOrPut(target.uuid) { HashSet() }
        visibleSet.add(targetGroup)
        // 发包给玩家
        val packet = PacketParticleGroupS2C(
            targetGroup.uuid,
            ControlType.CREATE,
            mutableMapOf(
                PacketArgsType.POS.ofArgs to ParticleControlerDataBuffers.vec3d(targetGroup.pos),
                PacketArgsType.GROUP_TYPE.ofArgs to ParticleControlerDataBuffers.string(targetGroup.getClientType()!!.name),
                PacketArgsType.CURRENT_TICK.ofArgs to ParticleControlerDataBuffers.int(targetGroup.clientTick),
                PacketArgsType.MAX_TICK.ofArgs to ParticleControlerDataBuffers.int(targetGroup.clientMaxTick),
                PacketArgsType.SCALE.ofArgs to ParticleControlerDataBuffers.double(targetGroup.scale)
            ).apply {
                putAll(targetGroup.otherPacketArgs())
            }
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, target)
    }

}
