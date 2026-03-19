package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomMaskRenderContext
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomRenderRequest
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomTexturedBillboard
import cn.coostack.cooparticlesapi.renderer.debug.GlDebugDumpUtil
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.texture.SupplierTexture
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL33
import org.lwjgl.opengl.GL33.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL33.GL_LEQUAL
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glGetInteger
import java.util.function.Supplier
import kotlin.math.roundToInt

/**
 * 内容驱动 mask bloom 的客户端执行器。
 *
 * 链路：
 * 1) `writeFrame` 把模型/贴图内容直接渲染到 mask FBO
 * 2) prefilter 只保留 alpha/emissive 内容，并生成 mip pyramid
 * 3) tent filter + accumulate 各级 mip，形成宽半径 bloom
 * 4) composite 到当前 frame final target
 */
object ClientMaskBloomManager {
    private const val MAX_BLOOM_LEVELS = 8

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()

    private var activeConfig = MaskBloomConfig()
    private var cameraWorldPos = Vector3f()
    private var screenSize = Vector2f(1.0f, 1.0f)
    private var frozenSceneColorTextureId = 0
    private var debugDumpPending = false
    private var debugDumpArmed = true
    private var debugForceSourceWhite = false
    private lateinit var sourceMaskPipe: SimpleShaderPipe
    private lateinit var prefilterPipe: SimpleShaderPipe
    private lateinit var tentPipe: SimpleShaderPipe
    private lateinit var accumulatePipe: SimpleShaderPipe
    private lateinit var compositePipe: SimpleShaderPipe
    private lateinit var sceneCopyPipe: ExternalTextureShaderPipe

    private var sourceHelperReady = false
    private val sourceBillboardBuffer = SimpleVertexBuffer().apply {
        setVertexes(
            ShaderUtil.genSquareUV(
                Vector3f(-0.5f, -0.5f, 0.0f),
                Vector3f(0.5f, -0.5f, 0.0f),
                Vector3f(0.5f, 0.5f, 0.0f),
                Vector3f(-0.5f, 0.5f, 0.0f)
            ),
            CooVertexFormat.POINT_TEXTURE_UV_FORMAT
        )
    }
    private val sourceTexturedMaskShader = AdvancedShaderProgramBuilder()
        .vertex("core/vertex/billboard_from_model_uv.vsh")
        .fragment("world/frag/mask_bloom_source_textured.fsh")
        .build()

    private fun ensureSourceHelperInitialized() {
        if (sourceHelperReady) {
            return
        }
        sourceHelperReady = true
        sourceBillboardBuffer.init()
        sourceTexturedMaskShader.init()
    }

    private fun createSceneCopyPipe(): ExternalTextureShaderPipe {
        val sceneCopyTextures = SimpleTextures().apply {
            addTexture(
                SupplierTexture {
                    if (frozenSceneColorTextureId > 0) {
                        frozenSceneColorTextureId
                    } else {
                        ClientRenderPipelineManager.currentSceneColorTextureId()
                    }
                }
            )
        }
        return ExternalTextureShaderPipe(sceneCopyTextures, Supplier { -1 })
    }

    private fun bloomMipLevels(): Int =
        activeConfig.blurRange.coerceIn(1.0f, MAX_BLOOM_LEVELS.toFloat()).roundToInt()

    private fun bloomTentLod(): Float =
        (activeConfig.blurSigma / 8.0f).coerceIn(0.0f, 4.0f)

    private val pipeline = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "mask_bloom")
    ).apply {
        enableBlend = false
        useDepth = false

        sourceMaskPipe = SimpleShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                GlShaderType.FRAGMENT
            ),
            Supplier { ClientRenderPipelineManager.currentSceneDepthTextureId() }
        )

        prefilterPipe = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "world/frag/mask_bloom_prefilter.fsh"),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 }
            ).addRenderHandler { program ->
                program.setInt("sourceMask", 0)
                program.setFloat("threshold", activeConfig.threshold.coerceIn(0.0f, 1.0f))
                program.setFloat("thresholdSoftness", activeConfig.thresholdSoftness.coerceIn(0.0f, 1.0f))
            }.useMipmap()
        ) as SimpleShaderPipe

        tentPipe = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/tent.fsh"),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 },
                1,
                GL33.GL_LINEAR_MIPMAP_LINEAR
            ).addRenderHandler { program ->
                program.setInt("scene", 0)
                program.setFloat("lod", bloomTentLod())
            }.useMipmap()
        ) as SimpleShaderPipe

        accumulatePipe = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/accumulate.fsh"),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 }
            ).addRenderHandler { program ->
                program.setInt("scene", 0)
                program.setFloat("intensity", 1.0f)
                program.setInt("levels", bloomMipLevels())
            }
        ) as SimpleShaderPipe

        val maskBloomSceneCopyPipe: ExternalTextureShaderPipe = createSceneCopyPipe()
        sceneCopyPipe = maskBloomSceneCopyPipe
        valueInput(sourceMaskPipe)
        addPipe(maskBloomSceneCopyPipe)

        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "world/frag/mask_bloom_composite.fsh"),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 }
            ).also {
                compositePipe = it
            }.addRenderHandler { program ->
                program.setInt("sceneTex", 0)
                program.setInt("filteredMask", 1)
                program.setInt("blurredMask", 2)
                program.setFloat("bloomIntensity", activeConfig.intensity.coerceAtLeast(0.0f))
                program.setFloat("baseMaskIntensity", activeConfig.baseMaskIntensity.coerceAtLeast(0.0f))
                program.setFloat3("tint", activeConfig.tint)
            }
        )

        beforeRender {
            maskBloomSceneCopyPipe.capture()
        }

        setLinkerFunc { linker ->
            linker.connect(sourceMaskPipe, 0, prefilterPipe, 0)
            linker.connect(prefilterPipe, 0, tentPipe, 0)
            linker.connect(tentPipe, 0, accumulatePipe, 0)
            linker.connect(maskBloomSceneCopyPipe, 0, valueOutput!!, 0)
            linker.connect(prefilterPipe, 0, valueOutput!!, 1)
            linker.connect(accumulatePipe, 0, valueOutput!!, 2)
        }
    }

    @JvmStatic
    fun initOnClient() {
        ClientRenderPipelineManager.register(pipeline)
    }

    @JvmStatic
    fun clear() {
        activeConfig = MaskBloomConfig()
        cameraWorldPos = Vector3f()
        screenSize = Vector2f(1.0f, 1.0f)
        frozenSceneColorTextureId = 0
        debugDumpArmed = true
        if (!sourceHelperReady) {
            return
        }
        sourceBillboardBuffer.release()
        sourceTexturedMaskShader.release()
        sourceHelperReady = false
    }

    fun renderRequests(requests: List<MaskBloomRenderRequest>) {
        if (requests.isEmpty()) {
            return
        }
        updateFrameState()
        if (debugDumpArmed) {
            debugDumpPending = true
            debugDumpArmed = false
        }
        requests.forEach { request ->
            activeConfig = request.config
            if (activeConfig.intensity <= 1.0e-4f && activeConfig.baseMaskIntensity <= 1.0e-4f) {
                return@forEach
            }
            drawMaskRequests(listOf(request))
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            try {
                logPipelineSnapshot("before-render")
                pipeline.render()
                logPipelineSnapshot("after-render")
                dumpDebugPipelineImages()
            } finally {
                RenderSystem.depthMask(true)
                RenderSystem.enableDepthTest()
            }
        }
    }

    private fun drawMaskRequests(requests: List<MaskBloomRenderRequest>) {
        pipeline.writeFrame {
            val previousDepthFunc = glGetInteger(GL_DEPTH_FUNC)
            RenderSystem.enableDepthTest()
            glDepthFunc(GL_LEQUAL)
            RenderSystem.depthMask(false)
            RenderSystem.enableCull()
            RenderSystem.disableBlend()
            try {
                if (debugForceSourceWhite) {
                    GL33.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
                    GL33.glClear(GL33.GL_COLOR_BUFFER_BIT)
                    CooParticlesConstants.logger.info("Injected persistent white source fill for mask bloom debug")
                }
                requests.forEach { request ->
                    val context = MaskBloomMaskRenderContext(
                        frameContext = request.frameContext,
                        sourceEntity = request.sourceEntity,
                        sourceInstanceId = request.sourceInstanceId,
                        cameraWorldPos = Vector3f(cameraWorldPos),
                        screenSize = Vector2f(screenSize),
                        texturedBillboardDrawer = { content ->
                            drawTexturedBillboard(content, request.frameContext)
                        }
                    )
                    request.renderMask(context)
                }
            } finally {
                glDepthFunc(previousDepthFunc)
                RenderSystem.disableBlend()
                RenderSystem.depthMask(true)
                RenderSystem.enableDepthTest()
                RenderSystem.enableCull()
            }
        }
    }

    private fun drawTexturedBillboard(content: MaskBloomTexturedBillboard, frameContext: RenderFrameContext) {
        ensureSourceHelperInitialized()
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        try {
            content.textures.drawWith {
                sourceTexturedMaskShader.useOnContext {
                    setMatrix4("projMat", frameContext.projMatrix)
                    setMatrix4("viewMat", frameContext.viewMatrix)
                    setMatrix4("transMat", content.modelMatrix)
                    setInt("tex", 0)
                    setFloat4("tint", content.tint)
                    setFloat("sourceBoost", content.sourceBoost.coerceAtLeast(0.0f))
                    setFloat("alphaCutoff", content.alphaCutoff.coerceIn(0.0f, 1.0f))
                    setFloat("emissiveCutoff", content.emissiveCutoff.coerceIn(0.0f, 1.0f))
                    setFloat("alphaWeight", content.alphaWeight.coerceAtLeast(0.0f))
                    setFloat("emissiveWeight", content.emissiveWeight.coerceAtLeast(0.0f))
                    setBoolean("fullQuadMask", content.fullQuadMask)
                    setFloat("fullQuadMaskSoftness", content.fullQuadMaskSoftness.coerceIn(0.0f, 0.45f))
                    sourceBillboardBuffer.draw()
                }
            }
        } finally {
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableCull()
        }
    }

    private fun updateFrameState() {
        val cameraPosition = minecraft.gameRenderer.mainCamera.position
        cameraWorldPos = Vector3f(
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        frozenSceneColorTextureId = ClientRenderPipelineManager.currentSceneColorTextureId()
        screenSize = Vector2f(
            ClientRenderPipelineManager.currentRenderWidth().toFloat(),
            ClientRenderPipelineManager.currentRenderHeight().toFloat()
        )
        CooParticlesConstants.logger.info(
            "MaskBloom frame freeze sceneColorTex={} renderSize={}x{} targetLabel={}",
            frozenSceneColorTextureId,
            screenSize.x.toInt(),
            screenSize.y.toInt(),
            ClientRenderPipelineManager.currentRenderTargetLabel()
        )
    }

    private fun logPipelineSnapshot(stage: String) {
        CooParticlesConstants.logger.info(
            "MaskBloom {} sceneColorTex={} sceneCopyOut={} sourceOut={} prefilterOut={} tentOut={} accumulateOut={} compositeOut={}",
            stage,
            frozenSceneColorTextureId,
            sceneCopyPipe.fbo().colorAttachments[0],
            sourceMaskPipe.fbo().colorAttachments[0],
            prefilterPipe.fbo().colorAttachments[0],
            tentPipe.fbo().colorAttachments[0],
            accumulatePipe.fbo().colorAttachments[0],
            compositePipe.fbo().colorAttachments[0]
        )
    }

    private fun dumpDebugPipelineImages() {
        if (!debugDumpPending) {
            return
        }
        debugDumpPending = false
        val width = ClientRenderPipelineManager.currentRenderWidth()
        val height = ClientRenderPipelineManager.currentRenderHeight()
        GlDebugDumpUtil.dumpTexture(
            sceneCopyPipe.fbo().colorAttachments[0],
            width,
            height,
            "mask-bloom-scene-copy.png"
        )
        GlDebugDumpUtil.dumpTexture(
            sourceMaskPipe.fbo().colorAttachments[0],
            width,
            height,
            "mask-bloom-source.png"
        )
        GlDebugDumpUtil.dumpTexture(
            prefilterPipe.fbo().colorAttachments[0],
            width,
            height,
            "mask-bloom-prefilter.png"
        )
        GlDebugDumpUtil.dumpTexture(
            tentPipe.fbo().colorAttachments[0],
            width,
            height,
            "mask-bloom-tent.png"
        )
        GlDebugDumpUtil.dumpTexture(
            accumulatePipe.fbo().colorAttachments[0],
            width,
            height,
            "mask-bloom-accumulate.png"
        )
        GlDebugDumpUtil.dumpTexture(
            compositePipe.fbo().colorAttachments[0],
            width,
            height,
            "mask-bloom-composite.png"
        )
        CooParticlesConstants.logger.info("Dumped mask bloom debug images to cooparticlesapi-debug/")
    }
}
