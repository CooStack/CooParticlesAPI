package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesConstants
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
    val particleTexturesMapper: MutableMap<String, ParticleRenderType> = mutableMapOf()

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

    @JvmStatic
    fun registerRenderType(type: ParticleRenderType) {
        particleTexturesMapper[type.toString()] = type
    }

    @JvmStatic
    fun fromString(sheet: String): ParticleRenderType? = particleTexturesMapper[sheet]

    @JvmStatic
    fun getOrDefault(sheet: String): ParticleRenderType {
        return fromString(sheet) ?: run {
            CooParticlesConstants.logger.error(
                "can not find textureSheet $sheet, register it via CooParticleTextureSheet.registerRenderType()"
            )
            ParticleRenderType.PARTICLE_SHEET_OPAQUE
        }
    }

    fun init() {
        registerRenderType(ParticleRenderType.PARTICLE_SHEET_LIT)
        registerRenderType(ParticleRenderType.TERRAIN_SHEET)
        registerRenderType(ParticleRenderType.NO_RENDER)
        registerRenderType(ParticleRenderType.CUSTOM)
        registerRenderType(ParticleRenderType.PARTICLE_SHEET_OPAQUE)
        registerRenderType(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
    }

    fun register(type: ParticleRenderType): ParticleRenderType {
        sheets.add(type)
        registerRenderType(type)
        return type
    }
}

fun ControlableParticleData.setTextureSheet(value: ParticleRenderType) {
    setTextureSheet(value.toString())
}
