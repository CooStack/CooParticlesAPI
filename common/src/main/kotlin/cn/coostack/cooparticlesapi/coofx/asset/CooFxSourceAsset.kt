package cn.coostack.cooparticlesapi.coofx.asset

import net.minecraft.resources.ResourceLocation

/**
 * glTF 材质的透明度策略。
 *
 * [OPAQUE] 对应 glTF `OPAQUE`，所有片元按不透明路径处理，也是缺省值。
 * [MASK] 对应 glTF `MASK`，由 [CooFxMaterial.alphaCutoff] 决定丢弃阈值。
 * [BLEND] 对应 glTF `BLEND`，仅保留元数据；首版网格粒子编译阶段必须拒绝执行。
 */
enum class CooFxAlphaMode {
    /** 不透明材质，可进入首版渲染编译。 */
    OPAQUE,

    /** Alpha 裁剪材质，可进入首版渲染编译。 */
    MASK,

    /** Alpha 混合材质，仅记录能力且不得伪装为已支持。 */
    BLEND,
}

/**
 * glTF 动画插值方式。
 *
 * [STEP] 在下一个关键帧前保持前一关键帧值。
 * [LINEAR] 对平移和缩放做线性插值，对旋转由播放层做归一化球面插值。
 * [CUBICSPLINE] 保存 glTF 入切线、值和出切线布局，由播放层按秒执行 Hermite 采样。
 */
enum class CooFxInterpolation {
    /** 阶跃采样，序列化值为 `STEP`。 */
    STEP,

    /** 线性采样，序列化值为 `LINEAR`，也是 glTF 缺省值。 */
    LINEAR,

    /** 三次样条采样，序列化值为 `CUBICSPLINE`。 */
    CUBICSPLINE,
}

/**
 * 动画通道修改的节点属性。
 *
 * [TRANSLATION] 修改三分量局部平移。
 * [ROTATION] 修改 `(x, y, z, w)` 顺序的局部四元数。
 * [SCALE] 修改三分量局部缩放。
 * [WEIGHTS] 保存 morph 权重轨道元数据，首版运行时不得执行。
 */
enum class CooFxAnimationPath {
    /** glTF `translation` 通道。 */
    TRANSLATION,

    /** glTF `rotation` 通道。 */
    ROTATION,

    /** glTF `scale` 通道。 */
    SCALE,

    /** glTF `weights` 通道，仅用于显式能力诊断。 */
    WEIGHTS,
}

data class CooFxSourceAsset(
    val resource: ResourceLocation,
    val schemaVersion: Int,
    val assetSeed: ULong,
    val modelResource: ResourceLocation,
    val scene: Int,
    val nodes: List<CooFxNode>,
    val meshes: List<CooFxMesh>,
    val materials: List<CooFxMaterial>,
    val animations: List<CooFxAnimation>,
    val emitters: List<CooFxEmitter>,
    val deformationMetadata: CooFxDeformationMetadata,
)

data class CooFxNode(
    val name: String?,
    val children: List<Int>,
    val mesh: Int?,
    val skin: Int?,
    val matrix: List<Float>?,
    val translation: List<Float>,
    val rotation: List<Float>,
    val scale: List<Float>,
)

data class CooFxMesh(
    val name: String?,
    val primitives: List<CooFxMeshPrimitive>,
    val weights: List<Float>,
)

data class CooFxMeshPrimitive(
    val positions: List<Float>,
    val normals: List<Float>?,
    val texCoords: List<Float>?,
    val colors: List<Float>?,
    val indices: List<Int>,
    val material: Int?,
    val morphTargetSemantics: List<Set<String>>,
)

data class CooFxMaterial(
    val name: String?,
    val baseColorFactor: List<Float>,
    val baseColorTexture: ResourceLocation?,
    val alphaMode: CooFxAlphaMode,
    val alphaCutoff: Float,
    val doubleSided: Boolean,
    val emissiveFactor: List<Float>,
)

data class CooFxAnimation(
    val name: String?,
    val channels: List<CooFxAnimationChannel>,
)

data class CooFxAnimationChannel(
    val node: Int,
    val path: CooFxAnimationPath,
    val interpolation: CooFxInterpolation,
    val inputSeconds: List<Float>,
    val outputValues: List<Float>,
    val outputComponentCount: Int,
)

data class CooFxEmitter(
    val id: String,
    val node: Int?,
    val mesh: Int?,
    val count: Int,
    val delayTicks: Int,
    val lifetimeTicks: Int,
)

data class CooFxDeformationMetadata(
    val skinCount: Int,
    val morphTargetCount: Int,
    val hasAnimatedWeights: Boolean,
    val vatExtensionIds: Set<ResourceLocation>,
)
