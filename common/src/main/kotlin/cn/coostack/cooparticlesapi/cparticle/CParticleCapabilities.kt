package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.ARBInstancedArrays
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL33

/**
 * GL 能力探测 (渲染线程惰性执行一次).
 *
 * - GL 3.1 draw instancing + vertex attrib divisor: cparticle 渲染的硬性要求
 *   (MC 1.21 的 GL 3.2 上下文通常通过 ARB_instanced_arrays 提供 divisor)
 * - GL 4.3 (compute + SSBO): GPU 模拟路径; 不满足时自动回退 CPU SoA 并行模拟
 */
object CParticleCapabilities {
    @Volatile
    private var detected = false

    @Volatile
    private var warnedMissingContext = false

    private var useArbInstancedArrays = false

    var instancingSupported = false
        private set

    var computeSupported = false
        private set

    internal val detectionComplete: Boolean
        get() = detected

    /** 手动禁用 GPU compute (调试/兼容用) */
    @JvmStatic
    var forceCpuSimulation = false

    /** 必须在持有 GL 上下文的线程调用 */
    @JvmStatic
    fun detect() {
        if (detected) return
        if (!RenderSystem.isOnRenderThreadOrInit()) return

        val caps = runCatching { GL.getCapabilities() }.getOrNull()
        if (caps == null) {
            if (!warnedMissingContext) {
                warnedMissingContext = true
                CooParticlesConstants.logger.warn("[cparticle] 当前渲染线程尚无 GLCapabilities, 稍后重试")
            }
            return
        }
        instancingSupported = supportsInstancing(
            caps.OpenGL31,
            caps.OpenGL33,
            caps.GL_ARB_instanced_arrays,
        )
        useArbInstancedArrays = !caps.OpenGL33 && caps.GL_ARB_instanced_arrays
        computeSupported = caps.OpenGL43 ||
                (caps.GL_ARB_compute_shader && caps.GL_ARB_shader_storage_buffer_object)
        detected = true
        CooParticlesConstants.logger.info(
            "[cparticle] GL能力: version={} glsl={} renderer={} instancing(GL31+divisor)={} computeSim(GL43/ARB)={}",
            runCatching { GL11.glGetString(GL11.GL_VERSION) }.getOrNull() ?: "unknown",
            runCatching { GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION) }.getOrNull() ?: "unknown",
            runCatching { GL11.glGetString(GL11.GL_RENDERER) }.getOrNull() ?: "unknown",
            instancingSupported,
            computeSupported,
        )
    }

    internal fun supportsInstancing(hasGl31: Boolean, hasGl33: Boolean, hasArbInstancedArrays: Boolean): Boolean {
        return hasGl31 && (hasGl33 || hasArbInstancedArrays)
    }

    internal fun setVertexAttribDivisor(index: Int, divisor: Int) {
        if (useArbInstancedArrays) {
            ARBInstancedArrays.glVertexAttribDivisorARB(index, divisor)
        } else {
            GL33.glVertexAttribDivisor(index, divisor)
        }
    }

    internal fun disableComputeSimulation(reason: String) {
        if (!computeSupported) return
        computeSupported = false
        CooParticlesConstants.logger.warn("[cparticle] GPU compute 初始化失败，回退 CPU SoA 模拟: {}", reason)
    }

    @JvmStatic
    fun useGpuSimulation(): Boolean = computeSupported && !forceCpuSimulation
}
