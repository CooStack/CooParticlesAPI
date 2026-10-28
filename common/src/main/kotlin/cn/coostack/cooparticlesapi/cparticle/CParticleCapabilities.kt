package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.ARBInstancedArrays
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL33

/**
 * GL 能力探测 (渲染线程惰性执行一次).
 *
 * - 26.3 OpenGL 后端保证 GL 3.3；实例化与属性 divisor 是渲染的硬性要求
 * - 当前 compute 源码声明 GLSL 4.30，因此 GPU 模拟要求 GL 4.3；较低版本使用同一 SoA 的 CPU 模拟
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
        if (!RenderSystem.isOnRenderThread()) return
        OpenGlRenderTargetBridge.requireOpenGlBackend()

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
        if (!computeSupported) {
            CooParticlesConstants.logger.info(
                "[cparticle] GLSL 4.30 compute unavailable; using CPU SoA simulation with instanced rendering and prepared respawns"
            )
        }
    }

    internal fun supportsInstancing(hasGl31: Boolean, hasGl33: Boolean, hasArbInstancedArrays: Boolean): Boolean {
        return hasGl31 && (hasGl33 || hasArbInstancedArrays)
    }

    /**
     * 判断当前上下文是否提供 compute shader 与 SSBO 所需的完整入口。
     *
     * 当前程序使用 GLSL 4.30，只有 ARB 扩展而没有对应 GLSL 版本时也不能编译。
     * 保留扩展参数以便诊断与旧调用方重编译，入口可用性仍由真实编译链接确认。
     */
    internal fun supportsCompute(hasGl43: Boolean, hasArbCompute: Boolean, hasArbSsbo: Boolean): Boolean {
        return hasGl43
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
     * 无 compute 能力时自动选择 CPU SoA，保留实例化渲染和出生预展开生命。
     * 已选择 GPU 后的真实编译或执行错误仍直接抛出，不隐藏资源错误。
     */
    @JvmStatic
    fun useGpuSimulation(): Boolean = !forceCpuSimulation && computeSupported

    /**
     * 返回当前调用方是否要求尝试 GPU。
     *
     * 与实际模拟路由一致；GL 3.3 下不得提前注册或编译 GLSL 4.30 程序。
     */
    internal fun gpuSimulationRequested(): Boolean = useGpuSimulation()
}
