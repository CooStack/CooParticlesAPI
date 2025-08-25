package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.OutputDepthPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

object ShaderPipes {
    fun simpleScreenOutput(depthSupplier: Supplier<Int>): ShaderPipe {
        return SimpleShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                GlShaderType.FRAGMENT
            ), depthSupplier
        )
    }


    fun depthTexture(depthSupplier: Supplier<Int>): ShaderPipe {
        return OutputDepthPipe(depthSupplier)
    }
}