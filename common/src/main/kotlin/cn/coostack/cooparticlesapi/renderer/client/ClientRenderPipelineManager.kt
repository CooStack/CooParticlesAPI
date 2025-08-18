package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.shader.pipe.ShaderPipeManager
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

object ClientRenderPipelineManager {
    val registerPipeLines = HashMap<ResourceLocation, ShaderPipeManager>()
    fun getPipeManager(id: ResourceLocation): ShaderPipeManager? = registerPipeLines[id]
    val minecraft: Minecraft get() = Minecraft.getInstance()
    var width = 1920
    var height = 1080
    var initialized = false
    fun register(pipe: ShaderPipeManager) {
        registerPipeLines[pipe.pipeID] = pipe
        if (initialized) {
            pipe.depthSupplier = Supplier {
                minecraft.mainRenderTarget.depthTextureId
            }
            pipe.init()
        }
    }

    fun init() {
        for (manager in registerPipeLines.values) {
            manager.depthSupplier = Supplier {
                minecraft.mainRenderTarget.depthTextureId
            }
            manager.resize(width, height)
            manager.init()
        }
    }

    fun release() {
        registerPipeLines.onEach {
            it.value.release()
        }.clear()
    }


    fun resizeTo(width: Int, height: Int) {
        this.width = width
        this.height = height
        registerPipeLines.onEach {
            it.value.resize(width, height)
        }
    }

}