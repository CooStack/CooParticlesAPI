package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import net.minecraft.resources.ResourceLocation
import kotlin.collections.isNotEmpty
import kotlin.collections.map


/**
 * 一个 post effect 的完整片段着色器执行图。
 *
 * `PostEffectChain` 负责描述“有哪些 pass、pass 之间如何取输入、最终写到哪里”，
 * 但不直接执行 OpenGL。真正执行时，[PostEffectFrameExecutor] 会把这里的声明展开成
 * `PostEffectExecutionStep`，再交给 `OpenGlPostEffectExecutionBackend` 创建或复用 FBO、
 * 绑定 texture、上传 uniform、绘制全屏 quad。
 *
 * 这个类型替代了调用方手写的典型后处理样板：
 *
 * - 手动创建多个中间 FBO
 * - 手动决定 A、C、E 先渲染，B 等待 A/C，D 等待 B/E
 * - 手动 `glActiveTexture(GL_TEXTURE0 + slot)` 和 `program.setInt("sampler", slot)`
 * - 手动检查 scene color / depth 是否可用
 * - 手动防止 pass 名重复、slot 重复、pass 自己引用自己
 *
 * 复杂 fan-in 示例：
 *
 * ```kotlin
 * chain {
 *     val a = post("A", shader("extract_a")) {
 *         inputSceneColor("scene", textureSlot = 0)
 *         outputToTemporary()
 *     }
 *     val c = post("C", shader("extract_c")) {
 *         inputCustomTexture("noise", textureSlot = 0)
 *         outputToTemporary()
 *     }
 *     val b = post("B", shader("merge_b")) {
 *         outputToTemporary()
 *     }
 *     val e = post("E", shader("mask_e")) {
 *         inputSceneDepth("depth", optional = true, textureSlot = 0)
 *         outputToMaskTarget()
 *     }
 *     val d = post("D", shader("final_d")) {
 *         inputSceneColor("scene", textureSlot = 0)
 *         outputToFinalScreen()
 *     }
 *
 *     a.asInputTo(b, samplerName = "aTex", textureSlot = 0)
 *     c.asInputTo(b, samplerName = "cTex", textureSlot = 1)
 *     b.asInputTo(d, samplerName = "mergedTex", textureSlot = 1)
 *     e.asInputTo(d, samplerName = "maskTex", textureSlot = 2)
 * }
 * ```
 *
 * 对应 `final_d.fsh` 只需要声明 sampler 名：
 *
 * ```glsl
 * uniform sampler2D scene;     // slot 0
 * uniform sampler2D mergedTex; // slot 1
 * uniform sampler2D maskTex;   // slot 2
 * ```
 */
data class PostEffectChain(
    val passes: List<PostEffectPass>,
    val output: PostEffectOutput = PostEffectOutput.FINAL_SCREEN
) {
    init {
        require(passes.isNotEmpty()) { "A post effect chain must contain at least one pass" }
        val passNames = passes.map { it.name }
        require(passNames.distinct().size == passNames.size) { "Post effect pass names must be unique: $passNames" }
        passes.forEach { pass ->
            val explicitSlots = pass.inputs.mapNotNull { it.textureSlot }
            require(explicitSlots.distinct().size == explicitSlots.size) {
                "Post effect pass ${pass.name} has duplicate texture input slot(s): $explicitSlots"
            }
            pass.inputs.forEach { input ->
                require(input.textureSlot == null || input.textureSlot >= 0) {
                    "Post effect pass ${pass.name} input ${input.samplerName} has invalid texture slot ${input.textureSlot}"
                }
                if (input.source == PostEffectInputSource.PASS_OUTPUT) {
                    require(input.sourcePassName in passNames) {
                        "Post effect pass ${pass.name} references unknown input pass ${input.sourcePassName}"
                    }
                    require(input.sourcePassName != pass.name) {
                        "Post effect pass ${pass.name} cannot use itself as an input"
                    }
                }
                if (input.source == PostEffectInputSource.SCENE_RESOURCE) {
                    require(input.sourceResourceId != null) {
                        "Post effect pass ${pass.name} scene resource input ${input.samplerName} must declare a resource id"
                    }
                }
            }
        }
    }
}

/**
 * 单个片段着色器 pass 的静态声明。
 *
 * 一个 pass 通常就是一次“绑定若干输入 texture -> 上传 uniform -> 画全屏 quad -> 写入输出 target”。
 * 这里不直接保存 OpenGL texture id，因为 texture id 是每帧、每 backend、每窗口尺寸动态解析的。
 *
 * @property name pass 在当前 chain 内的唯一名字。图连接依赖这个名字，改名会影响 `inputFromPass`。
 * @property fragment 片段着色器资源位置，通常指向 `assets/<modid>/shaders/post/x.fsh`。
 * @property inputs 当前 pass 需要绑定到 sampler 的输入列表。
 * @property output 当前 pass 的输出目标类别；非 final 输出会写入框架管理的中间 FBO。
 * @property uniforms 每帧从 [PostEffectInstance] 解析出的 uniform 值。
 * @property requiredCapabilities 缺失时整个 pass 会跳过的后端能力。
 * @property optionalCapabilities 可利用但不强制要求的后端能力。
 */
data class PostEffectPass(
    val name: String,
    val fragment: ResourceLocation,
    val inputs: List<PostEffectInput> = emptyList(),
    val output: PostEffectOutput = PostEffectOutput.TEMPORARY,
    val uniforms: List<PostEffectUniform> = emptyList(),
    val requiredCapabilities: Set<RenderBackendCapability> = emptySet(),
    val optionalCapabilities: Set<RenderBackendCapability> = emptySet()
)

/**
 * 一个 sampler 输入绑定声明。
 *
 * [samplerName] 必须和 GLSL 里的 `uniform sampler2D xxx` 名字一致。
 * [textureSlot] 是可选的固定纹理槽位；如果不写，OpenGL backend 会按输入顺序自动分配，
 * 并跳过已经手动占用的 slot。
 *
 * 典型建议：
 *
 * - 简单单输入 pass 可以不写 slot：`inputSceneColor("scene")`
 * - 多输入合成 pass 建议显式写 slot，shader 文件和 Kotlin 声明更容易对照
 * - 同一个 pass 内不能重复使用同一个显式 slot
 *
 * `sourcePassName` 只在 [PostEffectInputSource.PASS_OUTPUT] 时使用。
 * `sourceResourceId` / `sourceResourceChannel` 只在 [PostEffectInputSource.SCENE_RESOURCE] 时使用。
 */
data class PostEffectInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean = false,
    val sourcePassName: String? = null,
    val sourceResourceId: ResourceLocation? = null,
    val sourceResourceChannel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
    val textureSlot: Int? = null
)

/**
 * 每帧上传到 shader program 的非 sampler uniform。
 *
 * sampler2D 不通过这个类声明，sampler 走 [PostEffectInput]。这里用于 `float radius`、
 * `vec3 center`、`bool throughWalls`、`int iterations` 这类普通 uniform。
 *
 * provider 返回 null 时该 uniform 本帧不上传，适合可选参数或由 shader 默认值处理的参数。
 */
data class PostEffectUniform(
    val name: String,
    val provider: (PostEffectInstance) -> PostEffectParamValue?
)

/**
 * post pass 的输入来源。
 *
 * 这些来源把使用者从“我到底该从哪个 FBO / texture id 读”里解耦出来：
 *
 * - [SCENE_COLOR]：当前帧颜色副本，框架会在需要时 copy scene color
 * - [SCENE_DEPTH]：当前帧深度纹理，需要 backend 支持 depth read
 * - [MASK]：框架约定的 mask target，常用于局部屏幕效果
 * - [BRIGHT_COLOR]：bloom/亮色链路的中间结果
 * - [CUSTOM_TEXTURE]：由实例参数传入的 `ResourceLocation` 贴图或 GL texture id
 * - [PASS_OUTPUT]：另一个 pass 的输出，支持 A/C/E -> B/D 这种图式连接
 * - [SCENE_RESOURCE]：直接读取 `RenderFrameContext.sceneResources` 中的命名资源
 */
enum class PostEffectInputSource {
    SCENE_COLOR,
    SCENE_DEPTH,
    MASK,
    BRIGHT_COLOR,
    CUSTOM_TEXTURE,
    PASS_OUTPUT,
    SCENE_RESOURCE
}

/**
 * 读取命名 scene resource 时使用颜色附件还是深度附件。
 */
enum class PostEffectResourceChannel {
    COLOR,
    DEPTH
}

/**
 * pass 输出目标。
 *
 * [TEMPORARY]、[MASK]、[BLOOM] 都会写入框架管理的中间 target。
 * [FINAL_SCREEN] 会绘制回当前 final composite target 或主 framebuffer。
 *
 * 注意：在图式 pass 中，推荐通过 `asInputTo/asInputFrom` 读取上游 pass，而不是只依赖
 * `inputBrightColor/inputMask` 这类“按输出类型读取最近一次结果”的旧式链式语义。
 */
enum class PostEffectOutput {
    TEMPORARY,
    MASK,
    BLOOM,
    FINAL_SCREEN
}

/**
 * effect 的模型/绑定语义。
 *
 * 当前 OpenGL post backend 主要执行全屏 quad；这里的 model 用于描述 effect 设计意图：
 *
 * - [SCREEN_QUAD]：整屏处理
 * - [MASKED_SCREEN]：通常结合 binding mask，只影响屏幕局部
 * - [WORLD_PROJECTED]：绑定世界坐标/实体/方块，并把位置投影到屏幕
 * - [CUSTOM]：保留给自定义 executor 或未来扩展
 */
enum class PostEffectModel {
    SCREEN_QUAD,
    MASKED_SCREEN,
    WORLD_PROJECTED,
    CUSTOM
}

/**
 * 构建 [PostEffectChain] 的 DSL。
 *
 * 简单线性效果可以继续使用 [pass]：
 *
 * ```kotlin
 * pass("grayscale", shader("grayscale")) {
 *     inputSceneColor("scene")
 *     outputToFinalScreen()
 * }
 * ```
 *
 * 需要图连接时使用 [post] 或 [passRef] 拿到 [PostEffectPassRef]：
 *
 * ```kotlin
 * val blur = post("blur", shader("blur")) { outputToTemporary() }
 * val composite = post("composite", shader("composite")) { outputToFinalScreen() }
 * blur.asInputTo(composite, "blurTex", textureSlot = 1)
 * ```
 *
 * 这个 builder 替代调用方维护“先执行谁、后执行谁”的排序逻辑。
 * `PostEffectFrameExecutor` 会根据 [PostEffectInputSource.PASS_OUTPUT] 做拓扑排序。
 */
class PostEffectChainBuilder {
    private val passes = linkedMapOf<String, PostEffectPassBuilder>()
    private var output = PostEffectOutput.FINAL_SCREEN

    /**
     * 声明一个 pass，并保持旧的链式 DSL 返回值。
     *
     * 如果不需要引用该 pass 输出，使用这个函数最简洁。需要图连接时优先用 [post]。
     */
    fun pass(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}) = apply {
        post(name, fragment, block)
    }

    /**
     * 声明一个可被图连接引用的 pass。
     *
     * 返回的 [PostEffectPassRef] 只用于建立 pass output 依赖，不持有 texture id。
     * 真正的 texture id 会在每帧执行时由 backend 写入 `lastPassOutputTextures[passName]`。
     */
    fun post(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}): PostEffectPassRef {
        require(name !in passes) { "Post effect pass already declared: $name" }
        passes[name] = PostEffectPassBuilder(name, fragment).apply(block)
        return PostEffectPassRef(this, name)
    }

    /**
     * [post] 的语义别名，用于强调“我要拿一个引用，稍后连接输入”。
     */
    fun passRef(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}): PostEffectPassRef {
        return post(name, fragment, block)
    }

    internal fun linkPassOutput(
        fromPost: PostEffectPassRef,
        toPost: PostEffectPassRef,
        samplerName: String,
        optional: Boolean,
        textureSlot: Int?
    ) {
        require(fromPost.owner === this && toPost.owner === this) {
            "Post effect graph links must connect passes from the same chain"
        }
        val target = passes[toPost.name] ?: error("Post effect pass is not declared: ${toPost.name}")
        require(fromPost.name in passes) { "Post effect pass is not declared: ${fromPost.name}" }
        target.inputFromPass(samplerName, fromPost.name, optional, textureSlot)
    }

    /** 设置 chain 的默认目标标记；具体 pass 的输出仍以 [PostEffectPassBuilder] 内声明为准。 */
    fun outputToFinalScreen() = apply { output = PostEffectOutput.FINAL_SCREEN }
    fun outputToBloomTarget() = apply { output = PostEffectOutput.BLOOM }
    fun outputToMaskTarget() = apply { output = PostEffectOutput.MASK }

    fun build(): PostEffectChain = PostEffectChain(passes.values.map { it.build() }, output)
}

/**
 * pass 图连接用的轻量引用。
 *
 * 它不是一个运行时对象，不代表 FBO，也不代表 texture。它只记录“某个 pass 的输出会被另一个 pass
 * 作为 sampler 输入”。这样做的好处是，调用方不用手动保存每个中间 FBO 的 texture id。
 */
class PostEffectPassRef internal constructor(
    internal val owner: PostEffectChainBuilder,
    val name: String
) {
    /**
     * 把当前 pass 的输出作为 [toPost] 的输入。
     *
     * @param samplerName 目标 pass 的 GLSL sampler uniform 名。
     * @param optional 上游缺失时是否允许目标 pass 继续执行。
     * @param textureSlot 固定 sampler slot；例如 1 表示 backend 会执行等价于 `program.setInt(samplerName, 1)`。
     */
    fun asInputTo(
        toPost: PostEffectPassRef,
        samplerName: String = name,
        optional: Boolean = false,
        textureSlot: Int? = null
    ): PostEffectPassRef {
        owner.linkPassOutput(this, toPost, samplerName, optional, textureSlot)
        return toPost
    }

    /**
     * 把 [fromPost] 的输出作为当前 pass 的输入。
     *
     * 这是 [asInputTo] 的反向写法，适合在声明目标 pass 后集中列出它的输入。
     */
    fun asInputFrom(
        fromPost: PostEffectPassRef,
        samplerName: String = fromPost.name,
        optional: Boolean = false,
        textureSlot: Int? = null
    ): PostEffectPassRef {
        owner.linkPassOutput(fromPost, this, samplerName, optional, textureSlot)
        return this
    }
}

/**
 * 构建单个 [PostEffectPass] 的 DSL。
 *
 * 这里的输入函数都只声明 sampler 来源，不直接绑定 OpenGL。执行时 backend 会：
 *
 * 1. 解析输入对应的 texture id
 * 2. 分配或使用指定的 [PostEffectInput.textureSlot]
 * 3. `glActiveTexture(GL_TEXTURE0 + slot)`
 * 4. `glBindTexture(GL_TEXTURE_2D, textureId)`
 * 5. `program.setInt(samplerName, slot)`
 *
 * 因此自定义 `.fsh` 里只需要声明 sampler 名，slot 由 Kotlin DSL 控制。
 */
class PostEffectPassBuilder(
    private val name: String,
    private val fragment: ResourceLocation
) {
    private val inputs = mutableListOf<PostEffectInput>()
    private val uniforms = mutableListOf<PostEffectUniform>()
    private val requiredCapabilities = linkedSetOf<RenderBackendCapability>()
    private val optionalCapabilities = linkedSetOf<RenderBackendCapability>()
    private var output = PostEffectOutput.TEMPORARY

    /** 读取当前帧颜色副本，常用于大多数后处理的基础输入。 */
    fun inputSceneColor(samplerName: String = "scene", optional: Boolean = false, textureSlot: Int? = null) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.SCENE_COLOR, optional, textureSlot = textureSlot)
        addCapability(RenderBackendCapability.SCENE_COLOR_COPY, optional)
    }

    /** 读取当前帧深度纹理；默认 optional，避免不支持 depth read 的 backend 直接让效果失效。 */
    fun inputSceneDepth(samplerName: String = "depth", optional: Boolean = true, textureSlot: Int? = null) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.SCENE_DEPTH, optional, textureSlot = textureSlot)
        addCapability(RenderBackendCapability.SCENE_DEPTH_READ, optional)
    }

    /** 读取 mask target，适合局部屏幕效果或先生成遮罩再合成的效果。 */
    fun inputMask(samplerName: String = "mask", optional: Boolean = false, textureSlot: Int? = null) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.MASK, optional, textureSlot = textureSlot)
    }

    /** 读取 bloom/亮色链路的当前结果；复杂图连接中更推荐用 [inputFromPass] 指定明确上游。 */
    fun inputBrightColor(samplerName: String = "bright", optional: Boolean = false, textureSlot: Int? = null) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.BRIGHT_COLOR, optional, textureSlot = textureSlot)
        addCapability(RenderBackendCapability.SCENE_COLOR_COPY, optional)
    }

    /**
     * 读取实例参数传入的自定义 texture。
     *
     * 参数值可以是 [PostEffectParamValue.ResourceValue]，由 backend 载入资源贴图；
     * 也可以是 [PostEffectParamValue.IntValue] / [PostEffectParamValue.LongValue]，直接表示已有 GL texture id。
     */
    fun inputCustomTexture(samplerName: String, optional: Boolean = false, textureSlot: Int? = null) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.CUSTOM_TEXTURE, optional, textureSlot = textureSlot)
    }

    /**
     * 直接按 pass 名读取另一个 pass 的输出。
     *
     * 通常不手写这个函数，而是使用 [PostEffectPassRef.asInputTo] / [PostEffectPassRef.asInputFrom]，
     * 这样改 pass 名时更容易靠编译位置发现问题。
     */
    fun inputFromPass(
        samplerName: String,
        passName: String,
        optional: Boolean = false,
        textureSlot: Int? = null
    ) = apply {
        inputs += PostEffectInput(
            samplerName = samplerName,
            source = PostEffectInputSource.PASS_OUTPUT,
            optional = optional,
            sourcePassName = passName,
            textureSlot = textureSlot
        )
    }

    /**
     * 读取 `RenderFrameContext.sceneResources` 中的命名资源。
     *
     * 这适合接入 backend 已经暴露的 FBO / texture，例如某个原版层 target 或渲染管线里注册的资源。
     * 如果只想读取当前场景颜色/深度，优先使用 [inputSceneColor] / [inputSceneDepth]。
     */
    fun inputSceneResource(
        samplerName: String,
        resourceId: ResourceLocation,
        channel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
        optional: Boolean = false,
        textureSlot: Int? = null
    ) = apply {
        inputs += PostEffectInput(
            samplerName = samplerName,
            source = PostEffectInputSource.SCENE_RESOURCE,
            optional = optional,
            sourceResourceId = resourceId,
            sourceResourceChannel = channel,
            textureSlot = textureSlot
        )
    }

    /** [inputSceneResource] 的深度附件快捷写法。 */
    fun inputSceneResourceDepth(
        samplerName: String,
        resourceId: ResourceLocation,
        optional: Boolean = false,
        textureSlot: Int? = null
    ) = apply {
        inputSceneResource(samplerName, resourceId, PostEffectResourceChannel.DEPTH, optional, textureSlot)
    }

    /**
     * 声明普通 uniform。
     *
     * 示例：
     *
     * ```kotlin
     * uniform("radius") { it.params["radius"] ?: PostEffectParamValue.FloatValue(it.progress) }
     * uniform("color") { it.params["color"] ?: PostEffectParamValue.ColorValue(1f, 0.7f, 0.2f) }
     * ```
     */
    fun uniform(name: String, provider: (PostEffectInstance) -> PostEffectParamValue?) = apply {
        uniforms += PostEffectUniform(name, provider)
    }

    /** 写入普通中间 FBO。图式 pass 的中间结果一般用它。 */
    fun outputToTemporary() = apply { output = PostEffectOutput.TEMPORARY }
    /** 写回 final framebuffer。一个屏幕效果通常最后一个 pass 使用它。 */
    fun outputToFinalScreen() = apply { output = PostEffectOutput.FINAL_SCREEN }
    /** 写入 bloom target；内置 bloom 会额外做多级 mip/blur 展开。 */
    fun outputToBloomTarget() = apply { output = PostEffectOutput.BLOOM }
    /** 写入 mask target，供后续 pass 或局部效果读取。 */
    fun outputToMaskTarget() = apply { output = PostEffectOutput.MASK }

    /** 声明当前 pass 必须具备的 backend 能力；缺失时 pass 会被跳过。 */
    fun require(capability: RenderBackendCapability) = apply { requiredCapabilities += capability }
    /** 声明可选 backend 能力；缺失不会阻止 pass 运行。 */
    fun optional(capability: RenderBackendCapability) = apply { optionalCapabilities += capability }

    private fun addCapability(capability: RenderBackendCapability, optional: Boolean) {
        if (optional) optionalCapabilities += capability else requiredCapabilities += capability
    }

    fun build(): PostEffectPass {
        return PostEffectPass(
            name = name,
            fragment = fragment,
            inputs = inputs.toList(),
            output = output,
            uniforms = uniforms.toList(),
            requiredCapabilities = requiredCapabilities.toSet(),
            optionalCapabilities = optionalCapabilities.toSet()
        )
    }
}
