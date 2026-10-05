package cn.coostack.cooparticlesapi.entities.structure.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** 结构的客户端方块渲染平台边界，不从专用服务器初始化。 */
interface StructureBlockRenderBridge {
    /** 模型是否允许进入静态网格缓存；动态数据或未知渲染扩展必须返回 false。 */
    fun canCache(model: BakedModel): Boolean

    /**
     * 绘制一个结构局部方块，保留平台模型数据、渲染层和覆盖纹理。
     * @param view 只读结构邻接及变换后光照视图
     * @param pos 结构局部方块坐标
     * @param state 快照中的方块状态
     * @param display 只读展示方块实体，不执行 tick
     * @param matrices 已平移到当前方块的矩阵，方法不得改变调用方矩阵
     * @param buffers 本次实体渲染的缓冲源
     * @param random 每块按状态种子重置的随机源
     * @param overlay 原版伤害覆盖纹理坐标
     */
    fun render(view: BlockAndTintGetter, pos: BlockPos, state: BlockState, display: BlockEntity?,
               matrices: PoseStack, buffers: MultiBufferSource, random: RandomSource, overlay: Int)
}
