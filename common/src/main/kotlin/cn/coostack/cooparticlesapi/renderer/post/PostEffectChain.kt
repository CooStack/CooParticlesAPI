package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import net.minecraft.resources.ResourceLocation

/**
 * Pipeline graph 编译后的内部 pass 列表。
 *
 * 构造时会校验 pass 名称、attachment 数量、纹理单元和 pass 引用，任何一项不满足时都会
 * 拒绝生成链，避免运行时出现未绑定 sampler 或无效 framebuffer 连接。
 *
 * @param passes 按执行顺序排列的后处理 pass，至少包含一个元素
 * @param output 整条链的最终输出位置，默认写入最终屏幕
 */
internal data class PostEffectChain(
    val passes: List<PostEffectPass>,
    val output: PostEffectOutput = PostEffectOutput.FINAL_SCREEN
) {
    init {
        require(passes.isNotEmpty()) { "A post effect chain must contain at least one pass" }
        val passNames = passes.map(PostEffectPass::name)
        require(passNames.distinct().size == passNames.size) {
            "Post effect pass names must be unique: $passNames"
        }
        passes.forEach { pass ->
            require(pass.colorAttachmentCount > 0) {
                "Post effect pass ${pass.name} must declare at least one color attachment"
            }
            val explicitSlots = pass.inputs.mapNotNull(PostEffectInput::textureSlot)
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
                    val sourcePass = passes.first { it.name == input.sourcePassName }
                    require(input.sourcePassAttachment in 0 until sourcePass.colorAttachmentCount) {
                        "Post effect pass ${pass.name} references missing attachment " +
                            "${input.sourcePassAttachment} from ${sourcePass.name}"
                    }
                    input.expectedFormat?.let { expected ->
                        require(sourcePass.outputFormat == expected) {
                            "Post effect pass ${pass.name} input ${input.samplerName} expects $expected but " +
                                "${sourcePass.name} outputs ${sourcePass.outputFormat}"
                        }
                    }
                    require(sourcePass.mipLevels >= input.minimumMipLevels) {
                        "Post effect pass ${pass.name} input ${input.samplerName} requires " +
                            "${input.minimumMipLevels} mip levels but ${sourcePass.name} outputs ${sourcePass.mipLevels}"
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
 * 一个后处理 pass 的编译描述。
 *
 * @param name pass 的唯一名称，其他 pass 可用它引用输出
 * @param vertex 可选的顶点 shader 资源；为空时使用默认全屏顶点 shader
 * @param fragment 必填的片元 shader 资源
 * @param inputs sampler 输入声明及来源
 * @param output 当前 pass 的输出目标
 * @param uniforms 每帧计算并上传的 uniform provider
 * @param requiredCapabilities 执行该 pass 必须具备的 backend 能力
 * @param optionalCapabilities 可选 backend 能力，不满足时允许降级
 * @param outputTargetId 外部 framebuffer 资源位置；仅在输出类型需要时设置
 * @param outputTargetKey 运行时复用 framebuffer 的逻辑键
 * @param colorAttachmentCount 该 pass 创建的颜色 attachment 数量
 * @param outputFormat pass 中间颜色 attachment 的实际存储格式
 * @param mipLevels pass 输出分配的 mip 层数；1 表示仅 level 0
 * @param generateMipmaps 本次 pass 完成后是否刷新非零 mip 层
 * @param reuseOutputTarget 是否尝试复用同一输出目标以减少分配
 */
internal data class PostEffectPass(
    val name: String,
    val vertex: ResourceLocation? = null,
    val fragment: ResourceLocation,
    val inputs: List<PostEffectInput> = emptyList(),
    val output: PostEffectOutput = PostEffectOutput.TEMPORARY,
    val uniforms: List<PostEffectUniform> = emptyList(),
    val requiredCapabilities: Set<RenderBackendCapability> = emptySet(),
    val optionalCapabilities: Set<RenderBackendCapability> = emptySet(),
    val outputTargetId: ResourceLocation? = null,
    val outputTargetKey: String? = null,
    val colorAttachmentCount: Int = 1,
    val outputFormat: CooTextureFormat = CooTextureFormat.RGBA8,
    val mipLevels: Int = 1,
    val generateMipmaps: Boolean = false,
    val reuseOutputTarget: Boolean = false
) {
    init {
        require(mipLevels > 0) { "Post effect pass $name must allocate at least one mip level" }
        require(!generateMipmaps || mipLevels > 1) {
            "Post effect pass $name cannot generate mipmaps with only level 0"
        }
    }
}

/**
 * 一个 fragment shader sampler 的输入声明。
 *
 * @param samplerName shader 中的 sampler uniform 名称
 * @param source 输入纹理来源，决定它读取场景、其他 pass 还是自定义资源
 * @param optional 是否允许 backend 无法提供该输入；为 false 时会使 pass 不可执行
 * @param sourcePassName 当 [source] 为 [PostEffectInputSource.PASS_OUTPUT] 时引用的 pass 名称
 * @param sourcePassAttachment 被引用 pass 的颜色 attachment 索引
 * @param sourceResourceId 当 [source] 为 [PostEffectInputSource.SCENE_RESOURCE] 时的资源位置
 * @param sourceResourceAttachment 外部场景资源的 attachment 索引
 * @param sourceResourceChannel 外部场景资源使用颜色还是深度通道
 * @param textureSlot OpenGL texture unit；为空时由编译器自动分配
 * @param expectedFormat 输入纹理的期望存储格式；为空表示无法静态确认或接受任意格式
 * @param minimumMipLevels 输入至少需要的 mip 层数；1 表示只要求 level 0
 */
internal data class PostEffectInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean = false,
    val sourcePassName: String? = null,
    val sourcePassAttachment: Int = 0,
    val sourceResourceId: ResourceLocation? = null,
    val sourceResourceAttachment: Int = 0,
    val sourceResourceChannel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
    val textureSlot: Int? = null,
    val expectedFormat: CooTextureFormat? = null,
    val minimumMipLevels: Int = 1
) {
    init {
        require(minimumMipLevels > 0) {
            "Post effect input $samplerName must require at least one mip level"
        }
    }
}

/**
 * 一个 pass 的动态 uniform 绑定。
 *
 * @param name shader uniform 名称
 * @param provider 接收当前 post effect 实例并返回待上传参数；返回 null 表示本帧跳过上传
 */
internal data class PostEffectUniform(
    val name: String,
    val provider: (PostEffectInstance) -> PostEffectParamValue?
)

/**
 * 后处理 sampler 的输入来源。
 *
 * 不同值会改变 backend 查找纹理的路径：场景来源读取当前帧资源，pass 输出读取链中间
 * 结果，自定义来源则从实例参数解析纹理。
 */
internal enum class PostEffectInputSource {
    /** 当前场景颜色副本，常用于叠加、色调映射或折射。 */
    SCENE_COLOR,
    /** 当前场景深度纹理，常用于遮挡、边缘或深度衰减。 */
    SCENE_DEPTH,
    /** 当前效果生成的 mask 纹理。 */
    MASK,
    /** bloom 流程中的亮部颜色纹理。 */
    BRIGHT_COLOR,
    /** 由参数提供的自定义纹理或现有 OpenGL texture id。 */
    CUSTOM_TEXTURE,
    /** 读取链中另一个 pass 的颜色 attachment。 */
    PASS_OUTPUT,
    /** 读取场景资源 registry 中指定资源的 attachment。 */
    SCENE_RESOURCE
}

/** 外部场景资源可绑定的通道类型。 */
internal enum class PostEffectResourceChannel {
    /** 读取颜色 attachment。 */
    COLOR,
    /** 读取深度 attachment。 */
    DEPTH
}

/** 后处理 pass 将结果写入的逻辑目标。 */
internal enum class PostEffectOutput {
    /** 写入 backend 管理的临时纹理，供后续 pass 继续采样。 */
    TEMPORARY,
    /** 写入 mask 目标，供遮罩和 bloom 流程使用。 */
    MASK,
    /** 写入 bloom 目标，供亮部合成使用。 */
    BLOOM,
    /** 写入当前帧最终屏幕。 */
    FINAL_SCREEN
}

/** 后处理 pass 使用的几何模型，决定 fragment shader 的坐标来源。 */
internal enum class PostEffectModel {
    /** 普通全屏四边形，覆盖整个目标。 */
    SCREEN_QUAD,
    /** 仅在 mask 区域绘制的全屏模型。 */
    MASKED_SCREEN,
    /** 使用世界坐标投影到屏幕的模型，绑定点会影响屏幕位置和深度。 */
    WORLD_PROJECTED,
    /** 由自定义 executor 提供几何绘制逻辑。 */
    CUSTOM
}
