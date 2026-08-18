package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshBatchKey
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * 网格粒子的模拟空间，决定粒子是否持续跟随发射器变换。
 *
 * [WORLD] 在生成时把位置、速度和旋转转换到世界空间，之后不再跟随发射器。
 * [LOCAL] 保留局部模拟数据，批处理时使用发射器当前变换转换到世界空间。
 * 该枚举只用于运行时定义，不作为资产序列化值的隐式替代。
 */
enum class CooFxMeshSimulationSpace {
    /** 粒子生成后在世界空间独立模拟。 */
    WORLD,

    /** 粒子在发射器局部空间模拟，并持续跟随发射器变换。 */
    LOCAL,
}

/**
 * 网格粒子的发射调度方式。
 *
 * [BURST] 在延迟结束时一次生成 [CooFxMeshEmitterDefinition.emissionCount] 个粒子。
 * [CONTINUOUS] 在有效 tick 范围内按 [CooFxMeshEmitterDefinition.particlesPerTick] 累积生成。
 * 调度以整数 tick 推进，不读取真实时间；默认行为由定义显式指定。
 */
enum class CooFxMeshEmissionMode {
    /** 延迟结束后只执行一次的定量爆发。 */
    BURST,

    /** 在持续时间内按固定每 tick 速率连续发射。 */
    CONTINUOUS,
}

/**
 * 发射器引用 mesh/material 变体时的选择方式。
 *
 * [OBJECT] 固定使用列表中的第一个对象，适合只有一个 primitive 的 Blender object。
 * [COLLECTION] 使用粒子 seed 在有序列表中确定性选择一个对象，列表顺序属于资产契约。
 * [ALL] 为同一逻辑粒子生成列表中的全部 primitive 实例，保证多材质模型保持完整；这些实例共享
 * seed、初始变换和生命周期，但分别进入自己的 batch key。运行时不依赖集合迭代顺序，也不会在
 * 列表为空时静默降级。
 */
enum class CooFxMeshSelectionMode {
    /** 固定选择第一个变体。 */
    OBJECT,

    /** 按粒子 seed 在有序变体集合中选择一个变体。 */
    COLLECTION,

    /** 为同一逻辑粒子生成全部有序变体，适合一个模型包含多个 primitive/material。 */
    ALL,
}

/** 发射器在世界中的当前变换；局部粒子在构建实例数据时使用该值。 */
data class CooFxMeshEmitterTransform(
    val position: Vector3f = Vector3f(),
    val rotation: Quaternionf = Quaternionf(),
    val scale: Vector3f = Vector3f(1F),
) {
    init {
        require(position.isFiniteVector()) { "Emitter position must be finite" }
        require(rotation.isFiniteQuaternion()) { "Emitter rotation must be finite" }
        require(scale.isFiniteVector() && scale.x >= 0F && scale.y >= 0F && scale.z >= 0F) {
            "Emitter scale must be finite and non-negative"
        }
    }

    internal fun copyValue(): CooFxMeshEmitterTransform = CooFxMeshEmitterTransform(
        Vector3f(position),
        Quaternionf(rotation).normalize(),
        Vector3f(scale),
    )
}

/** 首版 CPU 模拟使用的受限经典力参数。 */
data class CooFxMeshForces(
    val gravity: Vector3f = Vector3f(),
    val wind: Vector3f = Vector3f(),
    val drag: Float = 0F,
    val noiseAmplitude: Vector3f = Vector3f(),
    val noiseFrequencyTicks: Int = 1,
) {
    init {
        require(gravity.isFiniteVector()) { "Gravity must be finite" }
        require(wind.isFiniteVector()) { "Wind must be finite" }
        require(drag.isFinite() && drag in 0F..1F) { "Drag must be in 0..1" }
        require(noiseAmplitude.isFiniteVector()) { "Noise amplitude must be finite" }
        require(noiseFrequencyTicks > 0) { "Noise frequency must be positive" }
    }
}

/** 一个可被 object 或 collection 选择语义引用的稳定渲染变体。 */
data class CooFxMeshVariant(
    val batchKey: CooFxMeshBatchKey,
    val meshVariant: Int = 0,
    val materialVariant: Int = 0,
    val nodeIndex: Int = 0,
) {
    init {
        require(meshVariant >= 0) { "Mesh variant must be non-negative" }
        require(materialVariant >= 0) { "Material variant must be non-negative" }
        require(nodeIndex >= 0) { "Node index must be non-negative" }
    }
}

/** 发射器每个数值通道的闭区间范围。 */
data class CooFxMeshFloatRange(val minimum: Float, val maximum: Float) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum) {
            "Float range must be finite and ordered"
        }
    }
}

/** 发射器三维通道的逐分量闭区间范围。 */
data class CooFxMeshVectorRange(val minimum: Vector3f, val maximum: Vector3f) {
    init {
        require(minimum.isFiniteVector() && maximum.isFiniteVector()) { "Vector range must be finite" }
        require(minimum.x <= maximum.x && minimum.y <= maximum.y && minimum.z <= maximum.z) {
            "Vector range must be ordered per component"
        }
    }
}

/** 发射器颜色通道的逐分量闭区间范围。 */
data class CooFxMeshColorRange(val minimum: Vector4f, val maximum: Vector4f) {
    init {
        require(minimum.isFiniteVector() && maximum.isFiniteVector()) { "Color range must be finite" }
        require(
            minimum.x <= maximum.x && minimum.y <= maximum.y &&
                minimum.z <= maximum.z && minimum.w <= maximum.w
        ) { "Color range must be ordered per component" }
    }
}

/**
 * 首版网格粒子发射器定义。
 *
 * 所有随机范围按稳定 channel seed 独立采样；修改一个通道不会移动其他通道的随机序列。
 * continuous 的持续时间为闭开区间 `[delayTicks, delayTicks + durationTicks)`。
 */
data class CooFxMeshEmitterDefinition(
    val emitterId: String,
    val simulationSpace: CooFxMeshSimulationSpace,
    val emissionMode: CooFxMeshEmissionMode,
    val selectionMode: CooFxMeshSelectionMode,
    val variants: List<CooFxMeshVariant>,
    val delayTicks: Int = 0,
    val durationTicks: Int = 1,
    val emissionCount: Int = 1,
    val particlesPerTick: Float = 1F,
    val lifetimeTicks: IntRange = 20..20,
    val position: CooFxMeshVectorRange = CooFxMeshVectorRange(Vector3f(), Vector3f()),
    val velocity: CooFxMeshVectorRange = CooFxMeshVectorRange(Vector3f(), Vector3f()),
    val acceleration: CooFxMeshVectorRange = CooFxMeshVectorRange(Vector3f(), Vector3f()),
    val rotationRadians: CooFxMeshVectorRange = CooFxMeshVectorRange(Vector3f(), Vector3f()),
    val angularVelocityRadians: CooFxMeshVectorRange = CooFxMeshVectorRange(Vector3f(), Vector3f()),
    val scale: CooFxMeshVectorRange = CooFxMeshVectorRange(Vector3f(1F), Vector3f(1F)),
    val color: CooFxMeshColorRange = CooFxMeshColorRange(Vector4f(1F), Vector4f(1F)),
    val packedLight: Int = 0,
    val clipIndex: Int = 0,
    val playbackSpeed: Float = 1F,
    val forces: CooFxMeshForces = CooFxMeshForces(),
) {
    init {
        require(emitterId.isNotBlank()) { "Emitter id must not be blank" }
        require(variants.isNotEmpty()) { "Emitter must contain at least one variant" }
        require(delayTicks >= 0) { "Delay must be non-negative" }
        require(durationTicks > 0) { "Duration must be positive" }
        require(emissionCount >= 0) { "Emission count must be non-negative" }
        require(particlesPerTick.isFinite() && particlesPerTick >= 0F) {
            "Particles per tick must be finite and non-negative"
        }
        require(lifetimeTicks.first > 0 && lifetimeTicks.last >= lifetimeTicks.first) {
            "Lifetime range must be positive and ordered"
        }
        require(scale.minimum.x >= 0F && scale.minimum.y >= 0F && scale.minimum.z >= 0F) {
            "Scale range must not contain negative values"
        }
        require(packedLight in 0..0xFFFFFF) { "Packed light must fit in 24 bits" }
        require(clipIndex in 0..0xFFFFFF) { "Clip index must fit in 24 bits" }
        require(playbackSpeed.isFinite()) { "Playback speed must be finite" }
    }
}

/** 单粒子生成后写入 dense storage 的完整初始状态。 */
data class CooFxMeshParticleSpawn(
    val stableParticleId: Long,
    val particleSeed: Long,
    val emitterRuntimeId: Long,
    val simulationSpace: CooFxMeshSimulationSpace,
    val batchKey: CooFxMeshBatchKey,
    val position: Vector3f,
    val velocity: Vector3f,
    val acceleration: Vector3f,
    val rotation: Quaternionf,
    val angularVelocityRadians: Vector3f,
    val scale: Vector3f,
    val lifetimeTicks: Int,
    val color: Vector4f,
    val packedLight: Int,
    val clipIndex: Int,
    val playbackSpeed: Float,
    val meshVariant: Int,
    val materialVariant: Int,
    val nodeIndex: Int = 0,
    val forces: CooFxMeshForces,
)

/** 按 stable particle id 排序后用于测试、诊断和跨压缩策略比较的值快照。 */
data class CooFxMeshParticleSnapshot(
    val stableParticleId: Long,
    val particleSeed: Long,
    val position: Vector3f,
    val previousPosition: Vector3f,
    val velocity: Vector3f,
    val rotation: Quaternionf,
    val previousRotation: Quaternionf,
    val scale: Vector3f,
    val ageTicks: Int,
    val lifetimeTicks: Int,
    val color: Vector4f,
    val clipTimeSeconds: Float,
    val previousClipTimeSeconds: Float,
    val meshVariant: Int,
    val materialVariant: Int,
)

internal fun Vector3f.isFiniteVector(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal fun Vector4f.isFiniteVector(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()

internal fun Quaternionf.isFiniteQuaternion(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()
