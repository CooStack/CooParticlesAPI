package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityRenderPass
import cn.coostack.cooparticlesapi.renderer.glow.BrightSourceOrbProfile
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveOrbGlowCompensation
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloom
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloomContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.max

/**
 * 演示“POST_PROCESS 球体本体 + PersistentBloom 帧尾外辉光”的 RenderEntity 样板。
 *
 * 这类实体的链路和 `TestGlowSphereEntity` 不同：
 * 1. 本体不在 `WORLD` pass 直接合成，而是进入 `POST_PROCESS` pipe。
 * 2. `render(...)` 只负责把可见球体和失真/遮罩写入当前 post-process 目标。
 * 3. 额外的远距外发光不靠 `ScreenGlow`，而是通过 `PersistentBloomContextProvider`
 *    在帧尾交给 `ClientPersistentBloomManager` 统一做 blur + composite。
 *
 * 这个类更适合当“稳定辉光球”模板，而不是“运行时高频调参同步”模板：
 * 当前没有覆盖 `loadProfileFromEntity(...)`，因此 `TOGGLE` 只会稳定同步基类字段，
 * 如果需要把 radius/intensity/color 等动态回写到客户端镜像，需要额外补这个钩子。
 */
class TestPersistentGlowSphereEntity(world: Level?) : RenderEntity(world), PersistentBloomContextProvider {
    companion object {
        private const val DIRECT_FADE_START_PX = 24.0f
        private const val DIRECT_FADE_END_PX = 7.0f

        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_persistent_glow_sphere"
        )

        /**
         * `RenderEntity.createCodec(...)` 会自动处理 `uuid/pos/canceled/age`，
         * 这里仅同步球体外观相关的额外字段。
         */
        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestPersistentGlowSphereEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.intensity)
                buf.writeFloat(entity.haloIntensity)
                buf.writeFloat(entity.haloRadiusScale)
                buf.writeFloat(entity.fresnelStrength)
                buf.writeFloat(entity.distanceCompensation)
                buf.writeFloat(entity.animationSpeed)
                buf.writeFloat(entity.overbrightClamp)
                buf.writeFloat(entity.glowColor.x)
                buf.writeFloat(entity.glowColor.y)
                buf.writeFloat(entity.glowColor.z)
            },
            { buf, entity ->
                entity.radius = buf.readFloat()
                entity.intensity = buf.readFloat()
                entity.haloIntensity = buf.readFloat()
                entity.haloRadiusScale = buf.readFloat()
                entity.fresnelStrength = buf.readFloat()
                entity.distanceCompensation = buf.readFloat()
                entity.animationSpeed = buf.readFloat()
                entity.overbrightClamp = buf.readFloat()
                entity.glowColor = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )

        private val sphereBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genBall(1f, 96, 144),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val glowShader = ShaderProgramBuilder()
            .vertex("test/vtx/glow_sphere.vsh")
            .fragment("test/frag/glow_sphere.fsh")
            .build()

        private var initialized = false

        /**
         * 静态 GL 资源只初始化一次，避免每个客户端镜像都重复创建 VBO / shader。
         */
        private fun initStatic() {
            if (initialized) return
            initialized = true
            sphereBuffer.init()
            glowShader.init()
        }

        fun reloadStaticResources() {
            sphereBuffer.release()
            glowShader.release()
            initialized = false
        }
    }

    /**
     * 这些参数全部用 `tracked(...)` 包装，服务端变更后会触发 dirty/requestSync 流程。
     * 但要让 `TOGGLE` 真正回写这些字段，仍然需要子类覆盖 `loadProfileFromEntity(...)`。
     */
    var radius by tracked(4.1f)
    var intensity by tracked(6.8f)
    var haloIntensity by tracked(3.4f)
    var haloRadiusScale by tracked(1.72f)
    var fresnelStrength by tracked(1.38f)
    var distanceCompensation by tracked(1.0f)
    var animationSpeed by tracked(1.08f)
    var overbrightClamp by tracked(6.6f)
    var glowColor by tracked(Vector3f(0.58f, 0.88f, 1.30f))

    private val worldPosition = Vector3f()
    private val coreGlowColor = Vector3f()
    private val haloGlowColor = Vector3f()

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    /**
     * 该效果必须走帧尾 pipe：
     * 本体先写入 `glowSphereDistortion`，后续再由 post-process 和 persistent bloom 统一合成。
     */
    override fun getRenderPass(): RenderEntityRenderPass = RenderEntityRenderPass.POST_PROCESS

    override fun release() {
    }

    /**
     * 直接绘制层。
     *
     * 这里不会生成最终的外辉光，只负责：
     * - 依据 `DistanceAdaptiveGlow.computeOrbBlend(...)` 决定近景是否还需要绘制球体本体
     * - 把球体本体和相关 mask/失真参数写进当前 post-process pipe
     */
    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        val context = createGlowContext(tickDelta, viewMatrix, projMatrix)
        val blend = computeBlend(context)
        if (blend.directWeight <= 1.0e-3f) {
            return
        }
        val compensation = computeOrbCompensation(blend.projectedRadiusPx)
        val sourceProfile = createSourceProfile(blend.projectedRadiusPx)
        val directProfile =
            DistanceAdaptiveGlow.persistentDirectSphereProfileFromProjectedRadiusPx(blend.projectedRadiusPx)

        RenderSystem.disableBlend()
        RenderSystem.enableCull()
        RenderSystem.depthMask(false)
        glowShader.useOnContext {
            matrices.pushMatrix()
            matrices.scale(radius, radius, radius)
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat3("color", glowColor)
            setFloat("intensity", intensity)
            setFloat("haloIntensity", haloIntensity)
            setFloat("haloRadiusScale", haloRadiusScale)
            setFloat("fresnelStrength", fresnelStrength)
            setFloat("animationSpeed", animationSpeed)
            setFloat("projectedRadiusPx", blend.projectedRadiusPx)
            setFloat("farPersistence", compensation.persistence)
            setFloat("directOpacity", blend.directWeight)
            setFloat("overbrightClamp", overbrightClamp)
            setFloat("coreWhiteness", sourceProfile.coreWhiteness)
            setFloat("shellVisibility", sourceProfile.shellVisibility)
            setFloat("sourceHaloSpread", sourceProfile.haloSpread)
            setFloat("solidCoreFill", directProfile.solidCoreFill)
            setFloat("outerShellOpacity", directProfile.outerShellOpacity)
            setFloat("distortionOpacity", directProfile.distortionOpacity)
            setFloat("time", getTime(tickDelta))
            sphereBuffer.draw()
            matrices.popMatrix()
        }
        RenderSystem.disableCull()
        RenderSystem.depthMask(true)
    }

    /**
     * `PersistentBloomContextProvider` 的核心回调。
     *
     * `render(...)` 负责近景本体；
     * 这个方法负责把“需要在帧尾额外扩散的辉光层”描述为 `PersistentBloom`，
     * 交给 `ClientPersistentBloomManager` 统一排序、裁剪、blur 和 composite。
     */
    override fun collectPersistentBlooms(
        context: ScreenGlowRenderContext,
        output: MutableList<PersistentBloom>
    ) {
        val blend = computeBlend(context)
        val haloWeight = mix(0.68f, 1.0f, 1.0f - blend.directWeight)
        if (haloWeight <= 1.0e-3f) {
            return
        }

        val compensation = computeOrbCompensation(blend.projectedRadiusPx)
        val sourceProfile = createSourceProfile(blend.projectedRadiusPx)
        val haloProfile = DistanceAdaptiveGlow.persistentHaloProfileFromProjectedRadiusPx(blend.projectedRadiusPx)
        val baseIntensity = ((intensity * 0.10f) + (haloIntensity * 0.38f)) * haloWeight
        val bloomRadius = radius * compensation.radiusScale
        coreGlowColor.set(glowColor).lerp(Vector3f(1.0f, 1.0f, 1.0f), sourceProfile.coreWhiteness)
        haloGlowColor.set(glowColor).lerp(Vector3f(1.0f, 1.0f, 1.0f), sourceProfile.coreWhiteness * 0.10f)
        coreGlowColor.lerp(haloGlowColor, 0.22f + sourceProfile.shellVisibility * 0.08f)

        output.add(
            PersistentBloom(
                position = Vector3f(currentWorldPosition()),
                color = Vector3f(haloGlowColor),
                radius = bloomRadius.coerceAtLeast(radius * 0.92f),
                intensity = (baseIntensity * compensation.intensityScale).coerceAtMost(9.2f),
                softness = (compensation.softness + 0.06f - sourceProfile.shellVisibility * 0.04f)
                    .coerceIn(0.56f, 0.84f),
                softOcclusionFloor = haloProfile.softOcclusionFloor,
                haloRadiusScale = haloRadiusScale * haloProfile.haloRadiusScale * 0.86f,
                brightnessNormalization = haloProfile.brightnessNormalization,
                haloOpacity = haloProfile.haloOpacity,
                blurSigma = haloProfile.blurSigma,
                blurRange = haloProfile.blurRange
            )
        )
    }

    private fun currentWorldPosition(): Vector3f {
        return worldPosition.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
    }

    private fun transitionRadius(): Float {
        return max(radius * 0.92f, 0.12f)
    }

    /**
     * `ScreenGlowRenderContext` 是屏幕投影分析的统一输入。
     * `computeOrbBlend(...)` 会根据世界半径投影到屏幕后的像素尺寸，
     * 给出“近景直接绘制权重”和“远景辉光权重”。
     */
    private fun computeBlend(context: ScreenGlowRenderContext) = DistanceAdaptiveGlow.computeOrbBlend(
        worldPosition = currentWorldPosition(),
        worldRadius = transitionRadius(),
        context = context,
        directFadeStartPx = DIRECT_FADE_START_PX,
        directFadeEndPx = DIRECT_FADE_END_PX,
        screenGlowFadeStartPx = 72.0f,
        screenGlowFadeEndPx = 18.0f
    )

    /**
     * 当球体投影尺寸变得很小时，`DistanceAdaptiveGlow` 会返回一组远距补偿参数，
     * 用来维持可读性，避免球体远处完全缩成一点。
     */
    private fun computeOrbCompensation(projectedRadiusPx: Float): DistanceAdaptiveOrbGlowCompensation {
        val base = DistanceAdaptiveGlow.farOrbCompensationFromProjectedRadiusPx(projectedRadiusPx)
        val strength = distanceCompensation.coerceIn(0.0f, 1.0f)
        return DistanceAdaptiveOrbGlowCompensation(
            persistence = base.persistence * strength,
            radiusScale = mix(1.0f, base.radiusScale, strength),
            intensityScale = mix(1.0f, base.intensityScale, strength),
            softness = mix(0.58f, base.softness, strength)
        )
    }

    /**
     * 亮源 profile 用来区分近景和远景时的核心白化、壳层可见度、halo 扩散范围。
     */
    private fun createSourceProfile(projectedRadiusPx: Float): BrightSourceOrbProfile {
        return DistanceAdaptiveGlow.brightSourceOrbProfileFromProjectedRadiusPx(projectedRadiusPx)
    }

    /**
     * 把当前帧的相机位置、矩阵和屏幕尺寸组装成 glow 分析上下文。
     * 这一套上下文会同时被直接绘制层和 persistent bloom 采样层复用。
     */
    private fun createGlowContext(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ): ScreenGlowRenderContext {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera.position
        return ScreenGlowRenderContext(
            tickDelta = tickDelta,
            cameraWorldPos = Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat()),
            viewMatrix = Matrix4f(viewMatrix),
            viewRotationMatrix = Matrix3f(viewMatrix),
            inverseViewRotationMatrix = Matrix3f(viewMatrix).invert(),
            projMatrix = Matrix4f(projMatrix),
            screenSize = Vector2f(
                minecraft.mainRenderTarget.width.toFloat(),
                minecraft.mainRenderTarget.height.toFloat()
            )
        )
    }

    private fun mix(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
