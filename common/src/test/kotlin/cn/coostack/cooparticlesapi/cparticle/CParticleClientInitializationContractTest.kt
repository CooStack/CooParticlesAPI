package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.render.CParticleRenderer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 校验 CParticle 的一次性程序准备不会落到首次粒子生成路径。
 *
 * 示例：客户端注册阶段创建托管 program，渲染初始化阶段再编译。
 * 禁止把需要 GL 上下文的调用直接放进 loader 的客户端注册回调。
 */
class CParticleClientInitializationContractTest {
    /**
     * 清理其他 shader 测试可能留下的统一注册表状态。
     *
     * 示例：每个测试都从零个图形和 compute program 开始。
     * 禁止依赖测试执行顺序。
     */
    @BeforeTest
    fun setUp() {
        CParticleRenderer.release()
        CParticleGpuSimulator.release()
        ShaderProgramRegistry.releaseAll()
    }

    /**
     * 释放本测试创建但未编译的 program 引用。
     *
     * 示例：后续 shader 测试可以重新注册自己的 fake program。
     * 禁止把全局注册表状态泄漏给其他测试。
     */
    @AfterTest
    fun tearDown() {
        CParticleRenderer.release()
        CParticleGpuSimulator.release()
        CParticleCapabilities.forceCpuSimulation = false
        ShaderProgramRegistry.releaseAll()
    }

    /**
     * 校验注册阶段只创建托管对象，不访问 OpenGL。
     *
     * 示例：没有 GL 上下文的单元测试仍能注册图形和 compute program。
     * 禁止注册方法直接编译 program。
     */
    @Test
    fun `program registration does not compile without a gl context`() {
        val graphics = CParticleRenderer.registerProgram()
        val compute = CParticleGpuSimulator.registerProgram()

        assertEquals(0, graphics.program)
        assertEquals(0, compute.program)
        assertEquals(1, ShaderProgramRegistry.graphicsCount())
        assertEquals(1, ShaderProgramRegistry.computeCount())
    }

    /**
     * 校验统一注册表完全释放后，单例缓存会重新进入注册表。
     *
     * 示例：调试释放后再次初始化仍能参加下一次 shader 重载。
     * 禁止缓存非空时直接返回一个未受注册表管理的 program。
     */
    @Test
    fun `cached programs register again after registry release all`() {
        val graphics = CParticleRenderer.registerProgram()
        val compute = CParticleGpuSimulator.registerProgram()

        ShaderProgramRegistry.releaseAll()

        assertEquals(graphics, CParticleRenderer.registerProgram())
        assertEquals(compute, CParticleGpuSimulator.registerProgram())
        assertEquals(1, ShaderProgramRegistry.graphicsCount())
        assertEquals(1, ShaderProgramRegistry.computeCount())
    }

    /**
     * 校验 CParticle 完全释放时不会在统一注册表留下旧 program。
     *
     * 示例：完全释放后图形和 compute program 数量都归零。
     * 禁止重新初始化后让旧、新对象一起参加全量编译。
     */
    @Test
    fun `component release unregisters cached programs`() {
        CParticleRenderer.registerProgram()
        CParticleGpuSimulator.registerProgram()

        CParticleRenderer.release()
        CParticleGpuSimulator.release()

        assertEquals(0, ShaderProgramRegistry.graphicsCount())
        assertEquals(0, ShaderProgramRegistry.computeCount())
    }

    /**
     * 校验强制 CPU 模拟会在统一重编译前注销已有 compute program。
     *
     * 示例：GPU 路径曾启用后切换到 CPU，再执行资源重载准备。
     * 禁止 [ShaderProgramRegistry.reinitializeAll] 绕过 CPU 模式重新编译 compute shader。
     */
    @Test
    fun `force cpu removes compute program before registry reinitialization`() {
        CParticleGpuSimulator.registerProgram()
        CParticleCapabilities.forceCpuSimulation = true

        CParticleGpuSimulator.initializeProgramIfSupported()

        assertEquals(0, ShaderProgramRegistry.computeCount())
    }

    /**
     * 校验 CParticle program 的注册、能力探测和编译顺序。
     *
     * 示例：首次世界渲染会在 [cn.coostack.cooparticlesapi.CooParticlesAPIClient.initShaderPrograms]
     * 中编译已注册 program。
     * 禁止等到首个 Emitter 或 Composition 出现后再创建 program。
     */
    @Test
    fun `client initialization prepares cparticle programs before first spawn`() {
        val client = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val simulator = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/simulate/CParticleGpuSimulator.kt"
        )

        val clientInit = client.substringAfter("fun init()")
            .substringBefore("fun checkIrisShaderPackUsed")
        val renderInit = client.substringAfter("fun initShaderPrograms()")
            .substringBefore("fun reloadShaderPrograms")
        val rendererRegistration = renderer.substringAfter("fun registerProgram()")
            .substringBefore("private fun ensureProgram")
        val simulatorRegistration = simulator.substringAfter("fun registerProgram()")
            .substringBefore("fun initializeProgramIfSupported")
        val simulatorInitialization = simulator.substringAfter("fun initializeProgramIfSupported()")
            .substringBefore("private fun ensureProgram")

        assertTrue("CParticleRenderer.registerProgram()" in clientInit)
        assertTrue("CParticleCapabilities.detect()" in renderInit)
        assertTrue("CParticleGpuSimulator.initializeProgramIfSupported()" in renderInit)
        assertTrue(
            renderInit.indexOf("CParticleCapabilities.detect()") <
                renderInit.indexOf("ShaderProgramRegistry.reinitializeAll()")
        )
        assertTrue(
            renderInit.indexOf("CParticleGpuSimulator.initializeProgramIfSupported()") <
                renderInit.indexOf("ShaderProgramRegistry.reinitializeAll()")
        )
        assertTrue(".init()" !in rendererRegistration)
        assertTrue(".init()" !in simulatorRegistration)
        assertTrue("if (!CParticleCapabilities.useGpuSimulation())" in simulatorInitialization)
        assertTrue("release()" in simulatorInitialization)
        assertTrue(
            simulatorInitialization.indexOf("release()") <
                simulatorInitialization.indexOf("val candidate = registerProgram()")
        )
    }

    /**
     * 读取仓库中的 UTF-8 源文件。
     *
     * 示例：传入 `common/src/main/kotlin/...` 相对路径。
     * 禁止传入工作区外的路径。
     *
     * @param relativePath 相对仓库根目录的文件路径
     * @return 文件完整内容
     */
    private fun readProjectFile(relativePath: String): String =
        Files.readString(findRepoRoot().resolve(relativePath))

    /**
     * 从测试工作目录向上定位仓库根目录。
     *
     * 示例：从根工程或 `common` 子工程运行测试都能找到 `settings.gradle`。
     * 禁止在不属于本仓库的目录树中调用。
     *
     * @return 最近的仓库根目录
     */
    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
