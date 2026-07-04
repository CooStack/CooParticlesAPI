package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import net.minecraft.resources.ResourceLocation

object ShaderPipeManagers {
    val default = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "default")
    )
    val simpleBloom = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "simple_bloom")
    )

    fun init() {
        default.init()
        simpleBloom.init()
    }

    fun release() {
        default.release()
        simpleBloom.release()
    }
}
