package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/** 描述区域 wire 类型的稳定注册标签，类型名不依赖 Kotlin 类名。 */
enum class CooTerrainMappingRegionType(val id: ResourceLocation) {
    /** 三维球形区域，中心和半径使用绝对世界坐标。 */
    SPHERE(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "sphere"));

    companion object {
        /** 将不受信任的 wire 标签解析为已注册区域类型。 */
        fun fromId(id: ResourceLocation): CooTerrainMappingRegionType? = entries.firstOrNull { it.id == id }
    }
}
