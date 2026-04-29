package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectExecutor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry

/**
 * 把 post effect 类型接入通用 [RenderEffectRegistry] 的桥。
 *
 * 自定义 post type 通过 [CooPostEffectTypes.register] 注册后，会自动调用 [registerType]。
 * frame-post 阶段收到对应 descriptor 时，这里的 generic executor 会取出 [PostEffectInstance]，
 * 交给 [PostEffectFrameExecutor] 生成 pass plan 并执行。
 *
 * 这个对象替代调用方为每个 post type 单独注册一个 RenderEffectExecutor 的样板。
 */
object PostEffectRuntimeRegistry {
    private val genericExecutor = RenderEffectExecutor { context, effects ->
        val instances = effects.mapNotNull { it.toPostEffectInstance() }
        if (instances.isEmpty()) {
            return@RenderEffectExecutor
        }
        PostEffectFrameExecutor.execute(context, instances)
    }

    /** 客户端初始化入口：注册内置类型，并把当前已注册类型接入 RenderEffectRegistry。 */
    fun initOnClient() {
        BuiltinPostEffectTypes.init()
        CooPostEffectTypes.all().forEach { type ->
            registerType(type)
        }
    }

    /** 把单个 post type 的 id 映射到通用 post executor。 */
    fun registerType(type: PostEffectType) {
        RenderEffectRegistry.register(type.id, genericExecutor)
    }

    private fun RenderEffectDescriptor.toPostEffectInstance(): PostEffectInstance? {
        return payload as? PostEffectInstance
    }
}
