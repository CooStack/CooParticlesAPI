package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.builtin.PersistentBloomRenderRequest
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloom
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloomContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.from
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.OutputDepthPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.PingPongShaderPipe
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
import org.lwjgl.opengl.GL33
import java.util.function.Supplier
import kotlin.math.max

object ClientPersistentBloomManager {
    private const val MAX_PERSISTENT_BLOOMS = 8
    private const val BLUR_ITERATIONS = 8
    private const val DEFAULT_BLUR_SIGMA = 4.6f
    private const val DEFAULT_BLUR_RANGE = 4.2f
    private const val EFFECT_ID = "builtin:persistent_bloom"
    private const val EFFECT_PRIORITY = 200

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()
    private val requiredCapabilities = setOf(
        RenderBackendCapability.FINAL_FRAME_POST,
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ
    )

    private val collectedBlooms = ArrayList<PersistentBloom>(MAX_PERSISTENT_BLOOMS * 2)
    private val sortedBlooms = ArrayList<PersistentBloom>(MAX_PERSISTENT_BLOOMS)
    private val bloomPositionData = FloatArray(MAX_PERSISTENT_BLOOMS * 3)
    private val bloomColorData = FloatArray(MAX_PERSISTENT_BLOOMS * 3)
    private val bloomData = FloatArray(MAX_PERSISTENT_BLOOMS * 4)
    private val bloomStyleData = FloatArray(MAX_PERSISTENT_BLOOMS * 4)

    private var prepared = false
    private var persistentBloomCount = 0
    private var blurSigma = DEFAULT_BLUR_SIGMA
    private var blurRange = DEFAULT_BLUR_RANGE
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
            "persistent_glow_bloom"
        )
    ).apply {
        enableBlend = false

        val sceneCopyPipe = createSceneCopyPipe()
        val sceneDepthPipe = OutputDepthPipe(Supplier { ClientRenderPipelineManager.currentSceneDepthTextureId() })
        val bloomMaskPipe = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "world/frag/persistent_glow_mask.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { ClientRenderPipelineManager.currentSceneDepthTextureId() }
            ).addRenderHandler { program ->
                program.setInt("sceneDepth", 0)
                program.setInt("persistentBloomCount", persistentBloomCount)
                program.setFloat2("screenSize", screenSize)
                program.setFloat3("cameraWorldPos", cameraWorldPos)
                program.setMatrix4("projMat", projMatrix)
                program.setMatrix3f("viewRotationMat", viewRotationMatrix)
                program.setMatrix3f("inverseViewRotationMat", inverseViewRotationMatrix)
                program.setFloat3Array("bloomPositions", bloomPositionData)
                program.setFloat3Array("bloomColors", bloomColorData)
                program.setFloat4Array("bloomData", bloomData)
                program.setFloat4Array("bloomStyleData", bloomStyleData)
            }
        )
        val bloomBlurPipe = addPipe(
            PingPongShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "core/bloom/blur.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 },
                1,
                BLUR_ITERATIONS,
                GL33.GL_LINEAR
            ).addRenderHandlerPong { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", blurSigma)
                program.setFloat("range", blurRange)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", blurSigma)
                program.setFloat("range", blurRange)
                program.setBoolean("horizontal", false)
            }
        )

        addPipe(sceneCopyPipe)
        addPipe(sceneDepthPipe)
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "world/frag/persistent_glow_composite.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { ClientRenderPipelineManager.currentSceneDepthTextureId() }
            ).addRenderHandler { program ->
                program.setInt("sceneTex", 0)
                program.setInt("persistentGlowMask", 1)
                program.setInt("blurredGlow", 2)
            }
        )

        beforeRender {
            sceneCopyPipe.capture()
        }
        setLinkerFunc { linker ->
            linker.from(sceneDepthPipe, 0).to(bloomMaskPipe, 0)
            linker.from(sceneCopyPipe, 0).to(valueOutput!!, 0)
            linker.from(bloomMaskPipe, 0).to(bloomBlurPipe, 0)
            linker.from(bloomMaskPipe, 0).to(valueOutput!!, 1)
            linker.from(bloomBlurPipe, 0).to(valueOutput!!, 2)
        }
    }

    @JvmStatic
    fun initOnClient() {
        ClientRenderPipelineManager.register(pipeline)
    }

    @JvmStatic
    fun clear() {
        prepared = false
        persistentBloomCount = 0
        blurSigma = DEFAULT_BLUR_SIGMA
        blurRange = DEFAULT_BLUR_RANGE
        collectedBlooms.clear()
        sortedBlooms.clear()
        bloomPositionData.fill(0f)
        bloomColorData.fill(0f)
        bloomData.fill(0f)
        bloomStyleData.fill(0f)
    }

    @JvmStatic
    fun render(
        entities: Collection<RenderEntity>,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ) {
        renderPrepared(tickDelta, viewMatrix, projMatrix) { context, output ->
            entities.forEach { entity ->
                if (entity is PersistentBloomContextProvider) {
                    entity.collectPersistentBlooms(context, output)
                }
            }
        }
    }

    fun renderRequests(requests: List<PersistentBloomRenderRequest>) {
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
        collect: (ScreenGlowRenderContext, MutableList<PersistentBloom>) -> Unit
    ) {
        collectBlooms(tickDelta, viewMatrix, projMatrix, collect)
        if (!prepared || persistentBloomCount <= 0) {
            return
        }
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        CooParticlesConstants.logger.debug(
            "Persistent bloom render count={} target={} sceneColor={} sceneDepth={}",
            persistentBloomCount,
            ClientRenderPipelineManager.currentRenderTargetLabel(),
            ClientRenderPipelineManager.currentSceneColorTextureId(),
            ClientRenderPipelineManager.currentSceneDepthTextureId()
        )
        pipeline.render()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
    }

    private fun collectBlooms(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        collect: (ScreenGlowRenderContext, MutableList<PersistentBloom>) -> Unit
    ) {
        clear()

        val context = updateFrameState(tickDelta, viewMatrix, projMatrix)
        collect(context, collectedBlooms)

        if (collectedBlooms.isEmpty()) {
            prepared = false
            return
        }

        sortedBlooms.addAll(
            collectedBlooms.sortedBy { bloom ->
                val distance = Vector3f(bloom.position).sub(cameraWorldPos).length()
                max(0f, distance - bloom.radius)
            }.take(MAX_PERSISTENT_BLOOMS)
        )

        persistentBloomCount = sortedBlooms.size
        if (persistentBloomCount <= 0) {
            prepared = false
            return
        }

        var blurSigmaSum = 0.0f
        var blurRangeSum = 0.0f
        var blurWeightSum = 0.0f
        sortedBlooms.forEachIndexed { index, bloom ->
            val posBase = index * 3
            bloomPositionData[posBase] = bloom.position.x
            bloomPositionData[posBase + 1] = bloom.position.y
            bloomPositionData[posBase + 2] = bloom.position.z

            bloomColorData[posBase] = bloom.color.x
            bloomColorData[posBase + 1] = bloom.color.y
            bloomColorData[posBase + 2] = bloom.color.z

            val dataBase = index * 4
            bloomData[dataBase] = bloom.radius.coerceAtLeast(0.01f)
            bloomData[dataBase + 1] = bloom.intensity.coerceAtLeast(0.0f)
            bloomData[dataBase + 2] = bloom.softness.coerceIn(0.05f, 0.95f)
            bloomData[dataBase + 3] = bloom.softOcclusionFloor.coerceIn(0.0f, 0.9f)

            bloomStyleData[dataBase] = bloom.haloRadiusScale.coerceIn(1.0f, 4.5f)
            bloomStyleData[dataBase + 1] = bloom.brightnessNormalization.coerceIn(0.6f, 2.0f)
            bloomStyleData[dataBase + 2] = bloom.haloOpacity.coerceIn(0.0f, 1.0f)
            bloomStyleData[dataBase + 3] = 0.0f

            val blurWeight = bloom.intensity.coerceAtLeast(0.15f)
            blurSigmaSum += bloom.blurSigma.coerceAtLeast(1.0f) * blurWeight
            blurRangeSum += bloom.blurRange.coerceAtLeast(1.0f) * blurWeight
            blurWeightSum += blurWeight
        }

        if (blurWeightSum > 1.0e-4f) {
            blurSigma = blurSigmaSum / blurWeightSum
            blurRange = blurRangeSum / blurWeightSum
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
        viewRotationMatrix = Matrix3f(viewMatrix)
        inverseViewRotationMatrix = Matrix3f(viewMatrix).invert()
        screenSize = Vector2f(
            ClientRenderPipelineManager.currentRenderWidth().toFloat(),
            ClientRenderPipelineManager.currentRenderHeight().toFloat()
        )
        return ScreenGlowRenderContext(
            tickDelta = tickDelta,
            cameraWorldPos = Vector3f(cameraWorldPos),
            viewMatrix = Matrix4f(viewMatrix),
            viewRotationMatrix = Matrix3f(viewRotationMatrix),
            inverseViewRotationMatrix = Matrix3f(inverseViewRotationMatrix),
            projMatrix = Matrix4f(this.projMatrix),
            screenSize = Vector2f(screenSize)
        )
    }
}
