package cn.coostack.cooparticlesapi.accessor

import net.minecraft.client.renderer.RenderBuffers

interface LevelRendererAccessor {
    fun renderBuffers(): RenderBuffers
}