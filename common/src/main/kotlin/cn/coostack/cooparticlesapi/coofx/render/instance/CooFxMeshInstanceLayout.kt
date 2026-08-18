package cn.coostack.cooparticlesapi.coofx.render.instance

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshEmitterTransform
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.CooFxMeshSimulationSpace
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshParticleStore
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import kotlin.math.sqrt
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * CooFX 实例 ABI 中九个 vec4 槽位的语义。
 *
 * [CURRENT_POSITION_AGE] 保存当前位置和年龄；[PREVIOUS_POSITION_LIFETIME] 保存上一 tick 位置和寿命；
 * [CURRENT_ROTATION] 与 [PREVIOUS_ROTATION] 保存归一化四元数；[CURRENT_SCALE_LIGHT] 保存当前缩放和
 * packed light；[PREVIOUS_SCALE_MATERIAL] 保存上一 tick 缩放和材质变体；[COLOR] 保存线性 RGBA；
 * [CLIP_PLAYBACK] 保存当前/上一 clip 时间、播放速度和 clip 索引；[SEED_FLAGS_ID] 保存 seed 的两个
 * 16 位片段、flags 和稳定粒子 ID 的低 24 位。槽位顺序属于 v1 稳定格式，不能引用 CParticle ABI。
 */
enum class CooFxInstanceSlot {
    /** 当前世界位置 xyz 与 ageTicks。 */
    CURRENT_POSITION_AGE,

    /** 上一 tick 世界位置 xyz 与 lifetimeTicks。 */
    PREVIOUS_POSITION_LIFETIME,

    /** 当前归一化旋转四元数 xyzw。 */
    CURRENT_ROTATION,

    /** 上一 tick 归一化旋转四元数 xyzw。 */
    PREVIOUS_ROTATION,

    /** 当前缩放 xyz 与 packed light 精确整数。 */
    CURRENT_SCALE_LIGHT,

    /** 上一 tick 缩放 xyz 与 material variant 精确整数。 */
    PREVIOUS_SCALE_MATERIAL,

    /** 线性颜色 rgba。 */
    COLOR,

    /** 当前/上一 clip 时间、播放速度和 clip 索引。 */
    CLIP_PLAYBACK,

    /** Seed 低/高 16 位、flags 和稳定粒子 ID 低 24 位。 */
    SEED_FLAGS_ID
}

data class CooFxInstanceAttribute(
    val slot: CooFxInstanceSlot,
    val shaderLocation: Int,
    val byteOffset: Int,
    val divisor: Int
)

data class CooFxFloat3(val x: Float, val y: Float, val z: Float) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Vector components must be finite" }
    }
}

data class CooFxQuaternion(val x: Float, val y: Float, val z: Float, val w: Float) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()) {
            "Quaternion components must be finite"
        }
        val length = sqrt(x * x + y * y + z * z + w * w)
        require(length in 0.9999F..1.0001F) { "Quaternion must be normalized" }
    }
}

data class CooFxColor(val red: Float, val green: Float, val blue: Float, val alpha: Float) {
    init {
        require(red.isFinite() && green.isFinite() && blue.isFinite() && alpha.isFinite()) {
            "Color components must be finite"
        }
    }
}

data class CooFxMeshInstanceData(
    val currentPosition: CooFxFloat3,
    val ageTicks: Float,
    val previousPosition: CooFxFloat3,
    val lifetimeTicks: Float,
    val currentRotation: CooFxQuaternion,
    val previousRotation: CooFxQuaternion,
    val currentScale: CooFxFloat3,
    val packedLight: Int,
    val previousScale: CooFxFloat3,
    val materialVariant: Int,
    val color: CooFxColor,
    val clipTimeSeconds: Float,
    val previousClipTimeSeconds: Float,
    val playbackSpeed: Float,
    val clipIndex: Int,
    val visibleSeed: UInt,
    val flags: Int,
    val stableParticleIdLow24: Int
) {
    init {
        require(ageTicks.isFinite() && ageTicks >= 0.0F) { "Age must be finite and non-negative" }
        require(lifetimeTicks.isFinite() && lifetimeTicks >= 0.0F) { "Lifetime must be finite and non-negative" }
        require(currentScale.x >= 0.0F && currentScale.y >= 0.0F && currentScale.z >= 0.0F) {
            "Current scale must not contain negative components"
        }
        require(previousScale.x >= 0.0F && previousScale.y >= 0.0F && previousScale.z >= 0.0F) {
            "Previous scale must not contain negative components"
        }
        require(packedLight in 0..0xFFFFFF) { "Packed light must fit an exact 24-bit float integer" }
        require(materialVariant in 0..0xFFFFFF) { "Material variant must fit an exact 24-bit float integer" }
        require(clipTimeSeconds.isFinite() && previousClipTimeSeconds.isFinite() && playbackSpeed.isFinite()) {
            "Clip playback values must be finite"
        }
        require(clipIndex in 0..0xFFFFFF) { "Clip index must fit an exact 24-bit float integer" }
        require(flags in 0..0xFFFFFF) { "Flags must fit an exact 24-bit float integer" }
        require(stableParticleIdLow24 in 0..0xFFFFFF) {
            "Stable particle id fragment must fit an exact 24-bit float integer"
        }
    }
}

/**
 * CooFX 网格实例布局 v1 的单一描述源。
 *
 * 该对象定义 9 个 vec4、36 个 float、144 字节的稳定 ABI，并从调用方给出的首个 shader location
 * 生成 attribute location、byte offset 与 divisor。所有常量属于跨 runtime、上传器和 shader 共享的
 * 稳定格式，不与字节数相同但语义不同的 CParticleStore 共享声明。
 */
object CooFxMeshInstanceLayout {
    const val VERSION = 1
    const val SLOT_COUNT = 9
    const val COMPONENTS_PER_SLOT = 4
    const val FLOAT_COUNT = SLOT_COUNT * COMPONENTS_PER_SLOT
    const val STRIDE_BYTES = FLOAT_COUNT * Float.SIZE_BYTES
    const val VECTOR_COUNT = SLOT_COUNT
    const val FLOATS_PER_VECTOR = COMPONENTS_PER_SLOT
    const val BYTE_STRIDE = STRIDE_BYTES
    const val CURRENT_POSITION = 0
    const val PREVIOUS_POSITION = 4
    const val CURRENT_ROTATION = 8
    const val PREVIOUS_ROTATION = 12
    const val CURRENT_SCALE = 16
    const val PREVIOUS_SCALE = 20
    const val COLOR_OFFSET = 24
    const val COLOR = COLOR_OFFSET
    const val CLIP = 28
    const val IDENTITY = 32

    fun attributes(firstShaderLocation: Int): List<CooFxInstanceAttribute> {
        require(firstShaderLocation >= 0) { "First shader location must not be negative" }
        return CooFxInstanceSlot.entries.mapIndexed { index, slot ->
            CooFxInstanceAttribute(
                slot = slot,
                shaderLocation = firstShaderLocation + index,
                byteOffset = index * COMPONENTS_PER_SLOT * Float.SIZE_BYTES,
                divisor = 1
            )
        }
    }

    fun encode(data: CooFxMeshInstanceData): FloatArray {
        val seedLow = (data.visibleSeed and 0xFFFFu).toFloat()
        val seedHigh = (data.visibleSeed shr 16).toFloat()
        return floatArrayOf(
            data.currentPosition.x, data.currentPosition.y, data.currentPosition.z, data.ageTicks,
            data.previousPosition.x, data.previousPosition.y, data.previousPosition.z, data.lifetimeTicks,
            data.currentRotation.x, data.currentRotation.y, data.currentRotation.z, data.currentRotation.w,
            data.previousRotation.x, data.previousRotation.y, data.previousRotation.z, data.previousRotation.w,
            data.currentScale.x, data.currentScale.y, data.currentScale.z, data.packedLight.toFloat(),
            data.previousScale.x, data.previousScale.y, data.previousScale.z, data.materialVariant.toFloat(),
            data.color.red, data.color.green, data.color.blue, data.color.alpha,
            data.clipTimeSeconds, data.previousClipTimeSeconds, data.playbackSpeed, data.clipIndex.toFloat(),
            seedLow, seedHigh, data.flags.toFloat(), data.stableParticleIdLow24.toFloat()
        )
    }

    internal fun write(
        store: CooFxMeshParticleStore,
        index: Int,
        target: FloatArray,
        targetFloatOffset: Int,
        currentEmitterTransform: CooFxMeshEmitterTransform?,
        previousEmitterTransform: CooFxMeshEmitterTransform?,
        packedLightResolver: ((Vector3f) -> Int)? = null,
    ) {
        require(targetFloatOffset >= 0 && targetFloatOffset + FLOAT_COUNT <= target.size) { "实例目标范围越界" }
        val currentPosition = worldPosition(store, index, store.positions, currentEmitterTransform)
        val previousPosition = worldPosition(store, index, store.previousPositions, previousEmitterTransform)
        val currentRotation = worldRotation(store, index, store.rotations, currentEmitterTransform)
        val previousRotation = worldRotation(store, index, store.previousRotations, previousEmitterTransform)
        val currentScale = worldScale(store, index, currentEmitterTransform)
        val previousScale = worldScale(store, index, previousEmitterTransform)
        val color = store.vector4(store.colors, index)
        val base = targetFloatOffset
        putVector3(target, base + CURRENT_POSITION, currentPosition)
        target[base + CURRENT_POSITION + 3] = store.ages[index].toFloat()
        putVector3(target, base + PREVIOUS_POSITION, previousPosition)
        target[base + PREVIOUS_POSITION + 3] = store.lifetimes[index].toFloat()
        putQuaternion(target, base + CURRENT_ROTATION, currentRotation)
        putQuaternion(target, base + PREVIOUS_ROTATION, previousRotation)
        putVector3(target, base + CURRENT_SCALE, currentScale)
        val packedLight = packedLightResolver?.invoke(currentPosition) ?: store.packedLights[index]
        require(packedLight in 0..0xFFFFFF) { "Resolved particle packed light must fit in 24 bits" }
        target[base + CURRENT_SCALE + 3] = packedLight.toFloat()
        putVector3(target, base + PREVIOUS_SCALE, previousScale)
        target[base + PREVIOUS_SCALE + 3] = store.materialVariants[index].toFloat()
        target[base + COLOR] = color.x
        target[base + COLOR + 1] = color.y
        target[base + COLOR + 2] = color.z
        target[base + COLOR + 3] = color.w
        target[base + CLIP] = store.clipTimes[index]
        target[base + CLIP + 1] = store.previousClipTimes[index]
        target[base + CLIP + 2] = store.playbackSpeeds[index]
        target[base + CLIP + 3] = store.clipIndices[index].toFloat()
        val seed = store.particleSeeds[index]
        val flags = if (store.simulationSpace(index) == CooFxMeshSimulationSpace.LOCAL) 1 else 0
        target[base + IDENTITY] = (seed and 0xFFFFL).toFloat()
        target[base + IDENTITY + 1] = ((seed ushr 16) and 0xFFFFL).toFloat()
        target[base + IDENTITY + 2] = flags.toFloat()
        target[base + IDENTITY + 3] = (store.stableParticleIds[index] and 0xFFFFFFL).toFloat()
    }

    private fun worldPosition(store: CooFxMeshParticleStore, index: Int, source: FloatArray, transform: CooFxMeshEmitterTransform?): Vector3f {
        val position = store.vector3(source, index)
        if (store.simulationSpace(index) != CooFxMeshSimulationSpace.LOCAL || transform == null) return position
        return transform.position + transform.rotation.transform(position.mul(transform.scale, Vector3f()), Vector3f())
    }

    private fun worldRotation(store: CooFxMeshParticleStore, index: Int, source: FloatArray, transform: CooFxMeshEmitterTransform?): Quaternionf {
        val rotation = store.quaternion(source, index)
        if (store.simulationSpace(index) != CooFxMeshSimulationSpace.LOCAL || transform == null) return rotation
        return Quaternionf(transform.rotation).mul(rotation).normalize()
    }

    private fun worldScale(store: CooFxMeshParticleStore, index: Int, transform: CooFxMeshEmitterTransform?): Vector3f {
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
