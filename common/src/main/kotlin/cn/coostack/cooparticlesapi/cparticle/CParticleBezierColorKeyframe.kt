package cn.coostack.cooparticlesapi.cparticle

import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * 描述一个 RGB 关键帧及其三次贝塞尔出入控制柄。
 *
 * 示例：出控制柄的值偏移为 `(0, 1, 0)` 时，曲线段会向绿色方向弯曲。
 * 禁止：[outX] 和 [inX] 使用百分比点，值控制柄则使用 RGB 相对偏移。
 *
 * @property time `0..1` 范围内的归一化曲线时间
 * @property value 此关键帧的 RGB 倍率
 * @property outX 出控制柄的时间偏移，单位为百分比点
 * @property outValueOffset 相对 [value] 的出控制柄 RGB 偏移
 * @property inX 入控制柄的时间偏移，单位为百分比点
 * @property inValueOffset 相对 [value] 的入控制柄 RGB 偏移
 */
data class CParticleBezierColorKeyframe(
    val time: Double,
    val value: Vector3fc,
    val outX: Double = 0.0,
    val outValueOffset: Vector3fc = Vector3f(),
    val inX: Double = 0.0,
    val inValueOffset: Vector3fc = Vector3f(),
)
