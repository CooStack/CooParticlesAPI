package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.core.BlockPos

/**
 * 在原版区块顶点写入过程中补充地形效果数据。
 *
 * 包装器原样转发位置、颜色和 UV0，在法线提交时根据完整顶点状态计算 UV1，
 * 并把位置生效 tick 写入 light UV 两个分量的空闲高位。调用顺序遵循原版 [VertexConsumer] 契约。
 *
 * @property delegate 接收最终顶点数据的原始消费者
 * @property mode 当前 Pipeline 的效果 UV 计算模式
 * @property blockPos 当前方块的世界坐标
 * @param activatedAt 该位置的绝对生效 tick；静态 Pipeline 使用 `0`
 */
internal class CooEffectUvVertexConsumer(
    private val delegate: VertexConsumer,
    private val mode: CooEffectUvMode,
    private val blockPos: BlockPos,
    activatedAt: Long
) : VertexConsumer {
    /** 写入顶点光照通道的绝对生效 tick。 */
    private val activatedAt = activatedAt
    private var x = 0F
    private var y = 0F
    private var z = 0F
    private var baseU = 0F
    private var baseV = 0F

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
        this.x = x
        this.y = y
        this.z = z
        delegate.addVertex(x, y, z)
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply {
        delegate.setColor(red, green, blue, alpha)
    }

    override fun setUv(u: Float, v: Float): VertexConsumer = apply {
        baseU = u
        baseV = v
        delegate.setUv(u, v)
    }

    /**
     * 忽略原调用方提交的 UV1，因为该通道由 EffectUV 独占。
     *
     * @return 当前包装器
     */
    override fun setUv1(u: Int, v: Int): VertexConsumer = this

    /**
     * 转发原版 light UV，并在两个分量的高八位写入生效 tick。
     *
     * @return 当前包装器
     */
    override fun setUv2(u: Int, v: Int): VertexConsumer = apply {
        val packed = CooEffectUvResolver.packLightWithActivation(
            (u and 0xFFFF) or ((v and 0xFFFF) shl 16),
            activatedAt
        )
        val packedU = packed and 0xFFFF
        val packedV = (packed ushr 16) and 0xFFFF
        delegate.setUv2(packedU, packedV)
    }

    /**
     * 使用此前收集的位置、UV0 和当前法线计算 EffectUV，然后提交完整顶点法线。
     *
     * @return 当前包装器
     */
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply {
        val effectUv = CooEffectUvResolver.resolve(
            mode = mode,
            blockPos = blockPos,
            x = this.x,
            y = this.y,
            z = this.z,
            baseU = baseU,
            baseV = baseV,
            normalX = x,
            normalY = y,
            normalZ = z
        )
        delegate.setUv1(CooEffectUvResolver.pack(effectUv.u), CooEffectUvResolver.pack(effectUv.v))
        delegate.setNormal(x, y, z)
    }
}
