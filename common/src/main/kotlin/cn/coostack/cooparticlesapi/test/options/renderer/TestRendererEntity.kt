package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
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
import org.joml.Random
import org.joml.Vector2f
import org.joml.Vector3f

@CooAutoRegister
class TestRendererEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    ScreenGlowContextProvider {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        const val SPHERE_RADIUS: Float = 5.0f
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_shader")
    }

    private val random = Random()
    val randomColor: Vector3f = Vector3f(
        random.nextFloat(),
        random.nextFloat(),
        random.nextFloat()
    )

    override fun getRenderID(): ResourceLocation = ID

    override fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>) {
        val blend = DistanceAdaptiveGlow.computeBlend(
            worldPosition = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
            worldRadius = SPHERE_RADIUS,
            context = context
        )
        if (blend.screenGlowWeight <= 1.0e-3f) {
            return
        }
        output.add(
            ScreenGlow(
                position = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
                color = Vector3f(randomColor),
                radius = SPHERE_RADIUS,
                intensity = 1.45f * blend.screenGlowWeight,
                softness = 0.58f
            )
        )
    }
}

class TestRendererEntityRenderer : RenderEntityRenderer<TestRendererEntity> {
    companion object {
        private val ballBuffer = SimpleVertexBuffer().apply {
            setVertexes(ShaderUtil.genBall(5f, 64, 64), CooVertexFormat.POINT_TEXTURE_UV_FORMAT)
        }
        private val ballShader = ShaderProgramBuilder()
            .vertex("core/vertex/point.vsh")
            .fragment("core/fragment/color.fsh")
            .build()
        private var initialized = false

        private fun initStatic() {
            if (initialized) {
                return
            }
            initialized = true
            ballBuffer.init()
            ballShader.init()
        }
    }

    private val renderColor = Vector3f()

    override fun initialize(instance: RenderEntityInstance<TestRendererEntity>) {
        initStatic()
    }

    override fun renderLocal(input: LocalRenderInput<TestRendererEntity>) {
        val entity = input.instance.entity
        val glowContext = createGlowContext(input)
        val blend = DistanceAdaptiveGlow.computeBlend(
            worldPosition = Vector3f(entity.pos.x.toFloat(), entity.pos.y.toFloat(), entity.pos.z.toFloat()),
            worldRadius = TestRendererEntity.SPHERE_RADIUS,
            context = glowContext
        )
        if (blend.directWeight <= 1.0e-3f) {
            return
        }

        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        try {
            ballShader.useOnContext {
                input.modelMatrix.pushMatrix()
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat3("color", renderColor.set(entity.randomColor).mul(blend.directWeight))
                ballBuffer.draw()
                input.modelMatrix.popMatrix()
            }
        } finally {
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.disableCull()
        }
    }

    private fun createGlowContext(input: LocalRenderInput<TestRendererEntity>): ScreenGlowRenderContext {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera.position
        return ScreenGlowRenderContext(
            tickDelta = input.tickDelta,
            cameraWorldPos = Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat()),
            viewMatrix = Matrix4f(input.viewMatrix),
            viewRotationMatrix = Matrix3f(input.viewMatrix),
            inverseViewRotationMatrix = Matrix3f(input.viewMatrix).invert(),
            projMatrix = Matrix4f(input.projMatrix),
            screenSize = Vector2f(
                minecraft.mainRenderTarget.width.toFloat(),
                minecraft.mainRenderTarget.height.toFloat()
            )
        )
    }
}
