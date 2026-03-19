package cn.coostack.cooparticlesapi.renderer.glow

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.builtin.PostGlowSphereRenderRequest
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA

/**
 * 描述单层直绘球体中“球心与外壳细节”的局部 profile。
 *
 * 这个类不控制球体画多大，也不决定整层最终有多亮，
 * 它只负责告诉 shader：当正在绘制这一层时，核心区域和外壳区域应该长什么样。
 *
 * 三个字段的基本含义是：
 * - `solidCoreFill`
 *   控制核心区域的填充程度。值越高，中心越容易保持实心亮核。
 * - `outerShellOpacity`
 *   控制外壳和外层过渡的可见度。值越高，球体轮廓越容易被读出来。
 * - `distortionOpacity`
 *   控制额外扰动层的存在感。它不是主亮度来源，更多是补一点动态细节。
 */
data class PersistentDirectSphereProfile(
    val solidCoreFill: Float,
    val outerShellOpacity: Float,
    val distortionOpacity: Float
)

/**
 * 描述一个 glow layer 应该如何绘制。
 *
 * 这套公共 glow API 的核心思路不是只画一个球，而是把同一个发光体拆成多层：
 * - 核心层负责保住本体和中心亮度
 * - halo 层负责把能量向外扩散
 * - 如果未来需要，也可以继续追加更多层
 *
 * 所以这个类描述的是“某一层怎么画”，而不是“整个光球是什么样”。
 *
 * 可以把字段按职责分成四组来理解：
 * 1. 空间尺度：
 *    `radiusScale`、`haloRadiusScale`
 * 2. 亮度权重：
 *    `directOpacity`、`intensityScale`、`haloIntensityScale`、`overbrightScale`
 * 3. 形状控制：
 *    `haloCenterSuppress`、`haloCenterSuppressRadius`、`silhouetteFadeStart`
 * 4. 组成部分占比：
 *    `coreOpacityScale`、`bodyOpacityScale`、`haloOpacityScale`、`shellOpacityScale`
 *
 * 其中新增的两个字段专门用于解决“扩散往里缩”的问题：
 * - `haloCenterSuppress`
 *   决定 halo 层是否主动压低中心区域的贡献。
 * - `haloCenterSuppressRadius`
 *   决定从多大的径向范围开始，把 halo 的能量更多交给外圈。
 *
 * 也就是说，这两个参数不是让球变小，而是让 halo 的亮度分布从“中心堆叠”
 * 改成“向外扩散”。
 */
data class PostGlowSphereLayerProfile(
    val radiusScale: Float,
    val directOpacity: Float,
    val intensityScale: Float,
    val haloIntensityScale: Float,
    val haloRadiusScale: Float,
    val haloCenterSuppress: Float,
    val haloCenterSuppressRadius: Float,
    val overbrightScale: Float,
    val coreOpacityScale: Float,
    val bodyOpacityScale: Float,
    val haloOpacityScale: Float,
    val shellOpacityScale: Float,
    val silhouetteFadeStart: Float,
    val profile: PersistentDirectSphereProfile
)

/**
 * 默认核心层 preset。
 *
 * 这一层的职责是保住发光体本身：
 * - 半径基本等于配置半径
 * - 不压中心
 * - 核心和主体部分贡献较高
 * - halo 只是辅助结构，不负责大范围扩散
 *
 * 如果把整个光球想成“灯芯 + 外晕”，
 * 这一层对应的就是灯芯和它周围最近的亮部。
 */
val DEFAULT_POST_GLOW_SPHERE_CORE_LAYER = PostGlowSphereLayerProfile(
    radiusScale = 1.0f,
    directOpacity = 1.0f,
    intensityScale = 1.0f,
    haloIntensityScale = 1.0f,
    haloRadiusScale = 1.0f,
    haloCenterSuppress = 0.0f,
    haloCenterSuppressRadius = 0.0f,
    overbrightScale = 1.0f,
    coreOpacityScale = 1.0f,
    bodyOpacityScale = 1.0f,
    haloOpacityScale = 0.78f,
    shellOpacityScale = 0.48f,
    silhouetteFadeStart = 0.72f,
    profile = PersistentDirectSphereProfile(
        solidCoreFill = 0.95f,
        outerShellOpacity = 0.16f,
        distortionOpacity = 0.02f
    )
)

/**
 * 默认 halo 层 preset。
 *
 * 这一层的任务和核心层相反，它主要负责“向外散”：
 * - 半径更大
 * - 中心更弱
 * - 中外圈更亮
 * - shell 与 halo 的存在感更强
 *
 * 当前这组默认值专门针对一个问题做过修正：
 * 用户感觉光球整体尺寸其实已经够大，但亮度还是主要堆在中心，
 * 视觉上像是“往里缩”，而不是“向外扩散”。
 *
 * 所以这里通过：
 * - 更大的 `radiusScale`
 * - 更高的 `haloIntensityScale`
 * - 明显开启的 `haloCenterSuppress`
 * - 更大的 `haloCenterSuppressRadius`
 *
 * 让这一层从“扩大版中心亮球”改成真正的外扩光晕层。
 */
val DEFAULT_POST_GLOW_SPHERE_HALO_LAYER = PostGlowSphereLayerProfile(
    radiusScale = 2.70f,
    directOpacity = 0.24f,
    intensityScale = 0.16f,
    haloIntensityScale = 2.85f,
    haloRadiusScale = 2.45f,
    haloCenterSuppress = 0.92f,
    haloCenterSuppressRadius = 0.64f,
    overbrightScale = 0.72f,
    coreOpacityScale = 0.0f,
    bodyOpacityScale = 0.0f,
    haloOpacityScale = 2.10f,
    shellOpacityScale = 0.30f,
    silhouetteFadeStart = 0.18f,
    profile = PersistentDirectSphereProfile(
        solidCoreFill = 0.0f,
        outerShellOpacity = 0.88f,
        distortionOpacity = 0.0f
    )
)

/**
 * 默认 layer 顺序。
 *
 * 这里采用“先 halo，后 core”的顺序：
 * - 先让大范围扩散层铺一层底
 * - 再让核心层把中心亮核和主体结构补回来
 *
 * 这样做的结果通常更稳定：
 * 中心不会因为 halo 层过大而变糊，外圈也仍然保留足够扩散感。
 */
val DEFAULT_POST_GLOW_SPHERE_LAYERS = listOf(
    DEFAULT_POST_GLOW_SPHERE_HALO_LAYER,
    DEFAULT_POST_GLOW_SPHERE_CORE_LAYER
)

/**
 * 帧尾 glow 球体效果的参数配置。
 *
 * 这个配置会作为 `BuiltinRenderEffectDescriptors.postGlowSphere(...)`
 * 的 payload 一部分被提交到 effect graph。
 *
 * 如果说 `PostGlowSphereLayerProfile` 描述的是“单层怎么画”，
 * 那这个类描述的就是“整个发光球体是什么样”。
 */
data class PostGlowSphereConfig(
    /** 球体主体半径。 */
    val radius: Float,
    /** 主体亮度强度。 */
    val intensity: Float,
    /** 外层 halo 强度。 */
    val haloIntensity: Float,
    /** halo 半径相对主体半径的倍率。 */
    val haloRadiusScale: Float,
    /** 菲涅耳边缘增强强度。 */
    val fresnelStrength: Float,
    /** 动画流动速度。 */
    val animationSpeed: Float,
    /** 过亮钳制值。 */
    val overbrightClamp: Float,
    /** glow 主颜色。 */
    val glowColor: Vector3f,
    /** 分层配置列表，用于形成更复杂的球体体积层次。 */
    val layers: List<PostGlowSphereLayerProfile> = DEFAULT_POST_GLOW_SPHERE_LAYERS
)

/**
 * 可复用的后处理球体 glow 渲染器。
 *
 * 它不是某个测试实体的私有实现，而是一层公共渲染能力。
 *
 * 整条链路可以概括成三步：
 * 1. 调用方先准备 `PostGlowSphereConfig`
 * 2. 调用 `submit(...)`，把一次 glow 球体绘制请求提交到帧效果系统
 * 3. 帧尾真正执行时，由 `render(...)` / `drawSphere(...)` / `drawLayer(...)`
 *    完成 GPU 状态切换、uniform 写入和球体绘制
 *
 * 之所以把它独立出来，而不是继续塞在 test 目录里，目的很明确：
 * - 让多个实体复用完全一致的 glow 算法
 * - 避免“测试代码逐渐变成公共 API”的结构污染
 * - 后续如果正式开放这一能力，可以直接复用这里的入口
 */
@Deprecated(
    message = "Legacy compatibility only. RenderEntity glow should use content-driven MASK_BLOOM.",
    replaceWith = ReplaceWith("BuiltinRenderEffectDescriptors.sharedModelMaskBloom(...)")
)
object PostGlowSphereRenderer {
    /**
     * 共享球体网格。
     *
     * 这里生成的是单位球，不带任何世界尺寸。
     * 真正画到屏幕上的大小，会在每一层绘制时通过矩阵缩放决定。
     *
     * 所有复用这套 glow 的实体都共享这一份顶点数据，
     * 可以避免重复生成球体网格。
     */
    private val sphereBuffer = SimpleVertexBuffer().apply {
        setVertexes(
            ShaderUtil.genBall(1f, 96, 144),
            CooVertexFormat.POINT_FORMAT
        )
    }

    /**
     * 共享的 glow shader 程序。
     *
     * 顶点着色器负责：
     * - 把单位球变换到世界空间和观察空间
     * - 传递法线、局部坐标等片元阶段需要的数据
     *
     * 片元着色器负责：
     * - 根据层配置生成 core / body / halo / shell 的分布
     * - 处理 halo 中心抑制，让扩散更多向外释放
     */
    private val glowDirectShader = AdvancedShaderProgramBuilder()
        .vertex("world/vtx/glow_sphere.vsh")
        .fragment("world/frag/glow_sphere_direct.fsh")
        .build()

    /**
     * 标记共享资源是否已经初始化。
     *
     * 这是最简单的一层懒加载保护，
     * 避免多个实体反复对同一份 shader 和 buffer 调用初始化。
     */
    private var initialized = false

    /**
     * 初始化共享的球体网格和 shader。
     *
     * 一般在实体 `initialize(...)` 阶段调用，
     * 但 `render(...)` 内部也会再次兜底调用一次，确保外部即使忘了手动初始化也不会直接失效。
     *
     * 这里使用 `@Synchronized`，是为了防止极端情况下并发重复初始化。
     */
    @Synchronized
    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true
        sphereBuffer.init()
        glowDirectShader.init()
    }

    fun renderRequests(requests: List<PostGlowSphereRenderRequest>) {
        requests.forEach { request ->
            render(request.entity, request.frameContext, request.config)
        }
    }

    /**
     * 真正执行一次 glow 球体绘制。
     *
     * 这个方法主要做四件事：
     * 1. 确保共享资源已经准备好
     * 2. 根据实体位置和当前 `tickDelta` 生成模型矩阵
     * 3. 配置本次绘制需要的 GPU 状态
     * 4. 调用 `drawSphere(...)` 按 layer 顺序绘制
     *
     * 这里使用加色混合 `SRC_ALPHA, ONE`，原因是发光体更适合“能量累加”式叠加，
     * 而不是普通透明物体那种标准 alpha 混合。
     *
     * 同时保留 depth test、关闭 depth write：
     * - depth test 负责避免发光直接穿过前景物体
     * - depthMask(false) 则避免光球自己污染深度缓冲
     */
    fun render(entity: RenderEntity, context: RenderFrameContext, config: PostGlowSphereConfig) {
        initialize()

        val matrices = Matrix4fStack(16)
        RenderUtil.setRenderStackWithEntity(matrices, entity, context.tickDelta)

        val compositeTarget = context.finalCompositeTarget ?: ClientRenderPipelineManager.currentFinalCompositeTarget()
        compositeTarget.bindWrite(false)
        CooParticlesConstants.logger.debug(
            "Post glow sphere draw source={} target={} targetFbo={}",
            entity.uuid,
            context.resolvedTargetLabel,
            compositeTarget.frameBufferId
        )
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE)
        RenderSystem.enableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        try {
            drawSphere(
                entity = entity,
                matrices = matrices,
                tickDelta = context.tickDelta,
                viewMatrix = context.viewMatrix,
                projMatrix = context.projMatrix,
                config = config
            )
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.disableCull()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
        }
    }

    /**
     * 按配置顺序绘制整个 glow 球体的全部 layer。
     *
     * 这里的职责非常单纯：
     * - 遍历 `config.layers`
     * - 每一层调用一次 `drawLayer(...)`
     *
     * 这样设计的好处是，把“整体层次组织”和“单层具体怎么画”分离开。
     * 以后如果想追加第三层、第四层，只需要扩展 layer 列表，不需要改底层执行骨架。
     */
    private fun drawSphere(
        entity: RenderEntity,
        matrices: Matrix4fStack,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        config: PostGlowSphereConfig
    ) {
        for (layer in config.layers) {
            drawLayer(
                entity = entity,
                matrices = matrices,
                tickDelta = tickDelta,
                viewMatrix = viewMatrix,
                projMatrix = projMatrix,
                config = config,
                layer = layer
            )
        }
    }

    /**
     * 绘制单独一层 glow layer。
     *
     * 这是整套公共 glow API 最底层的绘制步骤：
     * - 根据 `config.radius * layer.radiusScale` 算出这一层实际半径
     * - 写入 view / proj / model 矩阵
     * - 把这一层需要的能量、透明度和形状参数传给 shader
     * - 用共享球体网格执行一次 draw
     *
     * 这里几个容易混淆的字段需要特别说明：
     * - `haloRadiusScale`
     *   决定 shader 内部 halo 形状扩展的程度
     * - `haloCenterSuppress`
     *   决定是否主动压掉 halo 层中心亮度，避免能量重新缩回球心
     * - `core/body/halo/shellOpacityScale`
     *   决定这一层更偏向核心、主体、光晕还是边缘壳层
     *
     * `pushMatrix()` 和 `popMatrix()` 必须成对出现，
     * 否则当前层的缩放会污染下一层。
     */
    private fun drawLayer(
        entity: RenderEntity,
        matrices: Matrix4fStack,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        config: PostGlowSphereConfig,
        layer: PostGlowSphereLayerProfile
    ) {
        glowDirectShader.useOnContext {
            matrices.pushMatrix()
            try {
                val scaledRadius = config.radius * layer.radiusScale
                matrices.scale(scaledRadius, scaledRadius, scaledRadius)
                setMatrix4("projMat", projMatrix)
                setMatrix4("viewMat", viewMatrix)
                setMatrix4("transMat", matrices)
                setFloat3("color", config.glowColor)
                setFloat("intensity", config.intensity * layer.intensityScale)
                setFloat("haloIntensity", config.haloIntensity * layer.haloIntensityScale)
                setFloat("haloRadiusScale", config.haloRadiusScale * layer.haloRadiusScale)
                setFloat("haloCenterSuppress", layer.haloCenterSuppress)
                setFloat("haloCenterSuppressRadius", layer.haloCenterSuppressRadius)
                setFloat("fresnelStrength", config.fresnelStrength)
                setFloat("animationSpeed", config.animationSpeed)
                setFloat("directOpacity", layer.directOpacity)
                setFloat("overbrightClamp", config.overbrightClamp * layer.overbrightScale)
                setFloat("coreOpacityScale", layer.coreOpacityScale)
                setFloat("bodyOpacityScale", layer.bodyOpacityScale)
                setFloat("haloOpacityScale", layer.haloOpacityScale)
                setFloat("shellOpacityScale", layer.shellOpacityScale)
                setFloat("silhouetteFadeStart", layer.silhouetteFadeStart)
                setFloat("solidCoreFill", layer.profile.solidCoreFill)
                setFloat("outerShellOpacity", layer.profile.outerShellOpacity)
                setFloat("distortionOpacity", layer.profile.distortionOpacity)
                setFloat("time", entity.getTime(tickDelta))
                sphereBuffer.draw()
            } finally {
                matrices.popMatrix()
            }
        }
    }
}
