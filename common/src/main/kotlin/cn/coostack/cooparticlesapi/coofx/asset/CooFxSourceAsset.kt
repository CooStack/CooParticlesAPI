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

/**
 * CooFX clip 的独立播放循环策略。
 *
 * [ONCE] 到达末尾后停在末帧；[LOOP] 从头循环；[PING_PONG] 在首尾之间往返。
 */
enum class CooFxClipLoopMode {
    /** 只播放一次并停在末帧。 */
    ONCE,

    /** 到达末尾后从零开始循环。 */
    LOOP,

    /** 在开始和结束时间之间双向往返。 */
    PING_PONG,
}

data class CooFxClip(
    val id: String,
    val animation: Int,
    val loopMode: CooFxClipLoopMode,
) {
    init {
        require(id.isNotBlank()) { "CooFX clip id 不能为空" }
        require(animation >= 0) { "CooFX clip animation 索引不能为负数" }
    }
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
    val cameras: List<CooFxCamera> = emptyList(),
    val clips: List<CooFxClip> = emptyList(),
    val extensionMetadata: CooFxExtensionMetadata = CooFxExtensionMetadata(),
) {
    init {
        require(clips.map(CooFxClip::id).distinct().size == clips.size) { "CooFX clip id 不能重复" }
        require(clips.all { it.animation in animations.indices }) { "CooFX clip animation 索引越界" }
    }
}

data class CooFxNode(
    val name: String?,
    val children: List<Int>,
    val mesh: Int?,
    val skin: Int?,
    val matrix: List<Float>?,
    val translation: List<Float>,
    val rotation: List<Float>,
    val scale: List<Float>,
    val camera: Int? = null,
)

/** glTF 摄像机投影描述；空间姿态由引用它的 [CooFxNode] 提供。 */
sealed interface CooFxCamera {
    val id: String
    val name: String?

    data class Perspective(
        override val id: String,
        override val name: String?,
        val yfovRadians: Float,
        val aspectRatio: Float?,
        val znear: Float,
        val zfar: Float?,
    ) : CooFxCamera {
        init {
            require(id.isNotBlank()) { "CooFX camera id 不能为空" }
            require(yfovRadians.isFinite() && yfovRadians > 0F) { "透视 camera yfov 必须为正有限弧度" }
            require(aspectRatio == null || aspectRatio.isFinite() && aspectRatio > 0F) {
                "透视 camera aspectRatio 必须为正有限数"
            }
            require(znear.isFinite() && znear > 0F) { "透视 camera znear 必须为正有限数" }
            require(zfar == null || zfar.isFinite() && zfar > znear) { "透视 camera zfar 必须大于 znear" }
        }
    }

    data class Orthographic(
        override val id: String,
        override val name: String?,
        val xmag: Float,
        val ymag: Float,
        val znear: Float,
        val zfar: Float,
    ) : CooFxCamera {
        init {
            require(id.isNotBlank()) { "CooFX camera id 不能为空" }
            require(xmag.isFinite() && xmag > 0F && ymag.isFinite() && ymag > 0F) {
                "正交 camera xmag 和 ymag 必须为正有限数"
            }
            require(znear.isFinite() && znear >= 0F) { "正交 camera znear 必须为非负有限数" }
            require(zfar.isFinite() && zfar > znear) { "正交 camera zfar 必须大于 znear" }
        }
    }
}

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
    val emissiveTexture: ResourceLocation? = null,
    val emissiveStrength: Float = 1F,
) {
    init {
        require(baseColorFactor.size == 4 && baseColorFactor.all { it.isFinite() && it in 0F..1F }) {
            "glTF baseColorFactor 必须是四个 0 到 1 的有限分量"
        }
        require(alphaCutoff.isFinite() && alphaCutoff in 0F..1F) { "alphaCutoff 必须在 0 到 1" }
        require(emissiveFactor.size == 3 && emissiveFactor.all { it.isFinite() && it >= 0F }) {
            "glTF emissiveFactor 必须是三个非负有限分量"
        }
        require(emissiveStrength.isFinite() && emissiveStrength >= 0F) {
            "glTF emissiveStrength 必须是非负有限值"
        }
    }
}

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

data class CooFxFloat3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "CooFX 三维值必须全部为有限数" }
    }
}

data class CooFxFloat3Range(
    val minimum: CooFxFloat3,
    val maximum: CooFxFloat3,
) {
    init {
        require(
            minimum.x <= maximum.x &&
                minimum.y <= maximum.y &&
                minimum.z <= maximum.z
        ) { "CooFX 三维范围的 minimum 不能大于 maximum" }
    }
}

data class CooFxEmitter(
    val id: String,
    val node: Int?,
    val mesh: Int?,
    val count: Int,
    val delayTicks: Int,
    val lifetimeTicks: Int,
    val velocity: CooFxFloat3Range = CooFxFloat3Range(
        CooFxFloat3(0F, 0F, 0F),
        CooFxFloat3(0F, 0F, 0F),
    ),
    val rotationRadians: CooFxFloat3Range = CooFxFloat3Range(
        CooFxFloat3(0F, 0F, 0F),
        CooFxFloat3(0F, 0F, 0F),
    ),
    val scale: CooFxFloat3Range = CooFxFloat3Range(
        CooFxFloat3(1F, 1F, 1F),
        CooFxFloat3(1F, 1F, 1F),
    ),
) {
    init {
        require(scale.minimum.x > 0F && scale.minimum.y > 0F && scale.minimum.z > 0F) {
            "CooFX emitter 缩放范围必须为正数"
        }
    }
}

data class CooFxDeformationMetadata(
    val skinCount: Int,
    val morphTargetCount: Int,
    val hasAnimatedWeights: Boolean,
    val vatExtensionIds: Set<ResourceLocation>,
)

data class CooFxExtensionMetadata(
    val cooFx: Map<ResourceLocation, String> = emptyMap(),
    val gltf: Map<String, String?> = emptyMap(),
)
