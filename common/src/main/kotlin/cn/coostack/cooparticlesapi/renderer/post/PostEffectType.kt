package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import net.minecraft.resources.ResourceLocation

class PostEffectType(
    val id: ResourceLocation,
    val model: PostEffectModel,
    val chain: PostEffectChain,
    val requiredCapabilities: Set<RenderBackendCapability>,
    val optionalCapabilities: Set<RenderBackendCapability>,
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

object CooPostEffectTypes {
    private val types = LinkedHashMap<ResourceLocation, PostEffectType>()

    fun register(id: ResourceLocation, block: PostEffectTypeBuilder.() -> Unit): PostEffectType {
        val type = PostEffectTypeBuilder(id).apply(block).build()
        require(types.putIfAbsent(id, type) == null) { "Post effect type already registered: $id" }
        PostEffectRuntimeRegistry.registerType(type)
        return type
    }

    fun get(id: ResourceLocation): PostEffectType? = types[id]
    fun contains(id: ResourceLocation): Boolean = id in types
    fun all(): Collection<PostEffectType> = types.values.toList()
    fun clearForTests() = types.clear()
}

class PostEffectTypeBuilder(private val id: ResourceLocation) {
    private var model = PostEffectModel.SCREEN_QUAD
    private var chainBuilder = PostEffectChainBuilder()
    private val requiredCapabilities = linkedSetOf<RenderBackendCapability>()
    private val optionalCapabilities = linkedSetOf<RenderBackendCapability>()
    private var defaultPriority = 0
    private var descriptorFactory: ((PostEffectInstance) -> RenderEffectDescriptor)? = null

    fun screenQuad() = apply { model = PostEffectModel.SCREEN_QUAD }
    fun maskedScreen() = apply { model = PostEffectModel.MASKED_SCREEN }
    fun worldProjected() = apply { model = PostEffectModel.WORLD_PROJECTED }
    fun customModel() = apply { model = PostEffectModel.CUSTOM }

    fun priority(value: Int) = apply { defaultPriority = value }
    fun require(capability: RenderBackendCapability) = apply { requiredCapabilities += capability }
    fun optional(capability: RenderBackendCapability) = apply { optionalCapabilities += capability }

    fun chain(block: PostEffectChainBuilder.() -> Unit) = apply {
        chainBuilder = PostEffectChainBuilder().apply(block)
    }

    fun pass(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}) = apply {
        chainBuilder.pass(name, fragment, block)
    }

    fun outputToFinalScreen() = apply { chainBuilder.outputToFinalScreen() }
    fun outputToBloomTarget() = apply { chainBuilder.outputToBloomTarget() }
    fun outputToMaskTarget() = apply { chainBuilder.outputToMaskTarget() }

    fun descriptorFactory(factory: (PostEffectInstance) -> RenderEffectDescriptor) = apply {
        descriptorFactory = factory
    }

    fun build(): PostEffectType {
        val chain = chainBuilder.build()
        val passRequired = chain.passes.flatMap { it.requiredCapabilities }.toSet()
        val passOptional = chain.passes.flatMap { it.optionalCapabilities }.toSet()
        return PostEffectType(
            id = id,
            model = model,
            chain = chain,
            requiredCapabilities = requiredCapabilities + passRequired,
            optionalCapabilities = optionalCapabilities + passOptional,
            defaultPriority = defaultPriority,
            descriptorFactory = descriptorFactory ?: { instance ->
                RenderEffectDescriptor(
                    effectType = id,
                    effectId = instance.instanceId,
                    priority = instance.priority,
                    sourceInstanceId = instance.sourceId,
                    requiredCapabilities = requiredCapabilities + passRequired,
                    payload = instance
                )
            }
        )
    }
}
