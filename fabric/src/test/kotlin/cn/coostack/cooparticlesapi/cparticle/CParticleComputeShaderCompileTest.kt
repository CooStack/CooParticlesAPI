package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.CooShaderReloadSupport
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleSelector
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.render.CParticleGlBuffer
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleMetadataStore
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleRespawnRequest
import org.lwjgl.opengl.GL11.glFinish
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPoint
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathForwardAxis
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathBirth
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathCpuEvaluator
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathOffsetMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSegmentType
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathProgressMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPlayMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEndMode
import org.joml.Quaternionf
import org.joml.Vector3f
import net.minecraft.resources.ResourceLocation
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
import org.lwjgl.opengl.GL11.glGetString
import org.lwjgl.opengl.GL11.GL_RENDERER
import org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER
import org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER
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
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING
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
import kotlin.test.assertFailsWith

/**
 * 在隐藏的 OpenGL 4.3 core context 中编译并执行统一的 CParticle Force Command shader。
 *
 * 该测试专门拦截 shader 语法或链接错误，避免运行时静默禁用 compute 后把全部粒子交给 CPU。
 */
class CParticleComputeShaderCompileTest {
    /** CPU 后继只在 fence 就绪后接收一次最终状态；清理后不得送达旧事件。 */
    @Test
    fun `prepared cpu delivery is asynchronous once only and canceled by clear`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem("prepared-cpu-delivery", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            val baseline = CParticleSystemManager.totalAlive()
            try {
                system.setOriginIfEmpty(Vec3(10000.0, 20.0, -30000.0))
                val events = ArrayList<CParticleDeathState>()
                fun spawn(): Int {
                    val slot = system.store.spawn(CParticle().apply {
                        pos = system.origin; velocity = Vec3(1.0, 0.0, 0.0); maxAge = 2
                        updateMode = CParticleUpdateMode.STATIC
                    }, system.origin, 0, 15, 15, epochTick = system.tickCount + 1)
                    CParticleRespawnEngine.track(system, slot, false, true)
                    CParticleRespawnEngine.onCpuChildren(system, slot) { events.add(it) }
                    return slot
                }
                spawn()
                repeat(3) { system.tick(); CParticleRespawnEngine.finishTick() }
                assertTrue(events.isEmpty())
                glFinish()
                CParticleRespawnEngine.poll()
                assertEquals(1, events.size)
                assertEquals(Vec3(10002.0, 20.0, -30000.0), events.single().position)
                assertEquals(Vec3(1.0, 0.0, 0.0), events.single().velocity)
                assertEquals(2, events.single().age)
                assertEquals(RemoveReason.LIFECYCLE, events.single().reason)
                assertEquals(baseline, CParticleSystemManager.totalAlive())
                assertFalse(CParticleRespawnEngine.owns(system))
                CParticleCapabilities.forceCpuSimulation = true
                system.tick()
                CParticleCapabilities.forceCpuSimulation = false
                spawn()
                repeat(3) { system.tick(); CParticleRespawnEngine.finishTick() }
                system.clearParticles()
                glFinish(); CParticleRespawnEngine.poll()
                assertEquals(1, events.size)
                assertEquals(baseline, CParticleSystemManager.totalAlive())
            } finally { system.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 清空源存储会取消跨池预留的整条后继链并归还全局额度。 */
    @Test
    fun `clearing source store cancels cross system reservations`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val source = CParticleSystem("prepared-clear-source", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            val target = CParticleSystem("prepared-clear-target", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            val baseline = CParticleSystemManager.totalAlive()
            try {
                val root = source.store.spawn(CParticle().apply { maxAge = 100 }, Vec3.ZERO, 0, 15, 15, epochTick = 1)
                val children = IntArray(2) { target.store.spawn(CParticle().apply { maxAge = 100 }, Vec3.ZERO, 0, 15, 15, epochTick = 1) }
                CParticleRespawnEngine.track(source, root, false, true)
                children.forEach { CParticleRespawnEngine.track(target, it, true, true) }
                val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true)
                CParticleRespawnEngine.link(source, root, target, children[0], request)
                CParticleRespawnEngine.link(target, children[0], target, children[1], request)
                source.tick(); target.tick(); CParticleRespawnEngine.finishTick()
                source.store.clear()
                assertFalse(CParticleRespawnEngine.owns(source))
                repeat(3) { target.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                assertEquals(0, target.store.aliveCount)
                assertEquals(baseline, CParticleSystemManager.totalAlive())
                assertFalse(CParticleRespawnEngine.owns(target))
            } finally { source.release(); target.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 预留树扩容时保留 GPU 已激活状态，旧尺寸 staging 的 fence 仍可回收。 */
    @Test
    fun `prepared growth preserves activated children and pending retirement`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem("prepared-growth", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true)
                fun spawn(waiting: Boolean): Int {
                    val slot = system.store.spawn(CParticle().apply {
                        maxAge = 2; velocity = Vec3(1.0, 0.0, 0.0); updateMode = CParticleUpdateMode.STATIC
                    }, Vec3.ZERO, 0, 15, 15, epochTick = system.tickCount + 1)
                    CParticleRespawnEngine.track(system, slot, waiting, true)
                    return slot
                }
                val root = spawn(false)
                val child = spawn(true)
                CParticleRespawnEngine.link(system, root, system, child, request)
                repeat(3) { system.tick(); CParticleRespawnEngine.finishTick() }
                assertEquals(2F, readGpu(system, child)[0])
                system.growTo(66)
                val nextRoot = spawn(false)
                val nextChild = spawn(true)
                CParticleRespawnEngine.link(system, nextRoot, system, nextChild, request)
                // ring 已满时继续捕获，下一次 staging 可用后必须补齐回收。
                repeat(3) { system.tick(); CParticleRespawnEngine.finishTick() }
                assertEquals(2F, readGpu(system, nextChild)[0])
                glFinish(); CParticleRespawnEngine.poll()
                repeat(4) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                assertEquals(0, system.store.aliveCount)
                assertFalse(CParticleRespawnEngine.owns(system))
            } finally { system.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 非法浮点转换在提交记录前失败，回滚后不能留下会激活其他槽位的连接。 */
    @Test
    fun `invalid prepared link rolls back without corrupting another chain`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem("prepared-rollback", 3, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                val slots = IntArray(3) { system.store.spawn(CParticle().apply { maxAge = 1 }, Vec3.ZERO, 0, 15, 15, epochTick = 1) }
                slots.forEachIndexed { index, slot -> CParticleRespawnEngine.track(system, slot, index > 0, true) }
                val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true)
                CParticleRespawnEngine.link(system, slots[0], system, slots[1], request)
                assertFailsWith<IllegalArgumentException> {
                    CParticleRespawnEngine.link(system, slots[0], system, slots[2], request.copy(position = Vec3(1.0e100, 0.0, 0.0)))
                }
                CParticleRespawnEngine.rollback(system, slots[2])
                assertEquals(2, system.store.aliveCount)
                repeat(2) { system.tick(); CParticleRespawnEngine.finishTick() }
                assertEquals(1, readGpu(system, slots[1])[CParticleStore.OFF_FLAGS].toInt() and 1)
                assertEquals(0, readGpu(system, slots[2])[CParticleStore.OFF_FLAGS].toInt() and 1)
                repeat(2) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                assertEquals(0, system.store.aliveCount)
            } finally { system.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 重生两个 compute pass 和异步回读均恢复调用者的 SSBO 与 COPY 绑定。 */
    @Test
    fun `prepared passes restore indexed generic and copy buffer bindings`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem("prepared-bindings", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            val sentinels = IntArray(7) { glGenBuffers() }
            try {
                val slots = IntArray(2) { system.store.spawn(CParticle().apply { maxAge = 1 }, Vec3.ZERO, 0, 15, 15, epochTick = 1) }
                slots.forEachIndexed { index, slot -> CParticleRespawnEngine.track(system, slot, index > 0, true) }
                CParticleRespawnEngine.link(system, slots[0], system, slots[1], ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true))
                CParticleRespawnEngine.onCpuChildren(system, slots[0]) {}
                fun assertBindings() {
                    repeat(4) { assertEquals(sentinels[it], glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, it)) }
                    assertEquals(sentinels[4], glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING))
                    assertEquals(sentinels[5], glGetInteger(GL_COPY_READ_BUFFER))
                    assertEquals(sentinels[6], glGetInteger(GL_COPY_WRITE_BUFFER))
                    assertEquals(0, glGetInteger(GL_CURRENT_PROGRAM))
                }
                repeat(3) {
                    system.tick()
                    repeat(4) { index -> glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, sentinels[index]) }
                    glBindBuffer(GL_SHADER_STORAGE_BUFFER, sentinels[4])
                    glBindBuffer(GL_COPY_READ_BUFFER, sentinels[5])
                    glBindBuffer(GL_COPY_WRITE_BUFFER, sentinels[6])
                    CParticleRespawnEngine.finishTick()
                    assertBindings()
                    glFinish(); CParticleRespawnEngine.poll()
                    assertBindings()
                }
            } finally {
                repeat(4) { glBindBufferBase(GL_SHADER_STORAGE_BUFFER, it, 0) }
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)
                glBindBuffer(GL_COPY_READ_BUFFER, 0); glBindBuffer(GL_COPY_WRITE_BUFFER, 0)
                sentinels.forEach { glDeleteBuffers(it) }
                system.release(); CParticleCapabilities.forceCpuSimulation = previous
            }
        }
    }

    /** 少量逐槽补写和大量映射补写都只能改变 flags，不能污染交错运动字段。 */
    @Test
    fun `flag patches preserve every other interleaved particle field`() {
        withOpenGl43Context {
            for (count in listOf(3, 100)) {
                val buffer = CParticleGlBuffer(count)
                val incoming = glGenBuffers()
                try {
                    buffer.init()
                    val original = FloatArray(count * CParticleStore.STRIDE) { it.toFloat() }
                    buffer.uploadRange(original, 0, count - 1)
                    val modified = original.copyOf()
                    repeat(count) { modified[it * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS] = 0F }
                    glBindBuffer(GL_ARRAY_BUFFER, incoming)
                    buffer.patchFlags(modified, IntArray(count) { count - 1 - it }, count)
                    assertEquals(incoming, glGetInteger(GL_ARRAY_BUFFER_BINDING))
                    glBindBuffer(GL_ARRAY_BUFFER, buffer.vbo)
                    val actual = FloatArray(original.size)
                    glGetBufferSubData(GL_ARRAY_BUFFER, 0L, actual)
                    assertTrue(modified.contentEquals(actual), "Only strided flags may change for count=$count")
                } finally { glBindBuffer(GL_ARRAY_BUFFER, 0); glDeleteBuffers(incoming); buffer.release() }
            }
        }
    }

    /** 两条模拟路线都在 GPU 直接激活后继，年龄、最后位置与速度继承不依赖回读。 */
    @Test
    fun `prepared children activate on gpu before cpu retirement across systems`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            try {
                for (command in listOf(false, true)) {
                    val source = CParticleSystem("prepared-source", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
                    val target = CParticleSystem("prepared-target", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
                    try {
                        source.setOriginIfEmpty(Vec3(10000.0, 20.0, -30000.0))
                        target.setOriginIfEmpty(Vec3(10010.0, 20.0, -30000.0))
                        val root = source.store.spawn(CParticle().apply {
                            pos = source.origin; velocity = Vec3(1.0, 0.0, 0.0); maxAge = 2; sign = 7
                            updateMode = CParticleUpdateMode.STATIC
                        }, source.origin, 0, 15, 15, epochTick = 1)
                        val child = target.store.spawn(CParticle().apply {
                            pos = target.origin; velocity = Vec3(0.0, 0.5, 0.0); maxAge = 2; sign = 9
                            color = Vector3f(0.2F, 0.3F, 0.4F); updateMode = CParticleUpdateMode.STATIC
                        }, target.origin, 0, 15, 15, epochTick = 1)
                        CParticleRespawnEngine.track(source, root, false, true)
                        CParticleRespawnEngine.track(target, child, true, true)
                        CParticleRespawnEngine.link(source, root, target, child, ParticleRespawnRequest(
                            ControlableCParticleData(), Vec3(0.0, 3.0, 0.0), relativeToDeath = true, inheritVelocity = 0.5,
                        ))
                        if (command) {
                            source.forceSink.submit(CParticleForce.Gravity(Vec3.ZERO), CParticleSelector.SignEquals(7))
                            target.forceSink.submit(CParticleForce.Gravity(Vec3.ZERO), CParticleSelector.SignEquals(9))
                        }
                        repeat(2) {
                            target.tick(); source.tick(); CParticleRespawnEngine.finishTick()
                            assertEquals(0, readGpu(target, child)[CParticleStore.OFF_FLAGS].toInt() and 1)
                        }
                        source.tick(); target.tick(); CParticleRespawnEngine.finishTick()
                        val result = readGpu(target, child)
                        assertEquals(1, result[CParticleStore.OFF_FLAGS].toInt() and 1)
                        assertEquals(-8F, result[0]); assertEquals(3F, result[1])
                        assertEquals(result[0], result[CParticleStore.OFF_PREV])
                        assertEquals(0F, result[CParticleStore.OFF_AGE])
                        assertEquals(0.5F, result[CParticleStore.OFF_VEL])
                        assertEquals(0.5F, result[CParticleStore.OFF_VEL + 1])
                        assertEquals(0.2F, result[CParticleStore.OFF_COLOR])
                        assertEquals(1, source.store.aliveCount)
                        val metadata = FloatArray(CParticleMetadataStore.STRIDE)
                        glBindBuffer(GL_SHADER_STORAGE_BUFFER, target.metadataGlBuffer.buffer)
                        glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, child.toLong() * CParticleMetadataStore.BYTE_STRIDE, metadata)
                        assertEquals(-8F, metadata[CParticleMetadataStore.BIRTH_POSITION])
                        glFinish(); CParticleRespawnEngine.poll()
                        assertEquals(0, source.store.aliveCount)
                        repeat(2) { target.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                        assertEquals(0, target.store.aliveCount)
                    } finally { source.release(); target.release() }
                }
            } finally { CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 同一池中一对多和多代激活不能受槽位顺序影响，也不能在一次 tick 连锁模拟。 */
    @Test
    fun `prepared same pool branching generations survive slot reuse`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem("prepared-branch", 8, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                val slots = IntArray(4) { system.store.spawn(CParticle().apply {
                    maxAge = 1; velocity = Vec3(1.0, 0.0, 0.0); updateMode = CParticleUpdateMode.STATIC
                }, Vec3.ZERO, 0, 15, 15, epochTick = 1) }
                slots.forEachIndexed { index, slot -> CParticleRespawnEngine.track(system, slot, index > 0, true) }
                val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, relativeToDeath = true)
                CParticleRespawnEngine.link(system, slots[0], system, slots[1], request)
                CParticleRespawnEngine.link(system, slots[0], system, slots[2], request)
                CParticleRespawnEngine.link(system, slots[1], system, slots[3], request)
                repeat(2) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                assertEquals(1, readGpu(system, slots[1])[CParticleStore.OFF_FLAGS].toInt() and 1)
                assertEquals(1, readGpu(system, slots[2])[CParticleStore.OFF_FLAGS].toInt() and 1)
                assertEquals(0, readGpu(system, slots[3])[CParticleStore.OFF_FLAGS].toInt() and 1)
                val reused = system.store.spawn(CParticle().apply { maxAge = 50; pos = Vec3(90.0, 0.0, 0.0) },
                    Vec3.ZERO, 0, 15, 15, epochTick = system.tickCount + 1)
                assertEquals(slots[0], reused)
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll()
                assertEquals(2F, readGpu(system, slots[3])[0])
                assertEquals(0F, readGpu(system, slots[3])[CParticleStore.OFF_AGE])
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll()
                assertEquals(1, system.store.aliveCount)
                assertEquals(90F, readGpu(system, reused)[0])
            } finally { system.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    private fun readGpu(system: CParticleSystem, slot: Int): FloatArray {
        val previous = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        return try {
            glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
            FloatArray(CParticleStore.STRIDE).also {
                glGetBufferSubData(GL_ARRAY_BUFFER, slot.toLong() * CParticleStore.BYTE_STRIDE, it)
            }
        } finally { glBindBuffer(GL_ARRAY_BUFFER, previous) }
    }

    /** CALL 可以配置是否激活后继，QUEUE 会取消整条树，均不能重复触发。 */
    @Test
    fun `prepared manual removal and cleanup honor generation cancellation`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            try {
                for (includeManual in listOf(false, true)) for (reason in listOf(RemoveReason.CALL, RemoveReason.QUEUE)) {
                    val system = CParticleSystem("prepared-manual", 3, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
                    try {
                        val slots = IntArray(3) { system.store.spawn(CParticle().apply {
                            maxAge = 100; velocity = Vec3(1.0, 0.0, 0.0); updateMode = CParticleUpdateMode.STATIC
                        }, Vec3.ZERO, 0, 15, 15, epochTick = 1) }
                        slots.forEachIndexed { index, slot -> CParticleRespawnEngine.track(system, slot, index > 0, includeManual) }
                        val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true)
                        CParticleRespawnEngine.link(system, slots[0], system, slots[1], request)
                        CParticleRespawnEngine.link(system, slots[1], system, slots[2], request)
                        repeat(2) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                        system.store.kill(slots[0], reason = reason)
                        repeat(3) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                        assertEquals(if (includeManual && reason == RemoveReason.CALL) 2 else 0, system.store.aliveCount)
                    } finally { system.release() }
                }
            } finally { CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 已取消的预留槽位复用后，原父粒子的结束不能激活或覆盖新生命。 */
    @Test
    fun `cancelled child reuse cannot retain incoming gpu link`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem("prepared-cancel-reuse", 3, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                fun spawn() = system.store.spawn(CParticle().apply { maxAge = 100; updateMode = CParticleUpdateMode.STATIC },
                    Vec3.ZERO, 0, 15, 15, epochTick = system.tickCount + 1)
                val parent = spawn(); val child = spawn()
                CParticleRespawnEngine.track(system, parent, false, true)
                CParticleRespawnEngine.track(system, child, true, true)
                val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true)
                CParticleRespawnEngine.link(system, parent, system, child, request)
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll()
                system.store.kill(child, reason = RemoveReason.QUEUE)
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll()
                val reused = spawn(); val newParent = spawn()
                assertEquals(child, reused)
                CParticleRespawnEngine.track(system, reused, true, true)
                CParticleRespawnEngine.track(system, newParent, false, true)
                CParticleRespawnEngine.link(system, newParent, system, reused, request)
                system.store.kill(parent, reason = RemoveReason.CALL)
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll()
                assertEquals(0, readGpu(system, reused)[CParticleStore.OFF_FLAGS].toInt() and 1)
                system.store.kill(newParent, reason = RemoveReason.CALL)
                system.tick(); CParticleRespawnEngine.finishTick()
                assertEquals(1, readGpu(system, reused)[CParticleStore.OFF_FLAGS].toInt() and 1)
            } finally { system.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** 路径提前结束触发 GPU 重生，不进入同步路径结束回读。 */
    @Test
    fun `prepared path ending activates child at final path position`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val path = CParticlePathLibrary.create(CParticlePathPoint.polyline(listOf(Vec3.ZERO, Vec3(4.0, 0.0, 0.0))))
            val system = CParticleSystem("prepared-path-end", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                val slots = IntArray(2) { index -> system.store.spawn(CParticle().apply {
                    maxAge = 100; sign = index; updateMode = CParticleUpdateMode.STATIC
                }, Vec3.ZERO, 0, 15, 15, epochTick = 1) }
                CParticleRespawnEngine.track(system, slots[0], false, true)
                CParticleRespawnEngine.track(system, slots[1], true, true)
                CParticleRespawnEngine.link(system, slots[0], system, slots[1],
                    ParticleRespawnRequest(ControlableCParticleData(), Vec3(0.0, 2.0, 0.0), true))
                system.forceSink.submit(CParticleForce.Path(path, endMode = CParticlePathEndMode.DISAPPEAR, playPeriodTicks = 2.0),
                    CParticleSelector.SignEquals(0))
                repeat(5) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                val result = readGpu(system, slots[1])
                assertEquals(1, result[CParticleStore.OFF_FLAGS].toInt() and 1)
                assertEquals(4F, result[0], 0.001F); assertEquals(2F, result[1], 0.001F)
                assertEquals(0, system.pathEndBuffer.lastEndedCount)
                assertEquals(1, system.store.aliveCount)
            } finally { system.release(); CParticlePathLibrary.release(path); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }

    /** HOLD 到达终点后保持父粒子存活，寿命结束时才激活后继并送达死亡快照。 */
    @Test
    fun `prepared path hold waits for lifetime before respawning`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val path = CParticlePathLibrary.create(
                CParticlePathPoint.polyline(listOf(Vec3.ZERO, Vec3(4.0, 0.0, 0.0))),
            )
            val system = CParticleSystem(
                "prepared-path-hold", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
            )
            try {
                val parentLifetime = 6
                val parent = system.store.spawn(CParticle().apply {
                    maxAge = parentLifetime
                    sign = 0
                    updateMode = CParticleUpdateMode.STATIC
                }, Vec3.ZERO, 0, 15, 15, epochTick = 1)
                val child = system.store.spawn(CParticle().apply {
                    maxAge = 100
                    sign = 1
                    updateMode = CParticleUpdateMode.STATIC
                }, Vec3.ZERO, 0, 15, 15, epochTick = 1)
                CParticleRespawnEngine.track(system, parent, waiting = false, includeManual = true)
                CParticleRespawnEngine.track(system, child, waiting = true, includeManual = true)
                CParticleRespawnEngine.link(system, parent, system, child,
                    ParticleRespawnRequest(ControlableCParticleData(), Vec3(0.0, 2.0, 0.0), true))
                val deaths = mutableListOf<CParticleDeathState>()
                CParticleRespawnEngine.onCpuChildren(system, parent) { deaths.add(it) }
                system.forceSink.submit(
                    CParticleForce.Path(path, endMode = CParticlePathEndMode.HOLD, playPeriodTicks = 2.0),
                    CParticleSelector.SignEquals(0),
                )
                // 出生 tick 不推进年龄；达到路径终点后继续检查每一 tick，不能提前消费生命。
                repeat(parentLifetime) { tick ->
                    system.tick()
                    CParticleRespawnEngine.finishTick()
                    glFinish()
                    CParticleRespawnEngine.poll()
                    val parentState = readGpu(system, parent)
                    assertEquals(tick.toFloat(), parentState[CParticleStore.OFF_AGE])
                    assertEquals(1, parentState[CParticleStore.OFF_FLAGS].toInt() and 1)
                    assertEquals(0, parentState[CParticleStore.OFF_FLAGS].toInt() and CParticleInstanceFlags.PATH_ENDED)
                    if (tick >= 2) assertEquals(4F, parentState[0], 0.001F)
                    assertEquals(0, readGpu(system, child)[CParticleStore.OFF_FLAGS].toInt() and 1)
                    assertTrue(deaths.isEmpty())
                    assertEquals(2, system.store.aliveCount)
                }
                system.tick()
                CParticleRespawnEngine.finishTick()
                val childState = readGpu(system, child)
                assertEquals(1, childState[CParticleStore.OFF_FLAGS].toInt() and 1)
                assertEquals(4F, childState[0], 0.001F)
                assertEquals(2F, childState[1], 0.001F)
                assertEquals(0F, childState[CParticleStore.OFF_AGE])
                glFinish()
                CParticleRespawnEngine.poll()
                assertEquals(1, deaths.size)
                assertEquals(parentLifetime, deaths.single().age)
                assertEquals(RemoveReason.LIFECYCLE, deaths.single().reason)
                assertEquals(Vec3(4.0, 0.0, 0.0), deaths.single().position)
                assertEquals(1, system.store.aliveCount)
                repeat(2) {
                    system.tick()
                    CParticleRespawnEngine.finishTick()
                    glFinish()
                    CParticleRespawnEngine.poll()
                }
                assertEquals(1, deaths.size)
                assertEquals(1, system.store.aliveCount)
                assertEquals(0, system.pathEndBuffer.lastEndedCount)
            } finally {
                system.release()
                CParticlePathLibrary.release(path)
                CParticleCapabilities.forceCpuSimulation = previous
            }
        }
    }

    /** 最后匹配的路径决定终点行为；循环与往返不能因 DISAPPEAR 配置而提前死亡。 */
    @Test
    fun `last matching path end mode controls gpu lifetime`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val path = CParticlePathLibrary.create(
                CParticlePathPoint.polyline(listOf(Vec3.ZERO, Vec3(4.0, 0.0, 0.0))),
            )
            try {
                for (playMode in CParticlePathPlayMode.entries) {
                    for (firstEnd in CParticlePathEndMode.entries) {
                        for (lastEnd in CParticlePathEndMode.entries) {
                            val system = CParticleSystem(
                                "path-end-order", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
                            )
                            try {
                                val slot = system.store.spawn(CParticle().apply {
                                    maxAge = 100
                                    updateMode = CParticleUpdateMode.STATIC
                                }, Vec3.ZERO, 0, 15, 15, epochTick = 1)
                                system.forceSink.submit(CParticleForce.Path(
                                    path, playMode = playMode, endMode = firstEnd, playPeriodTicks = 2.0,
                                ))
                                system.forceSink.submit(CParticleForce.Path(
                                    path, playMode = playMode, endMode = lastEnd, playPeriodTicks = 2.0,
                                ))
                                repeat(5) { system.tick() }
                                val disappears = playMode == CParticlePathPlayMode.ONCE &&
                                    lastEnd == CParticlePathEndMode.DISAPPEAR
                                val state = readGpu(system, slot)
                                val flags = state[CParticleStore.OFF_FLAGS].toInt()
                                val label = "$playMode: $firstEnd -> $lastEnd"
                                assertEquals(!disappears, flags and 1 != 0, label)
                                assertEquals(disappears, flags and CParticleInstanceFlags.PATH_ENDED != 0, label)
                                assertEquals(if (disappears) 0 else 1, system.store.aliveCount, label)
                                if (playMode == CParticlePathPlayMode.ONCE) assertEquals(4F, state[0], 0.001F, label)
                            } finally {
                                system.release()
                            }
                        }
                    }
                }
            } finally {
                CParticlePathLibrary.release(path)
                CParticleCapabilities.forceCpuSimulation = previous
            }
        }
    }

    /** 同时结束超过旧结束列表容量的粒子，位图不会截断或漏回收。 */
    @Test
    fun `large prepared gpu death batch activates every child without polling`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val count = 66000
            val system = CParticleSystem("prepared-bulk", count * 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                val request = ParticleRespawnRequest(ControlableCParticleData(), Vec3.ZERO, true)
                repeat(count) { index ->
                    val parent = system.store.spawn(CParticle().apply {
                        pos = Vec3(index.toDouble(), 0.0, 0.0); velocity = Vec3(1.0, 0.0, 0.0)
                        maxAge = 1; updateMode = CParticleUpdateMode.STATIC
                    }, Vec3.ZERO, 0, 15, 15, epochTick = 1)
                    val child = system.store.spawn(CParticle().apply { maxAge = 2; updateMode = CParticleUpdateMode.STATIC },
                        Vec3.ZERO, 0, 15, 15, epochTick = 1)
                    CParticleRespawnEngine.track(system, parent, false, true)
                    CParticleRespawnEngine.track(system, child, true, true)
                    CParticleRespawnEngine.link(system, parent, system, child, request)
                }
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish()
                val started = System.nanoTime()
                system.tick(); CParticleRespawnEngine.finishTick(); glFinish()
                println("prepared bulk GPU death+activation: $count parents, ${(System.nanoTime() - started) / 1000000.0} ms; renderer=${glGetString(GL_RENDERER)}")
                val results = FloatArray(count * 2 * CParticleStore.STRIDE)
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo); glGetBufferSubData(GL_ARRAY_BUFFER, 0L, results)
                repeat(count) { index ->
                    val base = (index * 2 + 1) * CParticleStore.STRIDE
                    assertEquals(1, results[base + CParticleStore.OFF_FLAGS].toInt() and 1)
                    assertEquals(index + 1F, results[base]); assertEquals(0F, results[base + CParticleStore.OFF_AGE])
                }
                CParticleRespawnEngine.poll()
                assertEquals(count, system.store.aliveCount)
                repeat(2) { system.tick(); CParticleRespawnEngine.finishTick(); glFinish(); CParticleRespawnEngine.poll() }
                assertEquals(0, system.store.aliveCount)
            } finally { system.release(); CParticleCapabilities.forceCpuSimulation = previous }
        }
    }
    /** 两套 compute 和显式 CPU 模拟均应提供最后一步运动后的真实世界位置。 */
    @Test
    fun `death snapshots use final motion across cpu and both compute routes`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            try {
                for (cpu in listOf(false, true)) {
                    for (commandRoute in listOf(false, true)) {
                        CParticleCapabilities.forceCpuSimulation = cpu
                        val system = CParticleSystem(
                            "death-motion-test", 4, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
                        )
                        try {
                            val origin = Vec3(10000.0, 80.0, -20000.0)
                            system.setOriginIfEmpty(origin)
                            val events = ArrayList<CParticleDeathState>()
                            repeat(4) { index ->
                                val slot = system.store.spawn(CParticle().apply {
                                    pos = origin.add(index.toDouble(), 0.0, 0.0)
                                    velocity = Vec3(0.5, 1.0, -0.25)
                                    maxAge = 2
                                    sign = 5
                                    updateMode = CParticleUpdateMode.STATIC
                                }, origin, 0, 15, 15, epochTick = system.tickCount + 1)
                                system.trackDeath(slot, system.store.generations[slot], events::add)
                            }
                            val gravity = CParticleForce.Gravity(Vec3(0.0, -0.25, 0.0))
                            if (commandRoute) system.forceSink.submit(gravity, CParticleSelector.SignEquals(5))
                            else system.forceSink.submit(gravity)
                            system.tick()
                            system.tick()
                            assertTrue(events.isEmpty())
                            system.tick()
                            assertEquals(0, system.store.aliveCount)
                            assertEquals(4, events.size)
                            events.forEachIndexed { index, death ->
                                assertEquals(origin.add(index + 1.0, 1.25, -0.5), death.position)
                                assertEquals(Vec3(0.5, 0.5, -0.25), death.velocity)
                                assertEquals(2, death.age)
                                assertEquals(RemoveReason.LIFECYCLE, death.reason)
                            }
                            system.tick()
                            assertEquals(4, events.size)
                        } finally {
                            system.release()
                        }
                    }
                }
            } finally {
                CParticleCapabilities.forceCpuSimulation = previous
            }
        }
    }

    /** 手动删除的死亡快照不能在槽位复用后变成下一颗粒子的状态。 */
    @Test
    fun `gpu death snapshot stays stable after slot reuse and upload`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem(
                "death-reuse-test", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
            )
            try {
                val slot = system.store.spawn(CParticle().apply {
                    pos = Vec3(4.0, 5.0, 6.0)
                    velocity = Vec3(1.0, 0.0, 0.0)
                    maxAge = 20
                }, Vec3.ZERO, 0, 15, 15, epochTick = system.tickCount + 1)
                val deaths = ArrayList<CParticleDeathState>()
                system.trackDeath(slot, system.store.generations[slot], deaths::add)
                system.tick()
                system.tick()
                system.kill(slot, system.store.generations[slot])
                val child = system.store.spawn(CParticle().apply {
                    pos = Vec3(30.0, 40.0, 50.0)
                    maxAge = 20
                }, Vec3.ZERO, 0, 15, 15, epochTick = system.tickCount + 1)
                assertEquals(slot, child)
                system.uploadPendingSpawns()
                val uploaded = FloatArray(CParticleStore.STRIDE)
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, uploaded)
                assertEquals(30F, uploaded[0])
                assertEquals(0, system.store.getAge(child))
                assertTrue(deaths.isEmpty())
                system.tick()
                assertEquals(Vec3(5.0, 5.0, 6.0), deaths.single().position)
                assertEquals(RemoveReason.CALL, deaths.single().reason)
                assertEquals(0, system.store.getAge(child))
            } finally {
                system.release()
                CParticleCapabilities.forceCpuSimulation = previous
            }
        }
    }

    /** GPU 回读必须恢复外部 ARRAY_BUFFER 绑定，不能污染后续渲染。 */
    @Test
    fun `death mapping restores incoming buffer binding`() {
        withOpenGl43Context {
            val previous = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            val system = CParticleSystem(
                "death-binding-test", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
            )
            val external = glGenBuffers()
            try {
                val slot = system.store.spawn(CParticle().apply { maxAge = 20 }, Vec3.ZERO, 0, 15, 15)
                system.trackDeath(slot, system.store.generations[slot]) {}
                system.tick()
                glBindBuffer(GL_ARRAY_BUFFER, external)
                system.kill(slot, system.store.generations[slot])
                assertEquals(external, glGetInteger(GL_ARRAY_BUFFER_BINDING))
                system.store.deathTracker?.drain()
            } finally {
                glDeleteBuffers(external)
                system.release()
                CParticleCapabilities.forceCpuSimulation = previous
            }
        }
    }

    /** 不对称入出手柄确保真实 GPU dispatch 使用起点出手柄与终点入手柄，而非相反字段。 */
    @Test
    fun `bezier gpu handles agree with cpu for geometry orbit and birth offsets`() {
        withOpenGl43Context {
            val previousForceCpu = CParticleCapabilities.forceCpuSimulation
            CParticleCapabilities.forceCpuSimulation = false
            try {
                for (closed in listOf(false, true)) {
                    val path = CParticlePathLibrary.create(
                        listOf(
                            CParticlePathPoint(Vec3.ZERO, Vec3(-0.5, -2.0, 1.0), Vec3(0.0, 3.0, 0.5)),
                            CParticlePathPoint(Vec3(4.0, 0.0, 1.0), Vec3(0.0, 2.0, -1.0), Vec3(2.0, -0.5, 1.0)),
                            CParticlePathPoint(Vec3(2.0, 3.0, 3.0), Vec3(-1.0, 0.0, -2.0), Vec3(1.0, -2.0, 0.0)),
                        ),
                        CParticlePathSegmentType.BEZIER,
                        closed,
                    )
                    try {
                        for (progress in CParticlePathProgressMode.entries) {
                            for (offset in CParticlePathOffsetMode.entries) {
                                val system = CParticleSystem(
                                    "bezier-handle-test", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
                                )
                                val birth = Vec3(0.2, 0.5, -0.3)
                                try {
                                    system.store.spawn(CParticle().apply {
                                        pos = birth
                                        maxAge = 100
                                        updateMode = CParticleUpdateMode.STATIC
                                    }, Vec3.ZERO, 0, 15, 15)
                                    system.forceSink.submit(CParticleForce.Path(
                                        path, playMode = CParticlePathPlayMode.LOOP,
                                        progressMode = progress, playPeriodTicks = 20.0,
                                        offsetRadius = 0.7, phaseRadians = 0.3,
                                        angularVelocityRadiansPerTick = -0.2, offsetMode = offset,
                                    ))
                                    system.tick()
                                    repeat(24) {
                                        system.tick()
                                        val actual = FloatArray(CParticleStore.STRIDE)
                                        glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                                        glGetBufferSubData(GL_ARRAY_BUFFER, 0L, actual)
                                        val expected = assertNotNull(CParticlePathCpuEvaluator.evaluate(
                                            CParticlePathLibrary.currentLayerData(), path.slot,
                                            actual[CParticleStore.OFF_AGE].toDouble() - 1.0, 100.0,
                                            CParticlePathCpuEvaluator.Input(
                                                playMode = CParticlePathPlayMode.LOOP, progressMode = progress,
                                                playPeriodTicks = 20.0, offsetRadius = 0.7, phaseRadians = 0.3,
                                                angularVelocityRadiansPerTick = -0.2, offsetMode = offset,
                                                birth = CParticlePathBirth(birth),
                                            ),
                                        ))
                                        val position = Vec3(actual[0].toDouble(), actual[1].toDouble(), actual[2].toDouble())
                                        assertTrue(position.distanceTo(expected.position) < 2.0E-4,
                                            "closed=$closed progress=$progress offset=$offset actual=$position expected=${expected.position}")
                                    }
                                } finally {
                                    system.release()
                                }
                            }
                        }
                    } finally {
                        CParticlePathLibrary.release(path)
                    }
                }
            } finally {
                CParticleGpuSimulator.release()
                CParticleCapabilities.forceCpuSimulation = previousForceCpu
            }
        }
    }

    /** 多槽位真实 dispatch 与 CPU 回退都必须读取各自出生参考，且切换命令不会重置参考。 */
    @Test
    fun `birth offsets agree between gpu dispatch and cpu fallback`() {
        withOpenGl43Context {
            val previousForceCpu = CParticleCapabilities.forceCpuSimulation
            val path = CParticlePathLibrary.create(CParticlePathPoint.polyline(
                listOf(Vec3.ZERO, Vec3(5.0, 0.0, 0.0), Vec3(5.0, 5.0, 3.0)),
            ))
            try {
                for (forceCpu in listOf(false, true)) {
                    CParticleCapabilities.forceCpuSimulation = forceCpu
                    val system = CParticleSystem(
                        "path-birth-test", 2, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED,
                    )
                    try {
                        val births = listOf(Vec3(0.2, 0.5, -0.3), Vec3(-0.4, -1.2, 0.8))
                        births.forEachIndexed { index, birth ->
                            system.store.spawn(CParticle().apply {
                                pos = birth
                                maxAge = 100
                                age = index * 3
                                updateMode = CParticleUpdateMode.STATIC
                            }, Vec3.ZERO, 0, 15, 15)
                        }
                        val force = CParticleForce.Path(path, playPeriodTicks = 20.0).apply {
                            offsetMode = CParticlePathOffsetMode.BIRTH_POSITION
                            angularVelocityRadiansPerTick = 0.2
                        }
                        system.forceSink.submit(force)
                        system.tick()
                        repeat(12) { tick ->
                            if (tick == 4) force.offsetMode = CParticlePathOffsetMode.BIRTH_FRAME
                            system.tick()
                            val actual = if (forceCpu) system.store.data.copyOf() else {
                                FloatArray(CParticleStore.STRIDE * 2).also {
                                    glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                                    glGetBufferSubData(GL_ARRAY_BUFFER, 0L, it)
                                }
                            }
                            births.forEachIndexed { index, birth ->
                                val base = index * CParticleStore.STRIDE
                                val expected = assertNotNull(CParticlePathCpuEvaluator.evaluate(
                                    CParticlePathLibrary.currentLayerData(), path.slot,
                                    actual[base + CParticleStore.OFF_AGE].toDouble() - 1.0, 100.0,
                                    CParticlePathCpuEvaluator.Input(
                                        playPeriodTicks = 20.0,
                                        angularVelocityRadiansPerTick = 0.2,
                                        offsetMode = force.offsetMode,
                                        birth = CParticlePathBirth(birth, index * 3.0),
                                    ),
                                ))
                                val position = Vec3(actual[base].toDouble(), actual[base + 1].toDouble(), actual[base + 2].toDouble())
                                assertTrue(position.distanceTo(expected.position) < 2.0E-4,
                                    "CPU=$forceCpu tick=$tick slot=$index expected=${expected.position} actual=$position")
                            }
                        }
                    } finally {
                        system.release()
                    }
                }
            } finally {
                CParticlePathLibrary.release(path)
                CParticleGpuSimulator.release()
                CParticleCapabilities.forceCpuSimulation = previousForceCpu
            }
        }
    }

    /** 使用真实 compute dispatch 验证默认仅移动，显式开启后纹理高度轴对齐路径。 */
    @Test
    fun `gpu path orientation requires explicit opt in`() {
        withOpenGl43Context {
            val previousForceCpu = CParticleCapabilities.forceCpuSimulation
            val path = CParticlePathLibrary.create(
                CParticlePathPoint.polyline(listOf(Vec3.ZERO, Vec3(4.0, 2.0, -3.0))),
            )
            val system = CParticleSystem("path-orientation-test", 1, CParticleRenderLayer.TRANSLUCENT, CParticleSystemMode.SIMULATED)
            try {
                CParticleCapabilities.forceCpuSimulation = false
                val particle = CParticle().apply {
                    maxAge = 100
                    updateMode = CParticleUpdateMode.STATIC
                }
                system.store.spawn(particle, Vec3.ZERO, 0, 15, 15)
                val original = system.store.data.copyOf()
                val force = CParticleForce.Path(path, playPeriodTicks = 40.0)
                system.forceSink.submit(force)
                system.tick()
                system.tick()
                val result = FloatArray(CParticleStore.STRIDE)
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                assertTrue(result[0] > 0F)
                for (offset in CParticleStore.OFF_SIZE until CParticleStore.OFF_AXIS + 4) {
                    assertEquals(original[offset], result[offset], "关闭时姿态通道 $offset 不应改变")
                }
                assertEquals(
                    original[CParticleStore.OFF_FLAGS].toInt() and CParticleStore.FLAG_NEWBORN.inv(),
                    result[CParticleStore.OFF_FLAGS].toInt(),
                )
                force.faceMotion = true
                force.forwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_Y
                system.tick()
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                val forward = Quaternionf().rotationXYZ(
                    result[CParticleStore.OFF_SIZE + 3],
                    result[CParticleStore.OFF_SIZE + 2],
                    result[CParticleStore.OFF_AXIS + 3],
                ).transform(Vector3f(0F, 1F, 0F))
                val expected = Vec3(4.0, 2.0, -3.0).normalize()
                assertEquals(expected.x.toFloat(), forward.x, 1.0E-4F)
                assertEquals(expected.y.toFloat(), forward.y, 1.0E-4F)
                assertEquals(expected.z.toFloat(), forward.z, 1.0E-4F)
                assertTrue(result[CParticleStore.OFF_FLAGS].toInt() and CParticleInstanceFlags.PATH_ROTATION != 0)
                assertEquals(result[CParticleStore.OFF_SIZE + 3], result[CParticleStore.OFF_AXIS])
                assertEquals(result[CParticleStore.OFF_SIZE + 2], result[CParticleStore.OFF_AXIS + 1])
                assertEquals(result[CParticleStore.OFF_ROLL], result[CParticleStore.OFF_AXIS + 2])
                val oldAngles = floatArrayOf(
                    result[CParticleStore.OFF_SIZE + 3], result[CParticleStore.OFF_SIZE + 2], result[CParticleStore.OFF_ROLL],
                )
                force.offsetRadius = 0.9
                force.angularVelocityRadiansPerTick = 0.2
                system.tick()
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                repeat(3) { index -> assertEquals(oldAngles[index], result[CParticleStore.OFF_AXIS + index]) }
                val lastAngles = floatArrayOf(
                    result[CParticleStore.OFF_SIZE + 3], result[CParticleStore.OFF_SIZE + 2], result[CParticleStore.OFF_ROLL],
                )
                force.faceMotion = false
                system.tick()
                glBindBuffer(GL_ARRAY_BUFFER, system.glBuffer.vbo)
                glGetBufferSubData(GL_ARRAY_BUFFER, 0L, result)
                assertEquals(0, result[CParticleStore.OFF_FLAGS].toInt() and CParticleInstanceFlags.PATH_ROTATION)
                assertEquals(lastAngles[0], result[CParticleStore.OFF_SIZE + 3])
                assertEquals(lastAngles[1], result[CParticleStore.OFF_SIZE + 2])
                assertEquals(lastAngles[2], result[CParticleStore.OFF_ROLL])
            } finally {
                system.release()
                CParticleGpuSimulator.release()
                CParticlePathLibrary.releaseAll()
                CParticleCapabilities.forceCpuSimulation = previousForceCpu
            }
        }
    }

    @Test
    fun `cparticle compute shader compiles and links on opengl 43`() {
        withOpenGl43Context {
            val computeShaderIds = listOf(
                "core/compute/cparticle_sim.comp",
                "core/compute/cparticle_sim_legacy.comp",
                "core/compute/cparticle_death_capture.comp",
                "core/compute/cparticle_respawn.comp",
            )
            computeShaderIds.forEach { shaderId ->
                var shader = 0
                var program = 0
                try {
                    // 必须走 Coo 源码加载器：自有 shader 的 #coo_import 由框架解析，
                    // 直接读原始文件会把未展开的指令交给驱动。
                    val resource = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, shaderId)
                    val source = CooShaderReloadSupport.loadShaderSource(resource)
                    shader = glCreateShader(GL_COMPUTE_SHADER)
                    glShaderSource(shader, source)
                    glCompileShader(shader)
                    assertTrue(
                        glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE,
                        "$shaderId: ${glGetShaderInfoLog(shader)}",
                    )

                    program = glCreateProgram()
                    glAttachShader(program, shader)
                    glLinkProgram(program)
                    assertTrue(
                        glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE,
                        "$shaderId: ${glGetProgramInfoLog(program)}",
                    )
                    if (shaderId.endsWith("cparticle_sim.comp")) {
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
    fun `advance growth of initialized system preserves particle and metadata buffers`() {
        withOpenGl43Context {
            val computeSupportedField = CParticleCapabilities::class.java
                .getDeclaredField("computeSupported")
                .apply { isAccessible = true }
            val previousComputeSupported = computeSupportedField.getBoolean(CParticleCapabilities)
            val previousForceCpuSimulation = CParticleCapabilities.forceCpuSimulation
            val system = CParticleSystem(
                "gpu-growth-test",
                4,
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

                system.growTo(8)
                assertEquals(8, system.capacity)
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
        val metadata = FloatArray(36)
        metadata[0] = Float.fromBits(100)
        metadata[1] = Float.fromBits(4)
        metadata[12] = Float.fromBits(101)
        metadata[13] = Float.fromBits(5)
        metadata[24] = Float.fromBits(102)
        metadata[25] = Float.fromBits(6)
        metadata[4] = Float.NaN
        metadata[16] = Float.NaN
        metadata[28] = Float.NaN
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
            if (window != 0L) {
                // 每个用例创建独立 GL 上下文，不能让共享路径缓冲保留已销毁上下文的句柄。
                CParticleRespawnEngine.clear()
                CParticleRespawnEngine.releasePrograms()
                CParticlePathLibrary.invalidateGlResources()
                CParticleGpuSimulator.release()
                glfwDestroyWindow(window)
            }
            glfwTerminate()
        }
    }

}
