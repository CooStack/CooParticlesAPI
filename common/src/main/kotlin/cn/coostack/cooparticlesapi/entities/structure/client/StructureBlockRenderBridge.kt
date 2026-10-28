package cn.coostack.cooparticlesapi.entities.structure.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/** 结构的客户端方块渲染平台边界，不从专用服务器初始化。 */
interface StructureBlockRenderBridge {
    /**
     * 将结构局部方块的各材质层提交到当前帧队列，并保留平台模型扩展数据。
     *
     * 示例：按模型提供的每个 `RenderType` 分别调用 `submitCustomGeometry`。
     *
     * @param view 只读结构邻接及变换后光照视图
     * @param pos 结构局部方块坐标
     * @param state 快照中的方块状态
     * @param matrices 已平移到当前方块的姿势
     * @param collector 当前阶段的提交收集器
     * @param random 每块按状态种子重置的随机源
     * @param overlay 原版伤害覆盖纹理坐标
     */
    fun submit(
        view: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        random: RandomSource,
        overlay: Int,
    )

    /**
     * 按流体模型所属层提交结构中的一个流体单元。
     *
     * 示例：在 `FluidRenderer.tesselate` 的输出回调中，将 `ChunkSectionLayer` 映射到对应 RenderType。
     *
     * @param view 只读结构邻接及变换后位置光照视图
     * @param pos 结构局部方块坐标
     * @param state 快照中的宿主方块状态
     * @param fluidState 当前流体状态
     * @param matrices 已平移到当前方块的姿势
     * @param collector 当前阶段的提交收集器
     * @param overlay 原版伤害覆盖纹理坐标
     */
    fun submitFluid(
        view: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        fluidState: FluidState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        overlay: Int,
    )
}
