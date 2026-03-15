package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectInput
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereConfig
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Random
import org.joml.Vector3f

/**
 * 一个最小化的“球体泛光测试实体”。
 *
 * 它的职责不是展示复杂业务逻辑，而是验证一套可复用的后处理球体 glow
 * 能否稳定地挂接到 `RenderEntity` 渲染框架中。
 *
 * 当前版本的设计重点有两点：
 * 1. 不再走旧的 `renderLocal + ScreenGlow` 双通路混合方案。
 *    之前那种做法会让近距离和远距离走不同算法，表现容易不一致。
 * 2. 统一改为在 `collectFrameEffects(...)` 阶段提交到帧尾的
 *    `FINAL_FRAME_POST` 链路，由 `PostGlowSphereRenderer` 负责真正绘制。
 *
 * 这样做的基本原理是：
 * - 实体本身只保存“是什么”和“用什么参数渲染”。
 * - 真正的球体绘制、shader 绑定、状态切换都下沉到公共 glow renderer。
 * - 因为所有距离都走同一条后处理路径，所以近景和远景的算法来源一致，
 *   不会再出现一部分效果来自本地球体、一部分效果来自屏幕辉光的分裂情况。
 */
@CooAutoRegister
class TestRendererEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<TestRendererEntity> {
    /**
     * 给自动 codec / 反射注册体系使用的无参构造。
     *
     * 渲染实体在网络反序列化、自动注册或测试环境里经常需要先创建一个“空实例”，
     * 再由外部回填字段，因此这里保留一个标准的零参入口。
     */
    constructor() : this(null, Vec3.ZERO)

    companion object {
        /**
         * 球体的基础世界半径。
         *
         * `PostGlowSphereRenderer` 内部会再根据不同 layer 做二次缩放，
         * 所以这里表示的是“核心配置半径”，不是最终每一层真正绘制到 GPU 的绝对半径。
         */
        const val SPHERE_RADIUS: Float = 5.0f

        /**
         * 本体发光强度。
         *
         * 主要影响核心和主体部分的能量输出，数值越高，球心区域越亮。
         */
        private const val GLOW_INTENSITY: Float = 6.4f

        /**
         * 外层 halo 的强度。
         *
         * 这个值更偏向控制外扩的泛光圈，而不是核心实体本身。
         */
        private const val HALO_INTENSITY: Float = 3.2f

        /**
         * halo 半径放大因子。
         *
         * shader 会结合 layer 自己的 radiusScale 一起使用，用来控制外圈光晕张开的范围。
         */
        private const val HALO_RADIUS_SCALE: Float = 1.72f

        /**
         * 边缘菲涅耳强度。
         *
         * 基本原理是让接近轮廓线的位置获得更强的壳层感和边缘发光感，
         * 用来避免球体看起来过于“平”。
         */
        private const val FRESNEL_STRENGTH: Float = 1.38f

        /**
         * shader 内部时间流速。
         *
         * 它不会改变实体的世界运动，只影响 shader 中噪声、脉动、流动感的播放速度。
         */
        private const val ANIMATION_SPEED: Float = 1.08f

        /**
         * 过亮限制值。
         *
         * shader 会在输出前做一次 soft limit，避免极高亮度把颜色直接推爆，
         * 从而减轻“发灰”“发白”或边缘能量失控的问题。
         */
        private const val OVERBRIGHT_CLAMP: Float = 6.6f

        /**
         * 该 RenderEntity 对外暴露的稳定渲染 ID。
         *
         * 客户端管理器会用它来识别这是哪一类渲染实体。
         */
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_shader")
    }

    /**
     * 为测试实体生成一个稳定随机源。
     *
     * 这里不追求可复现种子，而是单纯想让每个测试球体在生成时拥有一个不同颜色，
     * 方便在场景里观察多个实例的叠加效果。
     */
    private val random = Random()

    /**
     * 当前实体的基础发光颜色。
     *
     * 它在实例创建时随机生成，之后保持不变。
     * 真正送入 shader 时会复制一份，避免公共配置对象意外共享可变引用。
     */
    val randomColor: Vector3f = Vector3f(
        random.nextFloat(),
        random.nextFloat(),
        random.nextFloat()
    )

    /**
     * 返回当前实体类型的渲染 ID。
     *
     * 这是 `RenderEntity` 体系最基础的识别入口之一，
     * 网络同步和客户端镜像恢复时都依赖它来找到正确的 renderer。
     */
    override fun getRenderID(): ResourceLocation = ID

    /**
     * 在客户端实例第一次可用时初始化公共 glow renderer。
     *
     * 这里不直接创建自己的 buffer / shader，而是显式委托给
     * `PostGlowSphereRenderer.initialize()`。
     *
     * 基本原理是把 GPU 资源集中到公共组件管理：
     * - 所有复用这套 glow 的实体共享同一份球体网格和 shader 程序
     * - 避免每种测试实体各自维护一份重复资源
     * - 后续如果要在正式 API 中复用，也不需要再从 test 目录搬代码
     */
    override fun initialize(instance: RenderEntityInstance<TestRendererEntity>) {
        PostGlowSphereRenderer.initialize()
    }

    /**
     * 在“帧效果收集阶段”提交本实体的 glow 绘制任务。
     *
     * 这是当前类最关键的方法。它不直接在这里操作 OpenGL，
     * 而是把一次渲染请求封装成 `FrameEffectSubmission` 提交给收集器。
     *
     * 整个流程的基本原理是：
     * 1. `ClientRenderEntityManager` 在帧尾遍历所有实体。
     * 2. 当前实体通过 `collectFrameEffects(...)` 告诉系统：
     *    “我需要在 FINAL_FRAME_POST 阶段画一个 glow 球体。”
     * 3. `PostGlowSphereRenderer` 在真正执行时，使用当前帧的 view/proj/context
     *    和这里提供的配置完成球体绘制。
     *
     * 这样做的好处是：
     * - 世界内的水、粒子、实体、云的遮挡关系统一由帧尾路径处理
     * - 不需要本地 world pass 和 screen glow pass 各画一遍
     * - 近景和远景都走同一套球体 shader 与同一份 layer 配置
     */
    override fun collectFrameEffects(
        input: FrameEffectInput<TestRendererEntity>,
        collector: FrameEffectCollector
    ) {
        val entity = input.instance.entity
        PostGlowSphereRenderer.submit(
            collector = collector,
            effectId = ID.toString(),
            sourceInstanceId = entity.uuid.toString(),
            entity = entity,
            frameContext = input.frameContext,
            config = entity.createGlowConfig()
        )
    }

    /**
     * 把当前实体状态转换成公共 glow renderer 可消费的配置对象。
     *
     * 这个方法的作用是做“实体参数层”和“渲染实现层”的解耦：
     * - 实体只决定自己想要什么视觉参数
     * - 公共 renderer 只关心收到的 `PostGlowSphereConfig`
     *
     * 这里没有做任何距离补偿或屏幕尺寸自适应计算，
     * 目的是保证 `TestRendererEntity` 与 `TestPersistentGlowSphereEntity`
     * 使用完全一致的球体 glow 算法来源。
     *
     * 其中 `glowColor = Vector3f(randomColor)` 要特别说明：
     * - `Vector3f` 是可变对象
     * - 这里复制一份是为了避免后续 shader 配置层误改实体自身的颜色字段
     */
    private fun createGlowConfig(): PostGlowSphereConfig {
        return PostGlowSphereConfig(
            radius = SPHERE_RADIUS,
            intensity = GLOW_INTENSITY,
            haloIntensity = HALO_INTENSITY,
            haloRadiusScale = HALO_RADIUS_SCALE,
            fresnelStrength = FRESNEL_STRENGTH,
            animationSpeed = ANIMATION_SPEED,
            overbrightClamp = OVERBRIGHT_CLAMP,
            glowColor = Vector3f(randomColor)
        )
    }
}
