package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.renderer.client.ClientPersistentBloomManager
import cn.coostack.cooparticlesapi.renderer.client.ClientScreenGlowManager
import cn.coostack.cooparticlesapi.renderer.client.ClientWorldLightManager
import cn.coostack.cooparticlesapi.renderer.client.ClientMaskBloomManager
import cn.coostack.cooparticlesapi.renderer.compute.ComputeDispatchRenderer
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereRenderer

/**
 * 内建 effect type 的客户端执行器注册入口。
 */
object BuiltinRenderEffectRegistry {
    private var initialized = false

    /**
     * 在客户端注册全部内建 effect executor。
     *
     * 该方法是幂等的，多次调用只会在第一次真正执行注册。
     */
    fun initOnClient() {
        if (initialized) {
            return
        }
        initialized = true
        RenderEffectRegistry.register(BuiltinRenderEffectTypes.SCREEN_GLOW) { effects ->
            ClientScreenGlowManager.renderRequests(payloads(effects))
        }
        RenderEffectRegistry.register(BuiltinRenderEffectTypes.PERSISTENT_BLOOM) { effects ->
            ClientPersistentBloomManager.renderRequests(payloads(effects))
        }
        RenderEffectRegistry.register(BuiltinRenderEffectTypes.MASK_BLOOM) { effects ->
            ClientMaskBloomManager.renderRequests(payloads(effects))
        }
        RenderEffectRegistry.register(BuiltinRenderEffectTypes.WORLD_LIGHT) { effects ->
            ClientWorldLightManager.renderRequests(payloads(effects))
        }
        RenderEffectRegistry.register(BuiltinRenderEffectTypes.POST_GLOW_SPHERE) { effects ->
            PostGlowSphereRenderer.renderRequests(payloads(effects))
        }
        RenderEffectRegistry.register(BuiltinRenderEffectTypes.COMPUTE_DISPATCH) { effects ->
            ComputeDispatchRenderer.renderRequests(payloads(effects))
        }
    }

    /**
     * 从 descriptor 列表中提取指定 payload 类型。
     */
    private inline fun <reified T> payloads(effects: List<RenderEffectDescriptor>): List<T> {
        return effects.mapNotNull { it.payload as? T }
    }
}
