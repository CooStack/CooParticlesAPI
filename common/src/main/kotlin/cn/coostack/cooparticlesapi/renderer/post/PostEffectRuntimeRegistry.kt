package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectExecutor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry

object PostEffectRuntimeRegistry {
    private val genericExecutor = RenderEffectExecutor { context, effects ->
        val instances = effects.mapNotNull { it.toPostEffectInstance() }
        if (instances.isEmpty()) {
            return@RenderEffectExecutor
        }
        PostEffectFrameExecutor.execute(context, instances)
    }

    fun initOnClient() {
        BuiltinPostEffectTypes.init()
        CooPostEffectTypes.all().forEach { type ->
            registerType(type)
        }
    }

    fun registerType(type: PostEffectType) {
        RenderEffectRegistry.register(type.id, genericExecutor)
    }

    private fun RenderEffectDescriptor.toPostEffectInstance(): PostEffectInstance? {
        return payload as? PostEffectInstance
    }
}
