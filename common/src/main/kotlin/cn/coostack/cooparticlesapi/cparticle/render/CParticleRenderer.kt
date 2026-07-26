package cn.coostack.cooparticlesapi.cparticle.render

import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.*

/**
 * GPU 粒子渲染器: 每个系统一次 instanced draw.
 *
 * 融入 Minecraft 帧: 使用 LevelRenderer 传入的 view/projection 矩阵,
 * 相机相对坐标 (双精度 CPU 侧相减), 原版粒子图集 + 光照贴图 + 原版雾效.
 * 调用方先应用当前粒子 ShaderInstance，使 Iris 绑定正确的 gbuffer framebuffer。
 */
object CParticleRenderer {

    private var program: CooShaderProgram? = null
    private val tmpVec3 = Vector3f()
    private val tmpCamUp = Vector3f()
    private val tmpOrigin = Vector3f()
    private val tmpVec4 = Vector4f()
    private val emptyCurve = FloatArray(16)
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
            .attributeLocation("iUv", 5)
            .attributeLocation("iColor", 6)
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
     * @param view LevelRenderer 的 frustum(model-view) 矩阵
     * @param partial tick 插值
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
        if (systems.none {
                pass.accepts(it.layer) && !it.released && it.store.highWater > 0 && it.isVisible(cameraPos)
            }
        ) return

        val shader = ensureProgram()
        if (shader.program == 0) return
        val frameId = CParticleSystemManager.currentRenderFrameId

        // ---- 状态快照 ----
        val prevProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val prevActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val prevTex0 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(GL_TEXTURE1)
        val prevTex1 = glGetInteger(GL_TEXTURE_BINDING_2D)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthFunc = glGetInteger(GL_DEPTH_FUNC)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val lightTexture = Minecraft.getInstance().gameRenderer.lightTexture()

        try {
            // ---- 纹理: 0=粒子图集 1=光照贴图 ----
            lightTexture.turnOnLightLayer()
            val lightmapId = RenderSystem.getShaderTexture(2)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, CParticleSprites.atlasGlId())
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, lightmapId)

            glUseProgram(shader.program)
            shader.setMatrix4("uView", view)
            shader.setMatrix4("uProj", proj)
            shader.setFloat("uPartial", partial)
            shader.setInt("uAtlas", 0)
            shader.setInt("uLightmap", 1)

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

            // ---- 分层绘制 (不透明 → 半透明 → 加法) ----
            for (layer in drawLayers) for (system in systems) {
                if (!pass.accepts(layer) || system.layer != layer || system.released ||
                    system.store.highWater <= 0 || !system.isVisible(cameraPos)
                ) {
                    continue
                }
                system.ensureGl()
                system.prepareDynamicVisuals(frameId)
                system.layer.applyState()

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
                val systemTime = system.tickCount + partial
                shader.setFloat("uSystemTime", systemTime)
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

                system.glBuffer.draw(system.store.highWater)
            }
        } finally {
            // ---- 状态还原 ----
            lightTexture.turnOffLightLayer()
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, prevTex1)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, prevTex0)
            glActiveTexture(prevActiveTexture)
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            if (depthEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            glDepthMask(depthMask)
            glDepthFunc(depthFunc)
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
            if (prevProgram > 0 && glIsProgram(prevProgram)) glUseProgram(prevProgram) else glUseProgram(0)
        }
    }

    fun release() {
        program?.release()
        program = null
    }
}
