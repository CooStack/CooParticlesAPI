package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatcher
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshEmitterTransformHistory
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshInstanceBatch
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.simulation.CooFxMeshParticleSimulator
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshParticleStore
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.floor

/**
 * 管理全部网格粒子实例、发射器状态、CPU tick 与稳定批次构建的单一 controller。
 *
 * 该类型不创建 RenderEntity，也不访问世界碰撞、实体查询、系统时间或随机单例。
 */
class CooFxMeshParticleManager(
    capacity: Int,
    private val simulator: CooFxMeshParticleSimulator = CooFxMeshParticleSimulator(),
    private val batcher: CooFxMeshBatcher = CooFxMeshBatcher(),
) {
    val store = CooFxMeshParticleStore(capacity)

    private val emitters = LinkedHashMap<Long, EmitterState>()
    private var nextEmitterRuntimeId = 0L
    private var nextStableParticleId = 0L

    val particleCount: Int
        get() = store.size

    /** 注册发射器，并从 asset/request seed 派生独立 emitter seed。 */
    fun startEmitter(
        definition: CooFxMeshEmitterDefinition,
        transform: CooFxMeshEmitterTransform,
        assetSeed: Long,
        requestSeed: Long,
    ): Long {
        val runtimeId = nextEmitterRuntimeId++
        val effectSeed = mix64(assetSeed xor requestSeed)
        val emitterSeed = mix64(effectSeed xor fnv1a64(definition.emitterId))
        val copiedTransform = transform.copyValue()
        emitters[runtimeId] = EmitterState(
            definition = definition,
            emitterSeed = emitterSeed,
            previousTransform = copiedTransform,
            currentTransform = copiedTransform,
        )
        return runtimeId
    }

    /** 更新发射器变换；previous/current 会在下一批实例中同时保留。 */
    fun updateEmitterTransform(runtimeId: Long, transform: CooFxMeshEmitterTransform) {
        val state = requireNotNull(emitters[runtimeId]) { "Unknown emitter runtime id" }
        state.previousTransform = state.currentTransform
        state.currentTransform = transform.copyValue()
    }

    /** 停止发射器；可选择同时移除它已经生成的粒子。 */
    fun stopEmitter(runtimeId: Long, removeParticles: Boolean = false): Boolean {
        val state = emitters[runtimeId] ?: return false
        state.stopped = true
        if (removeParticles) {
            store.removeEmitterParticles(runtimeId)
            emitters.remove(runtimeId)
        }
        return true
    }

    /** 先推进现有粒子，再按稳定的发射器注册顺序处理当前 tick 的生成计划。 */
    fun tick() {
        simulator.tick(store)
        emitters.forEach { (runtimeId, state) ->
            if (!state.stopped) emitScheduled(runtimeId, state)
            state.ageTicks++
        }
    }

    /** 为渲染阶段构建按 batch key 和 stable id 排序的连续实例批次。 */
    fun buildBatches(): List<CooFxMeshInstanceBatch> = batcher.build(store) { runtimeId ->
        emitters[runtimeId]?.let { state ->
            CooFxMeshEmitterTransformHistory(state.previousTransform, state.currentTransform)
        }
    }

    /** 清空所有发射器与粒子实例，不改变已分配容量。 */
    fun clear() {
        emitters.clear()
        store.clear()
    }

    private fun emitScheduled(runtimeId: Long, state: EmitterState) {
        val definition = state.definition
        when (definition.emissionMode) {
            CooFxMeshEmissionMode.BURST -> {
                if (!state.burstEmitted && state.ageTicks >= definition.delayTicks) {
                    repeat(definition.emissionCount) { emitOne(runtimeId, state) }
                    state.burstEmitted = true
                }
            }

            CooFxMeshEmissionMode.CONTINUOUS -> {
                val activeTick = state.ageTicks - definition.delayTicks
                if (activeTick in 0 until definition.durationTicks) {
                    state.emissionAccumulator += definition.particlesPerTick
                    val count = floor(state.emissionAccumulator).toInt()
                    state.emissionAccumulator -= count
                    repeat(count) { emitOne(runtimeId, state) }
                }
            }
        }
    }

    private fun emitOne(runtimeId: Long, state: EmitterState) {
        val definition = state.definition
        val ordinal = state.emissionOrdinal++
        val particleSeed = mix64(state.emitterSeed xor ordinal)
        val variant = selectVariant(definition, particleSeed)
        val localPosition = sample(definition.position, particleSeed, "position")
        val localVelocity = sample(definition.velocity, particleSeed, "velocity")
        val localAcceleration = sample(definition.acceleration, particleSeed, "acceleration")
        val rotationAngles = sample(definition.rotationRadians, particleSeed, "rotation")
        val localRotation = Quaternionf().rotationXYZ(rotationAngles.x, rotationAngles.y, rotationAngles.z)
        val localScale = sample(definition.scale, particleSeed, "scale")
        val transform = state.currentTransform
        val worldSpace = definition.simulationSpace == CooFxMeshSimulationSpace.WORLD
        val position = if (worldSpace) {
            val scaledPosition = localPosition.mul(transform.scale, Vector3f())
            transform.position + transform.rotation.transform(scaledPosition, Vector3f())
        } else {
            localPosition
        }
        val velocity = if (worldSpace) transform.rotation.transform(localVelocity, Vector3f()) else localVelocity
        val acceleration = if (worldSpace) {
            transform.rotation.transform(localAcceleration, Vector3f())
        } else {
            localAcceleration
        }
        val rotation = if (worldSpace) Quaternionf(transform.rotation).mul(localRotation).normalize() else localRotation
        val scale = if (worldSpace) localScale.mul(transform.scale, Vector3f()) else localScale
        val stableParticleId = nextStableParticleId++
        store.spawn(
            CooFxMeshParticleSpawn(
                stableParticleId = stableParticleId,
                particleSeed = particleSeed,
                emitterRuntimeId = runtimeId,
                simulationSpace = definition.simulationSpace,
                batchKey = variant.batchKey,
                position = position,
                velocity = velocity,
                acceleration = acceleration,
                rotation = rotation,
                angularVelocityRadians = sample(
                    definition.angularVelocityRadians,
                    particleSeed,
                    "angular_velocity",
                ),
                scale = scale,
                lifetimeTicks = sampleLifetime(definition.lifetimeTicks, particleSeed),
                color = sample(definition.color, particleSeed, "color"),
                packedLight = definition.packedLight,
                clipIndex = definition.clipIndex,
                playbackSpeed = definition.playbackSpeed,
                meshVariant = variant.meshVariant,
                materialVariant = variant.materialVariant,
                forces = definition.forces,
            )
        )
    }

    private fun selectVariant(definition: CooFxMeshEmitterDefinition, particleSeed: Long): CooFxMeshVariant =
        when (definition.selectionMode) {
            CooFxMeshSelectionMode.OBJECT -> definition.variants.first()
            CooFxMeshSelectionMode.COLLECTION -> {
                val index = sampleUnit(channelSeed(particleSeed, "variant")) * definition.variants.size
                definition.variants[index.toInt().coerceAtMost(definition.variants.lastIndex)]
            }
        }

    private fun sample(range: CooFxMeshVectorRange, particleSeed: Long, channel: String): Vector3f {
        val x = sampleUnit(channelSeed(particleSeed, "${channel}_x"))
        val y = sampleUnit(channelSeed(particleSeed, "${channel}_y"))
        val z = sampleUnit(channelSeed(particleSeed, "${channel}_z"))
        return Vector3f(
            range.minimum.x + (range.maximum.x - range.minimum.x) * x,
            range.minimum.y + (range.maximum.y - range.minimum.y) * y,
            range.minimum.z + (range.maximum.z - range.minimum.z) * z,
        )
    }

    private fun sample(range: CooFxMeshColorRange, particleSeed: Long, channel: String): Vector4f {
        val red = sampleUnit(channelSeed(particleSeed, "${channel}_r"))
        val green = sampleUnit(channelSeed(particleSeed, "${channel}_g"))
        val blue = sampleUnit(channelSeed(particleSeed, "${channel}_b"))
        val alpha = sampleUnit(channelSeed(particleSeed, "${channel}_a"))
        return Vector4f(
            range.minimum.x + (range.maximum.x - range.minimum.x) * red,
            range.minimum.y + (range.maximum.y - range.minimum.y) * green,
            range.minimum.z + (range.maximum.z - range.minimum.z) * blue,
            range.minimum.w + (range.maximum.w - range.minimum.w) * alpha,
        )
    }

    private fun sampleLifetime(range: IntRange, particleSeed: Long): Int {
        val width = range.last.toLong() - range.first.toLong() + 1L
        val offset = floor(sampleUnit(channelSeed(particleSeed, "lifetime")) * width).toLong()
        return (range.first.toLong() + offset.coerceAtMost(width - 1L)).toInt()
    }

    private fun channelSeed(particleSeed: Long, channel: String): Long = mix64(particleSeed xor fnv1a64(channel))

    private fun sampleUnit(value: Long): Float = ((value ushr 40) and 0xFFFFFFL).toFloat() / 16777216F

    private fun mix64(input: Long): Long {
        var value = input + 0x9E3779B97F4A7C15uL.toLong()
        value = (value xor (value ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        value = (value xor (value ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return value xor (value ushr 31)
    }

    private fun fnv1a64(value: String): Long {
        var hash = 0xCBF29CE484222325uL.toLong()
        value.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and 0xFFL)
            hash *= 0x100000001B3L
        }
        return hash
    }

    private data class EmitterState(
        val definition: CooFxMeshEmitterDefinition,
        val emitterSeed: Long,
        var previousTransform: CooFxMeshEmitterTransform,
        var currentTransform: CooFxMeshEmitterTransform,
        var ageTicks: Int = 0,
        var emissionOrdinal: Long = 0L,
        var emissionAccumulator: Float = 0F,
        var burstEmitted: Boolean = false,
        var stopped: Boolean = false,
    )
}
