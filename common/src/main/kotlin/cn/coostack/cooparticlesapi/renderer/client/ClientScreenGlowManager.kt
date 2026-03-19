package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.builtin.ScreenGlowRenderRequest
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.from
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.OutputDepthPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.texture.SupplierTexture
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.function.Supplier
import kotlin.math.max

object ClientScreenGlowManager {
    private const val MAX_SCREEN_GLOWS = 8
    private const val EFFECT_ID = "builtin:screen_glow"
    private const val EFFECT_PRIORITY = 300

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()
    private val requiredCapabilities = setOf(
        RenderBackendCapability.FINAL_FRAME_POST,
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ
    )

    private val collectedGlows = ArrayList<ScreenGlow>(MAX_SCREEN_GLOWS * 2)
    private val sortedGlows = ArrayList<ScreenGlow>(MAX_SCREEN_GLOWS)
    private val glowPositionData = FloatArray(MAX_SCREEN_GLOWS * 3)
    private val glowColorData = FloatArray(MAX_SCREEN_GLOWS * 3)
    private val glowData = FloatArray(MAX_SCREEN_GLOWS * 4)

    private var prepared = false
    private var screenGlowCount = 0
    private var cameraWorldPos = Vector3f()
    private var projMatrix = Matrix4f()
    private var viewRotationMatrix = Matrix3f()
    private var inverseViewRotationMatrix = Matrix3f()
    private var screenSize = Vector2f(1f, 1f)

    private fun createSceneCopyPipe(): ExternalTextureShaderPipe {
        val sceneCopyTextures = SimpleTextures().apply {
            addTexture(SupplierTexture { ClientRenderPipelineManager.currentSceneColorTextureId() })
        }
        return ExternalTextureShaderPipe(sceneCopyTextures, Supplier { -1 })
    }

    private val pipeline = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "screen_glow_composite"
        )
    ).apply {
        enableBlend = false

        val sceneCopyPipe = createSceneCopyPipe()
        val sceneDepthPipe = OutputDepthPipe({ ClientRenderPipelineManager.currentSceneDepthTextureId() })
        addPipe(sceneCopyPipe)
        addPipe(sceneDepthPipe)
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "world/frag/screen_glow_composite.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { ClientRenderPipelineManager.currentSceneDepthTextureId() },
                2
            ).addRenderHandler { program ->
                program.setInt("sceneTex", 0)
                program.setInt("sceneDepth", 1)
                program.setInt("screenGlowCount", screenGlowCount)
                program.setFloat2("screenSize", screenSize)
                program.setFloat3("cameraWorldPos", cameraWorldPos)
                program.setMatrix4("projMat", projMatrix)
                program.setMatrix3f("viewRotationMat", viewRotationMatrix)
                program.setMatrix3f("inverseViewRotationMat", inverseViewRotationMatrix)
                program.setFloat3Array("glowPositions", glowPositionData)
                program.setFloat3Array("glowColors", glowColorData)
                program.setFloat4Array("glowData", glowData)
            }
        )

        beforeRender {
            sceneCopyPipe.capture()
        }
        setLinkerFunc { linker ->
            linker.from(sceneCopyPipe, 0).to(valueOutput!!, 0)
            linker.from(sceneDepthPipe, 0).to(valueOutput!!, 1)
        }
    }

    @JvmStatic
    fun initOnClient() {
        ClientRenderPipelineManager.register(pipeline)
    }

    @JvmStatic
    fun clear() {
        prepared = false
        screenGlowCount = 0
        collectedGlows.clear()
        sortedGlows.clear()
        glowPositionData.fill(0f)
        glowColorData.fill(0f)
        glowData.fill(0f)
    }

    @JvmStatic
    fun render(entities: Collection<RenderEntity>, tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        renderPrepared(tickDelta, viewMatrix, projMatrix) { context, output ->
            entities.forEach { entity ->
                when (entity) {
                    is ScreenGlowContextProvider -> entity.collectScreenGlows(context, output)
                    is ScreenGlowProvider -> entity.collectScreenGlows(tickDelta, output)
                }
            }
        }
    }

    fun renderRequests(requests: List<ScreenGlowRenderRequest>) {
        val first = requests.firstOrNull() ?: return
        val context = first.frameContext
        renderPrepared(context.tickDelta, context.viewMatrix, context.projMatrix) { glowContext, output ->
            requests.forEach { request ->
                request.collect(glowContext, output)
            }
        }
    }

    private fun renderPrepared(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        collect: (ScreenGlowRenderContext, MutableList<ScreenGlow>) -> Unit
    ) {
        collectGlows(tickDelta, viewMatrix, projMatrix, collect)
        if (!prepared || screenGlowCount <= 0) {
            return
        }
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        CooParticlesConstants.logger.debug(
            "Screen glow render count={} target={} sceneColor={} sceneDepth={}",
            screenGlowCount,
            ClientRenderPipelineManager.currentRenderTargetLabel(),
            ClientRenderPipelineManager.currentSceneColorTextureId(),
            ClientRenderPipelineManager.currentSceneDepthTextureId()
        )
        pipeline.render()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
    }

    private fun collectGlows(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        collect: (ScreenGlowRenderContext, MutableList<ScreenGlow>) -> Unit
    ) {
        clear()

        val context = updateFrameState(tickDelta, viewMatrix, projMatrix)
        collect(context, collectedGlows)

        if (collectedGlows.isEmpty()) {
            prepared = false
            return
        }

        sortedGlows.addAll(
            collectedGlows.sortedBy { glow ->
                val distance = Vector3f(glow.position).sub(cameraWorldPos).length()
                max(0f, distance - glow.radius)
            }.take(MAX_SCREEN_GLOWS)
        )

        screenGlowCount = sortedGlows.size
        if (screenGlowCount <= 0) {
            prepared = false
            return
        }

        sortedGlows.forEachIndexed { index, glow ->
            val posBase = index * 3
            glowPositionData[posBase] = glow.position.x
            glowPositionData[posBase + 1] = glow.position.y
            glowPositionData[posBase + 2] = glow.position.z

            glowColorData[posBase] = glow.color.x
            glowColorData[posBase + 1] = glow.color.y
            glowColorData[posBase + 2] = glow.color.z

            val dataBase = index * 4
            glowData[dataBase] = glow.radius
            glowData[dataBase + 1] = glow.intensity
            glowData[dataBase + 2] = glow.softness
            glowData[dataBase + 3] = glow.haloProfile
        }

        prepared = true
    }

    private fun updateFrameState(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ): ScreenGlowRenderContext {
        val cameraPosition = minecraft.gameRenderer.mainCamera.position
        cameraWorldPos = Vector3f(
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        this.projMatrix = Matrix4f(projMatrix)
        this.viewRotationMatrix = Matrix3f(viewMatrix)
        inverseViewRotationMatrix = Matrix3f(viewMatrix).invert()
        screenSize = Vector2f(
            ClientRenderPipelineManager.currentRenderWidth().toFloat(),
            ClientRenderPipelineManager.currentRenderHeight().toFloat()
        )
        return ScreenGlowRenderContext(
            tickDelta = tickDelta,
            cameraWorldPos = Vector3f(cameraWorldPos),
            viewMatrix = Matrix4f(viewMatrix),
            viewRotationMatrix = Matrix3f(this.viewRotationMatrix),
            inverseViewRotationMatrix = Matrix3f(inverseViewRotationMatrix),
            projMatrix = Matrix4f(this.projMatrix),
            screenSize = Vector2f(screenSize)
        )
    }
}
