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
 * - GL 4.3，或 ARB compute + SSBO 扩展：GPU 模拟的硬性要求；不满足时直接报错
 * - capability 只负责确认 GL 调用入口可用，shader 是否兼容由真实编译和链接结果决定
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

    /** 显式选择 CPU 模拟，仅用于调试或调用方主动选择的兼容模式。 */
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
        val supportsOpenGl43 = caps.OpenGL43
        val supportsArbCompute = caps.GL_ARB_compute_shader
        val supportsArbSsbo = caps.GL_ARB_shader_storage_buffer_object
        computeSupported = supportsCompute(
            supportsOpenGl43,
            supportsArbCompute,
            supportsArbSsbo,
        )
        detected = true
        CooParticlesConstants.logger.info(
            "[cparticle] GL capabilities: version={} glsl={} renderer={} instancing={} compute={} gl43={} arbCompute={} arbSsbo={}",
            runCatching { GL11.glGetString(GL11.GL_VERSION) }.getOrNull() ?: "unknown",
            runCatching { GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION) }.getOrNull() ?: "unknown",
            runCatching { GL11.glGetString(GL11.GL_RENDERER) }.getOrNull() ?: "unknown",
            instancingSupported,
            computeSupported,
            supportsOpenGl43,
            supportsArbCompute,
            supportsArbSsbo,
        )
    }

    internal fun supportsInstancing(hasGl31: Boolean, hasGl33: Boolean, hasArbInstancedArrays: Boolean): Boolean {
        return hasGl31 && (hasGl33 || hasArbInstancedArrays)
    }

    /**
     * 判断当前上下文是否提供 compute shader 与 SSBO 所需的完整入口。
     *
     * ARB 路径只在两个扩展同时存在时成立。shader 源码是否能编译由 program 初始化阶段继续校验。
     */
    internal fun supportsCompute(hasGl43: Boolean, hasArbCompute: Boolean, hasArbSsbo: Boolean): Boolean {
        return hasGl43 || (hasArbCompute && hasArbSsbo)
    }

    /**
     * 确认 CParticle 渲染所需的实例化入口已经完成探测并可用。
     *
     * 能力缺失属于配置错误，不能让 emitter 静默转回传统 CPU 粒子。
     */
    internal fun requireGpuParticleRendering() {
        check(detectionComplete) {
            "[cparticle] GL capability detection did not complete on a thread with a current GL context"
        }
        check(instancingSupported) {
            "[cparticle] GPU particle rendering requires OpenGL 3.1 instancing and a vertex attrib divisor"
        }
    }

    internal fun setVertexAttribDivisor(index: Int, divisor: Int) {
        if (useArbInstancedArrays) {
            ARBInstancedArrays.glVertexAttribDivisorARB(index, divisor)
        } else {
            GL33.glVertexAttribDivisor(index, divisor)
        }
    }

    /**
     * 返回当前是否要求 GPU 模拟。
     *
     * 该结果只受显式 CPU 开关控制。能力探测结果仅用于诊断；真实 GPU 初始化或执行失败
     * 会直接抛错，不能把请求静默改成 CPU 模拟。
     */
    @JvmStatic
    fun useGpuSimulation(): Boolean = !forceCpuSimulation

    /**
     * 返回当前调用方是否要求尝试 GPU。
     *
     * 能力字段只用于诊断，不能在 shader 初始化之前把 GPU 请求静默改成 CPU；
     * 最终是否可执行必须由真实 program 编译、链接和 dispatch 结果决定。
     */
    internal fun gpuSimulationRequested(): Boolean = !forceCpuSimulation
}
