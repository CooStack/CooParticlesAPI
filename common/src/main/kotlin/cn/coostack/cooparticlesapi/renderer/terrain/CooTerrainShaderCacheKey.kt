package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.oit.OitStage

/**
 * 地形 shader 缓存键。
 *
 * @property shaderId shader 资源 ID
 * @property descriptor 根据 Pipeline 输入和 uniform 生成的完整描述文本
 * @property oitStage 原版 OIT 阶段，普通或 attachment 捕获程序为空
 * @property additive 是否使用 OIT 加法贡献
 * @property zeroToOne OIT 原生深度是否使用零至一 NDC
 */
internal data class CooTerrainShaderCacheKey(
    val shaderId: Identifier,
    val descriptor: String,
    val oitStage: OitStage? = null,
    val additive: Boolean = false,
    val zeroToOne: Boolean = false
)
