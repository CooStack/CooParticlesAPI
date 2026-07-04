package cn.coostack.cooparticlesapi.renderer.shader.pipe.manager

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.MCHookedShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.LinkedList
import java.util.function.Supplier

class ShaderPipeManager(val pipeID: ResourceLocation) {
    val pipes = LinkedList<ShaderPipe>()
    val beforeInitPipe = mutableListOf<ShaderPipeManager.() -> Unit>()
    var inputDepthAttachment = -1
    var useDepth = true
    private var initialized = false

    fun init() {
        if (initialized) {
            return
        }
        beforeInitPipe.forEach { it.invoke(this) }
        if (pipes.isEmpty()) {
            addPipe(SimpleShaderPipe(screenFragment(), Supplier { inputDepthAttachment }))
        }
        pipes.forEach { it.init() }
        initialized = true
    }

    fun beforeInit(invoke: ShaderPipeManager.() -> Unit): ShaderPipeManager {
        beforeInitPipe.add(invoke)
        return this
    }

    fun addMcHookedPipe(): ShaderPipeManager {
        return addPipe(MCHookedShaderPipe(screenFragment(), Minecraft.getInstance().mainRenderTarget))
    }

    fun addPipe(pipe: ShaderPipe): ShaderPipeManager {
        if (initialized) {
            pipe.init()
        }
        pipes.add(pipe)
        return this
    }

    fun writeFrame(invoker: Runnable): ShaderPipeManager {
        init()
        val first = pipes.firstOrNull() ?: return this
        if (useDepth) {
            RenderSystem.depthMask(true)
        }
        first.write {
            invoker.run()
        }
        return this
    }

    fun render() {
        init()
        if (pipes.isEmpty()) {
            return
        }
        var output = pipes.first().getFrameOutput()
        pipes.asSequence().drop(1).forEach { pipe ->
            output = pipe.writeFromChannel(output).getFrameOutput()
        }
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        pipes.last().drawPipeFrame()
        RenderSystem.disableBlend()
    }

    fun release() {
        pipes.forEach { it.release() }
        pipes.clear()
        initialized = false
    }

    fun resize(width: Int, height: Int) {
        pipes.forEach { it.resize(width, height) }
    }

    private fun screenFragment(): IdentifierShader {
        return IdentifierShader(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
            GlShaderType.FRAGMENT
        )
    }
}
