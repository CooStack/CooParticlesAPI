package cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshEmitterTransform
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshSimulationSpace
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import org.joml.Quaternionf
import org.joml.Vector3f

/** 单个实例属性的 GL3.3 描述，offset 与 stride 均以字节计。 */
data class CooFxMeshInstanceAttribute(
    val location: Int,
    val componentCount: Int,
    val byteOffset: Int,
    val byteStride: Int,
    val divisor: Int,
)

/**
 * CooFX 网格实例 ABI v1 的唯一布局描述。
 *
 * 该 ABI 固定为 9 个 vec4、36 个 float、144 字节，与 CParticleStore 没有类型或 offset 共享。
 * 属性 location 由调用方提供起点，offset、stride 和 divisor 只从本描述生成。
 */
object CooFxMeshInstanceLayout {
    const val VERSION = 1
    const val VECTOR_COUNT = 9
    const val FLOATS_PER_VECTOR = 4
    const val FLOAT_COUNT = VECTOR_COUNT * FLOATS_PER_VECTOR
    const val BYTE_STRIDE = FLOAT_COUNT * Float.SIZE_BYTES

    const val CURRENT_POSITION = 0
    const val PREVIOUS_POSITION = 4
    const val CURRENT_ROTATION = 8
    const val PREVIOUS_ROTATION = 12
    const val CURRENT_SCALE = 16
    const val PREVIOUS_SCALE = 20
    const val COLOR = 24
    const val CLIP = 28
    const val IDENTITY = 32

    /** 从第一个 location 生成 9 个 divisor=1 的 vec4 属性描述。 */
    fun attributes(firstLocation: Int): List<CooFxMeshInstanceAttribute> {
        require(firstLocation >= 0) { "First attribute location must be non-negative" }
        return List(VECTOR_COUNT) { vectorIndex ->
            CooFxMeshInstanceAttribute(
                location = firstLocation + vectorIndex,
                componentCount = FLOATS_PER_VECTOR,
                byteOffset = vectorIndex * FLOATS_PER_VECTOR * Float.SIZE_BYTES,
                byteStride = BYTE_STRIDE,
                divisor = 1,
            )
        }
    }

    /** 把一个 dense 槽位编码到连续实例数组。 */
    internal fun write(
        store: CooFxMeshParticleStore,
        index: Int,
        target: FloatArray,
        targetFloatOffset: Int,
        currentEmitterTransform: CooFxMeshEmitterTransform?,
        previousEmitterTransform: CooFxMeshEmitterTransform?,
    ) {
        require(targetFloatOffset >= 0 && targetFloatOffset + FLOAT_COUNT <= target.size) {
            "Instance target range is out of bounds"
        }
        val currentPosition = worldPosition(store, index, store.positions, currentEmitterTransform)
        val previousPosition = worldPosition(store, index, store.previousPositions, previousEmitterTransform)
        val currentRotation = worldRotation(store, index, store.rotations, currentEmitterTransform)
        val previousRotation = worldRotation(store, index, store.previousRotations, previousEmitterTransform)
        val currentScale = worldScale(store, index, currentEmitterTransform)
        val previousScale = worldScale(store, index, previousEmitterTransform)
        val color = store.vector4(store.colors, index)
        val seed = store.particleSeeds[index]
        val flags = if (store.simulationSpace(index) == CooFxMeshSimulationSpace.LOCAL) 1 else 0

        putVector3(target, targetFloatOffset + CURRENT_POSITION, currentPosition)
        target[targetFloatOffset + CURRENT_POSITION + 3] = store.ages[index].toFloat()
        putVector3(target, targetFloatOffset + PREVIOUS_POSITION, previousPosition)
        target[targetFloatOffset + PREVIOUS_POSITION + 3] = store.lifetimes[index].toFloat()
        putQuaternion(target, targetFloatOffset + CURRENT_ROTATION, currentRotation)
        putQuaternion(target, targetFloatOffset + PREVIOUS_ROTATION, previousRotation)
        putVector3(target, targetFloatOffset + CURRENT_SCALE, currentScale)
        target[targetFloatOffset + CURRENT_SCALE + 3] = store.packedLights[index].toFloat()
        putVector3(target, targetFloatOffset + PREVIOUS_SCALE, previousScale)
        target[targetFloatOffset + PREVIOUS_SCALE + 3] = store.materialVariants[index].toFloat()
        target[targetFloatOffset + COLOR] = color.x
        target[targetFloatOffset + COLOR + 1] = color.y
        target[targetFloatOffset + COLOR + 2] = color.z
        target[targetFloatOffset + COLOR + 3] = color.w
        target[targetFloatOffset + CLIP] = store.clipTimes[index]
        target[targetFloatOffset + CLIP + 1] = store.previousClipTimes[index]
        target[targetFloatOffset + CLIP + 2] = store.playbackSpeeds[index]
        target[targetFloatOffset + CLIP + 3] = store.clipIndices[index].toFloat()
        target[targetFloatOffset + IDENTITY] = (seed and 0xFFFFL).toFloat()
        target[targetFloatOffset + IDENTITY + 1] = ((seed ushr 16) and 0xFFFFL).toFloat()
        target[targetFloatOffset + IDENTITY + 2] = flags.toFloat()
        target[targetFloatOffset + IDENTITY + 3] = (store.stableParticleIds[index] and 0xFFFFFFL).toFloat()
    }

    private fun worldPosition(
        store: CooFxMeshParticleStore,
        index: Int,
        source: FloatArray,
        transform: CooFxMeshEmitterTransform?,
    ): Vector3f {
        val position = store.vector3(source, index)
        if (store.simulationSpace(index) != CooFxMeshSimulationSpace.LOCAL || transform == null) return position
        val scaled = position.mul(transform.scale, Vector3f())
        return transform.position + transform.rotation.transform(scaled, Vector3f())
    }

    private fun worldRotation(
        store: CooFxMeshParticleStore,
        index: Int,
        source: FloatArray,
        transform: CooFxMeshEmitterTransform?,
    ): Quaternionf {
        val rotation = store.quaternion(source, index)
        if (store.simulationSpace(index) != CooFxMeshSimulationSpace.LOCAL || transform == null) return rotation
        return Quaternionf(transform.rotation).mul(rotation).normalize()
    }

    private fun worldScale(
        store: CooFxMeshParticleStore,
        index: Int,
        transform: CooFxMeshEmitterTransform?,
    ): Vector3f {
        val scale = store.vector3(store.scales, index)
        if (store.simulationSpace(index) != CooFxMeshSimulationSpace.LOCAL || transform == null) return scale
        return scale.mul(transform.scale, Vector3f())
    }

    private fun putVector3(target: FloatArray, offset: Int, value: Vector3f) {
        target[offset] = value.x
        target[offset + 1] = value.y
        target[offset + 2] = value.z
    }

    private fun putQuaternion(target: FloatArray, offset: Int, value: Quaternionf) {
        target[offset] = value.x
        target[offset + 1] = value.y
        target[offset + 2] = value.z
        target[offset + 3] = value.w
    }
}
