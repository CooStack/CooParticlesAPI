package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData.Companion.particleTexturesMapper
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.server.packs.resources.ResourceManager

object CooParticleTextureSheet {

    @JvmStatic
    val sheets = mutableListOf<ParticleRenderType>()
    private var boundedTranslucentShader: ShaderInstance? = null

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

    /**
     * 使用带透明度的加法混合粒子, 但不写入深度.
     *
     * 适合大量半透明发光粒子叠加: 仍使用 vanilla 粒子 atlas 和 PARTICLE 顶点格式,
     * 只关闭深度写入以避免先渲染的半透明粒子挡住后面的半透明粒子.
     *
     * 缺点、会被云层穿透 不过开光影的情况下似乎不会出现这个问题
     */
    @JvmStatic
    val ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE = register(object : ParticleRenderType {
        override fun begin(
            tesselator: Tesselator,
            manager: TextureManager
        ): BufferBuilder {
            RenderSystem.depthMask(false)
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
            return "ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE"
        }
    })

    /**
     * 使用原版半透明混合但不写入深度。
     *
     * 示例：大量烟雾或薄片互相穿插时使用此层，Alpha 为零时不会改变背景。
     * 禁止把它用于需要遮挡后续粒子的实体表面。
     */
    @JvmStatic
    val PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE = registerSheet(
        "PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE",
        false,
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ZERO,
    )

    /**
     * 使用有界 Screen Blend，并写入深度。
     *
     * 示例：需要提亮背景但不能在 HDR 目标中无限累加时使用此层。
     * 禁止把它当作支持逐粒子 Alpha 的普通半透明层。
     */
    @JvmStatic
    val ADDITION_BLEND_NOT_HDR = registerSheet(
        "ADDITION_BLEND_NOT_HDR",
        true,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
        GlStateManager.SourceFactor.ZERO,
        GlStateManager.DestFactor.ONE,
    )

    /**
     * 使用有界 Screen Blend，但不写入深度。
     *
     * 示例：大量非透明发光薄片叠加时可避免深度互相截断。
     * 禁止用于必须写入深度的遮挡物。
     */
    @JvmStatic
    val ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE = registerSheet(
        "ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE",
        false,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
        GlStateManager.SourceFactor.ZERO,
        GlStateManager.DestFactor.ONE,
    )

    /**
     * 使用有界 Screen Blend，并写入深度。
     *
     * 示例：大量同色粒子叠加时，颜色会继续趋近白色，但不会超过颜色上限。
     * 禁止改回普通 Alpha 混合，否则叠加结果只会趋近源颜色。
     */
    @JvmStatic
    val ADDITION_BLEND_TRANSLUCENT_NOT_HDR = registerSheet(
        "ADDITION_BLEND_TRANSLUCENT_NOT_HDR",
        true,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
        GlStateManager.SourceFactor.ZERO,
        GlStateManager.DestFactor.ONE,
        true,
    )

    /**
     * 使用有界 Screen Blend，但不写入深度。
     *
     * 示例：大量非 HDR 粒子互相穿插时使用此层，重叠颜色仍会趋近白色。
     * 禁止用于需要由粒子深度遮挡后续几何的场景。
     */
    @JvmStatic
    val ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE = registerSheet(
        "ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE",
        false,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
        GlStateManager.SourceFactor.ZERO,
        GlStateManager.DestFactor.ONE,
        true,
    )

    fun init() {
        ControlableParticleData.registerRenderType(ParticleRenderType.PARTICLE_SHEET_LIT)
        ControlableParticleData.registerRenderType(ParticleRenderType.TERRAIN_SHEET)
        ControlableParticleData.registerRenderType(ParticleRenderType.NO_RENDER)
        ControlableParticleData.registerRenderType(ParticleRenderType.CUSTOM)
        ControlableParticleData.registerRenderType(ParticleRenderType.PARTICLE_SHEET_OPAQUE)
        ControlableParticleData.registerRenderType(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
    }

    /** 资源重载后重新创建有界透明粒子 shader。 */
    fun reloadShader(resourceManager: ResourceManager) {
        releaseShader()
        boundedTranslucentShader = ShaderInstance(
            resourceManager,
            "coo_particle_screen",
            DefaultVertexFormat.PARTICLE,
        ).also(IrisCompat::markUnskippable)
    }

    /** 释放当前资源周期持有的有界透明粒子 shader。 */
    fun releaseShader() {
        boundedTranslucentShader?.close()
        boundedTranslucentShader = null
    }

    fun register(type: ParticleRenderType): ParticleRenderType {
        sheets.add(type)
        ControlableParticleData.registerRenderType(type)
        return type
    }

    /**
     * 创建并注册一个使用粒子图集与 PARTICLE 顶点格式的渲染层。
     *
     * 示例：传入 `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` 创建普通半透明层。
     * 禁止传入依赖自定义顶点属性或自定义纹理绑定的混合配置。
     *
     * @param name ParticleRenderType 的稳定名称
     * @param depthWrite 是否写入深度
     * @param sourceRgb RGB 源混合因子
     * @param destinationRgb RGB 目标混合因子
     * @param sourceAlpha Alpha 源混合因子
     * @param destinationAlpha Alpha 目标混合因子
     * @param useBoundedTranslucentShader 是否在最终片元 Alpha 已知后执行有界预乘
     * @return 已注册的 ParticleRenderType
     */
    private fun registerSheet(
        name: String,
        depthWrite: Boolean,
        sourceRgb: GlStateManager.SourceFactor,
        destinationRgb: GlStateManager.DestFactor,
        sourceAlpha: GlStateManager.SourceFactor,
        destinationAlpha: GlStateManager.DestFactor,
        useBoundedTranslucentShader: Boolean = false,
    ): ParticleRenderType = register(object : ParticleRenderType {
        override fun begin(tesselator: Tesselator, manager: TextureManager): BufferBuilder {
            RenderSystem.depthMask(depthWrite)
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            if (useBoundedTranslucentShader) {
                RenderSystem.setShader { boundedTranslucentShader ?: GameRenderer.getParticleShader() }
            }
            RenderSystem.enableBlend()
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString(): String = name
    })
}
