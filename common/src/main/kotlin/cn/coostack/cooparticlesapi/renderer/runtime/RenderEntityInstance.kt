package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.client.ClientPersistentBloomManager
import cn.coostack.cooparticlesapi.renderer.client.ClientScreenGlowManager
import cn.coostack.cooparticlesapi.renderer.client.ClientWorldLightManager
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectInput
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloomContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowProvider
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

class RenderEntityInstance<T : RenderEntity>(
    val entity: T,
    val renderer: RenderEntityRenderer<T>
) {
    var visualProfile: RenderEntityVisualProfile = renderer.createVisualProfile(entity)
        private set
    val localRenderTargetPool = LocalRenderTargetPool()
    var localEffectChain = LocalEffectChain(localRenderTargetPool)
    private var initialized = false
    private var released = false

    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true
        renderer.initialize(this)
        visualProfile = renderer.createVisualProfile(entity)
    }

    fun reinitialize() {
        initialized = false
        initialize()
    }

    fun updateFrom(profile: RenderEntity) {
        entity.loadProfileFromEntity(profile)
        visualProfile = renderer.createVisualProfile(entity)
        renderer.update(this, entity)
    }

    fun renderLocal(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        stateGuard.use { renderState ->
            renderer.renderLocal(
                LocalRenderInput(
                    instance = this,
                    tickDelta = tickDelta,
                    viewMatrix = viewMatrix,
                    projMatrix = projMatrix,
                    modelMatrix = modelMatrix,
                    renderState = renderState
                )
            )
            localEffectChain.execute()
        }
    }

    fun markRemoved() {
        entity.canceled = true
    }

    fun collectFrameEffects(context: RenderFrameContext, collector: FrameEffectCollector) {
        renderer.collectFrameEffects(
            FrameEffectInput(
                instance = this,
                frameContext = context
            ),
            collector
        )
        when (entity) {
            is ScreenGlowContextProvider -> {
                ClientScreenGlowManager.submitFrameEffects(
                    sourceInstanceId = entity.uuid.toString(),
                    provider = entity,
                    context = context,
                    collector = collector
                )
            }

            is ScreenGlowProvider -> {
                ClientScreenGlowManager.submitFrameEffects(
                    sourceInstanceId = entity.uuid.toString(),
                    provider = entity,
                    context = context,
                    collector = collector
                )
            }
        }
        if (entity is PersistentBloomContextProvider) {
            ClientPersistentBloomManager.submitFrameEffects(
                sourceInstanceId = entity.uuid.toString(),
                provider = entity,
                context = context,
                collector = collector
            )
        }
        if (entity is WorldLightProvider) {
            ClientWorldLightManager.submitFrameEffects(
                sourceInstanceId = entity.uuid.toString(),
                provider = entity,
                context = context,
                collector = collector
            )
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        localEffectChain.replaceSteps(emptyList())
        renderer.release(this)
    }
}
