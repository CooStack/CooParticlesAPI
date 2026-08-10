package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.client.renderer.RenderType

/**
 * 需要在 Iris 地形合成覆盖阶段结束后执行的一次原版 section 绘制。
 *
 * @property renderType 原版 section 绘制层
 * @property draw 在正确 GL 状态和帧缓冲目标下执行的绘制回调
 */
internal data class DeferredVanillaDraw(
    val renderType: RenderType,
    val draw: Runnable
)
