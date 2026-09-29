package cn.coostack.cooparticlesapi.cparticle.path

import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.abs

/** 将纹理前向轴旋转到运动方向，输出与粒子 ROTATION 渲染一致的 XYZ 欧拉角。 */
object CParticlePathOrientation {
    /**
     * 求显式路径跟随所需的姿态；零方向保留已有姿态，不产生新旋转。
     * 示例：`angles(direction, CParticlePathForwardAxis.MODEL_POSITIVE_Y)` 对齐纹理高度轴。
     *
     * @param direction 绑定变换后的运动方向
     * @param axis 纹理或模型的本地前向轴
     * @param customAxis 自定义前向轴，仅 CUSTOM 使用
     * @return 俯仰、偏航、滚转（弧度）；方向退化时返回 null
     */
    fun angles(direction: Vec3, axis: CParticlePathForwardAxis, customAxis: Vec3? = null): Vector3f? {
        if (direction.lengthSqr() <= 1.0E-12) return null
        val forward = CParticlePathForwardAxis.resolve(axis, customAxis)
        val target = direction.normalize()
        val rotation = Quaternionf().rotationTo(
            forward.x.toFloat(), forward.y.toFloat(), forward.z.toFloat(),
            target.x.toFloat(), target.y.toFloat(), target.z.toFloat(),
        ).normalize()
        val x = rotation.transform(Vector3f(1F, 0F, 0F))
        val y = rotation.transform(Vector3f(0F, 1F, 0F))
        val z = rotation.transform(Vector3f(0F, 0F, 1F))
        // 万向节锁时合并 X/Z 旋转，避免 atan2(0, 0) 丢失有效姿态。
        return if (abs(z.x) >= 0.999999F) {
            Vector3f(atan2(y.z, y.y), asin(z.x.coerceIn(-1F, 1F)), 0F)
        } else {
            Vector3f(atan2(-z.y, z.z), asin(z.x.coerceIn(-1F, 1F)), atan2(-y.x, x.x))
        }
    }
}
