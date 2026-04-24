package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData.Companion.particleTexturesMapper
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
     *
     * 算是个半透明粒子吧？ 但是alpha由于混合算法问题不怎么生效
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

    /**
     * 使用带透明度的加法混合粒子
     *
     * 和上面比起来 alpha的参与更强 （alpha * rgb)
     */
    @JvmStatic
    val ADDITION_BLEND_TRANSLUCENT = register(object : ParticleRenderType {
        override fun begin(
            tesselator: Tesselator,
            manager: TextureManager
        ): BufferBuilder {
            // 半透明
            RenderSystem.depthMask(true)
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            RenderSystem.enableBlend()
            RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE
            )
            return tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE
            )
        }

        override fun toString(): String {
            return "ADDITION_BLEND_TRANSLUCENT"
        }
    })

    fun init() {
        ControlableParticleData.registerRenderType(ParticleRenderType.PARTICLE_SHEET_LIT)
        ControlableParticleData.registerRenderType(ParticleRenderType.TERRAIN_SHEET)
        ControlableParticleData.registerRenderType(ParticleRenderType.NO_RENDER)
        ControlableParticleData.registerRenderType(ParticleRenderType.CUSTOM)
        ControlableParticleData.registerRenderType(ParticleRenderType.PARTICLE_SHEET_OPAQUE)
        ControlableParticleData.registerRenderType(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
    }

    fun register(type: ParticleRenderType): ParticleRenderType {
        sheets.add(type)
        ControlableParticleData.registerRenderType(type)
        return type
    }
}
