package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.cparticle.CParticleIndexedBlendState
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.renderer.backend.glSamplerId
import cn.coostack.cooparticlesapi.renderer.backend.glTextureId
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.Minecraft
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.util.IdentityHashMap

/**
 * 将普通有界滤色粒子的提取快照交给既有 Iris 粒子 framebuffer。
 *
 * 仅在客户端渲染线程使用；collector 不直接执行 GL，而是在主 pass 的原版 RenderPass 关闭后统一绘制。
 * 自有片元保留纹理 Alpha、雾、颜色限制和预乘，不让 Iris program 替换这些效果。
 */
internal object IrisControlableParticleRenderer {
    /** 当前帧已经排队的快照身份，避免相同提交状态被重复绘制。 */
    private val queuedOwners = IdentityHashMap<Any, Boolean>()
    /** 当前帧按提交顺序保存的不可变几何与图层。 */
    private val batches = ArrayList<ParticleBatch>()
    /** 由统一 shader registry 维护重载的普通粒子程序。 */
    private var program: CooShaderProgram? = null
    /** 本对象拥有的顶点数组；不会引用原版的数组对象。 */
    private var vertexArray = 0
    /** 本对象拥有、每批次流式更新的普通顶点缓冲。 */
    private var vertexBuffer = 0

    /**
     * 在 collector 阶段排队一组已经提取的有界粒子，不读取实际粒子或控制器。
     *
     * 示例：普通粒子组提交时调用 `enqueue(state, layer, capturedVertices)`。
     * @param owner 当前帧 render-state 的对象身份
     * @param layer 保留纹理和深度语义的注册图层
     * @param vertices 摄像机相对坐标的四边形顶点快照
     */
    fun enqueue(owner: Any, layer: CooParticleTextureSheet.SheetLayer, vertices: List<ParticleVertex>) {
        if (queuedOwners.put(owner, true) != null || vertices.isEmpty()) return
        require(vertices.size % 4 == 0) { "Particle geometry must contain complete quads" }
        batches.add(ParticleBatch(layer, vertices))
    }

    /**
     * 在既有主 pass 尾部借用 Iris 目标提交本帧快照；无光影或无几何时不访问 GL。
     * 示例：`CParticleSystemManager` 在原版 RenderPass 关闭后调用 `renderSubmitted()`。
     */
    fun renderSubmitted() {
        if (batches.isEmpty() || !CooParticlesAPIClient.checkIrisShaderPackUsed()) return
        val camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        OpenGlPostEffectExecutionBackend.withPreservedGlState {
            val shader = ensureProgram()
            if (vertexArray == 0) vertexArray = glGenVertexArrays()
            if (vertexBuffer == 0) vertexBuffer = glGenBuffers()
            val drawn = IrisCompat.runWithParticleShader(
                CParticleRenderPass.TRANSLUCENT,
                camera.viewRotationMatrix,
                camera.projectionMatrix,
                fallback = { drawBatches(shader) },
            ) {
                drawBatches(shader)
            }
            if (!drawn) drawBatches(shader)
        }
        clear()
    }

    /**
     * 清空帧或世界持有的 CPU 快照，不触碰借用的纹理或 Iris framebuffer。
     * 示例：世界渲染帧开始时调用 `clear()`，丢弃上帧未执行的队列。
     */
    fun clear() {
        batches.clear()
        queuedOwners.clear()
    }

    /**
     * 资源重载或完全关闭时释放自有 GPU 对象，下一帧按需重建。
     * 示例：渲染线程资源重载入口调用 `release()`。
     */
    fun release() {
        clear()
        program?.let(ShaderProgramRegistry::unregister)
        program = null
        if (vertexBuffer != 0) glDeleteBuffers(vertexBuffer)
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray)
        vertexBuffer = 0
        vertexArray = 0
    }

    /** 在已核验的 OpenGL 3.3 基线上编译普通粒子的有界片元与快照顶点适配。 */
    private fun ensureProgram(): CooShaderProgram {
        val shader = program ?: AdvancedShaderProgramBuilder()
            .vertex("core/vertex/controlable_particle.vsh")
            .fragment("core/fragment/controlable_particle.fsh")
            .attributeLocation("Position", 0)
            .attributeLocation("UV0", 1)
            .attributeLocation("Color", 2)
            .attributeLocation("LightUV", 3)
            .managedId("particle/iris_bounded")
            .build()
            .also {
                program = it
                ShaderProgramRegistry.register(it)
            }
        if (shader.program == 0) shader.init()
        return shader
    }

    /** 在已经由 Iris setupState 选定的 framebuffer 内绘制，不改变其附件绑定。 */
    private fun drawBatches(shader: CooShaderProgram) {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        val fog = camera.fogData
        glUseProgram(shader.program)
        shader.setMatrix4("uView", camera.viewRotationMatrix)
        shader.setMatrix4("uProj", camera.projectionMatrix)
        shader.setInt("uMainTexture", 0)
        shader.setInt("uLightmap", 1)
        shader.setFloat("uFogEnvironmentalStart", fog.environmentalStart)
        shader.setFloat("uFogEnvironmentalEnd", fog.environmentalEnd)
        shader.setFloat("uFogRenderDistanceStart", fog.renderDistanceStart)
        shader.setFloat("uFogRenderDistanceEnd", fog.renderDistanceEnd)
        shader.setFloat4("uFogColor", Vector4f(fog.color))
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, minecraft.gameRenderer.lightmap().texture().glTextureId)
        glBindSampler(1, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR).glSamplerId)
        // 自有片元没有 shader-pack 的辅助输出，必须屏蔽主颜色以外的所有附件。
        repeat(glGetInteger(GL_MAX_DRAW_BUFFERS)) { index ->
            glColorMaski(index, index == 0, index == 0, index == 0, index == 0)
        }
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_GEQUAL)
        glDisable(GL_CULL_FACE)
        glEnablei(GL_BLEND, 0)
        CParticleIndexedBlendState.setEquation(0, GL_FUNC_ADD, GL_FUNC_ADD)
        CParticleIndexedBlendState.setFactors(0, GL_ONE, GL_ONE_MINUS_SRC_COLOR, GL_ZERO, GL_ONE)
        glBindVertexArray(vertexArray)
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glEnableVertexAttribArray(2)
        glEnableVertexAttribArray(3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 44, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 44, 12L)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 44, 20L)
        glVertexAttribPointer(3, 2, GL_FLOAT, false, 44, 36L)
        for (batch in batches) {
            val texture = minecraft.textureManager.getTexture(batch.layer.atlas)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, texture.texture.glTextureId)
            glBindSampler(0, texture.sampler.glSamplerId)
            glDepthMask(batch.layer.depthWrite)
            uploadAndDraw(batch.vertices)
        }
    }

    /** 与原 collector 的排序策略一致，先按四边形中心远近排序，再展开为六个三角形顶点。 */
    private fun uploadAndDraw(vertices: List<ParticleVertex>) {
        val quads = vertices.chunked(4).sortedByDescending { quad ->
            val x = quad.sumOf { it.x.toDouble() } / 4.0
            val y = quad.sumOf { it.y.toDouble() } / 4.0
            val z = quad.sumOf { it.z.toDouble() } / 4.0
            x * x + y * y + z * z
        }
        val buffer = MemoryUtil.memAlloc(quads.size * 6 * 44)
        try {
            val order = intArrayOf(0, 1, 2, 2, 3, 0)
            for (quad in quads) {
                for (index in order) {
                    val vertex = quad[index]
                    buffer.putFloat(vertex.x).putFloat(vertex.y).putFloat(vertex.z)
                    buffer.putFloat(vertex.u).putFloat(vertex.v)
                    buffer.putFloat((vertex.color ushr 16 and 255) / 255F)
                    buffer.putFloat((vertex.color ushr 8 and 255) / 255F)
                    buffer.putFloat((vertex.color and 255) / 255F)
                    buffer.putFloat((vertex.color ushr 24 and 255) / 255F)
                    buffer.putFloat(((vertex.light and 65535) + 8F) / 256F)
                    buffer.putFloat(((vertex.light ushr 16 and 65535) + 8F) / 256F)
                }
            }
            buffer.flip()
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STREAM_DRAW)
            glDrawArrays(GL_TRIANGLES, 0, quads.size * 6)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    /**
     * 单组普通粒子的帧快照，不持有粒子实体。
     *
     * @property layer 本组注册的图集与深度设置
     * @property vertices 当前帧完整四边形顶点
     */
    private data class ParticleBatch(
        val layer: CooParticleTextureSheet.SheetLayer,
        val vertices: List<ParticleVertex>,
    )
}
