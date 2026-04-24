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
    fun uvOffset(): Vector2f {
        return Vector2f(offsetU, offsetV)
    }

    fun uvScale(): Vector2f {
        return Vector2f(scaleU, scaleV)
    }

    fun uvRect(): Vector4f {
        return Vector4f(offsetU, offsetV, scaleU, scaleV)
    }

    fun apply(baseUv: Vector2f): Vector2f {
        return Vector2f(
            offsetU + baseUv.x * scaleU,
            offsetV + baseUv.y * scaleV
        )
    }
}
