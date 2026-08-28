package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleSelector
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL20.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL20.GL_LINK_STATUS
import org.lwjgl.opengl.GL20.GL_TRUE
import org.lwjgl.opengl.GL20.glAttachShader
import org.lwjgl.opengl.GL20.glCompileShader
import org.lwjgl.opengl.GL20.glCreateProgram
import org.lwjgl.opengl.GL20.glCreateShader
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDeleteShader
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.opengl.GL20.glGetShaderInfoLog
import org.lwjgl.opengl.GL20.glGetShaderi
import org.lwjgl.opengl.GL20.glLinkProgram
import org.lwjgl.opengl.GL20.glShaderSource
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform3f
import org.lwjgl.opengl.GL20.glUniform4fv
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL15.glGetBufferSubData
import org.lwjgl.opengl.GL30.glBindBufferBase
import org.lwjgl.opengl.GL30.glGetIntegeri
import org.lwjgl.opengl.GL33.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL43.GL_BUFFER_UPDATE_BARRIER_BIT
import org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER_BINDING
import org.lwjgl.opengl.GL43.glDispatchCompute
import org.lwjgl.opengl.GL43.glMemoryBarrier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 在隐藏的 OpenGL 4.3 core context 中编译并执行统一的 CParticle Force Command shader。
 *
 * 该测试专门拦截 shader 语法或链接错误，避免运行时静默禁用 compute 后把全部粒子交给 CPU。
 */
class CParticleComputeShaderCompileTest {
    @Test
    fun `cparticle compute shader compiles and links on opengl 43`() {
        withOpenGl43Context {
            val commonResourcesPrefix = "common/src/main/resources/"
            val computeShaderPaths = listOf(
                "${commonResourcesPrefix}assets/cooparticlesapi/shaders/core/compute/cparticle_sim.comp",
                "${commonResourcesPrefix}assets/cooparticlesapi/shaders/core/compute/cparticle_sim_legacy.comp",
            )
            computeShaderPaths.forEach { shaderPath ->
                val resourcePath = shaderPath.removePrefix(commonResourcesPrefix)
                val resource = assertNotNull(
                    javaClass.classLoader.getResource(resourcePath),
                    "$resourcePath is missing from the test runtime resources",
                )
                var shader = 0
                var program = 0
                try {
                    val source = resource.openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
                    shader = glCreateShader(GL_COMPUTE_SHADER)
                    glShaderSource(shader, source)
                    glCompileShader(shader)
                    assertTrue(
                        glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE,
                        "$shaderPath: ${glGetShaderInfoLog(shader)}",
                    )

                    program = glCreateProgram()
                    glAttachShader(program, shader)
                    glLinkProgram(program)
                    assertTrue(
                        glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE,
                        "$shaderPath: ${glGetProgramInfoLog(program)}",
                    )
                    if (shaderPath.endsWith("cparticle_sim.comp")) {
                        assertCommandSelectorDispatch(program)
                    }
                } finally {
                    if (program != 0) glDeleteProgram(program)
                    if (shader != 0) glDeleteShader(shader)
                }
            }
        }
    }

    @Test
    fun `legacy forces dispatch from the first live slot through the managed compute wrapper`() {
        withOpenGl43Context {
            val computeSupportedField = CParticleCapabilities::class.java
                .getDeclaredField("computeSupported")
                .apply { isAccessible = true }
            val previousComputeSupported = computeSupportedField.getBoolean(CParticleCapabilities)
            val previousForceCpuSimulation = CParticleCapabilities.forceCpuSimulation
            val system = CParticleSystem(
                "legacy-runtime-dispatch-test",
                3,
                CParticleRenderLayer.TRANSLUCENT,
                CParticleSystemMode.SIMULATED,
            )
            try {
                // 复现旧问题：诊断字段为 false 时，默认路径仍必须真实编译并 dispatch，不能改走 CPU。
                computeSupportedField.setBoolean(CParticleCapabilities, false)
                CParticleCapabilities.forceCpuSimulation = false
                CParticleGpuSimulator.release()

                val particle = CParticle().apply {
                    updateMode = CParticleUpdateMode.STATIC
                    pos = Vec3.ZERO
                    velocity = Vec3.ZERO
                    maxAge = 10
                    light = 15
                    speedLimit = 100F
                }
                repeat(3) { expectedSlot ->
                    assertEquals(
                        expectedSlot,
                        system.store.spawnWithMask(
                            p = particle,
                            origin = Vec3.ZERO,
                            animationId = 0,
                            blockLight = 15,
                            skyLight = 15,
                        ),
                    )
                }
                system.store.kill(0)
                assertEquals(1, system.store.firstAliveSlot)
                assertEquals(2, system.store.activeSlotCount)
                assertFalse(system.store.hasDynamicStorage)
                system.forceSink.submit(CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)))

                // 第一个 tick 清除 GPU newborn 标志，第二个 tick 验证 managed wrapper 持续 dispatch。
                system.tick()
                system.tick()
                assertFalse(system.store.hasDynamicStorage)
                assertFalse(system.metadataGlBuffer.initialized)

                val result = FloatArray(CParticleStore.STRIDE * 3)
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                assertEquals(0F, result[1], 1.0e-6F)
                assertEquals(-1F, result[CParticleStore.STRIDE + 1], 1.0e-6F)
                assertEquals(1F, result[CParticleStore.STRIDE + CParticleStore.OFF_AGE], 1.0e-6F)
                assertEquals(-1F, result[CParticleStore.STRIDE + CParticleStore.OFF_VEL + 1], 1.0e-6F)
                assertEquals(-1F, result[CParticleStore.STRIDE * 2 + 1], 1.0e-6F)
                assertEquals(-1F, result[CParticleStore.STRIDE * 2 + CParticleStore.OFF_VEL + 1], 1.0e-6F)
            } finally {
                system.release()
                CParticleGpuSimulator.release()
                CParticleCapabilities.forceCpuSimulation = previousForceCpuSimulation
                computeSupportedField.setBoolean(CParticleCapabilities, previousComputeSupported)
            }
        }
    }

    @Test
    fun `growing an initialized system preserves particle and metadata buffers`() {
        withOpenGl43Context {
            val computeSupportedField = CParticleCapabilities::class.java
                .getDeclaredField("computeSupported")
                .apply { isAccessible = true }
            val previousComputeSupported = computeSupportedField.getBoolean(CParticleCapabilities)
            val previousForceCpuSimulation = CParticleCapabilities.forceCpuSimulation
            val system = CParticleSystem(
                "gpu-growth-test",
                1,
                CParticleRenderLayer.TRANSLUCENT,
                CParticleSystemMode.SIMULATED,
            )
            try {
                // Command 路径也不能把能力诊断字段当成 CPU fallback 开关。
                computeSupportedField.setBoolean(CParticleCapabilities, false)
                CParticleCapabilities.forceCpuSimulation = false
                CParticleGpuSimulator.release()

                val first = CParticle().apply {
                    updateMode = CParticleUpdateMode.STATIC
                    pos = Vec3.ZERO
                    velocity = Vec3.ZERO
                    maxAge = 10
                    light = 15
                    speedLimit = 100F
                    sign = 5
                }
                assertEquals(0, system.store.spawn(first, Vec3.ZERO, 0, 15, 15))
                system.forceSink.submit(
                    CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)),
                    CParticleSelector.SignEquals(5),
                )
                system.tick()
                system.tick()
                assertTrue(system.commandGlBuffer.initialized)
                assertTrue(system.metadataGlBuffer.initialized)

                system.growTo(2)
                assertEquals(2, system.capacity)
                assertEquals(5, system.store.metadata.sign(0))

                val second = CParticle().apply {
                    updateMode = CParticleUpdateMode.STATIC
                    pos = Vec3.ZERO
                    velocity = Vec3.ZERO
                    maxAge = 10
                    light = 15
                    speedLimit = 100F
                    sign = 6
                }
                assertEquals(1, system.store.spawn(second, Vec3.ZERO, 0, 15, 15))
                system.tick()

                val result = FloatArray(CParticleStore.STRIDE * 2)
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                assertEquals(-3F, result[1], 1.0e-6F)
                assertEquals(2F, result[3], 1.0e-6F)
                assertTrue(result[CParticleStore.STRIDE + CParticleStore.OFF_FLAGS].toInt() and 1 != 0)
            } finally {
                system.release()
                CParticleGpuSimulator.release()
                CParticleCapabilities.forceCpuSimulation = previousForceCpuSimulation
                computeSupportedField.setBoolean(CParticleCapabilities, previousComputeSupported)
            }
        }
    }

    @Test
    fun `multiple standalone system dispatches restore gl state`() {
        withOpenGl43Context {
            val computeSupportedField = CParticleCapabilities::class.java
                .getDeclaredField("computeSupported")
                .apply { isAccessible = true }
            val previousComputeSupported = computeSupportedField.getBoolean(CParticleCapabilities)
            val previousForceCpuSimulation = CParticleCapabilities.forceCpuSimulation
            val systems = listOf(
                CParticleSystem("batch-dispatch-first", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED),
                CParticleSystem("batch-dispatch-second", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED),
            )
            val preservedBaseBuffers = IntArray(4)
            var preservedGenericBuffer = 0
            try {
                computeSupportedField.setBoolean(CParticleCapabilities, true)
                CParticleCapabilities.forceCpuSimulation = false
                CParticleGpuSimulator.release()

                systems.forEachIndexed { index, system ->
                    val particle = CParticle().apply {
                        updateMode = CParticleUpdateMode.STATIC
                        pos = Vec3.ZERO
                        velocity = Vec3.ZERO
                        maxAge = 10
                        light = 15
                        speedLimit = 100F
                        sign = if (index == 0) 0 else 5
                    }
                    assertEquals(0, system.store.spawn(particle, Vec3.ZERO, 0, 15, 15))
                    system.forceSink.submit(
                        CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)),
                        if (index == 0) CParticleSelector.All else CParticleSelector.SignEquals(5),
                    )
                }

                for (binding in preservedBaseBuffers.indices) {
                    preservedBaseBuffers[binding] = glGenBuffers()
                    glBindBuffer(GL_SHADER_STORAGE_BUFFER, preservedBaseBuffers[binding])
                    glBufferData(GL_SHADER_STORAGE_BUFFER, intArrayOf(binding), GL_DYNAMIC_DRAW)
                    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, preservedBaseBuffers[binding])
                }
                preservedGenericBuffer = glGenBuffers()
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, preservedGenericBuffer)
                glBufferData(GL_SHADER_STORAGE_BUFFER, intArrayOf(99), GL_DYNAMIC_DRAW)

                repeat(2) { systems.forEach(CParticleSystem::tick) }

                assertEquals(0, glGetInteger(GL_CURRENT_PROGRAM))
                assertEquals(preservedGenericBuffer, glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING))
                for (binding in preservedBaseBuffers.indices) {
                    assertEquals(
                        preservedBaseBuffers[binding],
                        glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, binding),
                    )
                }
                systems.forEach { system ->
                    val result = FloatArray(36)
                    glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                    glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                    assertEquals(-1F, result[1], 1.0e-6F)
                    assertEquals(-1F, result[9], 1.0e-6F)
                }
            } finally {
                glUseProgram(0)
                for (binding in preservedBaseBuffers.indices) {
                    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, 0)
                }
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)
                if (preservedGenericBuffer != 0) glDeleteBuffers(preservedGenericBuffer)
                preservedBaseBuffers.forEach { buffer ->
                    if (buffer != 0) glDeleteBuffers(buffer)
                }
                systems.forEach(CParticleSystem::release)
                CParticleGpuSimulator.release()
                CParticleCapabilities.forceCpuSimulation = previousForceCpuSimulation
                computeSupportedField.setBoolean(CParticleCapabilities, previousComputeSupported)
            }
        }
    }

    /** selector 暂时关闭期间出生的粒子，在重新启用 selector 后必须获得完整 metadata。 */
    @Test
    fun `metadata is resynchronized after commands temporarily stop using selectors`() {
        withOpenGl43Context {
            val computeSupportedField = CParticleCapabilities::class.java
                .getDeclaredField("computeSupported")
                .apply { isAccessible = true }
            val previousComputeSupported = computeSupportedField.getBoolean(CParticleCapabilities)
            val previousForceCpuSimulation = CParticleCapabilities.forceCpuSimulation
            val system = CParticleSystem(
                "metadata-resync-test",
                2,
                CParticleRenderLayer.TRANSLUCENT,
                CParticleSystemMode.SIMULATED,
            )
            try {
                computeSupportedField.setBoolean(CParticleCapabilities, true)
                CParticleCapabilities.forceCpuSimulation = false
                CParticleGpuSimulator.release()

                val first = CParticle().apply {
                    updateMode = CParticleUpdateMode.STATIC
                    pos = Vec3.ZERO
                    velocity = Vec3.ZERO
                    maxAge = 20
                    light = 15
                    speedLimit = 100F
                    sign = 1
                }
                assertEquals(0, system.store.spawn(first, Vec3.ZERO, 0, 15, 15))
                system.forceSink.submit(
                    CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)),
                    CParticleSelector.SignEquals(1),
                )
                repeat(2) { system.tick() }
                assertTrue(system.metadataGlBuffer.initialized)

                system.forceSink.clear()
                system.forceSink.submit(CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)))
                val second = CParticle().apply {
                    updateMode = CParticleUpdateMode.STATIC
                    pos = Vec3.ZERO
                    velocity = Vec3.ZERO
                    maxAge = 20
                    light = 15
                    speedLimit = 100F
                    sign = 2
                }
                assertEquals(1, system.store.spawn(second, Vec3.ZERO, 0, 15, 15))
                system.tick()

                system.forceSink.clear()
                system.forceSink.submit(
                    CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)),
                    CParticleSelector.SignEquals(2),
                )
                system.tick()

                val result = FloatArray(72)
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                assertEquals(-1F, result[37], 1.0e-6F)
                assertEquals(-1F, result[45], 1.0e-6F)
            } finally {
                system.release()
                CParticleGpuSimulator.release()
                CParticleCapabilities.forceCpuSimulation = previousForceCpuSimulation
                computeSupportedField.setBoolean(CParticleCapabilities, previousComputeSupported)
            }
        }
    }

    private fun assertCommandSelectorDispatch(program: Int) {
        val particles = FloatArray(108)
        for (slot in 0..2) {
            val base = slot * 36
            particles[base + 7] = 10F
            particles[base + 11] = 1F
            particles[base + 33] = -1F
        }
        val metadata = FloatArray(24)
        metadata[0] = Float.fromBits(100)
        metadata[1] = Float.fromBits(4)
        metadata[8] = Float.fromBits(101)
        metadata[9] = Float.fromBits(5)
        metadata[16] = Float.fromBits(102)
        metadata[17] = Float.fromBits(6)
        metadata[4] = Float.NaN
        metadata[12] = Float.NaN
        metadata[20] = Float.NaN
        val commands = FloatArray(ForceCommand.STRIDE)
        ForceCommand(
            CParticleForce.Gravity(Vec3(0.0, -1.0, 0.0)),
            CParticleSelector.SignEquals(5),
        ).pack(commands, 0, Vec3.ZERO)
        var particleBuffer = 0
        var collisionBuffer = 0
        var metadataBuffer = 0
        var commandBuffer = 0
        try {
            particleBuffer = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, particleBuffer)
            glBufferData(GL_SHADER_STORAGE_BUFFER, particles, GL_DYNAMIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, particleBuffer)

            collisionBuffer = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, collisionBuffer)
            glBufferData(GL_SHADER_STORAGE_BUFFER, intArrayOf(0), GL_DYNAMIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, collisionBuffer)

            metadataBuffer = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, metadataBuffer)
            glBufferData(GL_SHADER_STORAGE_BUFFER, metadata, GL_DYNAMIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, metadataBuffer)

            commandBuffer = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, commandBuffer)
            glBufferData(GL_SHADER_STORAGE_BUFFER, commands, GL_DYNAMIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, commandBuffer)

            glUseProgram(program)
            setInt(program, "uFirstSlot", 1)
            setInt(program, "uCount", 2)
            setInt(program, "uCommandCount", 1)
            setInt(program, "uMetadataEnabled", 1)
            setFloat(program, "uSpeedLimit", 100F)
            setFloat3(program, "uOrigin", 0F, 0F, 0F)
            setInt(program, "uTransformSimulation", 0)
            setInt(program, "uCollisionEnabled", 0)
            setInt(program, "uCollisionSize", 1)
            setFloat3(program, "uCollisionOffset", 0F, 0F, 0F)

            glDispatchCompute(1, 1, 1)
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT or GL_BUFFER_UPDATE_BARRIER_BIT)
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, particleBuffer)
            glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, particles)

            assertEquals(0F, particles[1], 1.0e-6F)
            assertEquals(0F, particles[9], 1.0e-6F)
            assertEquals(-1F, particles[37], 1.0e-6F)
            assertEquals(-1F, particles[45], 1.0e-6F)
            assertEquals(0F, particles[73], 1.0e-6F)
            assertEquals(0F, particles[81], 1.0e-6F)
        } finally {
            glUseProgram(0)
            for (binding in 0..3) glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, 0)
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)
            if (commandBuffer != 0) glDeleteBuffers(commandBuffer)
            if (metadataBuffer != 0) glDeleteBuffers(metadataBuffer)
            if (collisionBuffer != 0) glDeleteBuffers(collisionBuffer)
            if (particleBuffer != 0) glDeleteBuffers(particleBuffer)
        }
    }

    private fun setInt(program: Int, name: String, value: Int) {
        val location = glGetUniformLocation(program, name)
        assertTrue(location >= 0, "CParticle shader is missing $name")
        glUniform1i(location, value)
    }

    private fun setFloat(program: Int, name: String, value: Float) {
        val location = glGetUniformLocation(program, name)
        assertTrue(location >= 0, "CParticle shader is missing $name")
        glUniform1f(location, value)
    }

    private fun setFloat3(program: Int, name: String, x: Float, y: Float, z: Float) {
        val location = glGetUniformLocation(program, name)
        assertTrue(location >= 0, "CParticle shader is missing $name")
        glUniform3f(location, x, y, z)
    }

    /** 在隐藏的 OpenGL 4.3 core context 中执行测试内容，并保证释放窗口与 GLFW。 */
    private fun withOpenGl43Context(block: () -> Unit) {
        assertTrue(glfwInit(), "GLFW could not initialize")
        var window = 0L
        try {
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            window = glfwCreateWindow(1, 1, "cparticle-compute-test", 0L, 0L)
            assertTrue(window != 0L, "Could not create an OpenGL 4.3 context")
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            block()
        } finally {
            if (window != 0L) glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

}
