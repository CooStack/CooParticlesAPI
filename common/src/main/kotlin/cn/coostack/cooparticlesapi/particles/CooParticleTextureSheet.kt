package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureManager

object CooParticleTextureSheet {

    @JvmStatic
    val sheets = mutableListOf<ParticleRenderType>()

    /**
     * 使用加法混合不透明度粒子
     */
    @JvmStatic
    val ADDITION_BLEND = register(object : ParticleRenderType {
        override fun begin(
            tesselator: Tesselator,
            manager: TextureManager
        ): BufferBuilder {
            RenderSystem.depthMask(true)
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            RenderSystem.enableBlend()
            RenderSystem.blendFunc(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE
            )
            return tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE
            )
        }

        override fun toString(): String {
            return "ADDITION_BLEND"
        }
    })

    fun init() {
    }

    fun register(type: ParticleRenderType): ParticleRenderType {
        sheets.add(type)
        ControlableParticleData.registerRenderType(type)
        return type
    }
}