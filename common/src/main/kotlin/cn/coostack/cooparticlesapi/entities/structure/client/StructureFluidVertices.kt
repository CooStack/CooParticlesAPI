package cn.coostack.cooparticlesapi.entities.structure.client

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos

/** 将原版流体的区块局部顶点转到模型矩阵，并补齐实体层需要的红色覆盖。
 * @property target 原版批次顶点接收器
 * @property entry 当前方块的变换矩阵
 * @property pos 流体在结构里的局部坐标
 * @property overlay 本帧的受伤或死亡覆盖
 * @property packedLight 当前流体方块在变换后位置的打包光照，已合并自身发光等级
 */
class StructureFluidVertices(private val target: VertexConsumer, private val entry: PoseStack.Pose,
                         private val pos: BlockPos, private val overlay: Int, private val packedLight: Int) : VertexConsumer {
    /** 返回当前包装器，使后续颜色与光照调用继续保留模型变换。 */
    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        // 原版流体直接写入区块局部坐标，先减去当前方块格再应用已包含平移的模型矩阵。
        target.addVertex(entry.pose(), x - (pos.x and 15), y - (pos.y and 15), z - (pos.z and 15)).setOverlay(overlay)
        return this
    }
    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer { target.setColor(red, green, blue, alpha); return this }
    override fun setUv(u: Float, v: Float): VertexConsumer { target.setUv(u, v); return this }
    override fun setUv1(u: Int, v: Int): VertexConsumer { target.setOverlay(overlay); return this }
    override fun setUv2(u: Int, v: Int): VertexConsumer { target.setLight(packedLight); return this }
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer { target.setNormal(entry, x, y, z); return this }
}
