package cn.coostack.cooparticlesapi.cparticle.simulate

import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGrid
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43

/**
 * GPU compute 模拟器 (GL 4.3).
 *
 * 每个客户端 tick 对每个 GPU 模式的系统 dispatch 一次:
 * kernel 对每个存活粒子执行 prev=cur → 力场累加 → 限速 → 积分 → age+1.
 * 力场以 `vec4[MAX_FORCES*4]` uniform 数组传入, 数据与 CPU 模拟器完全一致.
 *
 * 注意: dispatch 直接调 GL43.glDispatchCompute 而非 program.dispatch(),
 * 规避 ComputeShaderProgram.useOnContext 嵌套 use() 造成的 prevProgram 覆盖问题.
 */
object CParticleGpuSimulator {
    /** SSBO binding 点 (与 cparticle_sim.comp 中 layout(binding=0) 一致) */
    private const val PARTICLE_BUFFER_BINDING = 0

    /** 方块占用位图 SSBO binding 点，与 compute shader 保持一致。 */
    private const val COLLISION_BUFFER_BINDING = 1

    private var program: CooComputeShaderProgram? = null
    private val tmpOrigin = Vector3f()
    private val tmpCollisionOffset = Vector3f()

    private fun ensureProgram(): CooComputeShaderProgram? {
        val current = program
        if (current != null) {
            if (current.program != 0) return current
            return initializeProgram(current)
        }
        val created = AdvancedShaderProgramBuilder()
            .compute("core/compute/cparticle_sim.comp")
            .managedId("cparticle/simulate")
            .buildCompute()
        program = created
        return initializeProgram(created)
    }

    private fun initializeProgram(candidate: CooComputeShaderProgram): CooComputeShaderProgram? {
        return try {
            candidate.init()
            candidate
        } catch (error: RuntimeException) {
            program = null
            ShaderProgramRegistry.unregister(candidate)
            runCatching { candidate.computeShader.deleteShader() }
            CParticleCapabilities.disableComputeSimulation(error.message ?: error.javaClass.simpleName)
            null
        }
    }

    /**
     * 推进一个 tick (必须在渲染线程).
     * @param packed 力场打包数据 (CParticleForce.STRIDE * MAX_FORCES 大小)
     */
    fun simulate(system: CParticleSystem, packed: FloatArray, forceCount: Int): Boolean =
        simulate(system, packed, forceCount, null)

    /**
     * 推进一个可选读取共享方块占用网格的 GPU tick。
     *
     * Example: SIMULATED system 仅在碰撞实例计数大于零时传入网格。
     * Forbidden: 外部调用方不能绑定或持有内部碰撞网格。
     *
     * @param system 被模拟的粒子 system
     * @param packed 力场打包数组
     * @param forceCount 有效力场数量
     * @param collisionGrid 可选共享方块占用网格
     * @return compute shader 成功 dispatch 时为 `true`
     */
    internal fun simulate(
        system: CParticleSystem,
        packed: FloatArray,
        forceCount: Int,
        collisionGrid: CParticleBlockCollisionGrid?,
    ): Boolean {
        val store = system.store
        if (store.highWater <= 0) return true
        val glBuffer = system.glBuffer
        if (!glBuffer.initialized) return false
        val compute = ensureProgram() ?: return false
        if (compute.program == 0) return false

        compute.useOnContext {
            setInt("uCount", store.highWater)
            setInt("uForceCount", forceCount)
            setFloat("uSpeedLimit", system.speedLimit)
            setFloat3(
                "uOrigin", tmpOrigin.set(
                    system.origin.x.toFloat(),
                    system.origin.y.toFloat(),
                    system.origin.z.toFloat()
                )
            )
            setInt("uCollisionEnabled", if (collisionGrid != null) 1 else 0)
            setInt("uCollisionSize", CParticleBlockCollisionGrid.SIZE)
            if (collisionGrid != null) {
                setFloat3(
                    "uCollisionOffset",
                    tmpCollisionOffset.set(
                        (system.origin.x - collisionGrid.minX).toFloat(),
                        (system.origin.y - collisionGrid.minY).toFloat(),
                        (system.origin.z - collisionGrid.minZ).toFloat(),
                    )
                )
            } else {
                setFloat3("uCollisionOffset", tmpCollisionOffset.zero())
            }
            if (forceCount > 0) {
                // vec4 数组: MAX_FORCES * 4 个 vec4
                setFloat4Array("uForces", packed)
            }
            val previousStorageBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
            val previousParticleBinding = GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                PARTICLE_BUFFER_BINDING,
            )
            val previousCollisionBinding = GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                COLLISION_BUFFER_BINDING,
            )
            try {
                glBuffer.bindShaderStorage(PARTICLE_BUFFER_BINDING)
                collisionGrid?.bindShaderStorage(COLLISION_BUFFER_BINDING)
                // 直接 GL 调用, 不用 dispatch() (见类注释)
                GL43.glDispatchCompute((store.highWater + 255) / 256, 1, 1)
            } finally {
                GL43.glBindBufferBase(
                    GL43.GL_SHADER_STORAGE_BUFFER,
                    PARTICLE_BUFFER_BINDING,
                    previousParticleBinding,
                )
                GL43.glBindBufferBase(
                    GL43.GL_SHADER_STORAGE_BUFFER,
                    COLLISION_BUFFER_BINDING,
                    previousCollisionBinding,
                )
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousStorageBuffer)
            }
        }
        // compute 写入 → instanced attribute 读取, 必须 barrier
        GL43.glMemoryBarrier(
            GL43.GL_SHADER_STORAGE_BARRIER_BIT or
                    GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT or
                    GL43.GL_BUFFER_UPDATE_BARRIER_BIT
        )
        return true
    }

    fun release() {
        program?.release()
        program = null
    }

    /** 供 GLSL/CPU 保持一致的常量: uniform 数组长度 = MAX_FORCES * 4 个 vec4 */
    const val PACKED_SIZE = CParticleForce.MAX_FORCES * CParticleForce.STRIDE
}
