package cn.coostack.cooparticlesapi.cparticle.simulate

import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticlePerfProbe
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGrid
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResourceTable
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEvaluator
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CooPathCommandAbi
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataBindingPoints
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL20.glIsProgram
import org.lwjgl.opengl.GL20.glUseProgram
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

    /**
     * 路径图层 SSBO binding 点，与 `cparticle_sim.comp` 中路径缓冲的 `binding` 一致。
     *
     * 取自 [CooGpuDataBindingPoints]，因为所有路径共用同一张图层与同一个绑定点。
     */
    internal val PATH_LAYER_BUFFER_BINDING = CooGpuDataBindingPoints.PATH_LAYER

    /**
     * 路径结束通道 SSBO binding 点。
     *
     * 每个 system 有自己的结束缓冲，但共用同一个绑定点：绑定与恢复都在一次 dispatch 的
     * try/finally 内完成，不会与其他 system 长期占用冲突。
     */
    private const val PATH_END_BUFFER_BINDING = CooGpuDataBindingPoints.PATH_END_CHANNEL

    /** v2 Command SSBO kernel. */
    private var program: CooComputeShaderProgram? = null
    /** 旧 1..9 uniform kernel；保留传输快路径，数学仍与 Command 类型一致。 */
    private var legacyProgram: CooComputeShaderProgram? = null
    private val tmpOrigin = Vector3f()
    private val tmpCollisionOffset = Vector3f()

    /**
     * 同一客户端 tick 的多个 system 共用一个 compute 上下文。
     *
     * 原实现每次 [simulate] 都通过 `useOnContext` 保存/恢复当前 program，并查询/恢复
     * 多个 SSBO binding。emitter 数量上升后，这些固定 GL 调用会按 system 数量线性放大，
     * 而真正的粒子 dispatch 反而只处理很小的工作组。批次只在首尾保存外部状态，中间
     * 允许后一个 system 覆盖前一个 system 的 CParticle binding。
     */
    private var batchActive = false
    private var batchPreviousProgram = 0
    private var batchBoundProgram = 0
    private var batchPreviousStorageBuffer = 0
    private val batchPreviousIndexedBindings = IntArray(CooGpuDataBindingPoints.REGISTERED_COUNT)
    private val batchValidatedPrograms = HashSet<Int>(2)
    private var batchBoundCollisionGrid: CParticleBlockCollisionGrid? = null
    private var batchPathLayerBound = false

    /**
     * 批次内的 uniform 快照。
     *
     * 多 emitter 通常共享速度上限、碰撞开关、命令开关等值；重复调用 glUniform
     * 仍然会进入驱动，即使写入的值没有变化。快照只在当前 program 的批次内生效，
     * program 切换或批次结束时立即清空，不改变普通 shader API 的外部语义。
     */
    private class BatchUniformState {
        private var intMask = 0
        private var firstSlot = 0
        private var count = 0
        private var forceCount = 0
        private var commandCount = 0
        private var metadataEnabled = 0
        private var pathLayerEnabled = 0
        private var pathEndCapacity = 0
        private var transformSimulation = 0
        private var collisionEnabled = 0
        private var collisionSize = 0
        private var speedLimit = 0F
        private var deltaTicks = 0F
        private var floatMask = 0
        private val vectorValues = FloatArray(6)
        private var vectorMask = 0
        private var forceArray = FloatArray(0)

        fun clear() {
            intMask = 0
            vectorMask = 0
            floatMask = 0
            forceArray = FloatArray(0)
        }

        fun setInt(program: CooComputeShaderProgram, key: String, value: Int) {
            val bit = when (key) {
                "uFirstSlot" -> 1
                "uCount" -> 2
                "uForceCount" -> 4
                "uCommandCount" -> 8
                "uMetadataEnabled" -> 16
                "uPathLayerEnabled" -> 32
                "uPathEndCapacity" -> 64
                "uTransformSimulation" -> 128
                "uCollisionEnabled" -> 256
                "uCollisionSize" -> 512
                else -> 0
            }
            val previous = when (key) {
                "uFirstSlot" -> firstSlot
                "uCount" -> count
                "uForceCount" -> forceCount
                "uCommandCount" -> commandCount
                "uMetadataEnabled" -> metadataEnabled
                "uPathLayerEnabled" -> pathLayerEnabled
                "uPathEndCapacity" -> pathEndCapacity
                "uTransformSimulation" -> transformSimulation
                "uCollisionEnabled" -> collisionEnabled
                "uCollisionSize" -> collisionSize
                else -> Int.MIN_VALUE
            }
            if (bit != 0 && intMask and bit != 0 && previous == value) return
            when (key) {
                "uFirstSlot" -> firstSlot = value
                "uCount" -> count = value
                "uForceCount" -> forceCount = value
                "uCommandCount" -> commandCount = value
                "uMetadataEnabled" -> metadataEnabled = value
                "uPathLayerEnabled" -> pathLayerEnabled = value
                "uPathEndCapacity" -> pathEndCapacity = value
                "uTransformSimulation" -> transformSimulation = value
                "uCollisionEnabled" -> collisionEnabled = value
                "uCollisionSize" -> collisionSize = value
            }
            if (bit != 0) intMask = intMask or bit
            program.setInt(key, value)
        }

        fun setFloat(program: CooComputeShaderProgram, key: String, value: Float) {
            val bit = when (key) {
                "uSpeedLimit" -> 1
                "uDeltaTicks" -> 2
                else -> 0
            }
            val previous = when (key) {
                "uSpeedLimit" -> speedLimit
                "uDeltaTicks" -> deltaTicks
                else -> Float.NaN
            }
            if (bit != 0 && floatMask and bit != 0 && previous == value) return
            when (key) {
                "uSpeedLimit" -> speedLimit = value
                "uDeltaTicks" -> deltaTicks = value
            }
            if (bit != 0) floatMask = floatMask or bit
            program.setFloat(key, value)
        }

        fun setFloat3(program: CooComputeShaderProgram, key: String, value: Vector3f) {
            val offset = when (key) {
                "uOrigin" -> 0
                "uCollisionOffset" -> 3
                else -> -1
            }
            if (offset >= 0 && vectorMask and (1 shl (offset / 3)) != 0 &&
                vectorValues[offset] == value.x && vectorValues[offset + 1] == value.y &&
                vectorValues[offset + 2] == value.z
            ) return
            if (offset >= 0) {
                vectorValues[offset] = value.x
                vectorValues[offset + 1] = value.y
                vectorValues[offset + 2] = value.z
                vectorMask = vectorMask or (1 shl (offset / 3))
            }
            program.setFloat3(key, value)
        }

        fun setFloat4Array(
            program: CooComputeShaderProgram,
            key: String,
            value: FloatArray,
            floatCount: Int,
        ) {
            if (key == "uForces" && forceArray.size == floatCount && valuesMatch(forceArray, value, floatCount)) {
                return
            }
            if (key == "uForces") {
                if (forceArray.size != floatCount) forceArray = FloatArray(floatCount)
                value.copyInto(forceArray, 0, 0, floatCount)
            }
            if (floatCount > 0) {
                program.setFloat4Array(key, if (key == "uForces") forceArray else value.copyOf(floatCount))
            }
        }

        private fun valuesMatch(previous: FloatArray, current: FloatArray, count: Int): Boolean {
            for (index in 0 until count) {
                if (previous[index].toRawBits() != current[index].toRawBits()) return false
            }
            return true
        }
    }

    private val batchUniformState = BatchUniformState()

    /** 开始一个 GPU system dispatch 批次；必须在渲染线程调用。 */
    internal fun beginBatch() {
        check(!batchActive) { "CParticle GPU dispatch batch is already active" }
        batchActive = true
        batchPreviousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        batchBoundProgram = 0
        batchValidatedPrograms.clear()
        batchBoundCollisionGrid = null
        batchPathLayerBound = false
        batchUniformState.clear()
        batchPreviousStorageBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        for (binding in batchPreviousIndexedBindings.indices) {
            // 路径图层 binding=4 由 CParticlePathLibrary 负责跨阶段保持，不在这里恢复。
            if (binding == PATH_LAYER_BUFFER_BINDING) continue
            batchPreviousIndexedBindings[binding] = GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                binding,
            )
        }
    }

    /** 结束 GPU system dispatch 批次并恢复批次开始前的 GL 状态。 */
    internal fun endBatch() {
        if (!batchActive) return
        try {
            for (binding in batchPreviousIndexedBindings.indices) {
                if (binding == PATH_LAYER_BUFFER_BINDING) continue
                GL43.glBindBufferBase(
                    GL43.GL_SHADER_STORAGE_BUFFER,
                    binding,
                    batchPreviousIndexedBindings[binding],
                )
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, batchPreviousStorageBuffer)
            glUseProgram(batchPreviousProgram)
        } finally {
            batchActive = false
            batchPreviousProgram = 0
            batchBoundProgram = 0
            batchPreviousStorageBuffer = 0
            batchPreviousIndexedBindings.fill(0)
            batchValidatedPrograms.clear()
            batchBoundCollisionGrid = null
            batchPathLayerBound = false
            batchUniformState.clear()
        }
    }

    /** 让当前批次绑定正确的 compute program；同一 program 不重复调用 glUseProgram。 */
    private fun bindBatchProgram(compute: CooComputeShaderProgram) {
        check(batchActive) { "CParticle GPU batch is not active" }
        if (batchBoundProgram == compute.program) return
        glUseProgram(compute.program)
        batchBoundProgram = compute.program
        batchUniformState.clear()
    }

    private fun CooComputeShaderProgram.setBatchInt(key: String, value: Int) {
        if (batchActive) batchUniformState.setInt(this, key, value) else setInt(key, value)
    }

    private fun CooComputeShaderProgram.setBatchFloat(key: String, value: Float) {
        if (batchActive) batchUniformState.setFloat(this, key, value) else setFloat(key, value)
    }

    private fun CooComputeShaderProgram.setBatchFloat3(key: String, value: Vector3f) {
        if (batchActive) batchUniformState.setFloat3(this, key, value) else setFloat3(key, value)
    }

    private fun CooComputeShaderProgram.setBatchFloat4Array(
        key: String,
        value: FloatArray,
        floatCount: Int,
    ) {
        if (batchActive) {
            batchUniformState.setFloat4Array(this, key, value, floatCount)
        } else if (floatCount > 0) {
            setFloat4Array(key, value.copyOf(floatCount))
        }
    }

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
        deferMemoryBarrier: Boolean = false,
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
        // glIsProgram 是同步的驱动查询。批次内同一 program 会被数百个 system 复用，
        // 只能在首次遇到该句柄时验证一次；旧写法会在首次之后对每个 system 重复查询，
        // 粒子计算本身很少时反而会把 CPU 卡在这里。
        val validProgram = compute.program != 0 && if (batchActive) {
            if (batchValidatedPrograms.add(compute.program)) glIsProgram(compute.program) else true
        } else {
            glIsProgram(compute.program)
        }
        check(compute.program != 0 && validProgram) {
            "[cparticle] ${if (useLegacy) "legacy Force" else "Force Command"} GPU compute program 无效，拒绝回退 CPU"
        }

        val textureBindings = if (useLegacy) emptyList() else system.forceResourceTable.textureBindings()
        val fluidBindings = if (useLegacy) emptyList() else system.forceResourceTable.fluidBindings()
        val usesPathConstraint = !useLegacy && commandUsesPath(commandPacked, commandCount)
        var dispatched = false
        val dispatchBody: CooComputeShaderProgram.() -> Unit = {
            setBatchInt("uFirstSlot", store.firstAliveSlot)
            setBatchInt("uCount", activeSlotCount)
            if (useLegacy) {
                setBatchInt("uForceCount", legacyForceCount)
                // 只上传有效力，避免每个小 system 都重复传输固定的 64 个 vec4。
                setBatchFloat4Array(
                    "uForces",
                    legacyPacked,
                    legacyForceCount * CParticleForce.STRIDE,
                )
            } else {
                setBatchInt("uCommandCount", commandCount)
                setBatchInt("uMetadataEnabled", if (metadataRequired) 1 else 0)
                setBatchInt("uPathLayerEnabled", if (usesPathConstraint) 1 else 0)
                setBatchInt("uPathEndCapacity", system.pathEndBuffer.listedCapacity)
                setBatchFloat("uDeltaTicks", CParticlePathEvaluator.DEFAULT_DELTA_TICKS.toFloat())
            }
            setBatchFloat("uSpeedLimit", system.speedLimit)
            setBatchFloat3(
                "uOrigin", tmpOrigin.set(
                    system.origin.x.toFloat(),
                    system.origin.y.toFloat(),
                    system.origin.z.toFloat(),
                )
            )
            setBatchInt("uTransformSimulation", if (simulationTransform == null) 0 else 1)
            if (simulationTransform != null && inverseSimulationTransform != null) {
                setMatrix4("uSimulationTransform", simulationTransform)
                setMatrix4("uInverseSimulationTransform", inverseSimulationTransform)
            }
            setBatchInt("uCollisionEnabled", if (collisionGrid != null) 1 else 0)
            setBatchInt("uCollisionSize", collisionGrid?.size ?: CParticleBlockCollisionGrid.SIZE)
            if (collisionGrid != null) {
                setBatchFloat3(
                    "uCollisionOffset",
                    tmpCollisionOffset.set(
                        (system.origin.x - collisionGrid.minX).toFloat(),
                        (system.origin.y - collisionGrid.minY).toFloat(),
                        (system.origin.z - collisionGrid.minZ).toFloat(),
                    )
                )
            } else {
                setBatchFloat3("uCollisionOffset", tmpCollisionOffset.zero())
            }
            val previousStorageBuffer = if (batchActive) 0 else
                GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
            val previousParticleBinding = if (batchActive) 0 else GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                PARTICLE_BUFFER_BINDING,
            )
            val previousCollisionBinding = if (batchActive || collisionGrid == null) 0 else GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                COLLISION_BUFFER_BINDING,
            )
            val previousMetadataBinding = if (batchActive || useLegacy || !metadataRequired) 0 else GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                METADATA_BUFFER_BINDING,
            )
            val previousCommandBinding = if (batchActive || useLegacy || commandCount <= 0) 0 else GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                COMMAND_BUFFER_BINDING,
            )
            val previousPathEndBinding = if (batchActive || useLegacy || !usesPathConstraint) 0 else GL30.glGetIntegeri(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                PATH_END_BUFFER_BINDING,
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
                if (collisionGrid != null &&
                    (!batchActive || batchBoundCollisionGrid !== collisionGrid)
                ) {
                    collisionGrid.bindShaderStorage(COLLISION_BUFFER_BINDING)
                    if (batchActive) batchBoundCollisionGrid = collisionGrid
                }
                if (!useLegacy && metadataRequired) {
                    system.metadataGlBuffer.bindShaderStorage(METADATA_BUFFER_BINDING)
                }
                if (!useLegacy && commandCount > 0) {
                    system.commandGlBuffer.bindShaderStorage(COMMAND_BUFFER_BINDING)
                }
                // 没有路径约束时不需要绑定共享路径图层；多 emitter 场景避免无效 bind。
                if (usesPathConstraint && (!batchActive || !batchPathLayerBound)) {
                    CParticlePathLibrary.bind()
                    if (batchActive) batchPathLayerBound = true
                }
                if (!useLegacy && usesPathConstraint) {
                    system.pathEndBuffer.bindShaderStorage(PATH_END_BUFFER_BINDING)
                }
                CParticlePerfProbe.measure(CParticlePerfProbe.Stage.GPU_DISPATCH_SUBMIT) {
                    GL43.glDispatchCompute((activeSlotCount + 255) / 256, 1, 1)
                }
                CParticlePerfProbe.count(CParticlePerfProbe.Stage.GPU_DISPATCH_COUNT)
                CParticlePerfProbe.count(
                    CParticlePerfProbe.Stage.GPU_DISPATCH_PARTICLES,
                    activeSlotCount.toLong(),
                )
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
                    if (!batchActive) {
                        GL43.glBindBufferBase(
                            GL43.GL_SHADER_STORAGE_BUFFER,
                            PARTICLE_BUFFER_BINDING,
                            previousParticleBinding,
                        )
                        if (!useLegacy && metadataRequired) {
                            GL43.glBindBufferBase(
                                GL43.GL_SHADER_STORAGE_BUFFER,
                                METADATA_BUFFER_BINDING,
                                previousMetadataBinding,
                            )
                        }
                        if (!useLegacy && commandCount > 0) {
                            GL43.glBindBufferBase(
                                GL43.GL_SHADER_STORAGE_BUFFER,
                                COMMAND_BUFFER_BINDING,
                                previousCommandBinding,
                            )
                        }
                        if (collisionGrid != null) {
                            GL43.glBindBufferBase(
                                GL43.GL_SHADER_STORAGE_BUFFER,
                                COLLISION_BUFFER_BINDING,
                                previousCollisionBinding,
                            )
                        }
                        if (!useLegacy && usesPathConstraint) {
                            GL43.glBindBufferBase(
                                GL43.GL_SHADER_STORAGE_BUFFER,
                                PATH_END_BUFFER_BINDING,
                                previousPathEndBinding,
                            )
                        }
                        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousStorageBuffer)
                    }
                }
            }
        }
        try {
            if (batchActive) {
                bindBatchProgram(compute)
                compute.dispatchBody()
            } else {
                compute.useOnContext(dispatchBody)
            }
        } finally {
            if (dispatched && !deferMemoryBarrier) memoryBarrier()
        }
        check(dispatched) {
            "[cparticle] Force Command GPU compute 未执行 dispatch，拒绝回退 CPU"
        }
        return true
    }

    /**
     * 发布本 tick 所有 compute dispatch 的 SSBO 写入。
     *
     * 多 emitter 场景会在同一 tick 连续 dispatch 大量小 system。单个 system
     * 立即 barrier 会把驱动同步成本放大为 system 数量；Manager 在整批 dispatch
     * 完成后调用一次即可满足后续渲染和 respawn compute 的依赖。
     */
    internal fun publishMemoryBarrier() {
        memoryBarrier()
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
     * 判断打包结果里是否包含路径位置约束命令。
     *
     * 本方法只在 CPU 侧扫描一次命令类型，用来决定是否需要绑定路径结束通道；
     * 每 tick 一次、命令数上限 128，成本与粒子数无关。
     *
     * @param commands 已打包的命令数组
     * @param commandCount 有效命令数量
     * @return 包含至少一条路径约束时返回 `true`
     */
    private fun commandUsesPath(commands: FloatArray, commandCount: Int): Boolean {
        for (index in 0 until commandCount) {
            val type = commands[index * ForceCommand.STRIDE].toRawBits()
            if (type == CooPathCommandAbi.TYPE_PATH_CONSTRAINT) return true
        }
        return false
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
