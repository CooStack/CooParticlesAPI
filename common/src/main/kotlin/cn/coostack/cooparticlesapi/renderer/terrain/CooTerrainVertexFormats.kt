package cn.coostack.cooparticlesapi.renderer.terrain

import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement

/** Terrain 顶点格式：图集 UV、效果 UV 和 light UV 使用互不覆盖的元素。 */
internal object CooTerrainVertexFormats {
    @JvmField
    val BLOCK_EFFECT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("BaseUV", VertexFormatElement.UV0)
        .add("EffectUV", VertexFormatElement.UV1)
        .add("LightUV", VertexFormatElement.UV2)
        .add("Normal", VertexFormatElement.NORMAL)
        .build()
}
