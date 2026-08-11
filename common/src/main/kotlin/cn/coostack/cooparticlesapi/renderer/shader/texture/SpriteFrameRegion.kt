package cn.coostack.cooparticlesapi.renderer.shader.texture

import org.joml.Vector2f
import org.joml.Vector4f

/**
 * 描述 sprite-sheet 当前帧在整张纹理中的 UV 区域。
 *
 * `uvRect()` 的返回格式为 `(offsetU, offsetV, scaleU, scaleV)`，
 * 方便直接上传到 shader 的 `vec4` uniform。
 */
data class SpriteFrameRegion(
    val frameIndex: Int,
    val column: Int,
    val row: Int,
    val offsetU: Float,
    val offsetV: Float,
    val scaleU: Float,
    val scaleV: Float
) {
    /**
     * 更新 `SpriteFrameRegion` 的 `uvOffset` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`uvOffset()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun uvOffset(): Vector2f {
        return Vector2f(offsetU, offsetV)
    }

    /**
     * 更新 `SpriteFrameRegion` 的 `uvScale` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`uvScale()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun uvScale(): Vector2f {
        return Vector2f(scaleU, scaleV)
    }

    /**
     * 更新 `SpriteFrameRegion` 的 `uvRect` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`uvRect()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun uvRect(): Vector4f {
        return Vector4f(offsetU, offsetV, scaleU, scaleV)
    }

    /**
     * 执行 `SpriteFrameRegion` 定义的 `apply` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`apply(baseUv = baseUv)`。
     *
     * @param baseUv 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun apply(baseUv: Vector2f): Vector2f {
        return Vector2f(
            offsetU + baseUv.x * scaleU,
            offsetV + baseUv.y * scaleV
        )
    }
}
