package cn.coostack.cooparticlesapi.cparticle.render

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.cparticle.CParticleAppearanceDescriptors
import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleIndexedBlendState
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticlePerfProbe
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureResolver
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import cn.coostack.cooparticlesapi.renderer.backend.glTextureId
import cn.coostack.cooparticlesapi.renderer.backend.glSamplerId
import cn.coostack.cooparticlesapi.renderer.state.CooGLSLStateManager
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.backend.opengl.GlStateManager
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33.*

/**
 * GPU 粒子渲染器: 每个系统一次 instanced draw，同层系统按基础纹理和蒙版纹理连续绘制。
 *
 * 无光影时直接使用 CParticle shader。Iris 光影启用后，常规层展开为原版粒子顶点，
 * 需要完整片元 Alpha 的有界 Screen 层只写 Iris framebuffer 的主颜色附件。
 * screen-only Terrain Mapping 活跃时，CParticle 延迟到 Mapping 最终合成后按原 layer 语义重放；
 * Iris shader pack 激活时不参与 Terrain Mapping 分流，直接保留 Iris 原生粒子阶段。
 */
object CParticleRenderer {

    private var program: CooShaderProgram? = null
    private val emptyCurve = FloatArray(16)
    private val emptyCurveHandles = FloatArray(CParticleCurve.MAX_KEYS * 4)
    private val emptyColorTimes = FloatArray(CParticleColorCurve.MAX_KEYS)
    private val emptyColorValues = FloatArray(CParticleColorCurve.MAX_KEYS * 3)
    private val emptyColorHandles = FloatArray(CParticleColorCurve.MAX_KEYS * 4)
    private val drawLayers = arrayOf(
        CParticleRenderLayer.OPAQUE,
        CParticleRenderLayer.TRANSLUCENT,
        CParticleRenderLayer.PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE,
        CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NOT_HDR,
        CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE,
        CParticleRenderLayer.ADDITION_BLEND_NOT_HDR,
        CParticleRenderLayer.ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE,
        CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT,
        CParticleRenderLayer.ADDITION_BLEND,
        CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE,
    )
    private val systemSortComparator = compareBy<CParticleSystem>(
        CParticleSystem::textureBindingKey,
    ).thenBy(CParticleSystem::maskTextureBindingKey)

    /**
     * 把 CParticle 图形 program 加入统一注册表，不触发 GL 编译。
     *
     * 示例：[CooParticlesAPIClient.init] 在 loader 客户端注册阶段调用。
     * 禁止：本方法不能调用 `init()`，否则早期客户端初始化可能还没有 GL 上下文。
     *
     * @return 已注册的共享图形 program
     */
    internal fun registerProgram(): CooShaderProgram {
        program?.let { return ShaderProgramRegistry.register(it) }
        return AdvancedShaderProgramBuilder()
            .vertex("core/vertex/cparticle.vsh")
            .fragment("core/fragment/cparticle.fsh")
            .attributeLocation("iPosAge", 0)
            .attributeLocation("iPrevMaxAge", 1)
            .attributeLocation("iVelFlags", 2)
            .attributeLocation("iSizeRot", 3)
            .attributeLocation("iAxisRoll", 4)
            .attributeLocation("iAnimation", 5)
            .attributeLocation("iColor", 6)
            .attributeLocation("iAngularEpoch", 7)
            .attributeLocation("iAppearance", 8)
            .transformFeedbackVaryings("tfPosition", "tfUv", "tfPacked")
            .managedId("cparticle/render")
            .build()
            .also { program = it }
    }

    /**
     * 返回可绘制的 CParticle program，遗漏客户端预注册时仍保留兼容兜底。
     *
     * 示例：正常路径直接返回渲染初始化阶段已经编译的 program。
     * 禁止：只能在持有 GL 上下文的渲染线程调用。
     *
     * @return 已完成编译的共享图形 program
     */
    private fun ensureProgram(): CooShaderProgram {
        return registerProgram().also { current ->
            if (current.program == 0) current.init()
        }
    }

    /**
     * 绘制所有系统 (渲染线程, 世界渲染阶段).
     *
     * 示例：粒子引擎 pass 通过 [CParticleSystemManager.renderParticlePass] 调用此方法。
     * 禁止：不要从非渲染线程调用，也不要绕过调用方提供的粒子 pass。
     *
     * @param view LevelRenderer 的 frustum(model-view) 矩阵
     * @param proj 当前世界投影矩阵
     * @param camera 当前渲染相机
     * @param partial tick 插值
     * @param pass 本次允许绘制的粒子覆盖范围
     */
    fun render(
        systems: Collection<CParticleSystem>,
        view: Matrix4f,
        proj: Matrix4f,
        camera: Camera,
        partial: Float,
        pass: CParticleRenderPass = CParticleRenderPass.ALL,
    ) {
        OpenGlRenderTargetBridge.requireOpenGlBackend()
        // 路径图层在渲染阶段重新绑定，使同一帧内 RenderEntity 的 shader 也能读到同一份资源。
        CParticlePathLibrary.bind()
        renderInternal(systems, view, proj, camera, partial, pass, coverageOnly = false, forceDirectShader = false)
    }

    /** 在 Terrain Mapping 之后使用 Coo shader 和原 layer 状态重放完整 CParticle 前景。 */
    internal fun renderTerrainForeground(
        systems: Collection<CParticleSystem>,
        view: Matrix4f,
        proj: Matrix4f,
        camera: Camera,
        partial: Float,
    ) {
        renderInternal(
            systems,
            view,
            proj,
            camera,
            partial,
            CParticleRenderPass.ALL,
            coverageOnly = false,
            forceDirectShader = true,
        )
    }

    /** 在 Terrain Mapping 执行前按当前帧投影生成全部可见 CParticle 的隔离覆盖蒙版。 */
    internal fun renderTerrainCoverageMask(
        systems: Collection<CParticleSystem>,
        view: Matrix4f,
        proj: Matrix4f,
        camera: Camera,
        partial: Float,
    ): Boolean {
        return renderInternal(
            systems,
            view,
            proj,
            camera,
            partial,
            CParticleRenderPass.ALL,
            coverageOnly = true,
            forceDirectShader = true,
        )
    }

    private fun renderInternal(
        systems: Collection<CParticleSystem>,
        view: Matrix4f,
        proj: Matrix4f,
        camera: Camera,
        partial: Float,
        pass: CParticleRenderPass,
        coverageOnly: Boolean,
        forceDirectShader: Boolean,
    ): Boolean {
        if (systems.isEmpty() || pass == CParticleRenderPass.NONE) return coverageOnly
        OpenGlRenderTargetBridge.requireOpenGlBackend()
        val cameraPos = camera.position()
        val visibleSystems = systems.filter {
            pass.accepts(it.layer) && !it.released && it.store.highWater > 0 && it.isVisible(cameraPos)
        }
        if (visibleSystems.isEmpty()) return coverageOnly
        val visibleBuckets = VisibleSystemBuckets(visibleSystems)

        val shader = ensureProgram()
        if (shader.program == 0) return false
        val frameId = CParticleSystemManager.currentRenderFrameId
        val irisShaderPackActive = CooParticlesAPIClient.checkIrisShaderPackUsed()
        val useIrisParticleShader = irisShaderPackActive && !coverageOnly && !forceDirectShader
        val uniformScratch = UniformScratch()

        // ---- 状态快照 ----
        val prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        // 普通 instanced draw 会切换到各 system 自己的 VAO，render pass 结束时统一恢复。
        // 避免 CParticleGlBuffer.draw() 为每个 system 查询并恢复一次外部 VAO。
        val prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val prevActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val prevTex0 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(GL_TEXTURE1)
        val prevTex1 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(GL_TEXTURE2)
        val prevTex2 = glGetInteger(GL_TEXTURE_BINDING_2D)
        val prevTex2Buffer = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        glActiveTexture(GL_TEXTURE3)
        val prevTex3Buffer = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        glActiveTexture(GL_TEXTURE4)
        val prevTex4 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(prevActiveTexture)
        val previousSamplers = IntArray(5) { GL30.glGetIntegeri(GL_SAMPLER_BINDING, it) }
        val cullFaceMode = glGetInteger(GL_CULL_FACE_MODE)
        val frontFaceMode = glGetInteger(GL_FRONT_FACE)
        CooGLSLStateManager.createState()

        try {
            // ---- 纹理: 0=基础纹理 1=光照贴图 2=纹理描述符 3=外观描述符 4=蒙版纹理 ----
            bindLightmap(1)

            for (system in visibleSystems) {
                system.ensureGl()
                system.prepareDynamicVisuals(frameId)
            }
            CParticleSprites.bindLookup(2)
            CParticleAppearanceDescriptors.bindLookup(3)

            glUseProgram(shader.program)
            shader.setMatrix4("uView", view)
            shader.setMatrix4("uProj", proj)
            shader.setFloat("uPartial", partial)
            shader.setInt("uMainTexture", 0)
            shader.setInt("uLightmap", 1)
            shader.setInt("uAnimationLookup", 2)
            shader.setInt("uAppearanceLookup", 3)
            shader.setInt("uMaskTexture", 4)
            shader.setInt("uDepthOnly", 0)
            shader.setInt("uCoverageMask", 0)
            shader.setInt("uPremultiplyRgbByAlpha", 0)

            // 相机朝向基 (BILLBOARD 用; 与原版粒子 q*X̂=left / q*Ŷ=up 一致)
            val left = camera.leftVector()
            val up = camera.upVector()
            shader.setFloat3("uCamLeft", Vector3f(left.x(), left.y(), left.z()))
            shader.setFloat3("uCamUp", Vector3f(up.x(), up.y(), up.z()))

            // 原版雾
            val fog = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData
            shader.setFloat("uFogEnvironmentalStart", fog.environmentalStart)
            shader.setFloat("uFogEnvironmentalEnd", fog.environmentalEnd)
            shader.setFloat("uFogRenderDistanceStart", fog.renderDistanceStart)
            shader.setFloat("uFogRenderDistanceEnd", fog.renderDistanceEnd)
            shader.setFloat4("uFogColor", Vector4f(fog.color))

            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GL_GEQUAL)
            glEnable(GL_CULL_FACE)
            glCullFace(GL_BACK)
            glFrontFace(GL_CCW)

            if (coverageOnly) {
                return renderCoverage(
                    shader,
                    visibleBuckets.globallySorted,
                    cameraPos,
                    partial,
                    uniformScratch,
                )
            }
            if (useIrisParticleShader) {
                renderWithIrisParticleShader(
                    shader,
                    visibleBuckets,
                    view,
                    proj,
                    cameraPos,
                    partial,
                    pass,
                    uniformScratch,
                )
            } else {
                shader.setInt("uIrisExpansion", 0)
                // ---- 分层后按 binding 连续绘制；纹理绑定次数只随批次数增长 ----
                val deferredDepthSystems = ArrayList<CParticleSystem>()
                for (layer in drawLayers) {
                    if (!pass.accepts(layer)) continue
                    val layerSystems = visibleBuckets.forLayer(layer)
                    if (layerSystems.isEmpty()) continue
                    if (forceDirectShader && irisShaderPackActive) {
                        layer.applyIndexedState(0)
                    } else {
                        layer.applyState()
                    }
                    shader.setInt("uPremultiplyRgbByAlpha", if (layer.premultiplyRgbByAlpha) 1 else 0)
                    if (layer.requiresDeferredDepthWrite) {
                        glDepthMask(false)
                        deferredDepthSystems.addAll(layerSystems)
                    }
                    val drawLayerSystems = {
                        var boundMainTexture: CParticleTextureBindingKey? = null
                        var boundMaskTexture: CParticleTextureBindingKey? = null
                        for (system in layerSystems) {
                            if (system.textureBindingKey != boundMainTexture) {
                                bindSystemTexture(system.textureBindingKey, 0)
                                boundMainTexture = system.textureBindingKey
                            }
                            val maskBinding = system.maskTextureBindingKey
                            if (maskBinding != null && maskBinding != boundMaskTexture) {
                                bindSystemTexture(maskBinding, 4)
                                boundMaskTexture = maskBinding
                            }

                            applySystemUniforms(shader, system, cameraPos, partial, uniformScratch)
                            drawSystem(system)
                        }
                    }
                    if (forceDirectShader && irisShaderPackActive) {
                        withPrimaryColorWriteOnly(drawLayerSystems)
                    } else {
                        drawLayerSystems()
                    }
                }

                if (deferredDepthSystems.isNotEmpty()) {
                    shader.setInt("uDepthOnly", 1)
                    glColorMaski(0, false, false, false, false)
                    glDepthMask(true)
                    var boundMainTexture: CParticleTextureBindingKey? = null
                    var boundMaskTexture: CParticleTextureBindingKey? = null
                    for (system in deferredDepthSystems.sortedWith(
                        compareBy(CParticleSystem::textureBindingKey)
                            .thenBy(CParticleSystem::maskTextureBindingKey)
                    )) {
                        if (system.textureBindingKey != boundMainTexture) {
                            bindSystemTexture(system.textureBindingKey, 0)
                            boundMainTexture = system.textureBindingKey
                        }
                        val maskBinding = system.maskTextureBindingKey
                        if (maskBinding != null && maskBinding != boundMaskTexture) {
                            bindSystemTexture(maskBinding, 4)
                            boundMaskTexture = maskBinding
                        }
                        applySystemUniforms(shader, system, cameraPos, partial, uniformScratch)
                        drawSystem(system)
                    }
                }
            }
        } finally {
            // ---- 状态还原 ----
            glActiveTexture(GL_TEXTURE4)
            glBindTexture(GL_TEXTURE_2D, prevTex4)
            GlStateManager._activeTexture(GL_TEXTURE4)
            GlStateManager._bindTexture(prevTex4)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_BUFFER, prevTex3Buffer)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_BUFFER, prevTex2Buffer)
            glBindTexture(GL_TEXTURE_2D, prevTex2)
            GlStateManager._activeTexture(GL_TEXTURE2)
            GlStateManager._bindTexture(prevTex2)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, prevTex1)
            GlStateManager._activeTexture(GL_TEXTURE1)
            GlStateManager._bindTexture(prevTex1)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, prevTex0)
            GlStateManager._activeTexture(GL_TEXTURE0)
            GlStateManager._bindTexture(prevTex0)
            previousSamplers.forEachIndexed { unit, sampler -> glBindSampler(unit, sampler) }
            GlStateManager._activeTexture(prevActiveTexture)
            glActiveTexture(prevActiveTexture)
            glCullFace(cullFaceMode)
            glFrontFace(frontFaceMode)
            glBindVertexArray(prevVao)
            if (prevProgram > 0 && glIsProgram(prevProgram)) glUseProgram(prevProgram) else glUseProgram(0)
            CooGLSLStateManager.resetState()
        }
        return true
    }

    /** 在独立 framebuffer 中把全部可见 CParticle 光栅化为颜色与覆盖度，不写回场景深度。 */
    private fun renderCoverage(
        shader: CooShaderProgram,
        systems: List<CParticleSystem>,
        cameraPos: Vec3,
        partial: Float,
        uniformScratch: UniformScratch,
    ): Boolean {
        if (systems.isEmpty()) return true
        return OpenGlPostEffectExecutionBackend.captureCParticleCoverage {
            glUseProgram(shader.program)
            shader.setInt("uIrisExpansion", 0)
            shader.setInt("uDepthOnly", 0)
            shader.setInt("uCoverageMask", 1)
            shader.setInt("uPremultiplyRgbByAlpha", 0)
            if (CooParticlesAPIClient.checkIrisShaderPackUsed() && CParticleIndexedBlendState.isAvailable()) {
                // 覆盖纹理记录可见粒子的最大 Alpha，避免被最后一次低 Alpha 绘制覆盖。
                GL30.glEnablei(GL_BLEND, 0)
                CParticleIndexedBlendState.setEquation(0, GL_MAX, GL_MAX)
                CParticleIndexedBlendState.setFactors(0, GL_ONE, GL_ONE, GL_ONE, GL_ONE)
            } else {
                glEnable(GL_BLEND)
                glBlendEquationSeparate(GL_MAX, GL_MAX)
                glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE)
            }
            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GL_GEQUAL)
            glEnable(GL_CULL_FACE)
            glCullFace(GL_BACK)
            glFrontFace(GL_CCW)
            try {
                drawInstancedSystems(
                    shader,
                    systems,
                    cameraPos,
                    partial,
                    uniformScratch,
                    alreadySorted = true,
                )
            } finally {
                shader.setInt("uCoverageMask", 0)
            }
        }
    }

    /**
     * 在 Iris 粒子 framebuffer 中按渲染层选择兼容绘制路径。
     *
     * 示例：常规层使用 `PARTICLES_TRANS` 处理展开顶点；有界透明 Screen 层直接采样纹理和 mask。
     * 禁止让直接绘制写入主颜色之外的 shaderpack 附件。
     *
     * @param shader 用于 transform feedback 和有界透明 Screen 层直接绘制的 CParticle program
     * @param systems 当前 pass 中可见且已准备好的系统
     * @param view 原版粒子 model-view 矩阵
     * @param proj 当前世界投影矩阵
     * @param cameraPos 当前相机世界坐标
     * @param partial tick 插值
     * @param pass 当前 Iris 粒子分流 pass
     */
    private fun renderWithIrisParticleShader(
        shader: CooShaderProgram,
        visibleBuckets: VisibleSystemBuckets,
        view: Matrix4f,
        proj: Matrix4f,
        cameraPos: Vec3,
        partial: Float,
        pass: CParticleRenderPass,
        uniformScratch: UniformScratch,
    ) {
        shader.setInt("uIrisExpansion", 1)
        shader.setInt("uPremultiplyRgbByAlpha", 0)
        for (system in visibleBuckets.original) {
            if (system.layer.premultiplyRgbByAlpha) continue
            applySystemUniforms(shader, system, cameraPos, partial, uniformScratch)
            val first = system.store.firstAliveSlot
            val count = system.store.highWater - first
            if (count > 0) system.glBuffer.expandForParticleShader(first, count)
        }
        shader.setInt("uIrisExpansion", 0)

        val firstTexture = visibleBuckets.original.firstOrNull()?.textureBindingKey ?: return
        bindSystemTexture(firstTexture, 0)
        bindLightmap(2)
        val drawn = IrisCompat.runWithParticleShader(pass, view, proj, fallback = {
            renderDirectIrisFallback(shader, visibleBuckets, cameraPos, partial, pass, uniformScratch)
        }) {
            // Iris setupState 可重绑辅助资源，保留单元 2 必须恢复为本批次的原版光照。
            bindLightmap(2)
            val irisParticleProgram = glGetInteger(GL_CURRENT_PROGRAM)
            val deferredExpandedSystems = ArrayList<CParticleSystem>()
            val deferredDirectSystems = ArrayList<CParticleSystem>()
            for (layer in drawLayers) {
                if (!pass.accepts(layer)) continue
                val layerSystems = visibleBuckets.forLayer(layer)
                if (layerSystems.isEmpty()) continue

                layer.applyIndexedState(0)
                if (layer.requiresDeferredDepthWrite) {
                    glDepthMask(false)
                    if (layer.premultiplyRgbByAlpha) {
                        deferredDirectSystems.addAll(layerSystems)
                    } else {
                        deferredExpandedSystems.addAll(layerSystems)
                    }
                }

                if (layer.premultiplyRgbByAlpha) {
                    glUseProgram(shader.program)
                    try {
                        // Iris 使用原版保留单元，切回 Coo program 后恢复自定义光照与描述符资源。
                        bindLightmap(1)
                        CParticleSprites.bindLookup(2)
                        CParticleAppearanceDescriptors.bindLookup(3)
                        shader.setInt("uPremultiplyRgbByAlpha", 1)
                        withPrimaryColorWriteOnly {
                            drawInstancedSystems(
                                shader,
                                layerSystems,
                                cameraPos,
                                partial,
                                uniformScratch,
                                alreadySorted = true,
                            )
                        }
                    } finally {
                        shader.setInt("uPremultiplyRgbByAlpha", 0)
                        glUseProgram(irisParticleProgram)
                    }
                } else {
                    drawExpandedSystems(layerSystems, alreadySorted = true)
                }
            }

            if (deferredExpandedSystems.isNotEmpty() || deferredDirectSystems.isNotEmpty()) {
                withAllColorWritesDisabled {
                    glDepthMask(true)
                    if (deferredExpandedSystems.isNotEmpty()) {
                        glUseProgram(irisParticleProgram)
                        drawExpandedSystems(deferredExpandedSystems)
                    }
                    if (deferredDirectSystems.isNotEmpty()) {
                        glUseProgram(shader.program)
                        try {
                            bindLightmap(1)
                            CParticleSprites.bindLookup(2)
                            CParticleAppearanceDescriptors.bindLookup(3)
                            shader.setInt("uDepthOnly", 1)
                            drawInstancedSystems(
                                shader,
                                deferredDirectSystems,
                                cameraPos,
                                partial,
                                uniformScratch,
                            )
                        } finally {
                            shader.setInt("uDepthOnly", 0)
                            glUseProgram(irisParticleProgram)
                        }
                    }
                }
            }
            glUseProgram(irisParticleProgram)
        }
        if (!drawn) {
            renderDirectIrisFallback(shader, visibleBuckets, cameraPos, partial, pass, uniformScratch)
        }
    }

    /** Iris 细分输入与现有三角形不一致时，保留完整纹理、蒙版、曲线及延迟深度语义。 */
    private fun renderDirectIrisFallback(
        shader: CooShaderProgram,
        visibleBuckets: VisibleSystemBuckets,
        cameraPos: Vec3,
        partial: Float,
        pass: CParticleRenderPass,
        uniformScratch: UniformScratch,
    ) {
        glUseProgram(shader.program)
        bindLightmap(1)
        CParticleSprites.bindLookup(2)
        CParticleAppearanceDescriptors.bindLookup(3)
        shader.setInt("uIrisExpansion", 0)
        val deferredDepthSystems = ArrayList<CParticleSystem>()
        withPrimaryColorWriteOnly {
            for (layer in drawLayers) {
                if (!pass.accepts(layer)) continue
                val systems = visibleBuckets.forLayer(layer)
                if (systems.isEmpty()) continue
                layer.applyIndexedState(0)
                shader.setInt("uPremultiplyRgbByAlpha", if (layer.premultiplyRgbByAlpha) 1 else 0)
                if (layer.requiresDeferredDepthWrite) {
                    glDepthMask(false)
                    deferredDepthSystems.addAll(systems)
                }
                drawInstancedSystems(shader, systems, cameraPos, partial, uniformScratch, alreadySorted = true)
            }
        }
        if (deferredDepthSystems.isNotEmpty()) {
            withAllColorWritesDisabled {
                shader.setInt("uDepthOnly", 1)
                glDepthMask(true)
                drawInstancedSystems(shader, deferredDepthSystems, cameraPos, partial, uniformScratch)
                shader.setInt("uDepthOnly", 0)
            }
        }
        shader.setInt("uPremultiplyRgbByAlpha", 0)
    }

    /** 只绘制存活槽位覆盖的范围，范围内允许保留稀疏死亡槽位。 */
    private fun drawSystem(system: CParticleSystem) {
        CParticlePerfProbe.measure(CParticlePerfProbe.Stage.RENDER_SYSTEM_SUBMIT) {
            val first = system.store.firstAliveSlot
            val count = system.store.highWater - first
            if (count > 0) system.glBuffer.drawBound(first, count)
        }
    }

    private fun drawInstancedSystems(
        shader: CooShaderProgram,
        systems: Collection<CParticleSystem>,
        cameraPos: Vec3,
        partial: Float,
        uniformScratch: UniformScratch,
        alreadySorted: Boolean = false,
    ) {
        var boundMainTexture: CParticleTextureBindingKey? = null
        var boundMaskTexture: CParticleTextureBindingKey? = null
        val orderedSystems = if (alreadySorted) systems else systems.sortedWith(systemSortComparator)
        for (system in orderedSystems) {
            if (system.textureBindingKey != boundMainTexture) {
                bindSystemTexture(system.textureBindingKey, 0)
                boundMainTexture = system.textureBindingKey
            }
            val maskBinding = system.maskTextureBindingKey
            if (maskBinding != null && maskBinding != boundMaskTexture) {
                bindSystemTexture(maskBinding, 4)
                boundMaskTexture = maskBinding
            }
            applySystemUniforms(shader, system, cameraPos, partial, uniformScratch)
            drawSystem(system)
        }
    }

    /**
     * 当前 render pass 的可见系统索引。
     *
     * `original` 保留调用方顺序，供 Iris transform feedback 按原顺序准备粒子顶点；
     * 其余视图只从同一份全局纹理排序结果切出 layer，避免每层重复 filter/sort/toList。
     */
    private class VisibleSystemBuckets(systems: List<CParticleSystem>) {
        val original: List<CParticleSystem> = systems
        val globallySorted: List<CParticleSystem> = systems.sortedWith(CParticleRenderer.systemSortComparator)
        private val systemsByLayer: Map<CParticleRenderLayer, List<CParticleSystem>> =
            globallySorted.groupBy(CParticleSystem::layer)

        fun forLayer(layer: CParticleRenderLayer): List<CParticleSystem> {
            return systemsByLayer[layer].orEmpty()
        }
    }

    private fun drawExpandedSystems(
        systems: Collection<CParticleSystem>,
        alreadySorted: Boolean = false,
    ) {
        var boundMainTexture: CParticleTextureBindingKey? = null
        val orderedSystems = if (alreadySorted) {
            systems
        } else {
            systems.sortedBy(CParticleSystem::textureBindingKey)
        }
        for (system in orderedSystems) {
            if (system.textureBindingKey != boundMainTexture) {
                bindSystemTexture(system.textureBindingKey, 0)
                boundMainTexture = system.textureBindingKey
            }
            val first = system.store.firstAliveSlot
            val count = system.store.highWater - first
            if (count > 0) system.glBuffer.drawExpanded(count)
        }
    }

    private fun withPrimaryColorWriteOnly(draw: () -> Unit) {
        withColorMasks({ drawBuffer -> drawBuffer == 0 }, draw)
    }

    /** 同时绑定当前资源纹理和对应 sampler；外层 pass 负责恢复入口绑定。 */
    private fun bindSystemTexture(binding: CParticleTextureBindingKey, unit: Int) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, CParticleTextureResolver.textureId(binding))
        glBindSampler(unit, CParticleTextureResolver.samplerId(binding))
    }

    /** 使用 26.3 原版光照视图和线性边缘夹取 sampler，不修改光照层生命周期。 */
    private fun bindLightmap(unit: Int) {
        val lightmap = Minecraft.getInstance().gameRenderer.lightmap().texture()
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, lightmap.glTextureId)
        glBindSampler(unit, sampler.glSamplerId)
    }

    private fun withAllColorWritesDisabled(draw: () -> Unit) {
        withColorMasks({ false }, draw)
    }

    private fun withColorMasks(enabled: (Int) -> Boolean, draw: () -> Unit) {
        val colorMasks = Array(glGetInteger(GL_MAX_DRAW_BUFFERS)) { drawBuffer ->
            IntArray(4).also { glGetIntegeri_v(GL_COLOR_WRITEMASK, drawBuffer, it) }
        }
        try {
            colorMasks.indices.forEach { drawBuffer ->
                if (!enabled(drawBuffer)) {
                    glColorMaski(drawBuffer, false, false, false, false)
                }
            }
            draw()
        } finally {
            colorMasks.forEachIndexed { drawBuffer, mask ->
                glColorMaski(
                    drawBuffer,
                    mask[0] != 0,
                    mask[1] != 0,
                    mask[2] != 0,
                    mask[3] != 0,
                )
            }
        }
    }

    private fun applySystemUniforms(
        shader: CooShaderProgram,
        system: CParticleSystem,
        cameraPos: Vec3,
        partial: Float,
        uniformScratch: UniformScratch,
    ) {
        shader.setFloat3(
            "uOriginRelCam", uniformScratch.origin.set(
                (system.origin.x - cameraPos.x).toFloat(),
                (system.origin.y - cameraPos.y).toFloat(),
                (system.origin.z - cameraPos.z).toFloat()
            )
        )
        uniformScratch.setMatrix4(shader, "uPrevGroupMat", system.previousGroupTransform)
        uniformScratch.setMatrix4(shader, "uGroupMat", system.currentGroupTransform)
        uniformScratch.setInt(
            shader,
            "uTransformParticleGeometry",
            if (system.transformsSimulatedParticleSpace) 1 else 0,
        )

        setScalarCurve(shader, "uAlpha", system.alphaCurve, uniformScratch)
        setScalarCurve(shader, "uScale", system.scaleCurve, uniformScratch)
        setColorCurve(shader, "uColor", system.colorCurve, uniformScratch)
        val systemTime = system.tickCount + partial
        uniformScratch.setFloat(shader, "uSystemTime", systemTime)
        uniformScratch.setInt(shader, "uSystemTick", system.tickCount)
        uniformScratch.setInt(shader, "uHasMask", if (system.maskTextureBindingKey == null) 0 else 1)
        uniformScratch.setFloat(shader, "uCurveCycleTicks", system.curveCycleTicks)
        uniformScratch.setFloat(shader, "uColorCycleTicks", system.colorCycleTicks)
        uniformScratch.setFloat(shader, "uColorCycleSpatialScale", system.colorCycleSpatialScale)

        val transition = system.visualTransition
        val transitionProgress = transition?.progressAt(systemTime)
        uniformScratch.setInt(shader, "uTransitionEnabled", if (transitionProgress == null) 0 else 1)
        setScalarCurve(shader, "uTransitionAlpha", transition?.alphaCurve, uniformScratch)
        setScalarCurve(shader, "uTransitionScale", transition?.scaleCurve, uniformScratch)
        if (transition != null && transitionProgress != null) {
            shader.setFloat4(
                "uTransitionParams",
                uniformScratch.params.set(
                    1F,
                    1F,
                    transitionProgress,
                    if (transition.hasColor) 1F else 0F,
                )
            )
            if (transition.hasColor) {
                shader.setFloat3("uTransitionColorFrom", transition.colorFrom)
                shader.setFloat3("uTransitionColorTo", transition.colorTo)
            }
        }
        val alphaTransition = system.alphaTransition
        val alphaTransitionProgress = alphaTransition?.progressAt(systemTime)
        setScalarCurve(
            shader,
            "uAlphaTransition",
            alphaTransition?.alphaCurve?.takeIf { alphaTransitionProgress != null },
            uniformScratch,
        )
        uniformScratch.setFloat(shader, "uAlphaTransitionProgress", alphaTransitionProgress ?: 0F)
    }

    /**
     * 按系统与 transition 路径共用的 uniform 命名上传一条标量曲线。
     *
     * 示例：前缀 `uAlpha` 会写入 `uAlphaKeys`、`uAlphaCurveType` 和两组数据数组。
     * 禁止：曲线缺失时必须上传 0 个关键帧，不能让旧 uniform 继续生效。
     *
     * @param shader 当前 CParticle shader
     * @param prefix uniform 组的前缀
     * @param curve 要上传的曲线；`null` 会上传 0 个关键帧，由对应 shader 路径使用默认值
     */
    private fun setScalarCurve(
        shader: CooShaderProgram,
        prefix: String,
        curve: CParticleCurve?,
        uniformScratch: UniformScratch,
    ) {
        if (!uniformScratch.shouldUploadScalarCurve(prefix, curve)) return
        shader.setInt("${prefix}Keys", curve?.keyCount ?: 0)
        shader.setInt("${prefix}CurveType", curve?.interpolation?.wireId ?: 0)
        shader.setFloatArray("${prefix}Curve", curve?.packedData ?: emptyCurve)
        shader.setFloat4Array("${prefix}CurveHandles", curve?.packedHandleData ?: emptyCurveHandles)
    }

    /**
     * 上传一条 RGB 曲线及其两组值控制柄。
     *
     * 示例：前缀 `uColor` 会写入锚点以及独立的 RGB 出入控制柄。
     * 禁止：曲线缺失时必须上传 0 个关键帧，让 shader 保留粒子原色。
     *
     * @param shader 当前 CParticle shader
     * @param prefix uniform 组的前缀
     * @param curve 要上传的曲线；`null` 表示 RGB 倍率恒为 1
     */
    private fun setColorCurve(
        shader: CooShaderProgram,
        prefix: String,
        curve: CParticleColorCurve?,
        uniformScratch: UniformScratch,
    ) {
        if (!uniformScratch.shouldUploadColorCurve(prefix, curve)) return
        shader.setInt("${prefix}Keys", curve?.keyCount ?: 0)
        shader.setInt("${prefix}CurveType", curve?.interpolation?.wireId ?: 0)
        shader.setFloatArray("${prefix}CurveTimes", curve?.packedTimeData ?: emptyColorTimes)
        shader.setFloat3Array("${prefix}CurveValues", curve?.packedColorData ?: emptyColorValues)
        shader.setFloat4Array("${prefix}CurveOutHandles", curve?.packedOutHandleData ?: emptyColorHandles)
        shader.setFloat4Array("${prefix}CurveInHandles", curve?.packedInHandleData ?: emptyColorHandles)
    }

    private class UniformScratch {
        val origin = Vector3f()
        val params = Vector4f()
        private val scalarCurves = HashMap<String, CParticleCurve?>()
        private val colorCurves = HashMap<String, CParticleColorCurve?>()
        private val intValues = HashMap<String, Int>()
        private val floatValues = HashMap<String, Int>()
        private val matrices = HashMap<String, Matrix4f>()

        /** 同一 render pass 内固定字段通常在数百个 system 间完全相同，跳过重复 glUniform。 */
        fun setInt(shader: CooShaderProgram, key: String, value: Int) {
            if (intValues[key] == value) return
            intValues[key] = value
            shader.setInt(key, value)
        }

        /** 使用原始位比较，保留 NaN 与带符号零的 uniform 语义。 */
        fun setFloat(shader: CooShaderProgram, key: String, value: Float) {
            val bits = value.toRawBits()
            if (floatValues[key] == bits) return
            floatValues[key] = bits
            shader.setFloat(key, value)
        }

        /** 只在矩阵内容改变时上传；缓存副本避免 system 后续修改矩阵后误判为相同。 */
        fun setMatrix4(shader: CooShaderProgram, key: String, value: Matrix4f) {
            val cached = matrices[key]
            if (cached != null && cached == value) return
            shader.setMatrix4(key, value)
            if (cached == null) matrices[key] = Matrix4f(value) else cached.set(value)
        }

        /**
         * 同一 render pass 内按曲线对象身份去重。
         *
         * 曲线创建后按不可变数据使用；system 通常共享同一曲线实例。以身份而不是数组内容
         * 做比较，避免每个 emitter 重复扫描和上传几十个固定 float。
         */
        fun shouldUploadScalarCurve(prefix: String, curve: CParticleCurve?): Boolean {
            if (scalarCurves.containsKey(prefix) && scalarCurves[prefix] === curve) return false
            scalarCurves[prefix] = curve
            return true
        }

        fun shouldUploadColorCurve(prefix: String, curve: CParticleColorCurve?): Boolean {
            if (colorCurves.containsKey(prefix) && colorCurves[prefix] === curve) return false
            colorCurves[prefix] = curve
            return true
        }
    }

    /**
     * 释放 CParticle 图形 program 和外观查找表，并从统一注册表注销。
     *
     * 示例：[CParticleSystemManager.releaseAll] 完全关闭 CParticle 子系统时调用。
     * 禁止：普通资源重载不能调用本方法，注册表需要保留同一对象并重新编译。
     */
    fun release() {
        program?.let(ShaderProgramRegistry::unregister)
        program = null
        CParticleAppearanceDescriptors.release()
    }
}
