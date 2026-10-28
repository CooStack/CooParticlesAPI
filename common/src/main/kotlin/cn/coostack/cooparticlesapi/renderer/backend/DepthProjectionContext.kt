package cn.coostack.cooparticlesapi.renderer.backend

import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector4f
import kotlin.math.abs

/**
 * 与被采样深度纹理一同捕获的投影约定，不从后处理执行时的活动矩阵猜测。
 *
 * 原版可能使用零至一或负一至一 NDC；Iris 的 gbuffer 投影为正向负一至一，
 * 但实际深度纹理保持反向深度，因此两者必须显式记录映射。
 * @param view 深度产生时使用的相机相对视图矩阵
 * @param projection 与下面深度映射匹配的投影矩阵
 * @property depthToNdcScale 原始窗口深度转换到矩阵 NDC 深度的倍率
 * @property depthToNdcBias 原始窗口深度转换到矩阵 NDC 深度的偏移
 * @property closerIsGreater 原始纹理中较大值是否代表更近的表面
 */
class DepthProjectionContext(
    view: Matrix4fc,
    projection: Matrix4fc,
    val depthToNdcScale: Float,
    val depthToNdcBias: Float,
    val closerIsGreater: Boolean,
) {
    /** 捕获时复制的只读视图，不共享调用方可变矩阵。 */
    val viewMatrix: Matrix4fc = Matrix4f(view)
    /** 捕获时复制的只读投影，不共享调用方可变矩阵。 */
    val projectionMatrix: Matrix4fc = Matrix4f(projection)
    /** 与原始纹理比较方向一致的天空及清除值。 */
    val backgroundDepth: Float get() = if (closerIsGreater) 0F else 1F

    init {
        require(depthToNdcScale.isFinite() && abs(depthToNdcScale) > 0F && depthToNdcBias.isFinite())
        require(Matrix4f(view).get(FloatArray(16)).all(Float::isFinite))
        require(Matrix4f(projection).get(FloatArray(16)).all(Float::isFinite))
        require(abs(Matrix4f(view).determinant()) > 0F) { "深度视图矩阵不可逆" }
        require(abs(Matrix4f(projection).determinant()) > 0F) { "深度投影矩阵不可逆" }
    }

    /**
     * 把原始深度转换到捕获矩阵的 NDC，不假定 OpenGL 一定为负一至一。
     * 示例：`depthToNdc(0.5F)`。
     * @param depth 纹理中的窗口深度
     * @return 投影矩阵使用的 NDC 深度
     */
    fun depthToNdc(depth: Float): Float = depth * depthToNdcScale + depthToNdcBias

    /**
     * 把捕获矩阵产生的 NDC 转回对应原始深度纹理的数值。
     * 示例：`ndcToDepth(clip.z / clip.w)`。
     * @param ndc 投影除法后的深度
     * @return 与原始深度纹理相同约定的窗口深度
     */
    fun ndcToDepth(ndc: Float): Float = (ndc - depthToNdcBias) / depthToNdcScale

    /**
     * 正值表示源点在场景表面后方，适用于正向和反向深度的遮挡比较。
     * 示例：`occlusionDelta(sourceDepth, sceneDepth)`。
     * @param sourceDepth 待判定源点的原始窗口深度
     * @param sceneDepth 采样得到的场景深度
     * @return 与遮挡方向一致的有符号差
     */
    fun occlusionDelta(sourceDepth: Float, sceneDepth: Float): Float =
        (sourceDepth - sceneDepth) * if (closerIsGreater) -1F else 1F

    companion object {
        /**
         * 为原版矩阵记录实际 NDC 范围，并通过投影后的远近顺序确定原始深度比较方向。
         * 示例：`native(view, projection, zeroToOne)`。
         * @param view 深度产生时的视图矩阵
         * @param projection 深度产生时的投影矩阵
         * @param zeroToOne 捕获时设备使用的 NDC 范围
         * @return 仅包含 CPU 数学状态的深度上下文
         */
        fun native(view: Matrix4fc, projection: Matrix4fc, zeroToOne: Boolean): DepthProjectionContext {
            val near = projection.transform(Vector4f(0F, 0F, -1F, 1F))
            val far = projection.transform(Vector4f(0F, 0F, -2F, 1F))
            require(near.w != 0F && far.w != 0F)
            val nearDepth = near.z / near.w
            val farDepth = far.z / far.w
            require(nearDepth.isFinite() && farDepth.isFinite() && nearDepth != farDepth)
            return DepthProjectionContext(view, projection, if (zeroToOne) 1F else 2F,
                if (zeroToOne) 0F else -1F, nearDepth > farDepth)
        }

        /**
         * Iris 的 MatrixUtils.undoRevZ 输出正向负一至一矩阵，raw depth 仍是反向深度。
         * 示例：`iris(view, gbufferProjection)`。
         * @param view Iris 捕获的 gbuffer 视图矩阵
         * @param forwardProjection Iris 捕获的正向负一至一 gbuffer 投影
         * @return raw depth 通过一减深度后与 gbuffer 投影一致的上下文
         */
        @JvmStatic
        fun iris(view: Matrix4fc, forwardProjection: Matrix4fc): DepthProjectionContext =
            DepthProjectionContext(view, forwardProjection, -2F, 1F, true)
    }
}
