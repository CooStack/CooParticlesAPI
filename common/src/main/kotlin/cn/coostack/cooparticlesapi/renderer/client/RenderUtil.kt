package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Matrix4fStack

object RenderUtil {
    @JvmStatic
    /**
     * 更新 `RenderUtil` 的 `setRenderStackWithEntity` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setRenderStackWithEntity(stack = stack, entity = entity, tickDelta = tickDelta)`。
     *
     * @param stack 当前对象使用的模型变换或矩阵栈
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun setRenderStackWithEntity(
        stack: Matrix4fStack,
        entity: RenderEntity,
        tickDelta: Float
    ): Matrix4fStack {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position.toVector3f()
        val last = entity.lastRenderPos.toVector3f()
        val now = entity.pos.toVector3f()
        val x = Mth.lerp(tickDelta, last.x, now.x)
        val y = Mth.lerp(tickDelta, last.y, now.y)
        val z = Mth.lerp(tickDelta, last.z, now.z)
        stack.translate(x - camera.x, y - camera.y, z - camera.z)
        return stack
    }

    @JvmStatic
    /**
     * 根据输入和 `RenderUtil` 当前配置创建 `buildModelMatrix` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`buildModelMatrix(entity = entity, tickDelta = tickDelta)`。
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @return 根据当前输入生成的新对象或数据结果
     */
    fun buildModelMatrix(entity: RenderEntity, tickDelta: Float): Matrix4f {
        return Matrix4f(setRenderStackWithEntity(Matrix4fStack(16), entity, tickDelta))
    }
}
