package cn.coostack.cooparticlesapi.cparticle.path

import net.minecraft.world.phys.Vec3

/**
 * # 模型前方轴约定
 *
 * 声明纹理或模型本地坐标中的前方，仅在路径命令显式设置 `faceMotion = true` 时参与姿态求解。
 * 默认 +X 表示宽度轴，+Y 表示高度轴，+Z 表示面法线；负轴表示相应反向。
 * 竖向长条纹理应声明 +Y；此枚举不会根据粒子尺寸或纹理内容自动选择轴。
 * CUSTOM 接收显式单位方向，传统命令支持；GPU 命令当前仅支持六个坐标轴。
 *
 * [wireValue] 属于 GPU/CPU 共用的 Command ABI，不能按枚举顺序重新编号。
 */
enum class CParticlePathForwardAxis(
    /** 写入 Command 参数打包值的数值标识。 */
    val wireValue: Int,
) {
    /** 模型或纹理前方为本地 `+X`，即粒子宽度正方向。 */
    MODEL_POSITIVE_X(0),

    /** 模型前方为本地 `+Z`，即粒子面法线正方向。 */
    MODEL_POSITIVE_Z(1),

    /** 模型前方为本地 `+Y`。 */
    MODEL_POSITIVE_Y(2),

    /** 模型前方为本地 `+X` 的反向。 */
    MODEL_NEGATIVE_X(3),

    /** 模型前方为本地 `+Z` 的反向。 */
    MODEL_NEGATIVE_Z(4),

    /** 模型前方为本地 `+Y` 的反向。 */
    MODEL_NEGATIVE_Y(5),

    /**
     * 由调用方显式给出模型前方轴。
     *
     * 适合非坐标轴朝向的模型；该轴会在打包时归一化，零向量会回退到 [MODEL_POSITIVE_X]。
     */
    CUSTOM(6),
    ;

    companion object {
        /** 把模型前方轴换算成单位向量；[forwardAxis] 只在 [CUSTOM] 时参与。 */
        @JvmStatic
        fun resolve(axis: CParticlePathForwardAxis, forwardAxis: Vec3?): Vec3 = when (axis) {
            MODEL_POSITIVE_X -> POSITIVE_X
            MODEL_POSITIVE_Z -> POSITIVE_Z
            MODEL_POSITIVE_Y -> POSITIVE_Y
            MODEL_NEGATIVE_X -> POSITIVE_X.reverse()
            MODEL_NEGATIVE_Z -> POSITIVE_Z.reverse()
            MODEL_NEGATIVE_Y -> POSITIVE_Y.reverse()
            CUSTOM -> forwardAxis?.takeIf { it.lengthSqr() > 1.0E-12 }?.normalize() ?: POSITIVE_X
        }

        private val POSITIVE_X = Vec3(1.0, 0.0, 0.0)
        private val POSITIVE_Y = Vec3(0.0, 1.0, 0.0)
        private val POSITIVE_Z = Vec3(0.0, 0.0, 1.0)
    }
}
