package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityInputBlendMode
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Matrix3f
import org.joml.Random
import org.joml.Vector2f
import org.joml.Vector3f

class TestRendererEntity(world: Level?) : RenderEntity(world), ScreenGlowContextProvider {
    companion object {
        private const val SPHERE_RADIUS = 5.0f

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = StreamCodec.of<FriendlyByteBuf, RenderEntity>(
            { buf, data ->
                encodeBase(buf, data)
            }, {
                val instance = TestRendererEntity(null)
                decodeBase(it, instance)
                instance
            }
        )
        val ballBuffer = SimpleVertexBuffer().apply {
            setVertexes(ShaderUtil.genBall(5f, 64, 64), CooVertexFormat.POINT_TEXTURE_UV_FORMAT)
        }
        val ballShader = ShaderProgramBuilder()
            .vertex("core/vertex/point.vsh")
            .fragment("core/fragment/color.fsh")
            .build()
        var initialized = false
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_shader")
        fun initStatic() {
            if (initialized) {
                return
            }
            initialized = true
            ballBuffer.init()
            ballShader.init()
        }

        fun reloadStaticResources() {
            ballBuffer.release()
            ballShader.release()
            initialized = false
        }
    }

    val random = Random()
    val randomColor = Vector3f(
        random.nextFloat(),
        random.nextFloat(),
        random.nextFloat(),
    )
    private val renderColor = Vector3f()

    override fun initialize() {
        initStatic()
    }


    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> {
        return codec
    }

    override fun getRenderID(): ResourceLocation {
        return id
    }

    override fun getInputBlendMode(): RenderEntityInputBlendMode {
        return RenderEntityInputBlendMode.ADDITIVE
    }

    override fun release() {
    }

    override fun render(
        matrices: Matrix4fStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    ) {
        val blend = DistanceAdaptiveGlow.computeBlend(
            worldPosition = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
            worldRadius = SPHERE_RADIUS,
            context = createGlowContext(tickDelta, viewMatrix, projMatrix)
        )
        if (blend.directWeight <= 1.0e-3f) {
            return
        }

        RenderSystem.disableCull()
        val stack = PoseStack()
        ballShader.useOnContext {
            matrices.pushMatrix()
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat3("color", renderColor.set(randomColor).mul(blend.directWeight))
            ballBuffer.draw()
            matrices.popMatrix()
        }
    }

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
}
