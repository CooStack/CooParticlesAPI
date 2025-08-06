package cn.coostack.cooparticlesapi.renderer.server

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
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
            val player = CooParticleAPI.server.playerManager.getPlayer(entity.key)
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
            if (entity.alwaysToggle || entity.dirty) {
                toggle(entity)
            }
        }
        clearEmptyData()
    }


    fun updateVisible(entity: RenderEntity) {
        CooParticleAPI.server.playerManager.playerList.forEach {
            // 世界转换
            val actualCanView = playerCanView(it.uuid, entity)
            if (it.world != entity.world) {
                if (actualCanView) {
                    removeVisible(it, entity)
                }
                return@forEach
            }
            if (it.isDead && actualCanView) {
                removeVisible(it, entity)
                return@forEach
            }
            val pos = it.pos
            val entityPos = entity.pos
            val dis = pos.distanceTo(entityPos)
            val checkCurrentCanView = dis <= entity.renderRange
            if (!checkCurrentCanView && actualCanView) {
                removeVisible(it, entity)
                return@forEach
            }
            if (!actualCanView) {
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
        val packet = entity.getTogglePacket() ?: return
        CooParticleAPI.server.playerManager.playerList.filter { playerCanView(entity.uuid, entity) }.forEach {
            ServerPlayNetworking.send(it, packet)
        }
    }

    fun addVisible(who: ServerPlayerEntity, entity: RenderEntity) {
        initPlayer(who.uuid)
        getPlayerViewable(who.uuid).add(entity)
        // 发包让玩家可见
        val packet = entity.getPacket(PacketRenderEntityS2C.Method.CREATE) ?: return
        ServerPlayNetworking.send(who, packet)
    }

    fun removeVisible(who: ServerPlayerEntity, entity: RenderEntity) {
        initPlayer(who.uuid)
        getPlayerViewable(who.uuid).remove(entity)
        val packet = entity.getPacket(PacketRenderEntityS2C.Method.REMOVE) ?: return
        ServerPlayNetworking.send(who, packet)
    }

    fun addViewIfVisible(entity: RenderEntity) {
        val world = entity.world ?: return
        world.players.forEach {
            addVisible(it as ServerPlayerEntity, entity)
        }
    }

    fun removeAllView(entity: RenderEntity) {
        val world = entity.world ?: return
        playerViewable.entries.forEach {
            val player = world.getPlayerByUuid(it.key) as? ServerPlayerEntity ?: return@forEach
            if (it.value.contains(entity)) {
                removeVisible(player, entity)
            }
        }
    }


}