package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout

class ShaderProgramBuilder {
    private val delegate = AdvancedShaderProgramBuilder()

    fun vertex(path: String): ShaderProgramBuilder {
        delegate.vertex(path)
        return this
    }

    fun fragment(path: String): ShaderProgramBuilder {
        delegate.fragment(path)
        return this
    }

    fun geometry(path: String): ShaderProgramBuilder {
        delegate.geometry(path)
        return this
    }

    fun tessellationControl(path: String): ShaderProgramBuilder {
        delegate.tessellationControl(path)
        return this
    }

    fun tessellationEvaluation(path: String): ShaderProgramBuilder {
        delegate.tessellationEvaluation(path)
        return this
    }

    fun compute(path: String): ShaderProgramBuilder {
        delegate.compute(path)
        return this
    }

    fun vertex(shader: GlShader): ShaderProgramBuilder {
        delegate.vertex(shader)
        return this
    }

    fun fragment(shader: GlShader): ShaderProgramBuilder {
        delegate.fragment(shader)
        return this
    }

    fun geometry(shader: GlShader): ShaderProgramBuilder {
        delegate.geometry(shader)
        return this
    }

    fun tessellationControl(shader: GlShader): ShaderProgramBuilder {
        delegate.tessellationControl(shader)
        return this
    }

    fun tessellationEvaluation(shader: GlShader): ShaderProgramBuilder {
        delegate.tessellationEvaluation(shader)
        return this
    }

    fun compute(shader: GlShader): ShaderProgramBuilder {
        delegate.compute(shader)
        return this
    }

    fun bufferLayout(layout: ShaderBufferLayout<*>): ShaderProgramBuilder {
        delegate.bufferLayout(layout)
        return this
    }

    fun build(): CooShaderProgram {
        return delegate.build()
    }

    fun buildCompute(): CooComputeShaderProgram {
        return delegate.buildCompute()
    }
}
