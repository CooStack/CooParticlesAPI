package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.client.renderer.oit.OitStage

/**
 * 一个执行中的原版 OIT terrain 调用所借用的纹理名称，不拥有任何 FrameGraph 资源。
 * @property stage 当前深度范围、透射或颜色累积阶段
 * @property depthBoundsTexture 当前原版深度范围纹理名称
 * @property coefficientTextures 当前原版透射系数纹理名称，按 Coeff0 开始排列
 */
internal data class CooTerrainOitContext(
    val stage: OitStage,
    val depthBoundsTexture: Int,
    val coefficientTextures: List<Int>
)
