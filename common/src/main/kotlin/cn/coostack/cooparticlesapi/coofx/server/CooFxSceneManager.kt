package cn.coostack.cooparticlesapi.coofx.server

import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * CooFX 长期服务端实例管理器。
 *
 * 它复用 RenderEntity 的服务端可见性边沿同步：服务端保存实例和权威字段，客户端只保存镜像与
 * CooFX 播放句柄。所有方法都应在 Minecraft server thread 调用，不会创建客户端或 GPU 对象。
 */
object CooFxSceneManager {
    private val scenes = LinkedHashMap<UUID, CooFxSceneRenderEntity>()
    private val sceneOwners = HashMap<String, UUID>()

    /** 创建并立即加入同维度可见性管理。重复 owner 或 sceneId 会先停止旧实例。 */
    fun spawn(
        level: ServerLevel,
        spec: CooFxSceneSpec,
        sceneId: UUID = UUID.randomUUID(),
        ownerKey: String? = null,
    ): CooFxSceneHandle {
        ownerKey?.let { owner ->
            sceneOwners[owner]?.let(::stop)
        }
        stop(sceneId)
        val entity = CooFxSceneRenderEntity(
            level = level,
            position = Vec3(spec.transform.x, spec.transform.y, spec.transform.z),
            spec = spec,
        ).also { created ->
            created.uuid = sceneId
            scenes[sceneId] = created
            ownerKey?.let { owner -> sceneOwners[owner] = sceneId }
        }
        ServerRenderEntityManager.spawn(entity)
        // 首次创建也使用维度和距离判断，避免先把场景发给远处玩家再等待下一 tick 移除。
        ServerRenderEntityManager.updateVisible(entity)
        return Handle(sceneId)
    }

    /** 对服务端权威实例应用增量 patch，并由 RenderEntity dirty 状态触发同步。 */
    fun update(sceneId: UUID, patch: CooFxScenePatch): Boolean {
        return scenes[sceneId]?.applyPatch(patch) == true
    }

    /** 用完整服务端快照替换资源、模式和全部参数。 */
    fun replace(sceneId: UUID, spec: CooFxSceneSpec): Boolean {
        val entity = scenes[sceneId] ?: return false
        entity.setSceneSpec(spec)
        return true
    }

    /** 停止实例并向当前可见客户端发送 REMOVE。 */
    fun stop(sceneId: UUID): Boolean {
        val entity = scenes.remove(sceneId) ?: return false
        sceneOwners.entries.removeIf { entry -> entry.value == sceneId }
        ServerRenderEntityManager.removeAllView(entity)
        entity.remove()
        return true
    }

    /** 让指定玩家立即补收当前仍在可见范围内的场景。 */
    fun syncTo(player: ServerPlayer) {
        scenes.values.forEach { entity ->
            val level = entity.world as? ServerLevel ?: return@forEach
            if (level.dimension() != player.level().dimension()) return@forEach
            if (player.position().distanceTo(entity.pos) > entity.renderRange) return@forEach
            if (!ServerRenderEntityManager.playerCanView(player.uuid, entity)) {
                ServerRenderEntityManager.addVisible(player, entity)
            }
        }
    }

    /** 清理当前服务器全部 CooFX 场景和其可见性索引。 */
    fun clearServer() {
        scenes.values.forEach { entity -> ServerRenderEntityManager.removeAllView(entity) }
        scenes.clear()
        sceneOwners.clear()
    }

    /** @return 当前服务端是否仍持有指定场景。 */
    fun isActive(sceneId: UUID): Boolean = scenes[sceneId]?.isValid() == true

    /** @return 当前服务端场景数量，供调试和测试使用。 */
    fun size(): Int = scenes.size

    private class Handle(
        override val sceneId: UUID,
    ) : CooFxSceneHandle {
        override val isActive: Boolean
            get() = CooFxSceneManager.isActive(sceneId)

        override fun update(patch: CooFxScenePatch): Boolean = CooFxSceneManager.update(sceneId, patch)

        override fun replace(spec: CooFxSceneSpec): Boolean = CooFxSceneManager.replace(sceneId, spec)

        override fun stop(): Boolean = CooFxSceneManager.stop(sceneId)
    }
}
