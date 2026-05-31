package cn.coostack.cooparticlesapi.renderer.server

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object ServerRenderEntityManager {
    val entities = HashMap<UUID, RenderEntity>()

    val playerViewable = HashMap<UUID, HashSet<RenderEntity>>()


    fun spawn(entity: RenderEntity) {
        entities[entity.uuid] = entity
    }

    fun getPlayerViewable(player: UUID): HashSet<RenderEntity> {
        return playerViewable[player] ?: HashSet()
    }

    fun initPlayer(player: UUID) {
        if (playerViewable.containsKey(player)) {
            return
        }
        playerViewable[player] = HashSet()
    }

    fun clearEmptyData() {
        val iterator = playerViewable.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            val player = CooParticlesAPI.server.playerList.getPlayer(entity.key)
            if (entity.value.isEmpty() || player == null) iterator.remove()
        }
    }


    fun tick() {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next().value
            updateVisible(entity)
//            toggle(entity)
            entity.tick()
            if (entity.canceled) {
                removeAllView(entity)
                iterator.remove()
                continue
            }
            if (entity.shouldSync()) {
                toggle(entity)
            }
        }
        clearEmptyData()
    }


    fun updateVisible(entity: RenderEntity) {
        CooParticlesAPI.server.playerList.players.forEach {
            // 世界转换
            val actualCanView = playerCanView(it.uuid, entity)
            if (it.level().dimension() != entity.world?.dimension()) {
                if (actualCanView) {
                    removeVisible(it, entity)
                }
                return@forEach
            }
            if (it.isDeadOrDying && actualCanView) {
                removeVisible(it, entity)
                return@forEach
            }
            val pos = it.position()
            val entityPos = entity.pos
            val dis = pos.distanceTo(entityPos)
            val checkCurrentCanView = dis <= entity.renderRange
            if (!checkCurrentCanView && actualCanView) {
                removeVisible(it, entity)
                return@forEach
            }
            if (!actualCanView && checkCurrentCanView) {
                addVisible(it, entity)
            }
        }
    }


    fun playerCanView(player: UUID, entity: RenderEntity): Boolean {
        initPlayer(player)
        val views = getPlayerViewable(player)
        return views.contains(entity)
    }

    fun toggle(entity: RenderEntity) {
        val packet = entity.getTogglePacket(entity.alwaysToggle) ?: return
        val targets = CooParticlesAPI.server.playerList.players.filter { playerCanView(it.uuid, entity) }
        if (targets.isEmpty()) {
            entity.onSynced()
            return
        }
        targets.forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
        entity.onSynced()
    }

    fun addVisible(who: ServerPlayer, entity: RenderEntity) {
        initPlayer(who.uuid)
        getPlayerViewable(who.uuid).add(entity)
        // 发包让玩家可见
        val packet = entity.getPacket(PacketRenderEntityS2C.Method.CREATE) ?: return
        CooParticlesServices.SERVER_NETWORK.send(packet, who)
    }

    fun removeVisible(who: ServerPlayer, entity: RenderEntity) {
        initPlayer(who.uuid)
        getPlayerViewable(who.uuid).remove(entity)
        val packet = entity.getPacket(PacketRenderEntityS2C.Method.REMOVE) ?: return
        CooParticlesServices.SERVER_NETWORK.send(packet, who)
    }

    fun addViewIfVisible(entity: RenderEntity) {
        val world = entity.world ?: return
        world.players().forEach {
            addVisible(it as ServerPlayer, entity)
        }
    }

    fun removeAllView(entity: RenderEntity) {
        val world = entity.world ?: return
        playerViewable.entries.forEach {
            val player = world.getPlayerByUUID(it.key) as? ServerPlayer ?: return@forEach
            if (it.value.contains(entity)) {
                removeVisible(player, entity)
            }
        }
    }

    fun clear() {
        entities.clear()
        playerViewable.clear()
    }

}
