package cn.coostack.cooparticlesapi.cparticle.simulate

import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGrid
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResourceTable
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20.glIsProgram
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43

/**
 * GPU compute 模拟器 (GL 4.3).
 *
 * 每个客户端 tick 对每个 GPU 模式的系统 dispatch 一次:
 * kernel 对每个存活粒子执行 prev=cur → 力场累加 → 限速 → 积分 → age+1.
 * 旧 1..9 Force、`selector=All` 且不超过 16 条时使用原有 uniform kernel；
 * selector、新 Force 和资源 Force 使用共享 Command SSBO kernel。两条路径都在 GPU 上执行。
 *
 * 注意：dispatch 直接调用 GL43.glDispatchCompute，而不是 program.dispatch()，
 * 规避 ComputeShaderProgram.useOnContext 嵌套 use() 造成的 prevProgram 覆盖问题.
 */
object CParticleGpuSimulator {
    /** SSBO binding 点 (与 cparticle_sim.comp 中 layout(binding=0) 一致) */
    private const val PARTICLE_BUFFER_BINDING = 0

    /** 方块占用位图 SSBO binding 点，与 compute shader 保持一致。 */
    private const val COLLISION_BUFFER_BINDING = 1
    private const val METADATA_BUFFER_BINDING = 2
    private const val COMMAND_BUFFER_BINDING = 3

    /** v2 Command SSBO kernel. */
    private var program: CooComputeShaderProgram? = null
    /** 旧 1..9 uniform kernel；保留传输快路径，数学仍与 Command 类型一致。 */
    private var legacyProgram: CooComputeShaderProgram? = null
    private val tmpOrigin = Vector3f()
    private val tmpCollisionOffset = Vector3f()

    /**
     * 把 CParticle compute program 加入统一注册表，不触发 GL 编译。
     *
     * 示例：客户端选择默认 GPU 路径后，由 [initializeProgramForRequestedRoute] 调用。
     * 禁止：显式 CPU 模式下不能注册，否则全量重载会尝试编译它。
     *
     * @return 已注册的共享 compute program
     */
    internal fun registerProgram(): CooComputeShaderProgram {
        program?.let { return ShaderProgramRegistry.register(it) }
        return AdvancedShaderProgramBuilder()
            .compute("core/compute/cparticle_sim.comp")
            .managedId("cparticle/simulate")
            .buildCompute()
            .also { program = it }
    }

    /** 注册旧 1..9 Force 的 uniform compute kernel，不触发 GL 编译。 */
    private fun registerLegacyProgram(): CooComputeShaderProgram {
        legacyProgram?.let { return ShaderProgramRegistry.register(it) }
        return AdvancedShaderProgramBuilder()
            .compute("core/compute/cparticle_sim_legacy.comp")
            .managedId("cparticle/simulate_legacy")
            .buildCompute()
            .also { legacyProgram = it }
    }

    /**
     * 按调用方选择的模拟路径提前编译 compute program。
     *
     * 示例：客户端渲染初始化在 [ShaderProgramRegistry.reinitializeAll] 前调用。
     * 禁止：能力诊断字段不能在真实编译前把默认 GPU 请求改成 CPU；只有显式 CPU 模式不注册 program。
     */
    internal fun initializeProgramForRequestedRoute() {
        if (!CParticleCapabilities.gpuSimulationRequested()) {
            release()
            return
        }
        try {
            val commandCandidate = registerProgram()
            if (commandCandidate.program == 0) initializeProgram(commandCandidate, false)
            val legacyCandidate = registerLegacyProgram()
            if (legacyCandidate.program == 0) initializeProgram(legacyCandidate, true)
        } catch (error: RuntimeException) {
            // 两个 kernel 必须作为一个 GPU 能力单元成功；避免 command 已编译而 legacy 失败时
            // 留下半初始化的 program，下一次资源重载仍会误用旧句柄。
            release()
            throw error
        }
    }

    /**
     * 返回可用的 compute program；编译或链接失败时直接抛出异常。
     *
     * 示例：program 已在客户端渲染初始化阶段编译时直接返回缓存实例。
     * 禁止：只有显式 CPU 模式才跳过创建；compute 能力不足或 shader 不兼容必须在初始化时抛错。
     *
     * @return 可用的 compute program；仅显式 CPU 模式返回 `null`
     */
    private fun ensureProgram(legacy: Boolean): CooComputeShaderProgram? {
        if (!CParticleCapabilities.gpuSimulationRequested()) return null
        val current = if (legacy) legacyProgram else program
        current?.takeIf { it.program != 0 }?.let { return it }
        initializeProgramForRequestedRoute()
        return (if (legacy) legacyProgram else program)?.takeIf { it.program != 0 }
    }

    private fun initializeProgram(
        candidate: CooComputeShaderProgram,
        legacy: Boolean,
    ): CooComputeShaderProgram {
        return try {
            candidate.init()
            candidate
        } catch (error: RuntimeException) {
            ShaderProgramRegistry.unregister(candidate)
            runCatching { candidate.computeShader.deleteShader() }
            if (legacy) legacyProgram = null else program = null
            throw IllegalStateException(
                "[cparticle] ${if (legacy) "legacy Force" else "Force Command"} GPU compute shader 编译或链接失败，拒绝回退 CPU",
                error,
            )
        }
    }

    /**
     * 执行 GPU 模拟。旧 1..9 且 selector=All 的批次走 uniform 快路径；其余批次走 Command SSBO。
     * 两条 kernel 共享粒子生命周期、碰撞、坐标变换和限速阶段，不存在 CPU fallback。
     */
    internal fun simulate(
        system: CParticleSystem,
        legacyPacked: FloatArray,
        legacyForceCount: Int,
        commandPacked: FloatArray,
        commandCount: Int,
        metadataRequired: Boolean,
        collisionGrid: CParticleBlockCollisionGrid?,
        simulationTransform: Matrix4f?,
        inverseSimulationTransform: Matrix4f?,
    ): Boolean {
        require((simulationTransform == null) == (inverseSimulationTransform == null)) {
            "simulation transform and inverse must be supplied together"
        }
        require(legacyForceCount >= -1 && legacyPacked.size >=
            legacyForceCount.coerceAtLeast(0) * CParticleForce.STRIDE) {
            "invalid legacy Force payload: count=$legacyForceCount floats=${legacyPacked.size}"
        }
        require(commandCount >= 0 && commandPacked.size >= commandCount * ForceCommand.STRIDE) {
            "invalid Force Command payload: count=$commandCount floats=${commandPacked.size}"
        }
        val useLegacy = legacyForceCount >= 0
        require(useLegacy || commandCount > 0) {
            "command route requires at least one Force Command"
        }
        val store = system.store
        val activeSlotCount = store.activeSlotCount
        if (activeSlotCount <= 0) return true
        check(!CParticleCapabilities.forceCpuSimulation) {
            "[cparticle] Force Command GPU compute 未启用，拒绝回退 CPU"
        }
        check(system.glBuffer.initialized) {
            "[cparticle] system=${system.name} 的粒子 SSBO 尚未初始化"
        }
        check(useLegacy || commandCount == 0 || system.commandGlBuffer.initialized) {
            "[cparticle] system=${system.name} 的 Command SSBO 尚未初始化"
        }
        check(useLegacy || !metadataRequired || system.metadataGlBuffer.initialized) {
            "[cparticle] system=${system.name} 的 metadata SSBO 尚未初始化"
        }
        val compute = checkNotNull(ensureProgram(useLegacy)) {
            "[cparticle] ${if (useLegacy) "legacy Force" else "Force Command"} GPU compute program 不可用"
        }
        check(compute.program != 0 && glIsProgram(compute.program)) {
            "[cparticle] ${if (useLegacy) "legacy Force" else "Force Command"} GPU compute program 无效，拒绝回退 CPU"
        }

        val textureBindings = if (useLegacy) emptyList() else system.forceResourceTable.textureBindings()
        val fluidBindings = if (useLegacy) emptyList() else system.forceResourceTable.fluidBindings()
        var dispatched = false
        try {
            compute.useOnContext {
                setInt("uFirstSlot", store.firstAliveSlot)
                setInt("uCount", activeSlotCount)
                if (useLegacy) {
                    setInt("uForceCount", legacyForceCount)
                    setFloat4Array("uForces", legacyPacked)
                } else {
                    setInt("uCommandCount", commandCount)
                    setInt("uMetadataEnabled", if (metadataRequired) 1 else 0)
                }
                setFloat("uSpeedLimit", system.speedLimit)
                setFloat3(
                    "uOrigin", tmpOrigin.set(
                        system.origin.x.toFloat(),
                        system.origin.y.toFloat(),
                        system.origin.z.toFloat(),
                    )
                )
                setInt("uTransformSimulation", if (simulationTransform == null) 0 else 1)
                if (simulationTransform != null && inverseSimulationTransform != null) {
                    setMatrix4("uSimulationTransform", simulationTransform)
                    setMatrix4("uInverseSimulationTransform", inverseSimulationTransform)
                }
                setInt("uCollisionEnabled", if (collisionGrid != null) 1 else 0)
                setInt("uCollisionSize", collisionGrid?.size ?: CParticleBlockCollisionGrid.SIZE)
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
                val previousStorageBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
                val previousParticleBinding = GL30.glGetIntegeri(
                    GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                    PARTICLE_BUFFER_BINDING,
                )
                val previousCollisionBinding = GL30.glGetIntegeri(
                    GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                    COLLISION_BUFFER_BINDING,
                )
                val previousMetadataBinding = GL30.glGetIntegeri(
                    GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                    METADATA_BUFFER_BINDING,
                )
                val previousCommandBinding = GL30.glGetIntegeri(
                    GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                    COMMAND_BUFFER_BINDING,
                )
                var boundTextureCount = 0
                var boundFluidCount = 0
                try {
                    for (index in textureBindings.indices) {
                        textureBindings[index].bindCompute(index)
                        boundTextureCount++
                        setInt("uTextureResources[$index]", index)
                    }
                    for (index in fluidBindings.indices) {
                        val textureUnit = CParticleForceResourceTable.MAX_TEXTURE_RESOURCES + index
                        fluidBindings[index].bindCompute(textureUnit)
                        boundFluidCount++
                        setInt("uFluidResources[$index]", textureUnit)
                    }
                    system.glBuffer.bindShaderStorage(PARTICLE_BUFFER_BINDING)
                    collisionGrid?.bindShaderStorage(COLLISION_BUFFER_BINDING)
                    if (!useLegacy && metadataRequired) {
                        system.metadataGlBuffer.bindShaderStorage(METADATA_BUFFER_BINDING)
                    }
                    if (!useLegacy && commandCount > 0) {
                        system.commandGlBuffer.bindShaderStorage(COMMAND_BUFFER_BINDING)
                    }
                    GL43.glDispatchCompute((activeSlotCount + 255) / 256, 1, 1)
                    dispatched = true
                } finally {
                    try {
                        var resetFailure: RuntimeException? = null
                        for (index in boundFluidCount - 1 downTo 0) {
                            try {
                                fluidBindings[index].resetCompute()
                            } catch (error: RuntimeException) {
                                if (resetFailure == null) resetFailure = error else resetFailure.addSuppressed(error)
                            }
                        }
                        for (index in boundTextureCount - 1 downTo 0) {
                            try {
                                textureBindings[index].resetCompute()
                            } catch (error: RuntimeException) {
                                if (resetFailure == null) resetFailure = error else resetFailure.addSuppressed(error)
                            }
                        }
                        if (resetFailure != null) throw resetFailure
                    } finally {
                        GL43.glBindBufferBase(
                            GL43.GL_SHADER_STORAGE_BUFFER,
                            PARTICLE_BUFFER_BINDING,
                            previousParticleBinding,
                        )
                        GL43.glBindBufferBase(
                            GL43.GL_SHADER_STORAGE_BUFFER,
                            METADATA_BUFFER_BINDING,
                            previousMetadataBinding,
                        )
                        GL43.glBindBufferBase(
                            GL43.GL_SHADER_STORAGE_BUFFER,
                            COMMAND_BUFFER_BINDING,
                            previousCommandBinding,
                        )
                        GL43.glBindBufferBase(
                            GL43.GL_SHADER_STORAGE_BUFFER,
                            COLLISION_BUFFER_BINDING,
                            previousCollisionBinding,
                        )
                        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousStorageBuffer)
                    }
                }
            }
        } finally {
            if (dispatched) memoryBarrier()
        }
        check(dispatched) {
            "[cparticle] Force Command GPU compute 未执行 dispatch，拒绝回退 CPU"
        }
        return true
    }

    /** 发布 compute 对 SSBO 的写入，供后续实例属性读取和缓冲更新使用。 */
    private fun memoryBarrier() {
        GL43.glMemoryBarrier(
            GL43.GL_SHADER_STORAGE_BARRIER_BIT or
                    GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT or
                    GL43.GL_BUFFER_UPDATE_BARRIER_BIT
        )
    }

    /**
     * 释放并注销 CParticle compute program。
     *
     * 示例：强制 CPU 模拟或完全关闭 CParticle 子系统时调用。
     * 禁止：program 仍需参加下一次资源重载时不能提前调用。
     */
    fun release() {
        program?.let(ShaderProgramRegistry::unregister)
        legacyProgram?.let(ShaderProgramRegistry::unregister)
        program = null
        legacyProgram = null
    }
}
