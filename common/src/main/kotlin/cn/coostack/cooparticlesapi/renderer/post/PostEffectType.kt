package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import net.minecraft.resources.ResourceLocation

/**
 * 一类可复用的后处理效果定义。
 *
 * `PostEffectType` 是“效果类型”，不是一次正在播放的效果。它包含：
 *
 * - shader pass 图：[chain]
 * - 几何/绑定语义：[model]
 * - backend 能力需求：[requiredCapabilities]
 * - 默认优先级：[defaultPriority]
 * - 如何把实例提交到 [cn.coostack.cooparticlesapi.renderer.effects.RenderEffectGraph] 的 descriptor factory
 *
 * 对外使用时通常不直接构造，而是通过 [CooPostEffectTypes.register] 注册：
 *
 * ```kotlin
 * val HEAT_DISTORT = CooPostEffectTypes.register(id("heat_distort")) {
 *     maskedScreen()
 *     require(RenderBackendCapability.FINAL_FRAME_POST)
 *     pass("distort", shader("heat_distort")) {
 *         inputSceneColor("scene", textureSlot = 0)
 *         inputSceneDepth("depth", optional = true, textureSlot = 1)
 *         outputToFinalScreen()
 *         uniform("strength") { it.params["strength"] ?: PostEffectParamValue.FloatValue(0.04f) }
 *     }
 * }
 * ```
 *
 * 这层 API 替代了“每个效果自己接入 frame-post hook、自己注册 executor、自己管理优先级”的分散写法。
 */
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
    /**
     * 创建一次后处理实例。
     *
     * 返回值只是本地对象；要真正让它运行，需要：
     *
     * - 客户端本地效果：`CooPostEffects.client.add(type.create(...))`
     * - 服务端同步给玩家：`CooPostEffects.server.send(player, type.create(...))`
     * - 服务端同步给区块附近玩家：`CooPostEffects.server.spawn(level, type.create(...))`
     *
     * 常见复杂用法：
     *
     * ```kotlin
     * HEAT_DISTORT.create()
     *     .bindBlock(pos, offset = PostEffectParamValue.Vec3Value(0.5, 1.2, 0.5))
     *     .duration(40)
     *     .params {
     *         float("strength", 0.08f)
     *         float("radius", 0.35f)
     *     }
     * ```
     */
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

    /**
     * 把实例包装成 RenderEffectGraph 可调度的 descriptor。
     *
     * 自定义类型通常不需要改这个；只有当你要接入非默认 executor、特殊排序或特殊 payload 时，
     * 才通过 [PostEffectTypeBuilder.descriptorFactory] 覆盖。
     */
    fun toDescriptor(instance: PostEffectInstance): RenderEffectDescriptor = descriptorFactory(instance)
}

/**
 * post effect 类型注册表。
 *
 * 注册后的类型会同时进入 [PostEffectRuntimeRegistry]，这样 frame-post 阶段收到 descriptor 后可以找到
 * 对应 executor。注册 id 必须全局唯一。
 */
object CooPostEffectTypes {
    private val types = LinkedHashMap<ResourceLocation, PostEffectType>()

    /** 注册一个新的后处理类型。 */
    fun register(id: ResourceLocation, block: PostEffectTypeBuilder.() -> Unit): PostEffectType {
        val type = PostEffectTypeBuilder(id).apply(block).build()
        require(types.putIfAbsent(id, type) == null) { "Post effect type already registered: $id" }
        PostEffectRuntimeRegistry.registerType(type)
        return type
    }

    /** 按 id 查询已注册类型。网络同步反序列化时会用到。 */
    fun get(id: ResourceLocation): PostEffectType? = types[id]
    /** 判断某个 id 是否已注册。 */
    fun contains(id: ResourceLocation): Boolean = id in types
    /** 返回当前全部注册类型的快照。 */
    fun all(): Collection<PostEffectType> = types.values.toList()
    /** 测试用清空入口；业务代码不要调用。 */
    fun clearForTests() = types.clear()
}

/**
 * [PostEffectType] 的声明式 builder。
 *
 * 这里聚合用户自定义 post 效果时最常用的几类信息：
 *
 * - `screenQuad/maskedScreen/worldProjected/customModel`：效果语义
 * - `require/optional`：backend 能力
 * - `pass`：shader pass 图
 * - `priority`：和其他 frame-post descriptor 的相对顺序
 *
 * `pass(...)` 是唯一推荐的 pass 声明入口。线性效果可以忽略返回值；
 * 图连接效果保存返回值后调用 `asInputTo/asInputFrom`。
 */
class PostEffectTypeBuilder(private val id: ResourceLocation) {
    private var model = PostEffectModel.SCREEN_QUAD
    private var chainBuilder = PostEffectChainBuilder()
    private val requiredCapabilities = linkedSetOf<RenderBackendCapability>()
    private val optionalCapabilities = linkedSetOf<RenderBackendCapability>()
    private var defaultPriority = 0
    private var descriptorFactory: ((PostEffectInstance) -> RenderEffectDescriptor)? = null

    /** 整屏 quad 后处理。适合调色、泛光合成、全屏扭曲。 */
    fun screenQuad() = apply { model = PostEffectModel.SCREEN_QUAD }
    /** 局部屏幕效果。通常和 mask / binding center / radius 配合。 */
    fun maskedScreen() = apply { model = PostEffectModel.MASKED_SCREEN }
    /** 绑定世界点、方块或实体，并投影到屏幕的效果。 */
    fun worldProjected() = apply { model = PostEffectModel.WORLD_PROJECTED }
    /** 预留给自定义模型或自定义 executor 的效果语义。 */
    fun customModel() = apply { model = PostEffectModel.CUSTOM }

    /** 设置默认优先级，值越大通常越靠后执行，具体排序由 RenderEffectGraph 统一处理。 */
    fun priority(value: Int) = apply { defaultPriority = value }
    /** 声明整个 effect 必需的 backend 能力。缺失时实例不会提交执行。 */
    fun require(capability: RenderBackendCapability) = apply { requiredCapabilities += capability }
    /** 声明整个 effect 可选的 backend 能力。 */
    fun optional(capability: RenderBackendCapability) = apply { optionalCapabilities += capability }

    /** 兼容旧草案的分组入口。新代码直接在 type builder 顶层调用 [pass]。 */
    @Deprecated(
        message = "Declare passes directly with pass(...). chain { ... } duplicates the top-level DSL and resets previous passes.",
        level = DeprecationLevel.HIDDEN
    )
    fun chain(block: PostEffectChainBuilder.() -> Unit) = apply {
        chainBuilder = PostEffectChainBuilder().apply(block)
    }

    /**
     * 声明单个 pass，并返回可连接引用。
     *
     * 线性效果可以忽略返回值：
     *
     * ```kotlin
     * pass("shockwave", shader("shockwave")) { outputToFinalScreen() }
     * ```
     *
     * 图式效果保存返回值：
     *
     * ```kotlin
     * val a = pass("A", shader("a")) { outputToTemporary() }
     * val b = pass("B", shader("b")) { outputToFinalScreen() }
     * a.asInputTo(b, samplerName = "aTex", textureSlot = 1)
     * ```
     */
    fun pass(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}): PostEffectPassRef {
        return chainBuilder.pass(name, fragment, block)
    }

    /**
     * 兼容旧草案的别名。新代码使用 [pass]。
     */
    @Deprecated(
        message = "Use pass(...) as the single post pass declaration API.",
        replaceWith = ReplaceWith("pass(name, fragment, block)"),
        level = DeprecationLevel.HIDDEN
    )
    fun post(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}): PostEffectPassRef {
        return pass(name, fragment, block)
    }

    /**
     * 兼容旧草案的别名。新代码使用 [pass]。
     */
    @Deprecated(
        message = "Use pass(...) as the single post pass declaration API.",
        replaceWith = ReplaceWith("pass(name, fragment, block)"),
        level = DeprecationLevel.HIDDEN
    )
    fun passRef(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}): PostEffectPassRef {
        return pass(name, fragment, block)
    }

    /** 设置 chain 默认输出为最终屏幕。 */
    fun outputToFinalScreen() = apply { chainBuilder.outputToFinalScreen() }
    /** 设置 chain 默认输出为 bloom target。 */
    fun outputToBloomTarget() = apply { chainBuilder.outputToBloomTarget() }
    /** 设置 chain 默认输出为 mask target。 */
    fun outputToMaskTarget() = apply { chainBuilder.outputToMaskTarget() }

    /**
     * 覆盖 descriptor 构造逻辑。
     *
     * 绝大多数自定义 post 不需要它。只有当你要让某个 post 使用非默认 executor、
     * 特殊 effect id、特殊 payload 或特殊排序语义时才使用。
     */
    fun descriptorFactory(factory: (PostEffectInstance) -> RenderEffectDescriptor) = apply {
        descriptorFactory = factory
    }

    /** 构建并聚合 pass/type 层级的能力需求。 */
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
