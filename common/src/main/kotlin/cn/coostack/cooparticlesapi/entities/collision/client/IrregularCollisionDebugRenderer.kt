package cn.coostack.cooparticlesapi.entities.collision.client

import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/** 在 F3+B 的原版线段批次绘制真实有向框及支点坐标轴，不触碰全局渲染状态。 */
object IrregularCollisionDebugRenderer {
    /**
     * 绘制实体碰撞框、白色支点和红绿蓝 XYZ 轴。
     * 示例：在 F3+B 回调内调用 `draw(matrices, vertices, model, 1F, 1F, 1F)`。
     * @param matrices 已处于实体原点的矩阵栈，返回时恢复
     * @param vertices 原版线段层接收器
     * @param model 当前实体的不规则碰撞能力
     * @param red 碰撞线红通道，范围 0 到 1
     * @param green 碰撞线绿通道，范围 0 到 1
     * @param blue 碰撞线蓝通道，范围 0 到 1
     */
    @JvmStatic
    fun draw(matrices: PoseStack, vertices: VertexConsumer, model: IrregularCollisionEntity,
             red: Float, green: Float, blue: Float) {
        drawCollision(matrices, vertices, model, red, green, blue)
        drawAxes(matrices, vertices, model)
    }

    /**
     * 只绘制碰撞线框，用于独立开关的预览；例如 `drawCollision(matrices, vertices, model, 1F, 1F, 1F)`。
     * @param matrices 已定位到实体原点的矩阵
     * @param vertices 线段顶点接收器
     * @param model 被观察的不规则实体
     * @param red 红通道，范围 0 到 1
     * @param green 绿通道，范围 0 到 1
     * @param blue 蓝通道，范围 0 到 1
     */
    fun drawCollision(matrices: PoseStack, vertices: VertexConsumer, model: IrregularCollisionEntity,
                      red: Float, green: Float, blue: Float) {
        val geometry = model.collisionGeometry
        val origin = model.collisionOrigin
        val color = (255 shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()
        for (box in geometry.parts) {
            val corners = (0..7).map { mask ->
                box.point(if (mask and 1 == 0) -box.half[0] else box.half[0],
                    if (mask and 2 == 0) -box.half[1] else box.half[1],
                    if (mask and 4 == 0) -box.half[2] else box.half[2]).subtract(origin)
            }
            for (corner in 0..7) for (axis in 0..2) {
                if (corner and (1 shl axis) == 0) line(matrices.last(), vertices, corners[corner], corners[corner or (1 shl axis)], color)
            }
        }
    }

    /**
     * 只绘制支点和正向轴，不包含碰撞；例如 `drawAxes(matrices, vertices, model)`。
     * @param matrices 已定位到实体原点的矩阵，调用结束时恢复
     * @param vertices 线段顶点接收器
     * @param model 提供世界支点和旋转的实体
     */
    fun drawAxes(matrices: PoseStack, vertices: VertexConsumer, model: IrregularCollisionEntity) {
        val origin = model.collisionOrigin
        matrices.pushPose()
        try {
            val pivot = model.collisionPivot.subtract(origin)
            matrices.translate(pivot.x, pivot.y, pivot.z)
            matrices.mulPose(Quaternionf(model.collisionRotation))
            LevelRenderer.renderLineBox(matrices, vertices, AABB(-0.06, -0.06, -0.06, 0.06, 0.06, 0.06), 1F, 1F, 1F, 1F)
            axis(matrices.last(), vertices, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), 0xFFFF4040.toInt())
            axis(matrices.last(), vertices, Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), 0xFF40FF40.toInt())
            axis(matrices.last(), vertices, Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0), 0xFF4080FF.toInt())
        } finally {
            matrices.popPose()
        }
    }

    /** 提交一格长的正向轴及两条箭头翼，不接受模型缩放。 */
    private fun axis(pose: PoseStack.Pose, vertices: VertexConsumer, end: Vec3, side: Vec3, color: Int) {
        line(pose, vertices, Vec3.ZERO, end, color)
        line(pose, vertices, end, end.scale(0.8).add(side.scale(0.12)), color)
        line(pose, vertices, end, end.scale(0.8).subtract(side.scale(0.12)), color)
    }

    /** 线段提交带有方向法线的成对顶点。 */
    private fun line(pose: PoseStack.Pose, vertices: VertexConsumer, start: Vec3, end: Vec3, color: Int) {
        val normal = end.subtract(start).normalize()
        vertices.addVertex(pose, start.x.toFloat(), start.y.toFloat(), start.z.toFloat()).setColor(color)
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
        vertices.addVertex(pose, end.x.toFloat(), end.y.toFloat(), end.z.toFloat()).setColor(color)
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }
}
