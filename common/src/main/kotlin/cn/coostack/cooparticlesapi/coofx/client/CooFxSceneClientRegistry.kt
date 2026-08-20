package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxEmitterParameterNames
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxModelPlayResult
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxParameterValue
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayRequest
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayResult
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlaybackHandle
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneMode
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneRenderEntity
import net.minecraft.client.Minecraft
import java.util.UUID

/**
 * 客户端 RenderEntity 镜像与 CooFX model/camera 播放句柄之间的适配层。
 *
 * 服务端只同步 [CooFxSceneRenderEntity] 字段；该 registry 在客户端逻辑 tick 中按字段生成或更新
 * CooFX 请求，Queued 请求会在每个 tick 重试而不会丢失长期 scene 状态。
 */
internal object CooFxSceneClientRegistry {
    private val scenes = LinkedHashMap<UUID, ClientSceneState>()

    /** 返回客户端当前跟踪的 CooFX scene 数。 */
    fun activeSceneCount(): Int = scenes.size

    fun onCreated(entity: CooFxSceneRenderEntity) {
        scenes.remove(entity.uuid)?.stop()
        scenes[entity.uuid] = ClientSceneState(entity)
        CooParticlesConstants.logger.info(
            "[CooFX-CREATE] scene registry accepted sceneId=${entity.uuid}, resource=${entity.resourceIdText}, mode=${entity.mode()}, cameraTargets=${entity.cameraTargetPlayers()}, activeScenes=${scenes.size}",
        )
    }

    fun onUpdated(entity: CooFxSceneRenderEntity) {
        scenes[entity.uuid]?.entity = entity
    }

    fun onRemoved(sceneId: UUID) {
        scenes.remove(sceneId)?.stop()
        CooFxCameraTrackingManager.remove(sceneId)
    }

    fun tick() {
        CooFxCameraTrackingManager.beginTick()
        val iterator = scenes.iterator()
        while (iterator.hasNext()) {
            val (_, state) = iterator.next()
            if (state.entity.canceled) {
                state.stop()
                iterator.remove()
                continue
            }
            runCatching { updateModel(state) }
                .onSuccess { state.modelUpdateFailure.onSuccess() }
                .onFailure(state.modelUpdateFailure::onFailure)
            runCatching { updateEmitter(state) }
                .onSuccess { state.emitterUpdateFailure.onSuccess() }
                .onFailure(state.emitterUpdateFailure::onFailure)
            runCatching { updateCamera(state) }
                .onSuccess { state.cameraUpdateFailure.onSuccess() }
                .onFailure(state.cameraUpdateFailure::onFailure)
        }
        CooFxCameraTrackingManager.apply()
    }

    fun clear() {
        scenes.values.forEach { state -> state.stop() }
        scenes.clear()
        CooFxCameraTrackingManager.clear()
    }

    private fun updateModel(state: ClientSceneState) {
        val entity = state.entity
        val mode = entity.mode()
        if (!rendersModel(mode)) {
            state.modelHandle?.stop()
            state.modelHandle = null
            state.modelDefinitionKey = null
            return
        }
        val resourceId = entity.resourceId()
        val request = CooFxModelPlayRequest(
            resourceId = resourceId,
            transform = entity.worldTransform(),
            requestSeed = entity.requestSeed,
            clipId = entity.clipId(),
            playbackSpeed = entity.playbackSpeed,
        )
        val definitionKey = "${entity.resourceIdText}|${entity.requestSeed}"
        val handle = state.modelHandle
        if (handle == null || !handle.isAlive || state.modelDefinitionKey != definitionKey) {
            handle?.stop()
            state.modelHandle = null
            state.modelDefinitionKey = definitionKey
            when (val result = CooFXClient.playModel(request)) {
                is CooFxModelPlayResult.Started -> state.modelHandle = result.handle
                is CooFxModelPlayResult.Queued -> CooFXClient.cancelQueuedModel(result.requestId)
                is CooFxModelPlayResult.Failed -> throw IllegalStateException(result.failure.message, result.failure.cause)
            }
            return
        }
        if (!CooFXClient.updateModel(handle, request)) {
            handle.stop()
            state.modelHandle = null
        }
    }

    private fun updateEmitter(state: ClientSceneState) {
        val entity = state.entity
        val emitterId = entity.emitterId()
        if (!usesEmitter(entity.mode()) || emitterId == null) {
            state.emitterHandle?.stop()
            state.emitterHandle = null
            state.emitterDefinitionKey = null
            return
        }
        val overrides = buildMap {
            if (entity.emitterCount >= 0) {
                put(CooFxEmitterParameterNames.COUNT, CooFxParameterValue.IntValue(entity.emitterCount))
            }
            if (entity.emitterDelayTicks >= 0) {
                put(CooFxEmitterParameterNames.DELAY_TICKS, CooFxParameterValue.IntValue(entity.emitterDelayTicks))
            }
            if (entity.emitterLifetimeTicks > 0) {
                put(
                    CooFxEmitterParameterNames.LIFETIME_TICKS,
                    CooFxParameterValue.IntValue(entity.emitterLifetimeTicks),
                )
            }
            put(
                CooFxEmitterParameterNames.PLAYBACK_SPEED,
                CooFxParameterValue.FloatValue(entity.playbackSpeed),
            )
        }
        val request = CooFxPlayRequest(
            resourceId = entity.resourceId(),
            transform = entity.worldTransform(),
            requestSeed = entity.requestSeed,
            clipId = entity.clipId(),
            emitterId = emitterId,
            parameterOverrides = overrides,
        )
        val definitionKey = listOf(
            entity.resourceIdText,
            entity.requestSeed.toString(),
            entity.clipIdText,
            emitterId,
            entity.emitterCount.toString(),
            entity.emitterDelayTicks.toString(),
            entity.emitterLifetimeTicks.toString(),
            entity.playbackSpeed.toString(),
        ).joinToString("|")
        val handle = state.emitterHandle
        if (handle == null || !handle.isAlive || state.emitterDefinitionKey != definitionKey) {
            handle?.stop()
            state.emitterHandle = null
            state.emitterDefinitionKey = definitionKey
            when (val result = CooFXClient.play(request)) {
                is CooFxPlayResult.Started -> state.emitterHandle = result.handle
                is CooFxPlayResult.Queued -> CooFXClient.cancelQueuedParticle(result.requestId)
                is CooFxPlayResult.Failed -> throw IllegalStateException(result.failure.message, result.failure.cause)
            }
            return
        }
        if (!CooFXClient.updateParticle(handle, request)) {
            handle.stop()
            state.emitterHandle = null
        }
    }

    private fun updateCamera(state: ClientSceneState) {
        val entity = state.entity
        if (!tracksCamera(entity.mode())) {
            CooFxCameraTrackingManager.remove(entity.uuid)
            return
        }
        val player = Minecraft.getInstance().player
        if (player == null || !entity.isCameraTargetedAt(player.uuid)) {
            CooFxCameraTrackingManager.remove(entity.uuid)
            return
        }
        val pose = CooFXClient.sampleCamera(
            resourceId = entity.resourceId(),
            cameraSelector = entity.cameraId(),
            clipId = entity.clipId(),
            rawTimeSeconds = entity.age.toFloat() * 0.05F * entity.playbackSpeed,
            transform = entity.worldTransform(),
        ) ?: run {
            CooFxCameraTrackingManager.remove(entity.uuid)
            return
        }
        CooFxCameraTrackingManager.submit(entity.uuid, entity.cameraPriority, pose)
    }

    private fun rendersModel(mode: CooFxSceneMode): Boolean = when (mode) {
        CooFxSceneMode.MODEL,
        CooFxSceneMode.MODEL_AND_CAMERA,
        CooFxSceneMode.MODEL_AND_EMITTER,
        CooFxSceneMode.MODEL_CAMERA_AND_EMITTER -> true
        else -> false
    }

    private fun usesEmitter(mode: CooFxSceneMode): Boolean = when (mode) {
        CooFxSceneMode.MODEL_AND_EMITTER,
        CooFxSceneMode.MODEL_CAMERA_AND_EMITTER,
        CooFxSceneMode.EMITTER,
        CooFxSceneMode.CAMERA_AND_EMITTER -> true
        else -> false
    }

    private fun tracksCamera(mode: CooFxSceneMode): Boolean = when (mode) {
        CooFxSceneMode.CAMERA_TRACKING,
        CooFxSceneMode.MODEL_AND_CAMERA,
        CooFxSceneMode.MODEL_CAMERA_AND_EMITTER,
        CooFxSceneMode.CAMERA_AND_EMITTER -> true
        else -> false
    }

    private class ClientSceneState(
        var entity: CooFxSceneRenderEntity,
        var modelHandle: CooFxPlaybackHandle? = null,
        var modelDefinitionKey: String? = null,
        var emitterHandle: CooFxPlaybackHandle? = null,
        var emitterDefinitionKey: String? = null,
    ) {
        val modelUpdateFailure = failureReporter("模型")
        val emitterUpdateFailure = failureReporter("emitter")
        val cameraUpdateFailure = failureReporter("camera")

        private fun failureReporter(component: String): CooFxFailureTransitionReporter = CooFxFailureTransitionReporter(
            emitFailure = { failure ->
                CooParticlesConstants.logger.error("CooFX scene $component 更新失败：sceneId=${entity.uuid}", failure)
            },
            emitRecovery = {
                CooParticlesConstants.logger.info("CooFX scene $component 更新已恢复：sceneId=${entity.uuid}")
            },
        )

        fun stop() {
            modelHandle?.stop()
            modelHandle = null
            emitterHandle?.stop()
            emitterHandle = null
            CooFxCameraTrackingManager.remove(entity.uuid)
        }
    }
}
