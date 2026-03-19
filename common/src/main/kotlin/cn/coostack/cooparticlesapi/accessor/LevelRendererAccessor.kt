package cn.coostack.cooparticlesapi.accessor

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.renderer.RenderBuffers

interface LevelRendererAccessor {
    fun renderBuffers(): RenderBuffers
    fun translucentTarget(): RenderTarget?
    fun itemEntityTarget(): RenderTarget?
    fun particlesTarget(): RenderTarget?
    fun weatherTarget(): RenderTarget?
    fun cloudsTarget(): RenderTarget?
    fun hasTransparencyChain(): Boolean
}
