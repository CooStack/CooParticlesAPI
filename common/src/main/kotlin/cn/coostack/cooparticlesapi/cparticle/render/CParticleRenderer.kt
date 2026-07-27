package cn.coostack.cooparticlesapi.cparticle.render

import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleAppearanceDescriptors
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureResolver
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.*

/**
 * GPU 粒子渲染器: 每个系统一次 instanced draw，同层系统按主纹理绑定连续绘制。
 *
 * 融入 Minecraft 帧: 使用 LevelRenderer 传入的 view/projection 矩阵,
 * 相机相对坐标 (双精度 CPU 侧相减), 通用主纹理 + 光照贴图 + 原版雾效.
 * 调用方先应用当前粒子 ShaderInstance，使 Iris 绑定正确的 gbuffer framebuffer。
 */
object CParticleRenderer {

    private var program: CooShaderProgram? = null
    private val tmpVec3 = Vector3f()
    private val tmpCamUp = Vector3f()
    private val tmpOrigin = Vector3f()
    private val tmpVec4 = Vector4f()
    private val emptyCurve = FloatArray(16)
    private val emptyColorTimes = FloatArray(CParticleColorCurve.MAX_KEYS)
    private val emptyColorValues = FloatArray(CParticleColorCurve.MAX_KEYS * 3)
    private val drawLayers = arrayOf(
        CParticleRenderLayer.OPAQUE,
        CParticleRenderLayer.TRANSLUCENT,
        CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT,
        CParticleRenderLayer.ADDITION_BLEND,
        CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE,
    )

    private fun ensureProgram(): CooShaderProgram {
        val current = program
        if (current != null) {
            if (current.program == 0) current.init()
            return current
        }
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
            .managedId("cparticle/render")
            .build()
            .also {
                it.init()
                program = it
            }
    }

    /**
     * 绘制所有系统 (渲染线程, 世界渲染阶段).
     *
     * Example: 粒子引擎 pass 通过 [CParticleSystemManager.renderParticlePass] 调用此方法。
     * Forbidden: 不要从非渲染线程调用，也不要绕过调用方提供的粒子 pass。
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

        // ---- 状态快照 ----
        val prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val prevActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        RenderSystem.activeTexture(GL_TEXTURE0)
        val prevTex0 = glGetInteger(GL_TEXTURE_BINDING_2D)
        RenderSystem.activeTexture(GL_TEXTURE1)
        val prevTex1 = glGetInteger(GL_TEXTURE_BINDING_2D)
        RenderSystem.activeTexture(GL_TEXTURE2)
        val prevTex2Buffer = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        RenderSystem.activeTexture(GL_TEXTURE3)
        val prevTex3Buffer = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        RenderSystem.activeTexture(prevActiveTexture)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB)
        val blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA)
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthFunc = glGetInteger(GL_DEPTH_FUNC)
        val colorMask = IntArray(4)
        glGetIntegeri_v(GL_COLOR_WRITEMASK, 0, colorMask)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val lightTexture = Minecraft.getInstance().gameRenderer.lightTexture()
        val lightmapWasEnabled = RenderSystem.getShaderTexture(2) != 0

        try {
            // ---- 纹理: 0=当前批次主纹理 1=光照贴图 2=纹理描述符 3=外观描述符 ----
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
            shader.setInt("uDepthOnly", 0)

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
            glDisable(GL_CULL_FACE)

            // ---- 分层后按 binding 连续绘制；纹理绑定次数只随批次数增长 ----
            val deferredDepthSystems = ArrayList<CParticleSystem>()
            for (layer in drawLayers) {
                if (!pass.accepts(layer)) continue
                val layerSystems = visibleSystems.asSequence()
                    .filter { it.layer == layer }
                    .sortedBy(CParticleSystem::textureBindingKey)
                    .toList()
                if (layerSystems.isEmpty()) continue
                layer.applyState()
                if (layer.requiresDeferredDepthWrite) {
                    glDepthMask(false)
                    deferredDepthSystems.addAll(layerSystems)
                }
                var boundMainTexture: CParticleTextureBindingKey? = null

                for (system in layerSystems) {
                    if (system.textureBindingKey != boundMainTexture) {
                        RenderSystem.activeTexture(GL_TEXTURE0)
                        RenderSystem.bindTexture(CParticleTextureResolver.textureId(system.textureBindingKey))
                        boundMainTexture = system.textureBindingKey
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
                for (system in deferredDepthSystems.sortedBy(CParticleSystem::textureBindingKey)) {
                    if (system.textureBindingKey != boundMainTexture) {
                        RenderSystem.activeTexture(GL_TEXTURE0)
                        RenderSystem.bindTexture(CParticleTextureResolver.textureId(system.textureBindingKey))
                        boundMainTexture = system.textureBindingKey
                    }
                    applySystemUniforms(shader, system, cameraPos, partial)
                    system.glBuffer.draw(system.store.highWater)
                }
            }
        } finally {
            // ---- 状态还原 ----
            if (!lightmapWasEnabled) lightTexture.turnOffLightLayer()
            RenderSystem.activeTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_BUFFER, prevTex3Buffer)
            RenderSystem.activeTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_BUFFER, prevTex2Buffer)
            RenderSystem.activeTexture(GL_TEXTURE1)
            RenderSystem.bindTexture(prevTex1)
            RenderSystem.activeTexture(GL_TEXTURE0)
            RenderSystem.bindTexture(prevTex0)
            RenderSystem.activeTexture(prevActiveTexture)
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha)
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
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (prevProgram > 0 && glIsProgram(prevProgram)) glUseProgram(prevProgram) else glUseProgram(0)
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

        val alphaCurve = system.alphaCurve
        shader.setInt("uAlphaKeys", alphaCurve?.keyCount ?: 0)
        shader.setFloatArray("uAlphaCurve", alphaCurve?.packed ?: emptyCurve)
        val sizeCurve = system.sizeCurve
        shader.setInt("uSizeKeys", sizeCurve?.keyCount ?: 0)
        shader.setFloatArray("uSizeCurve", sizeCurve?.packed ?: emptyCurve)
        val colorCurve = system.colorCurve
        shader.setInt("uColorKeys", colorCurve?.keyCount ?: 0)
        shader.setFloatArray("uColorCurveTimes", colorCurve?.packedTimes ?: emptyColorTimes)
        shader.setFloat3Array("uColorCurveValues", colorCurve?.packedColors ?: emptyColorValues)
        val systemTime = system.tickCount + partial
        shader.setFloat("uSystemTime", systemTime)
        shader.setInt("uSystemTick", system.tickCount)
        shader.setFloat("uCurveCycleTicks", system.curveCycleTicks)
        shader.setFloat("uColorCycleTicks", system.colorCycleTicks)
        shader.setFloat("uColorCycleSpatialScale", system.colorCycleSpatialScale)

        val transition = system.visualTransition
        val transitionProgress = transition?.progressAt(systemTime)
        shader.setInt("uTransitionEnabled", if (transitionProgress == null) 0 else 1)
        if (transition != null && transitionProgress != null) {
            shader.setFloat4(
                "uTransitionParams",
                tmpVec4.set(
                    transition.alphaCurve?.sample(transitionProgress) ?: 1f,
                    transition.sizeCurve?.sample(transitionProgress) ?: 1f,
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
        shader.setFloat(
            "uAlphaTransitionScale",
            if (alphaTransition != null && alphaTransitionProgress != null) {
                alphaTransition.alphaCurve?.sample(alphaTransitionProgress) ?: 1f
            } else {
                1f
            },
        )
    }

    fun release() {
        program?.release()
        program = null
        CParticleAppearanceDescriptors.release()
    }
}
