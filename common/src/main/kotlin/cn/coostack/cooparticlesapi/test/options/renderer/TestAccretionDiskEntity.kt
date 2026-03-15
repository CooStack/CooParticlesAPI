package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectInput
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectSubmission
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE7
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.glActiveTexture
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glGetInteger

@CooAutoRegister
class TestAccretionDiskEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<TestAccretionDiskEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_accretion_disk"
        )

        private val screenBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genSquareUVScreen(
                    Vector3f(-1f, 1f, 0f),
                    Vector3f(1f, 1f, 0f),
                    Vector3f(1f, -1f, 0f),
                    Vector3f(-1f, -1f, 0f)
                ),
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
        }

        private val sceneShader = ShaderProgramBuilder()
            .vertex("pipe/vertexes/screen.vsh")
            .fragment("test/frag/accretion_disk.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            screenBuffer.init()
            sceneShader.init()
        }
    }

    /**
     * 世界单位，既是 RenderEntity 影响域包围球半径，也是 shader 的积分裁剪边界。
     */
    @field:CodecField
    var radius: Float = 12.0f

    /**
     * Schwarzschild 事件视界半径 Rs，单位与世界坐标一致。
     * shader 内约定 G = c = 1。
     */
    @field:CodecField
    var schwarzschildRadius: Float = 0.92f

    @field:CodecField
    var diskInnerRadius: Float = 2.9f

    @field:CodecField
    var diskOuterRadius: Float = 7.8f

    @field:CodecField
    var diskHalfThickness: Float = 0.30f

    /**
     * 以 Kelvin 近似标定盘内缘温度。
     */
    @field:CodecField
    var diskTemperatureScale: Float = 9600.0f

    @field:CodecField
    var lensingStrength: Float = 1.08f

    @field:CodecField
    var spinSpeed: Float = 0.92f

    @field:CodecField
    var stepCount: Int = 168

    @field:CodecField
    var maxDistance: Float = 34.0f

    @field:CodecField
    var diskDensity: Float = 4.6f

    @field:CodecField
    var diskEmissionStrength: Float = 2.35f

    @field:CodecField
    var diskNormal: Vector3f = Vector3f(0f, 1f, 0f)

    /**
     * 盘的轻度色调偏置，不取代物理温度映射。
     */
    @field:CodecField
    var diskColor: Vector3f = Vector3f(1.18f, 1.06f, 0.78f)

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<TestAccretionDiskEntity>) {
        initStatic()
    }

    override fun collectFrameEffects(input: FrameEffectInput<TestAccretionDiskEntity>, collector: FrameEffectCollector) {
        collector.submit(
            FrameEffectSubmission(
                effectId = ID.toString(),
                sourceInstanceId = uuid.toString(),
                requiredCapabilities = setOf(RenderBackendCapability.FINAL_FRAME_POST)
            ) {
                renderFrameEffect(input.frameContext)
            }
        )
    }

    private fun renderFrameEffect(context: RenderFrameContext) {
        val minecraft = Minecraft.getInstance()
        val depthTextureId = minecraft.mainRenderTarget.depthTextureId
        if (depthTextureId <= 0) {
            return
        }
        val cameraPosition = minecraft.gameRenderer.mainCamera.position
        val cameraWorldPos = Vector3f(
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        val blackHoleWorldPos = Vector3f(
            pos.x.toFloat(),
            pos.y.toFloat(),
            pos.z.toFloat()
        )
        val blackHoleCameraRelativePos = Vector3f(blackHoleWorldPos).sub(cameraWorldPos)
        val normalizedDiskNormal = Vector3f(diskNormal).normalize()
        val inverseProjMatrix = Matrix4f(context.projMatrix).invert()
        val viewRotationMatrix = Matrix3f(context.viewMatrix)
        val inverseViewRotationMatrix = Matrix3f(viewRotationMatrix).invert()
        val effectRadiusWorld = maxOf(
            radius,
            diskOuterRadius + schwarzschildRadius * 4.5f,
            schwarzschildRadius * 6.0f
        )
        val lightingInfluenceWorld = maxOf(
            radius * 1.55f,
            diskOuterRadius * 2.15f + diskEmissionStrength * 3.40f,
            effectRadiusWorld * 1.30f
        )
        val traceDistanceWorld = maxOf(maxDistance, effectRadiusWorld * 2.35f)
        val screenSize = Vector2f(
            minecraft.mainRenderTarget.width.toFloat(),
            minecraft.mainRenderTarget.height.toFloat()
        )

        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        try {
            TestRelativisticShaderPipelines.renderAccretionDisk {
                sceneShader.useOnContext {
                    setMatrix4("projMat", context.projMatrix)
                    setMatrix4("inverseProjMat", inverseProjMatrix)
                    setMatrix3f("viewRotationMat", viewRotationMatrix)
                    setMatrix3f("inverseViewRotationMat", inverseViewRotationMatrix)
                    setFloat("time", getTime(context.tickDelta))
                    setFloat2("screenSize", screenSize)
                    setFloat3("blackHoleWorldPos", blackHoleWorldPos)
                    setFloat3("blackHoleCameraRelativePos", blackHoleCameraRelativePos)
                    setFloat("effectRadiusWorld", effectRadiusWorld)
                    setFloat("lightingInfluenceWorld", lightingInfluenceWorld)
                    setFloat("schwarzschildRadius", schwarzschildRadius)
                    setFloat("diskInnerRadius", diskInnerRadius)
                    setFloat("diskOuterRadius", diskOuterRadius)
                    setFloat("diskHalfThickness", diskHalfThickness)
                    setFloat("diskTemperatureScale", diskTemperatureScale)
                    setFloat("lensingStrength", lensingStrength)
                    setFloat("spinSpeed", spinSpeed)
                    setInt("stepCount", stepCount.coerceIn(48, 192))
                    setFloat("maxDistance", traceDistanceWorld)
                    setFloat("diskDensity", diskDensity)
                    setFloat("diskEmissionStrength", diskEmissionStrength)
                    setFloat3("diskColor", diskColor)
                    setFloat3("diskNormal", normalizedDiskNormal)
                    val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
                    glActiveTexture(GL_TEXTURE7)
                    val previousDepthBinding = glGetInteger(GL_TEXTURE_BINDING_2D)
                    glBindTexture(GL_TEXTURE_2D, depthTextureId)
                    setInt("sceneDepth", 7)
                    screenBuffer.draw()
                    glBindTexture(GL_TEXTURE_2D, previousDepthBinding)
                    glActiveTexture(previousActiveTexture)
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
        }
    }
}
