package cn.coostack.cooparticlesapi.renderer.shader.pipe

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.*
import java.util.LinkedList
import java.util.function.Supplier

/**
 * @param depthSupplier 共享的深度
 */
class ShaderPipeManager(
    val pipeID: ResourceLocation,
) {
    private var initialized = false
    private val screenBuffer = VertexBuffers.getScreenBuffer()
    val pipes = LinkedList<ShaderPipe>()

    /**
     * 如果让他自动生成 depthAttachment
     * 则返回-1
     */
    var depthSupplier: Supplier<Int> = Supplier { -1 }
    val beforeInitPipe = mutableListOf<ShaderPipeManager.() -> Unit>()

    /**
     * depth mask
     */
    var useDepth = true
    fun init() {
        if (initialized) {
            return
        }

        beforeInitPipe.forEach {
            it()
        }

        initialized = true
        // 判断pipes是否为空
        if (pipes.isEmpty()) {
            val mc = Minecraft.getInstance()
            val window = mc.window
            val simplePipe = SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                    GlShaderType.FRAGMENT
                ), depthSupplier
            )
            addPipe(simplePipe)
        }
        pipes.forEachIndexed { current, it ->
            // 可能是在静态位置构建pipeline 导致inputAttachment是-1 然后在初始化的时候对其赋值
            // 但是pipes里面的attachment没有传过去 因此再次调用此方法用于设置深度附件
            handlePipeDepthAttachmentAtIndex(current - 1, it)
            // 初始化pipe
            it.init()
        }
        screenBuffer.init()
    }

    fun beforeInit(invoke: ShaderPipeManager.() -> Unit): ShaderPipeManager {
        beforeInitPipe.add(invoke)
        return this
    }

    fun addMcHookedPipe(): ShaderPipeManager {
        val pipe = MCHookedShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID,
                    "pipe/frags/screen.fsh"
                ), GlShaderType.FRAGMENT
            ),
            Minecraft.getInstance().mainRenderTarget
        )
        if (initialized) {
            pipe.init()
        }
        pipes.add(pipe)
        return this
    }

    /**
     * 如果没有初始化 则直接加入
     * 如果已经初始化了 则加入前先初始化
     * 如果这个PipeManager没有加入任何的初始化, 则会自动生成一个初始化(什么都不干)
     */
    fun addPipe(pipe: ShaderPipe): ShaderPipeManager {
        handlePipeDepthAttachment(pipe)
        if (initialized) {
            pipe.init()
        }
        pipes.add(pipe)
        return this
    }

    private fun handlePipeDepthAttachmentAtIndex(lastIndex: Int, pipe: ShaderPipe) {
        if (!pipe.shareDepth()) {
            return
        }
        val lastPipe = if (lastIndex in pipes.indices) {
            pipes[lastIndex].fbo().depthSupplier
        } else {
            depthSupplier
        }
        pipe.fbo().depthSupplier = lastPipe
    }

    private fun handlePipeDepthAttachment(pipe: ShaderPipe) {
        if (depthSupplier.get() == -1) return
        if (pipe.shareDepth()) {
            val useAttachment = if (pipes.isNotEmpty()) {
                val last = pipes.last()
                last.fbo().depthSupplier
            } else depthSupplier
            pipe.fbo().depthSupplier = useAttachment
        }
    }

    /**
     * 写入基础内容到管线中
     */
    fun writeFrame(draw: Runnable): ShaderPipeManager {
        if (!initialized) {
            return this
        }
        if (useDepth) {
            RenderSystem.depthMask(true)
        }
        val first = pipes.first()
        first.write {
            draw.run()
        }
        return this
    }

    /**
     * 传递管线, 最后绘制last
     */
    fun render() {
        if (!initialized) {
            return
        }
        val iterator = pipes.iterator()
        var last = iterator.next()
        while (iterator.hasNext()) {
            val current = iterator.next()
            current.write {
                RenderSystem.enableBlend()
                last.drawPipeFrame()
            }
            last = current
        }
        // 绘制到当前 (公共 frame)
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GL_ONE, GL_ONE)
        RenderSystem.depthMask(false)
        last.drawPipeFrame()
        RenderSystem.defaultBlendFunc()
        RenderSystem.depthMask(true)
    }

    fun release() {
        pipes.forEach {
            it.release()
        }
    }

    fun resize(width: Int, height: Int) {
        pipes.forEach {
            it.resize(width, height)
        }
    }
}