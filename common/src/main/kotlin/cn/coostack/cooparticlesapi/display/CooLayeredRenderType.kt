package cn.coostack.cooparticlesapi.display

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType

/** 保存多层展示名称与各层管线描述，不持有当前帧资源。 */
data class CooLayeredRenderTypeDescriptor(
    val name: String,
    val layers: List<CooRenderTypeDescriptor>
)

/** 把同一份几何分别提交到多个材质层，回调由提交队列延迟执行。 */
class CooLayeredRenderType(
    val name: String,
    val layers: List<RenderType>
) {
    /**
     * 为每个材质层分别提交一份几何，并在该层的回调中合并顶点写入。
     *
     * 示例：`layered.submit(collector, poseStack) { pose, vertices -> emit(pose, vertices) }`。
     * collector 回调延迟执行，因此顶点接收器只能在回调内部使用，不能返回给调用方缓存。
     *
     * @param collector 当前渲染阶段的命令收集器
     * @param poseStack 提交时使用的变换姿势
     * @param draw 为每个层写入同一份顶点几何
     */
    fun submit(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        draw: (PoseStack.Pose, VertexConsumer) -> Unit,
    ) {
        layers.forEach { layer ->
            collector.submitCustomGeometry(poseStack, layer) { pose, vertices ->
                draw(pose, vertices)
            }
        }
    }
}

/** 将顶点的全部属性广播给同一次提交中的多个接收器，不允许跨提交缓存。 */
class LayeredVertexConsumer(
    private val delegates: List<VertexConsumer>
) : VertexConsumer {
    init {
        require(delegates.isNotEmpty()) { "layered vertex consumer requires at least one delegate" }
    }

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegates.forEach { it.addVertex(x, y, z) }
        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        delegates.forEach { it.setColor(red, green, blue, alpha) }
        return this
    }

    override fun setColor(color: Int): VertexConsumer {
        delegates.forEach { it.setColor(color) }
        return this
    }

    override fun setUv(u: Float, v: Float): VertexConsumer {
        delegates.forEach { it.setUv(u, v) }
        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer {
        delegates.forEach { it.setUv1(u, v) }
        return this
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer {
        delegates.forEach { it.setUv2(u, v) }
        return this
    }

    override fun setUv3(u: Float, v: Float): VertexConsumer {
        delegates.forEach { it.setUv3(u, v) }
        return this
    }

    override fun setNormal(normalX: Float, normalY: Float, normalZ: Float): VertexConsumer {
        delegates.forEach { it.setNormal(normalX, normalY, normalZ) }
        return this
    }

    override fun setLineWidth(width: Float): VertexConsumer {
        delegates.forEach { it.setLineWidth(width) }
        return this
    }
}
