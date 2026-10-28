package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.extend.minus
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.PoseStack.Pose
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 世界坐标变换
 * T * R * S * Local
 * 位移 - 旋转 - 缩放 - 相对位置
 *
 */
object MinecraftRendererUtil {
    data class AxialBillboardBasis(
        val axis: Vec3,
        val face: Vec3,
        val right: Vec3
    )

    /**
     * 将线段几何加入当前帧的 26.3 提交队列。
     *
     * 示例：`submitLines(collector, poseStack) { pose, vertices -> ... }`。
     * 提交回调由渲染队列稍后执行，不能在回调外保留其顶点接收器。
     *
     * @param collector 当前阶段的命令收集器
     * @param poseStack 提交时使用的世界姿势
     * @param draw 在本次几何提交中写入线段顶点
     */
    fun submitLines(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        draw: (Pose, VertexConsumer) -> Unit,
    ) {
        collector.submitCustomGeometry(poseStack, RenderTypes.lines()) { pose, vertices ->
            draw(pose, vertices)
        }
    }

    /**
     * 将相机相对包围盒作为线段几何提交。
     *
     * 示例：`submitLineBox(collector, poseStack, box, camera, 1F, 0F, 0F, 1F)`。
     *
     * @param collector 当前阶段的命令收集器
     * @param poseStack 提交时使用的世界姿势
     * @param box 世界坐标包围盒
     * @param camera 当前帧相机世界位置
     * @param red 红色分量
     * @param green 绿色分量
     * @param blue 蓝色分量
     * @param alpha 透明度分量
     */
    fun submitLineBox(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        box: net.minecraft.world.phys.AABB,
        cameraPos: Vec3,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        val relative = box.move(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        val corners = arrayOf(
            Vec3(relative.minX, relative.minY, relative.minZ),
            Vec3(relative.maxX, relative.minY, relative.minZ),
            Vec3(relative.maxX, relative.minY, relative.maxZ),
            Vec3(relative.minX, relative.minY, relative.maxZ),
            Vec3(relative.minX, relative.maxY, relative.minZ),
            Vec3(relative.maxX, relative.maxY, relative.minZ),
            Vec3(relative.maxX, relative.maxY, relative.maxZ),
            Vec3(relative.minX, relative.maxY, relative.maxZ),
        )
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7,
        )
        submitLines(collector, poseStack) { pose, vertices ->
            edges.forEach { (firstIndex, secondIndex) ->
                val first = corners[firstIndex]
                val second = corners[secondIndex]
                vertices.addVertex(pose, first.x.toFloat(), first.y.toFloat(), first.z.toFloat())
                    .setColor(red, green, blue, alpha)
                vertices.addVertex(pose, second.x.toFloat(), second.y.toFloat(), second.z.toFloat())
                    .setColor(red, green, blue, alpha)
            }
        }
    }

    /**
     * 将一条相机相对线段加入当前帧提交队列。
     *
     * @param collector 当前阶段的命令收集器
     * @param poseStack 提交时使用的世界姿势
     * @param start 世界坐标起点
     * @param end 世界坐标终点
     * @param cameraPos 当前帧相机世界位置
     * @param red 红色分量
     * @param green 绿色分量
     * @param blue 蓝色分量
     * @param alpha 透明度分量
     */
    fun submitLine(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        start: Vec3,
        end: Vec3,
        cameraPos: Vec3,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        val relativeStart = start.subtract(cameraPos)
        val relativeEnd = end.subtract(cameraPos)
        submitLines(collector, poseStack) { pose, vertices ->
            vertices.addVertex(pose, relativeStart.x.toFloat(), relativeStart.y.toFloat(), relativeStart.z.toFloat())
                .setColor(red, green, blue, alpha)
            vertices.addVertex(pose, relativeEnd.x.toFloat(), relativeEnd.y.toFloat(), relativeEnd.z.toFloat())
                .setColor(red, green, blue, alpha)
        }
    }

    /**
     * 变换相对渲染位置到世界坐标位置
     *
     * @param cameraPos 摄像机位置
     * @param to 渲染目标的世界坐标
     * @param stack 需要作用变换的stack
     * @param invoker 在变换范围内进行操作
     */
    fun transformTo(cameraPos: Vec3, to: Vec3, stack: PoseStack, invoker: PoseStack.() -> Unit) {
        val final = to - cameraPos
        stack.pushPose()
        stack.translate(final.x, final.y, final.z)
        invoker(stack)
        stack.popPose()
    }

    /**
     * 变换相对渲染位置到世界坐标位置
     *
     * @param camera 摄像机
     * @param to 渲染目标的世界坐标
     * @param stack 需要作用变换的stack
     * @param invoker 在变换范围内进行操作
     */
    fun transformTo(camera: Camera, to: Vec3, stack: PoseStack, invoker: PoseStack.() -> Unit) {
        transformTo(camera.position(), to, stack, invoker)
    }

    /**
     * 旋转矩阵 应用欧拉角
     *
     * 这里假设 图形的对称轴在z轴上 (如果不在首先要把他变换到Z轴上才行)
     *
     * @param stack 模型矩阵
     * @param yaw 水平角度 角度制
     * @param pitch 垂直角度 角度制
     * @param roll  滚动角度 角度制
     */
    fun applyRotation(stack: PoseStack, yaw: Float, pitch: Float, roll: Float) {
        val q = Quaternionf()
            .rotateY(-yaw.asFloatRadian())
            .rotateX(-pitch.asFloatRadian())
            .rotateZ(roll.asFloatRadian())

        stack.rotate(q)
    }

    fun applyRotation(stack: PoseStack, q: Quaternionf) {
        stack.rotate(q)
    }

    /**
     * TODO 未测试有效性
     *
     * 如果模型默认对称轴不是z轴 而是其他轴
     *
     * 使用此方法正合适
     *
     * @param stack 模型矩阵
     * @param originalAxis 模型所在的轴 （正向）
     * @param to  目标方向
     * @param roll 滚动角
     */
    fun applyRotationAxisTo(
        stack: PoseStack,
        originalAxis: Vec3,
        to: Vec3,
        roll: Float
    ) {
        val from = originalAxis.toVector3f().normalize()
        val target = to.toVector3f().normalize()
        val alignQ = Quaternionf().rotateTo(from, target)
        val rollQ = Quaternionf().rotateAxis(
            roll.asFloatRadian(),
            target.x, target.y, target.z
        )
        alignQ.mul(rollQ)
        stack.rotate(alignQ)
    }

    /**
     * 如果模型默认对称轴不是z轴 而是其他轴
     *
     * 使用此方法正合适
     *
     * @param stack 模型矩阵
     * @param originalAxis 模型所在的轴 （正向）
     * @param yaw 目标水平角度
     * @param pitch 目标垂直角度
     * @param roll 滚动角
     */
    fun applyRotationAxisTo(
        stack: PoseStack,
        originalAxis: Vec3,
        yaw: Float,
        pitch: Float,
        roll: Float
    ) {
        applyRotationAxisTo(
            stack,
            originalAxis,
            directionFromYawPitch(yaw, pitch),
            roll
        )
    }

    /**
     * 将原先的旋转矩阵转回到Z轴
     * TODO 未测试其有效性 目前是通过计算yaw pitch然后进行反向旋转得到结果
     *
     * @param stack 模型矩阵
     * @param originalAxis 模型当前的 “z”轴
     */
    fun resetRotationToZAxis(stack: PoseStack, originalAxis: Vec3, originalRoll: Float) {
        val yaw = Math3DUtil.getYawFromLocation(originalAxis) * 180 / PI + 90F
        val pitch = Math3DUtil.getPitchFromLocation(originalAxis) * 180 / PI
        applyRotation(stack, -yaw.toFloat(), -pitch.toFloat(), -originalRoll)
    }

    /**
     * 这里仍然认为stack没有任何旋转， 并且模型是面向Z轴正半轴的
     * TODO 未测试有效性， 目前来看Math3DUtil在旋转粒子组的时候并无大碍
     *
     * @param stack 模型矩阵
     * @param direction 视角方向
     * @param roll 滚动角度大小 （角度制）
     */
    fun applyRotationLookAt(stack: PoseStack, direction: Vec3, roll: Float) {
        // 转到角度制
        val yawFromLocation = Math3DUtil.getYawFromLocation(direction) * 180 / PI
        val pitchFromLocation = Math3DUtil.getPitchFromLocation(direction) * 180 / PI
        applyRotation(stack, yawFromLocation.toFloat(), pitchFromLocation.toFloat(), roll)
    }

    fun axialBillboardBasis(axisDirection: Vec3, camera: Camera, center: Vec3): AxialBillboardBasis {
        return axialBillboardBasis(axisDirection, camera.position(), center)
    }

    fun axialBillboardBasis(axisDirection: Vec3, cameraPos: Vec3, center: Vec3): AxialBillboardBasis {
        val axis = normalizedOr(axisDirection, Vec3(0.0, 1.0, 0.0))
        val toCamera = cameraPos.subtract(center)
        val face = if (toCamera.lengthSqr() <= 1.0E-6) {
            perpendicular(axis)
        } else {
            val projected = toCamera.subtract(axis.scale(toCamera.dot(axis)))
            normalizedOr(projected, perpendicular(axis))
        }
        val right = normalizedOr(face.cross(axis), perpendicular(axis))
        return AxialBillboardBasis(axis, face, right)
    }

    fun applyAtPoint(point: Vec3, stack: PoseStack, invoker: PoseStack.() -> Unit) {
        stack.translate(point.x, point.y, point.z)
        invoker(stack)
        stack.translate(-point.x, -point.y, -point.z)
    }

    /**
     * 解析物品模型状态并将它的所有材质层提交到当前帧队列。
     *
     * 示例：手持展示可使用 `ItemDisplayContext.FIXED` 和空 owner。
     *
     * @param stack 待绘制物品及其数据组件
     * @param displayContext 原版物品展示上下文
     * @param world 当前客户端世界
     * @param owner 提供模型上下文数据的实体，可为空
     * @param seed 物品模型选择使用的随机种子
     * @param poseStack 物品模型变换
     * @param collector 当前帧提交收集器
     * @param light 打包光照值
     * @param overlay 原版伤害覆盖坐标
     */
    fun submitItem(
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        world: Level,
        owner: ItemOwner?,
        seed: Int,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        overlay: Int,
    ) {
        val state = ItemStackRenderState()
        Minecraft.getInstance().itemModelResolver.updateForTopItem(
            state,
            stack,
            displayContext,
            world,
            owner,
            seed,
        )
        state.submit(poseStack, collector, light, overlay, 0)
    }

    fun Float.asFloatRadian(): Float {
        return this * PI.toFloat() / 180F
    }

    /**
     * TODO  未测试有效
     *
     * @param yaw 角度制 水平角
     * @param pitch 角度制 垂直角
     * @return
     */
    fun directionFromYawPitch(yaw: Float, pitch: Float): Vec3 {
        val yawRad = yaw.asFloatRadian() + PI / 2
        val pitchRad = pitch.asFloatRadian()

        val x = -sin(yawRad) * cos(pitchRad)
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cos(pitchRad)

        return Vec3(x, y.toDouble(), z)
    }

    /**
     * 在三角形 ABC 上按网格采样（density 越大点越密）
     * 采样点数约为 (density+1)(density+2)/2
     */
    fun sampleTriangle(a: Vec3, b: Vec3, c: Vec3, density: Int, out: MutableList<Vec3>): MutableList<Vec3> {
        val d = max(1, density)
        for (i in 0..d) {
            for (j in 0..(d - i)) {
                val u = i.toDouble() / d
                val v = j.toDouble() / d
                val w = 1.0 - u - v

                // p = a*w + b*u + c*v
                val p = a.scale(w).add(b.scale(u)).add(c.scale(v))
                out.add(p)
            }
        }
        return out
    }

    /** 四边形拆成两个三角形：(0,1,2) + (0,2,3) */
    fun sampleQuad(v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3, density: Int, out: MutableList<Vec3>): MutableList<Vec3> {
        sampleTriangle(v0, v1, v2, density, out)
        sampleTriangle(v0, v2, v3, density, out)
        return out
    }

    private fun perpendicular(axis: Vec3): Vec3 {
        val base = if (abs(axis.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        return normalizedOr(axis.cross(base), Vec3(1.0, 0.0, 0.0))
    }

    private fun normalizedOr(value: Vec3, fallback: Vec3): Vec3 {
        return if (value.lengthSqr() <= 1.0E-6) fallback.normalize() else value.normalize()
    }
}
