package cn.coostack.cooparticlesapi.entities.structure.client

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import com.mojang.blaze3d.vertex.PoseStack

/**
 * 在网格构建时记录实际使用的纹理，顶点仍由原版写入器处理。
 * @property target 原版网格构建器，不由本对象关闭
 * @property sprites 当前网格拥有的纹理集合，用于 Sodium 可见动画通知
 */
internal class ModelMeshVertices(private val target: VertexConsumer, private val sprites: MutableSet<TextureAtlasSprite>) :
    VertexConsumer by target {
    override fun putBulkData(entry: PoseStack.Pose, quad: BakedQuad, red: Float, green: Float, blue: Float,
                      alpha: Float, light: Int, overlay: Int) {
        sprites.add(quad.sprite)
        target.putBulkData(entry, quad, red, green, blue, alpha, light, overlay)
    }

    override fun putBulkData(entry: PoseStack.Pose, quad: BakedQuad, brightnesses: FloatArray,
                      red: Float, green: Float, blue: Float, alpha: Float, lights: IntArray,
                      overlay: Int, useQuadColorData: Boolean) {
        sprites.add(quad.sprite)
        target.putBulkData(entry, quad, brightnesses, red, green, blue, alpha, lights, overlay, useQuadColorData)
    }
}
