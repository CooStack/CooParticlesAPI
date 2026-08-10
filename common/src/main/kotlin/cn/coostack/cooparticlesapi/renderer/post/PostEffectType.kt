package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import net.minecraft.resources.ResourceLocation

/** Pipeline compiler 生成的内部执行类型，不属于公开渲染 API。 */
internal class PostEffectType(
    val id: ResourceLocation,
    val model: PostEffectModel,
    val chain: PostEffectChain,
    val requiredCapabilities: Set<RenderBackendCapability>,
    val optionalCapabilities: Set<RenderBackendCapability>,
    val paramUniformNames: Set<String> = emptySet(),
    val defaultPriority: Int = 0,
    val descriptorFactory: (PostEffectInstance) -> RenderEffectDescriptor = { instance ->
        RenderEffectDescriptor(
            effectType = instance.type.id,
            effectId = instance.instanceId,
            priority = instance.priority,
            sourceInstanceId = instance.sourceId,
            requiredCapabilities = instance.type.requiredCapabilities,
            payload = instance
        )
    }
) {
    fun create(
        instanceId: String = CooPostEffects.nextInstanceId(),
        binding: PostEffectBinding = PostEffectBinding.Screen,
        lifecycle: PostEffectLifecycle = PostEffectLifecycle(durationTicks = 1),
        params: PostEffectParams = PostEffectParams.EMPTY,
        sourceId: String = "",
        priority: Int = defaultPriority,
        serverSynced: Boolean = false
    ): PostEffectInstance {
        return PostEffectInstance(
            type = this,
            instanceId = instanceId,
            binding = binding,
            lifecycle = lifecycle,
            params = params,
            sourceId = sourceId,
            priority = priority,
            serverSynced = serverSynced
        )
    }

    fun toDescriptor(instance: PostEffectInstance): RenderEffectDescriptor = descriptorFactory(instance)
}

internal fun PostEffectType.withParamUniforms(names: Set<String>): PostEffectType {
    if (names.isEmpty()) return this
    val updatedPasses = chain.passes.map { pass ->
        val existing = pass.uniforms.mapTo(linkedSetOf(), PostEffectUniform::name)
        val dynamic = names.filterNot { it in existing }.map { name ->
            PostEffectUniform(name) { instance -> instance.params[name] }
        }
        pass.copy(uniforms = pass.uniforms + dynamic)
    }
    return PostEffectType(
        id = id,
        model = model,
        chain = chain.copy(passes = updatedPasses),
        requiredCapabilities = requiredCapabilities,
        optionalCapabilities = optionalCapabilities,
        paramUniformNames = paramUniformNames + names,
        defaultPriority = defaultPriority,
        descriptorFactory = descriptorFactory
    )
}
