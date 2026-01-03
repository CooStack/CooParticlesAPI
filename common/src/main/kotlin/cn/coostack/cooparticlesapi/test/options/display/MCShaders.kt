package cn.coostack.cooparticlesapi.test.options.display

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.server.packs.resources.ResourceManager

/**
 * 事实证明， 即使使用原版API应用的着色器也不兼容iris
 *
 * @constructor Create empty M c shaders
 */
object MCShaders {

    lateinit var GLOW: ShaderInstance
        private set

    fun init(resourceManager: ResourceManager) {
        GLOW = ShaderInstance(
            resourceManager,
            "coo_glow",
            DefaultVertexFormat.POSITION_COLOR
        )
    }
}