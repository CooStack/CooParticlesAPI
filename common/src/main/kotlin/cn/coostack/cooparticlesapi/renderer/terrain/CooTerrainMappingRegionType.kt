package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/**
 * 描述程序化地形映射区域的稳定网络类型。
 *
 * `id` 是网络协议和跨端模板契约的一部分，不依赖 Kotlin 枚举成员名称。
 * `shaderValue` 是着色器中使用的形状分支值；新增成员时必须保持已有值不变。
 * 所有当前形状都使用绝对世界坐标，BOX 为轴对齐长方体，CYLINDER 为沿 Y 轴的圆柱体。
 */
enum class CooTerrainMappingRegionType(
    val id: ResourceLocation,
    val shaderValue: Int
) {
    /** 三维球形区域，参数为中心和半径。 */
    SPHERE(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "sphere"), 0),

    /** 轴对齐长方体区域，参数为中心和三个半轴长度；正方体是三个半轴相等的 BOX。 */
    BOX(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "box"), 1),

    /** 沿 Y 轴的圆柱体区域，参数为中心、半径和完整高度。 */
    CYLINDER(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "cylinder"), 2);

    companion object {
        /** 将不受信任的 wire 标签解析为已注册区域类型。 */
        fun fromId(id: ResourceLocation): CooTerrainMappingRegionType? = entries.firstOrNull { it.id == id }
    }
}
