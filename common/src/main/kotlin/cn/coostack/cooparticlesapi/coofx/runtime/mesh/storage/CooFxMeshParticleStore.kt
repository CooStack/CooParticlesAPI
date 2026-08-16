package cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshParticleSnapshot
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshParticleSpawn
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshSimulationSpace
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.isFiniteQuaternion
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.isFiniteVector
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatchKey
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * 所有网格粒子的独立 dense SoA 存储。
 *
 * 活跃槽位始终位于 `[0, size)`；删除使用末尾元素 swap-remove。stable particle id、完整 seed
 * 和 emission ordinal 派生结果都随槽位复制，不依赖槽位位置或压缩次序。
 */
class CooFxMeshParticleStore(val capacity: Int) {
    init {
        require(capacity > 0) { "Capacity must be positive" }
    }

    var size: Int = 0
        private set

    internal val stableParticleIds = LongArray(capacity)
    internal val particleSeeds = LongArray(capacity)
    internal val emitterRuntimeIds = LongArray(capacity)
    internal val simulationSpaces = ByteArray(capacity)
    internal val batchKeys = arrayOfNulls<CooFxMeshBatchKey>(capacity)

    internal val positions = FloatArray(capacity * 3)
    internal val previousPositions = FloatArray(capacity * 3)
    internal val velocities = FloatArray(capacity * 3)
    internal val accelerations = FloatArray(capacity * 3)
    internal val rotations = FloatArray(capacity * 4)
    internal val previousRotations = FloatArray(capacity * 4)
    internal val angularVelocities = FloatArray(capacity * 3)
    internal val scales = FloatArray(capacity * 3)
    internal val colors = FloatArray(capacity * 4)

    internal val ages = IntArray(capacity)
    internal val lifetimes = IntArray(capacity)
    internal val packedLights = IntArray(capacity)
    internal val clipIndices = IntArray(capacity)
    internal val clipTimes = FloatArray(capacity)
    internal val previousClipTimes = FloatArray(capacity)
    internal val playbackSpeeds = FloatArray(capacity)
    internal val meshVariants = IntArray(capacity)
    internal val materialVariants = IntArray(capacity)

    internal val gravities = FloatArray(capacity * 3)
    internal val winds = FloatArray(capacity * 3)
    internal val drags = FloatArray(capacity)
    internal val noiseAmplitudes = FloatArray(capacity * 3)
    internal val noiseFrequencyTicks = IntArray(capacity)

    /** 写入一个粒子并返回 dense 槽位；容量耗尽时返回 `-1`。 */
    fun spawn(particle: CooFxMeshParticleSpawn): Int {
        if (size >= capacity) return -1
        validateSpawn(particle)
        val index = size++
        stableParticleIds[index] = particle.stableParticleId
        particleSeeds[index] = particle.particleSeed
        emitterRuntimeIds[index] = particle.emitterRuntimeId
        simulationSpaces[index] = particle.simulationSpace.ordinal.toByte()
        batchKeys[index] = particle.batchKey
        setVector3(positions, index, particle.position)
        setVector3(previousPositions, index, particle.position)
        setVector3(velocities, index, particle.velocity)
        setVector3(accelerations, index, particle.acceleration)
        setQuaternion(rotations, index, Quaternionf(particle.rotation).normalize())
        setQuaternion(previousRotations, index, Quaternionf(particle.rotation).normalize())
        setVector3(angularVelocities, index, particle.angularVelocityRadians)
        setVector3(scales, index, particle.scale)
        setVector4(colors, index, particle.color)
        ages[index] = 0
        lifetimes[index] = particle.lifetimeTicks
        packedLights[index] = particle.packedLight
        clipIndices[index] = particle.clipIndex
        clipTimes[index] = 0F
        previousClipTimes[index] = 0F
        playbackSpeeds[index] = particle.playbackSpeed
        meshVariants[index] = particle.meshVariant
        materialVariants[index] = particle.materialVariant
        setVector3(gravities, index, particle.forces.gravity)
        setVector3(winds, index, particle.forces.wind)
        drags[index] = particle.forces.drag
        setVector3(noiseAmplitudes, index, particle.forces.noiseAmplitude)
        noiseFrequencyTicks[index] = particle.forces.noiseFrequencyTicks
        return index
    }

    /** 删除指定 dense 槽位，并返回被交换到该槽位的 stable id；没有交换时返回 `null`。 */
    fun removeAt(index: Int): Long? {
        require(index in 0 until size) { "Particle index is out of range" }
        val last = size - 1
        val movedStableId = if (index != last) stableParticleIds[last] else null
        if (index != last) copySlot(last, index)
        clearSlot(last)
        size = last
        return movedStableId
    }

    /** 删除属于指定发射器运行实例的全部粒子。 */
    fun removeEmitterParticles(emitterRuntimeId: Long): Int {
        var removed = 0
        var index = size - 1
        while (index >= 0) {
            if (emitterRuntimeIds[index] == emitterRuntimeId) {
                removeAt(index)
                removed++
            }
            index--
        }
        return removed
    }

    /** 清空全部实例，但保留已分配的数组容量。 */
    fun clear() {
        batchKeys.fill(null, 0, size)
        size = 0
    }

    /** 按 stable particle id 排序生成与 dense 压缩顺序无关的快照。 */
    fun stableSnapshot(): List<CooFxMeshParticleSnapshot> = (0 until size)
        .sortedBy { stableParticleIds[it] }
        .map { index ->
            CooFxMeshParticleSnapshot(
                stableParticleId = stableParticleIds[index],
                particleSeed = particleSeeds[index],
                position = vector3(positions, index),
                previousPosition = vector3(previousPositions, index),
                velocity = vector3(velocities, index),
                rotation = quaternion(rotations, index),
                previousRotation = quaternion(previousRotations, index),
                scale = vector3(scales, index),
                ageTicks = ages[index],
                lifetimeTicks = lifetimes[index],
                color = vector4(colors, index),
                clipTimeSeconds = clipTimes[index],
                previousClipTimeSeconds = previousClipTimes[index],
                meshVariant = meshVariants[index],
                materialVariant = materialVariants[index],
            )
        }

    internal fun simulationSpace(index: Int): CooFxMeshSimulationSpace =
        CooFxMeshSimulationSpace.entries[simulationSpaces[index].toInt()]

    internal fun batchKey(index: Int): CooFxMeshBatchKey = checkNotNull(batchKeys[index])

    internal fun vector3(source: FloatArray, index: Int): Vector3f {
        val base = index * 3
        return Vector3f(source[base], source[base + 1], source[base + 2])
    }

    internal fun quaternion(source: FloatArray, index: Int): Quaternionf {
        val base = index * 4
        return Quaternionf(source[base], source[base + 1], source[base + 2], source[base + 3])
    }

    internal fun vector4(source: FloatArray, index: Int): Vector4f {
        val base = index * 4
        return Vector4f(source[base], source[base + 1], source[base + 2], source[base + 3])
    }

    internal fun setVector3(target: FloatArray, index: Int, value: Vector3f) {
        val base = index * 3
        target[base] = value.x
        target[base + 1] = value.y
        target[base + 2] = value.z
    }

    internal fun setQuaternion(target: FloatArray, index: Int, value: Quaternionf) {
        val base = index * 4
        target[base] = value.x
        target[base + 1] = value.y
        target[base + 2] = value.z
        target[base + 3] = value.w
    }

    private fun setVector4(target: FloatArray, index: Int, value: Vector4f) {
        val base = index * 4
        target[base] = value.x
        target[base + 1] = value.y
        target[base + 2] = value.z
        target[base + 3] = value.w
    }

    private fun validateSpawn(particle: CooFxMeshParticleSpawn) {
        require(particle.stableParticleId >= 0L) { "Stable particle id must be non-negative" }
        require(particle.position.isFiniteVector()) { "Particle position must be finite" }
        require(particle.velocity.isFiniteVector()) { "Particle velocity must be finite" }
        require(particle.acceleration.isFiniteVector()) { "Particle acceleration must be finite" }
        require(particle.rotation.isFiniteQuaternion()) { "Particle rotation must be finite" }
        require(particle.angularVelocityRadians.isFiniteVector()) { "Angular velocity must be finite" }
        require(
            particle.scale.isFiniteVector() && particle.scale.x >= 0F &&
                particle.scale.y >= 0F && particle.scale.z >= 0F
        ) { "Particle scale must be finite and non-negative" }
        require(particle.color.isFiniteVector()) { "Particle color must be finite" }
        require(particle.lifetimeTicks > 0) { "Particle lifetime must be positive" }
        require(particle.packedLight in 0..0xFFFFFF) { "Packed light must fit in 24 bits" }
        require(particle.clipIndex in 0..0xFFFFFF) { "Clip index must fit in 24 bits" }
        require(particle.playbackSpeed.isFinite()) { "Playback speed must be finite" }
        require(particle.meshVariant in 0..0xFFFFFF) { "Mesh variant must fit in 24 bits" }
        require(particle.materialVariant in 0..0xFFFFFF) { "Material variant must fit in 24 bits" }
    }

    private fun copySlot(source: Int, target: Int) {
        stableParticleIds[target] = stableParticleIds[source]
        particleSeeds[target] = particleSeeds[source]
        emitterRuntimeIds[target] = emitterRuntimeIds[source]
        simulationSpaces[target] = simulationSpaces[source]
        batchKeys[target] = batchKeys[source]
        copyComponents(positions, source, target, 3)
        copyComponents(previousPositions, source, target, 3)
        copyComponents(velocities, source, target, 3)
        copyComponents(accelerations, source, target, 3)
        copyComponents(rotations, source, target, 4)
        copyComponents(previousRotations, source, target, 4)
        copyComponents(angularVelocities, source, target, 3)
        copyComponents(scales, source, target, 3)
        copyComponents(colors, source, target, 4)
        ages[target] = ages[source]
        lifetimes[target] = lifetimes[source]
        packedLights[target] = packedLights[source]
        clipIndices[target] = clipIndices[source]
        clipTimes[target] = clipTimes[source]
        previousClipTimes[target] = previousClipTimes[source]
        playbackSpeeds[target] = playbackSpeeds[source]
        meshVariants[target] = meshVariants[source]
        materialVariants[target] = materialVariants[source]
        copyComponents(gravities, source, target, 3)
        copyComponents(winds, source, target, 3)
        drags[target] = drags[source]
        copyComponents(noiseAmplitudes, source, target, 3)
        noiseFrequencyTicks[target] = noiseFrequencyTicks[source]
    }

    private fun clearSlot(index: Int) {
        batchKeys[index] = null
    }

    private fun copyComponents(array: FloatArray, source: Int, target: Int, width: Int) {
        val sourceBase = source * width
        val targetBase = target * width
        array.copyInto(array, targetBase, sourceBase, sourceBase + width)
    }
}
