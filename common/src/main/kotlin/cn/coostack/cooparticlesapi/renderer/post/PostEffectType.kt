package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import net.minecraft.resources.ResourceLocation

/**
 * Pipeline compiler 生成的内部执行类型，不属于公开渲染 API。
 *
 * [defaultSubject] 只为直接调用 `compile(pipeline, subject)` 的旧调用方保留。共享的
 * RenderEntity pipeline 会在 [create] 时传入当前实体，不会把实体放进共享类型。
 *
 * @property defaultSubject 调用 [create] 时没有显式传入 subject 所使用的默认对象
 */
internal class PostEffectType(
    val id: ResourceLocation,
    val model: PostEffectModel,
    val chain: PostEffectChain,
    val requiredCapabilities: Set<RenderBackendCapability>,
    val optionalCapabilities: Set<RenderBackendCapability>,
    val paramUniformNames: Set<String> = emptySet(),
    val defaultPriority: Int = 0,
    val defaultSubject: Any = Unit,
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
    /**
     * 基于共享类型创建一次轻量的后处理运行时实例。
     *
     * @param subject 本地 uniform provider 读取的对象
     * @return 可提交到后处理执行图的实例
     */
    fun create(
        instanceId: String = CooPostEffects.nextInstanceId(),
        binding: PostEffectBinding = PostEffectBinding.Screen,
        lifecycle: PostEffectLifecycle = PostEffectLifecycle(durationTicks = 1),
        params: PostEffectParams = PostEffectParams.EMPTY,
        sourceId: String = "",
        priority: Int = defaultPriority,
        serverSynced: Boolean = false,
        subject: Any = defaultSubject
    ): PostEffectInstance {
        return PostEffectInstance(
            type = this,
            instanceId = instanceId,
            binding = binding,
            lifecycle = lifecycle,
            params = params,
            sourceId = sourceId,
            priority = priority,
            serverSynced = serverSynced,
            subject = subject
        )
    }

    /**
     * 执行 `PostEffectType` 定义的 `toDescriptor` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`toDescriptor(instance = instance)`。
     *
     * @param instance 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun toDescriptor(instance: PostEffectInstance): RenderEffectDescriptor = descriptorFactory(instance)
}

/**
 * 执行 `当前组件` 定义的 `withParamUniforms` 操作；输入和返回值用于该组件当前的渲染职责。
 *
 * 示例：`withParamUniforms(names = names)`。
 *
 * @param names 当前操作需要的输入值；其语义由方法名和所属组件共同限定
 *
 * @return 当前操作计算、更新或查询得到的结果
 */
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
        defaultSubject = defaultSubject,
        descriptorFactory = descriptorFactory
    )
}
