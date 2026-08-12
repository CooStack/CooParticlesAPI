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
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureResolver
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import com.mojang.blaze3d.systems.RenderSystem
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
 */
object CParticleRenderer {

    private var program: CooShaderProgram? = null
    private val tmpVec3 = Vector3f()
    private val tmpCamUp = Vector3f()
    private val tmpOrigin = Vector3f()
    private val tmpVec4 = Vector4f()
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

    /**
     * 把 CParticle 图形 program 加入统一注册表，不触发 GL 编译。
     *
     * Example: [CooParticlesAPIClient.init] 在 loader 客户端注册阶段调用。
     * Forbidden: 本方法不能调用 `init()`，否则早期客户端初始化可能还没有 GL 上下文。
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
     * Example: 正常路径直接返回渲染初始化阶段已经编译的 program。
     * Forbidden: 只能在持有 GL 上下文的渲染线程调用。
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
        if (systems.isEmpty() || pass == CParticleRenderPass.NONE) return
        val cameraPos = camera.position
        val visibleSystems = systems.filter {
            pass.accepts(it.layer) && !it.released && it.store.highWater > 0 && it.isVisible(cameraPos)
        }
        if (visibleSystems.isEmpty()) return

        val shader = ensureProgram()
        if (shader.program == 0) return
        val frameId = CParticleSystemManager.currentRenderFrameId
        val irisShaderPackActive = CooParticlesAPIClient.checkIrisShaderPackUsed()

        // ---- 状态快照 ----
        val prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val prevShaderTexture0 = RenderSystem.getShaderTexture(0)
        val prevActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        RenderSystem.activeTexture(GL_TEXTURE0)
        val prevTex0 = glGetInteger(GL_TEXTURE_BINDING_2D)
        RenderSystem.activeTexture(GL_TEXTURE1)
        val prevTex1 = glGetInteger(GL_TEXTURE_BINDING_2D)
        RenderSystem.activeTexture(GL_TEXTURE2)
        val prevTex2Buffer = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        RenderSystem.activeTexture(GL_TEXTURE3)
        val prevTex3Buffer = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        RenderSystem.activeTexture(GL_TEXTURE4)
        val prevTex4 = glGetInteger(GL_TEXTURE_BINDING_2D)
        RenderSystem.activeTexture(prevActiveTexture)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB)
        val blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA)
        val indexedBlendStateAvailable = irisShaderPackActive && CParticleIndexedBlendState.isAvailable()
        val indexedBlendEnabled = indexedBlendStateAvailable && GL30.glIsEnabledi(GL_BLEND, 0)
        val indexedBlendSrcRgb = if (indexedBlendStateAvailable) GL30.glGetIntegeri(GL_BLEND_SRC_RGB, 0) else 0
        val indexedBlendDstRgb = if (indexedBlendStateAvailable) GL30.glGetIntegeri(GL_BLEND_DST_RGB, 0) else 0
        val indexedBlendSrcAlpha = if (indexedBlendStateAvailable) GL30.glGetIntegeri(GL_BLEND_SRC_ALPHA, 0) else 0
        val indexedBlendDstAlpha = if (indexedBlendStateAvailable) GL30.glGetIntegeri(GL_BLEND_DST_ALPHA, 0) else 0
        val indexedBlendEquationRgb = if (indexedBlendStateAvailable) GL30.glGetIntegeri(GL_BLEND_EQUATION_RGB, 0) else 0
        val indexedBlendEquationAlpha = if (indexedBlendStateAvailable) GL30.glGetIntegeri(GL_BLEND_EQUATION_ALPHA, 0) else 0
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthFunc = glGetInteger(GL_DEPTH_FUNC)
        val colorMask = IntArray(4)
        glGetIntegeri_v(GL_COLOR_WRITEMASK, 0, colorMask)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val cullFaceMode = glGetInteger(GL_CULL_FACE_MODE)
        val frontFaceMode = glGetInteger(GL_FRONT_FACE)
        val lightTexture = Minecraft.getInstance().gameRenderer.lightTexture()
        val lightmapWasEnabled = RenderSystem.getShaderTexture(2) != 0

        try {
            // ---- 纹理: 0=基础纹理 1=光照贴图 2=纹理描述符 3=外观描述符 4=蒙版纹理 ----
            if (!lightmapWasEnabled) {
                RenderSystem.activeTexture(GL_TEXTURE0)
                lightTexture.turnOnLightLayer()
            }
            val lightmapId = RenderSystem.getShaderTexture(2)
            RenderSystem.activeTexture(GL_TEXTURE1)
            RenderSystem.bindTexture(lightmapId)

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
            shader.setInt("uPremultiplyRgbByAlpha", 0)

            // 相机朝向基 (BILLBOARD 用; 与原版粒子 q*X̂=left / q*Ŷ=up 一致)
            val left = camera.leftVector
            val up = camera.upVector
            shader.setFloat3("uCamLeft", tmpVec3.set(left.x(), left.y(), left.z()))
            shader.setFloat3("uCamUp", tmpCamUp.set(up.x(), up.y(), up.z()))

            // 原版雾
            val fogColor = RenderSystem.getShaderFogColor()
            shader.setFloat("uFogStart", RenderSystem.getShaderFogStart())
            shader.setFloat("uFogEnd", RenderSystem.getShaderFogEnd())
            shader.setFloat4("uFogColor", tmpVec4.set(fogColor[0], fogColor[1], fogColor[2], fogColor[3]))
            shader.setInt("uFogShape", RenderSystem.getShaderFogShape().index)

            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GL_LEQUAL)
            glEnable(GL_CULL_FACE)
            glCullFace(GL_BACK)
            glFrontFace(GL_CCW)

            if (irisShaderPackActive) {
                renderWithIrisParticleShader(shader, visibleSystems, view, proj, cameraPos, partial, pass)
            } else {
                shader.setInt("uIrisExpansion", 0)
                // ---- 分层后按 binding 连续绘制；纹理绑定次数只随批次数增长 ----
                val deferredDepthSystems = ArrayList<CParticleSystem>()
                for (layer in drawLayers) {
                    if (!pass.accepts(layer)) continue
                    val layerSystems = visibleSystems.asSequence()
                        .filter { it.layer == layer }
                        .sortedWith(compareBy(CParticleSystem::textureBindingKey)
                            .thenBy(CParticleSystem::maskTextureBindingKey))
                        .toList()
                    if (layerSystems.isEmpty()) continue
                    layer.applyState()
                    shader.setInt("uPremultiplyRgbByAlpha", if (layer.premultiplyRgbByAlpha) 1 else 0)
                    if (layer.requiresDeferredDepthWrite) {
                        glDepthMask(false)
                        deferredDepthSystems.addAll(layerSystems)
                    }
                    var boundMainTexture: CParticleTextureBindingKey? = null
                    var boundMaskTexture: CParticleTextureBindingKey? = null

                    for (system in layerSystems) {
                        if (system.textureBindingKey != boundMainTexture) {
                            RenderSystem.activeTexture(GL_TEXTURE0)
                            RenderSystem.bindTexture(CParticleTextureResolver.textureId(system.textureBindingKey))
                            boundMainTexture = system.textureBindingKey
                        }
                        val maskBinding = system.maskTextureBindingKey
                        if (maskBinding != null && maskBinding != boundMaskTexture) {
                            RenderSystem.activeTexture(GL_TEXTURE4)
                            RenderSystem.bindTexture(CParticleTextureResolver.textureId(maskBinding))
                            boundMaskTexture = maskBinding
                        }

                        applySystemUniforms(shader, system, cameraPos, partial)
                        system.glBuffer.draw(system.store.highWater)
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
                            RenderSystem.activeTexture(GL_TEXTURE0)
                            RenderSystem.bindTexture(CParticleTextureResolver.textureId(system.textureBindingKey))
                            boundMainTexture = system.textureBindingKey
                        }
                        val maskBinding = system.maskTextureBindingKey
                        if (maskBinding != null && maskBinding != boundMaskTexture) {
                            RenderSystem.activeTexture(GL_TEXTURE4)
                            RenderSystem.bindTexture(CParticleTextureResolver.textureId(maskBinding))
                            boundMaskTexture = maskBinding
                        }
                        applySystemUniforms(shader, system, cameraPos, partial)
                        system.glBuffer.draw(system.store.highWater)
                    }
                }
            }
        } finally {
            // ---- 状态还原 ----
            if (!lightmapWasEnabled) lightTexture.turnOffLightLayer()
            RenderSystem.activeTexture(GL_TEXTURE4)
            RenderSystem.bindTexture(prevTex4)
            RenderSystem.activeTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_BUFFER, prevTex3Buffer)
            RenderSystem.activeTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_BUFFER, prevTex2Buffer)
            RenderSystem.activeTexture(GL_TEXTURE1)
            RenderSystem.bindTexture(prevTex1)
            RenderSystem.activeTexture(GL_TEXTURE0)
            RenderSystem.bindTexture(prevTex0)
            RenderSystem.setShaderTexture(0, prevShaderTexture0)
            RenderSystem.activeTexture(prevActiveTexture)
            if (indexedBlendStateAvailable) {
                if (indexedBlendEnabled) glEnablei(GL_BLEND, 0) else glDisablei(GL_BLEND, 0)
                CParticleIndexedBlendState.setFactors(
                    0,
                    indexedBlendSrcRgb,
                    indexedBlendDstRgb,
                    indexedBlendSrcAlpha,
                    indexedBlendDstAlpha,
                )
                CParticleIndexedBlendState.setEquation(
                    0, indexedBlendEquationRgb, indexedBlendEquationAlpha,
                )
            } else {
                if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
                glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
                glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha)
            }
            if (depthEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            glDepthMask(depthMask)
            glDepthFunc(depthFunc)
            glColorMaski(
                0,
                colorMask[0] != 0,
                colorMask[1] != 0,
                colorMask[2] != 0,
                colorMask[3] != 0,
            )
            glCullFace(cullFaceMode)
            glFrontFace(frontFaceMode)
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (prevProgram > 0 && glIsProgram(prevProgram)) glUseProgram(prevProgram) else glUseProgram(0)
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
        systems: List<CParticleSystem>,
        view: Matrix4f,
        proj: Matrix4f,
        cameraPos: Vec3,
        partial: Float,
        pass: CParticleRenderPass,
    ) {
        shader.setInt("uIrisExpansion", 1)
        shader.setInt("uPremultiplyRgbByAlpha", 0)
        for (system in systems) {
            if (system.layer.premultiplyRgbByAlpha) continue
            applySystemUniforms(shader, system, cameraPos, partial)
            system.glBuffer.expandForParticleShader(system.store.highWater)
        }
        shader.setInt("uIrisExpansion", 0)

        val firstTexture = systems.firstOrNull()?.textureBindingKey ?: return
        RenderSystem.setShaderTexture(0, CParticleTextureResolver.textureId(firstTexture))
        IrisCompat.runWithParticleShader(pass, view, proj) {
            val irisParticleProgram = glGetInteger(GL_CURRENT_PROGRAM)
            val deferredExpandedSystems = ArrayList<CParticleSystem>()
            val deferredDirectSystems = ArrayList<CParticleSystem>()
            for (layer in drawLayers) {
                if (!pass.accepts(layer)) continue
                val layerSystems = systems.asSequence()
                    .filter { it.layer == layer }
                    .sortedWith(compareBy(CParticleSystem::textureBindingKey)
                        .thenBy(CParticleSystem::maskTextureBindingKey))
                    .toList()
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
                        // Iris apply 会按 ShaderInstance 的槽位重绑纹理，切回本 program 后恢复其 GPU 资源。
                        RenderSystem.activeTexture(GL_TEXTURE1)
                        RenderSystem.bindTexture(RenderSystem.getShaderTexture(2))
                        CParticleSprites.bindLookup(2)
                        CParticleAppearanceDescriptors.bindLookup(3)
                        shader.setInt("uPremultiplyRgbByAlpha", 1)
                        withPrimaryColorWriteOnly {
                            drawInstancedSystems(shader, layerSystems, cameraPos, partial)
                        }
                    } finally {
                        shader.setInt("uPremultiplyRgbByAlpha", 0)
                        glUseProgram(irisParticleProgram)
                    }
                } else {
                    drawExpandedSystems(layerSystems)
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
                            shader.setInt("uDepthOnly", 1)
                            drawInstancedSystems(shader, deferredDirectSystems, cameraPos, partial)
                        } finally {
                            shader.setInt("uDepthOnly", 0)
                            glUseProgram(irisParticleProgram)
                        }
                    }
                }
            }
            glUseProgram(irisParticleProgram)
        }
    }

    private fun drawInstancedSystems(
        shader: CooShaderProgram,
        systems: Collection<CParticleSystem>,
        cameraPos: Vec3,
        partial: Float,
    ) {
        var boundMainTexture: CParticleTextureBindingKey? = null
        var boundMaskTexture: CParticleTextureBindingKey? = null
        for (system in systems.sortedWith(
            compareBy(CParticleSystem::textureBindingKey)
                .thenBy(CParticleSystem::maskTextureBindingKey)
        )) {
            if (system.textureBindingKey != boundMainTexture) {
                RenderSystem.activeTexture(GL_TEXTURE0)
                RenderSystem.bindTexture(CParticleTextureResolver.textureId(system.textureBindingKey))
                boundMainTexture = system.textureBindingKey
            }
            val maskBinding = system.maskTextureBindingKey
            if (maskBinding != null && maskBinding != boundMaskTexture) {
                RenderSystem.activeTexture(GL_TEXTURE4)
                RenderSystem.bindTexture(CParticleTextureResolver.textureId(maskBinding))
                boundMaskTexture = maskBinding
            }
            applySystemUniforms(shader, system, cameraPos, partial)
            system.glBuffer.draw(system.store.highWater)
        }
    }

    private fun drawExpandedSystems(systems: Collection<CParticleSystem>) {
        var boundMainTexture: CParticleTextureBindingKey? = null
        for (system in systems.sortedBy(CParticleSystem::textureBindingKey)) {
            if (system.textureBindingKey != boundMainTexture) {
                val textureId = CParticleTextureResolver.textureId(system.textureBindingKey)
                RenderSystem.setShaderTexture(0, textureId)
                RenderSystem.activeTexture(GL_TEXTURE0)
                RenderSystem.bindTexture(textureId)
                boundMainTexture = system.textureBindingKey
            }
            system.glBuffer.drawExpanded(system.store.highWater)
        }
    }

    private fun withPrimaryColorWriteOnly(draw: () -> Unit) {
        withColorMasks({ drawBuffer -> drawBuffer == 0 }, draw)
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
    ) {
        shader.setFloat3(
            "uOriginRelCam", tmpOrigin.set(
                (system.origin.x - cameraPos.x).toFloat(),
                (system.origin.y - cameraPos.y).toFloat(),
                (system.origin.z - cameraPos.z).toFloat()
            )
        )
        shader.setMatrix4("uPrevGroupMat", system.previousGroupTransform)
        shader.setMatrix4("uGroupMat", system.currentGroupTransform)
        shader.setInt(
            "uTransformParticleGeometry",
            if (system.transformsSimulatedParticleSpace) 1 else 0,
        )

        setScalarCurve(shader, "uAlpha", system.alphaCurve)
        setScalarCurve(shader, "uScale", system.scaleCurve)
        setColorCurve(shader, "uColor", system.colorCurve)
        val systemTime = system.tickCount + partial
        shader.setFloat("uSystemTime", systemTime)
        shader.setInt("uSystemTick", system.tickCount)
        shader.setInt("uHasMask", if (system.maskTextureBindingKey == null) 0 else 1)
        shader.setFloat("uCurveCycleTicks", system.curveCycleTicks)
        shader.setFloat("uColorCycleTicks", system.colorCycleTicks)
        shader.setFloat("uColorCycleSpatialScale", system.colorCycleSpatialScale)

        val transition = system.visualTransition
        val transitionProgress = transition?.progressAt(systemTime)
        shader.setInt("uTransitionEnabled", if (transitionProgress == null) 0 else 1)
        setScalarCurve(shader, "uTransitionAlpha", transition?.alphaCurve)
        setScalarCurve(shader, "uTransitionScale", transition?.scaleCurve)
        if (transition != null && transitionProgress != null) {
            shader.setFloat4(
                "uTransitionParams",
                tmpVec4.set(
                    1f,
                    1f,
                    transitionProgress,
                    if (transition.hasColor) 1f else 0f,
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
        )
        shader.setFloat("uAlphaTransitionProgress", alphaTransitionProgress ?: 0f)
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
    private fun setScalarCurve(shader: CooShaderProgram, prefix: String, curve: CParticleCurve?) {
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
    private fun setColorCurve(shader: CooShaderProgram, prefix: String, curve: CParticleColorCurve?) {
        shader.setInt("${prefix}Keys", curve?.keyCount ?: 0)
        shader.setInt("${prefix}CurveType", curve?.interpolation?.wireId ?: 0)
        shader.setFloatArray("${prefix}CurveTimes", curve?.packedTimeData ?: emptyColorTimes)
        shader.setFloat3Array("${prefix}CurveValues", curve?.packedColorData ?: emptyColorValues)
        shader.setFloat4Array("${prefix}CurveOutHandles", curve?.packedOutHandleData ?: emptyColorHandles)
        shader.setFloat4Array("${prefix}CurveInHandles", curve?.packedInHandleData ?: emptyColorHandles)
    }

    /**
     * 释放 CParticle 图形 program 和外观查找表，并从统一注册表注销。
     *
     * Example: [CParticleSystemManager.releaseAll] 完全关闭 CParticle 子系统时调用。
     * Forbidden: 普通资源重载不能调用本方法，注册表需要保留同一对象并重新编译。
     */
    fun release() {
        program?.let(ShaderProgramRegistry::unregister)
        program = null
        CParticleAppearanceDescriptors.release()
    }
}
