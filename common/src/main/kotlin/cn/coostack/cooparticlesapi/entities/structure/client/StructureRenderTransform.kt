package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import org.joml.Quaternionf

/**
 * 将结构变换应用到已经位于放置锚点的矩阵，供实体与碰撞调试渲染共用。
 * 示例：`settings.applyModelTransform(matrices)`；矩阵的压栈、出栈由调用方负责。
 * @receiver 已验证的模型设置，中心坐标来自结构局部空间
 * @param matrices 客户端渲染线程独占的矩阵栈
 * @param deathDegrees 围绕同一中心的额外倒地角，单位为度，正常显示使用零
 */
fun StructureModelSettings.applyModelTransform(matrices: PoseStack, deathDegrees: Float = 0F) {
    val center = pivotPosition
    matrices.translate(center.x, center.y, center.z)
    if (deathDegrees != 0F) matrices.mulPose(Axis.ZP.rotationDegrees(deathDegrees))
    matrices.mulPose(Quaternionf(quaternion()))
    matrices.scale(scale[0].toFloat(), scale[1].toFloat(), scale[2].toFloat())
    matrices.translate(-pivot[0], -pivot[1], -pivot[2])
}
