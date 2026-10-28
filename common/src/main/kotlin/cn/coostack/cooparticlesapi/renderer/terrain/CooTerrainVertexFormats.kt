package cn.coostack.cooparticlesapi.renderer.terrain

import com.mojang.renderpearl.api.vertex.VertexFormat
import com.mojang.renderpearl.api.GpuFormat

/** Terrain 顶点格式：图集 UV、效果 UV 和 light UV 使用互不覆盖的元素。 */
internal object CooTerrainVertexFormats {
    /** Pipeline 与地形覆盖层共同使用的只读布局注册单例，三个 UV 通道互相独立。 */
    @JvmField
    val BLOCK_EFFECT: VertexFormat = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("UV1", GpuFormat.RG16_SINT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
        .build()
}
