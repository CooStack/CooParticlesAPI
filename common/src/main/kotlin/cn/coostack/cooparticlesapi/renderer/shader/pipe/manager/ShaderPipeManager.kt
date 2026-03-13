package cn.coostack.cooparticlesapi.renderer.shader.pipe.manager

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.exceptions.RenderPipeLinkerNotSetException
import cn.coostack.cooparticlesapi.exceptions.RenderPipeOutputNotSetException
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.GlobalUniform
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeLinker
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeLinkerNode
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.Matrix4fGlobalUniform
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.*
import java.util.function.Supplier

/**
 * @param depthSupplier 共享的深度
 */
class ShaderPipeManager(
    val pipeID: ResourceLocation
) {
    private var initialized = false
    private val screenBuffer = VertexBuffers.getScreenBuffer()
    private val screenVertex = IdentifierShader(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/vertexes/screen.vsh"),
        GlShaderType.VERTEX
    )
    private val screenFragment = IdentifierShader(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
        GlShaderType.FRAGMENT
    )
    private val screenProgram = ShaderProgramBuilder()
        .vertex(screenVertex)
        .fragment(screenFragment)
        .build()

    private val uploadUniformPrePrograms = HashMap<String, GlobalUniform<*>>()

    val pipes = HashSet<ShaderPipe>()
    val linker = GraphPipeLinker()

    /**
     * 如果让他自动生成 depthAttachment
     * 则返回-1
     */
    var depthSupplier: Supplier<Int> = Supplier { -1 }
    var valueInputPipe: ShaderPipe? = null
        private set
    var valueOutput: ShaderPipe? = null
        private set
    val beforeInitPipe = mutableListOf<ShaderPipeManager.() -> Unit>()
    var enableBlend = true
    var blendFuncSrc = GL_ONE
    var blendFuncDst = GL_ONE

    private var linkerSet = false
    private var linkerFunc: ShaderPipeManager.(PipeLinker) -> Unit = {}
    private val beforeRenderPipe = mutableListOf<ShaderPipeManager.() -> Unit>()

    /**
     * 每经过一层渲染时，就会执行这个
     * 用于设置全局变量（真）
     *
     * 如果是 addPipe(pipe.addRenderHandler()) 则会被覆盖
     * 反之 addPipe(pipe).addRenderHandler() 就会覆盖这个
     */
    fun addGlobalUniform(upload: GlobalUniform<*>): ShaderPipeManager {
        uploadUniformPrePrograms[upload.key] = upload
        return this
    }

    fun updateGlobalUniform(key: String, value: Any): ShaderPipeManager {
        val uniform = (uploadUniformPrePrograms[key] ?: return this) as GlobalUniform<Any>

        if (uniform.value::class.java != value::class.java) {
            return this
        }
        uniform.value = value
        return this
    }

    fun addTransformUniformMatrix(): ShaderPipeManager {
        return addGlobalUniform(Matrix4fGlobalUniform("transMat"))
            .addGlobalUniform(Matrix4fGlobalUniform("viewMat"))
            .addGlobalUniform(Matrix4fGlobalUniform("projMat"))
    }


    fun setLinkerFunc(func: ShaderPipeManager.(PipeLinker) -> Unit): ShaderPipeManager {
        linkerSet = true
        linkerFunc = func
        return this
    }

    /**
     * 输入管道
     */
    fun valueInput(pipe: ShaderPipe): ShaderPipeManager {
        this.valueInputPipe = pipe
        addPipe(pipe)
        return this
    }

    fun valueOutput(pipe: ShaderPipe): ShaderPipeManager {
        this.valueOutput = pipe
        addPipe(pipe)
        return this
    }

    /**
     * depth mask
     */
    var useDepth = true
    fun init() {
        if (initialized) {
            return
        }

        if (!linkerSet) {
            throw RenderPipeLinkerNotSetException(pipeID)
        }

        beforeInitPipe.forEach {
            it()
        }
        initialized = true
        // 判断pipes是否为空
        if (valueInputPipe == null) {
            valueInputPipe = SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                    GlShaderType.FRAGMENT
                ), depthSupplier
            )
        }
        pipes.add(valueInputPipe!!)
        pipes.forEachIndexed { current, it ->
            // 初始化pipe
            it.init()
        }
        screenBuffer.init()
        screenProgram.init()
        linkerFunc(linker)
    }

    fun beforeInit(invoke: ShaderPipeManager.() -> Unit): ShaderPipeManager {
        beforeInitPipe.add(invoke)
        return this
    }

    fun beforeRender(invoke: ShaderPipeManager.() -> Unit): ShaderPipeManager {
        beforeRenderPipe.add(invoke)
        return this
    }

    /**
     * 如果没有初始化 则直接加入
     * 如果已经初始化了 则加入前先初始化
     * 如果这个PipeManager没有加入任何的初始化, 则会自动生成一个初始化(什么都不干)
     */
    fun addPipe(pipe: ShaderPipe): ShaderPipe {
        uploadUniformPrePrograms.forEach { handler ->
            pipe.addRenderHandler {
                handler.value.upload(it)
            }
        }
        if (initialized) {
            pipe.init()
        }
        pipes.add(pipe)
        return pipe
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
        valueInputPipe!!.write {
            draw.run()
        }
        return this
    }


    private fun inputPipe(initPipes: MutableSet<ShaderPipe>, targetPipe: ShaderPipe) {
        initPipes.add(targetPipe)
        val all = linker.findAllChannel(targetPipe)
        if (all.isEmpty()) {
            // 最初输入
            return
        }
        val channelList = arrayOfNulls<PipeLinkerNode>(all.size)
        all.forEach {
            val channel = it.key
            val output = it.value
            if (output.pipe !in initPipes) {
                inputPipe(initPipes, output.pipe)
            }
            channelList[channel] = output
        }
        val pipeChannels = FramePipeChannels()
        for (node in channelList) {
            pipeChannels.addChannel(node!!.pipe.getFrameOutput().getChannel(node.channel))
        }
        targetPipe.writeFromChannel(pipeChannels)
    }


    /**
     * 传递管线, 最后绘制last
     */
    fun render() {
        if (!initialized) {
            return
        }
        if (valueOutput == null) {
            throw RenderPipeOutputNotSetException(pipeID)
        }
        beforeRenderPipe.forEach {
            it()
        }
        inputPipe(HashSet(), valueOutput!!)
        // 绘制到当前 (公共 frame)
        if (enableBlend) {
            RenderSystem.enableBlend()
            RenderSystem.blendFunc(blendFuncSrc, blendFuncDst)
        } else {
            RenderSystem.disableBlend()
        }
        RenderSystem.depthMask(false)
        screenProgram.useOnContext {
            valueOutput!!.getFrameOutput().useOnContext {
                screenBuffer.draw()
            }
        }
        RenderSystem.defaultBlendFunc()
        RenderSystem.depthMask(true)
    }

    fun release() {
        pipes.forEach {
            it.release()
        }
        screenProgram.release()
        screenBuffer.release()
        linker.clear()
        initialized = false
    }

    fun resize(width: Int, height: Int) {
        pipes.forEach {
            it.resize(width, height)
        }
    }
}
