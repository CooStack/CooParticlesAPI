package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.mixin.events.world.client.ItemRendererInvoker
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 世界坐标变换
 * T * R * S * Local
 * 位移 - 旋转 - 缩放 - 相对位置
 *
 */
object MinecraftRendererUtil {
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
        transformTo(camera.position, to, stack, invoker)
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

        stack.mulPose(q)
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
        stack.mulPose(alignQ)
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
        val yaw = Math3DUtil.getYawFromLocation(originalAxis) * 180 / PI + 90f
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


    fun applyAtPoint(point: Vec3, stack: PoseStack, invoker: PoseStack.() -> Unit) {
        stack.translate(point.x, point.y, point.z)
        invoker(stack)
        stack.translate(-point.x, -point.y, -point.z)
    }

    fun renderItemModel(
        renderer: ItemRenderer,
        stack: ItemStack,
        pose: PoseStack,
        model: BakedModel,
        light: Int,
        overlay: Int,
        consumer: VertexConsumer
    ) {
        renderer as ItemRendererInvoker
        renderer.renderModel(model, stack, light, overlay, pose, consumer)
    }

    fun renderItemModel(
        renderer: ItemRenderer,
        stack: ItemStack,
        pose: PoseStack,
        model: BakedModel,
        light: Int,
        overlay: Int,
        buffer: MultiBufferSource,
        type: RenderType
    ) {
        renderItemModel(renderer, stack, pose, model, light, overlay, buffer.getBuffer(type))
    }

    fun Float.asFloatRadian(): Float {
        return this * PI.toFloat() / 180f
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

}