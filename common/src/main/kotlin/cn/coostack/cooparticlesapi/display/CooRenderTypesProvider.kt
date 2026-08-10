package cn.coostack.cooparticlesapi.display

import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline

/**
 * 把 common 模块的渲染描述转换为当前加载器可用的 [RenderType]。
 *
 * Fabric 与 NeoForge 分别实现缓存、Iris 包装和 terrain 状态组装。common 渲染代码只依赖本接口，
 * 调用方可依赖相同描述在同一资源生命周期内返回可复用的 RenderType。
 */
interface CooRenderTypesProvider {
    /**
     * 获取 API 默认的加法混合发光 RenderType。
     *
     * @return 当前平台缓存或创建的发光 RenderType
     */
    fun glow(): RenderType

    /**
     * 创建使用完整亮度和指定纹理的实体裁剪发光 RenderType。
     *
     * @param texture 实体纹理资源
     * @return 适用于实体四边形的发光 RenderType
     */
    fun entityCutoutEmissive(texture: ResourceLocation): RenderType {
        return entityCutoutEmissive(texture, 1F)
    }

    /**
     * 创建使用指定纹理和亮度倍率的实体裁剪发光 RenderType。
     *
     * @param texture 实体纹理资源
     * @param brightness 发光亮度倍率；实现会把负值限制为 `0`
     * @return 适用于实体四边形的发光 RenderType
     */
    fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float): RenderType

    /**
     * 创建使用指定纹理、亮度倍率和透明度的实体裁剪发光 RenderType。
     *
     * 不支持独立透明度的平台可以退化为 [entityCutoutEmissive] 的亮度重载。
     *
     * @param texture 实体纹理资源
     * @param brightness 发光亮度倍率；实现会把负值限制为 `0`
     * @param alpha 透明度，平台实现应限制到 `0.0F..1.0F`
     * @return 适用于实体四边形的发光 RenderType
     */
    fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float, alpha: Float): RenderType {
        return entityCutoutEmissive(texture, brightness)
    }

    /**
     * 根据不可变描述创建或复用一个 RenderType。
     *
     * @param descriptor 顶点格式、绘制模式和渲染状态描述
     * @return 与 [descriptor] 对应的平台 RenderType
     */
    fun create(descriptor: CooRenderTypeDescriptor): RenderType

    /**
     * 为区块批处理创建使用扩展方块顶点格式的 terrain layer。
     *
     * [batchKey] 在同一批次只更新 uniform 时应保持不变，使实现能够复用 RenderType；
     * 当几何必须进入不同批次时应提供不同键。
     *
     * @param pipeline 为该批次提供 terrain shader、顶点 UV 模式和渲染配置的 Pipeline
     * @param baseLayer 原始方块几何所属的原版 terrain layer
     * @param batchKey 标识可共享 RenderType 的批次，按相等性参与平台缓存
     * @return 使用地形扩展顶点格式和对应渲染状态的 RenderType
     */
    fun terrain(
        pipeline: CooRenderPipeline<BlockState>,
        baseLayer: RenderType,
        batchKey: Any = pipeline
    ): RenderType

    /**
     * 在 shader reload 或客户端资源释放时清空平台 terrain layer 缓存。
     *
     * 实现不得关闭由 Minecraft 或其他缓存拥有的原版 RenderType。
     */
    fun clearTerrainCache() = Unit

    /**
     * 按资源 ID 查找已注册描述并创建对应 RenderType。
     *
     * @param id [CooRenderTypeResourceRegistry] 中的描述 ID
     * @return 对应 RenderType；ID 未注册时返回 `null`
     */
    fun named(id: ResourceLocation): RenderType? {
        return CooRenderTypeResourceRegistry.get(id)?.let(::create)
    }

    /**
     * 按给定顺序组合多个 RenderType。
     *
     * @param name 组合 RenderType 的调试名称
     * @param layers 按绘制顺序排列的子层
     * @return 包含这些子层的 [CooLayeredRenderType]
     */
    fun layered(name: String, vararg layers: RenderType): CooLayeredRenderType {
        return CooLayeredRenderType(name, layers.toList())
    }

    /**
     * 根据分层描述创建组合 RenderType。
     *
     * @param descriptor 组合名称及各子层描述
     * @return 按描述顺序创建的 [CooLayeredRenderType]
     */
    fun layered(descriptor: CooLayeredRenderTypeDescriptor): CooLayeredRenderType {
        return CooLayeredRenderType(
            descriptor.name,
            descriptor.layers.map(::create)
        )
    }

    /**
     * 按资源 ID 查找已注册的分层描述并创建组合 RenderType。
     *
     * @param id [CooRenderTypeResourceRegistry] 中的分层描述 ID
     * @return 对应组合 RenderType；ID 未注册时返回 `null`
     */
    fun layered(id: ResourceLocation): CooLayeredRenderType? {
        return CooRenderTypeResourceRegistry.getLayered(id)?.let(::layered)
    }
}
