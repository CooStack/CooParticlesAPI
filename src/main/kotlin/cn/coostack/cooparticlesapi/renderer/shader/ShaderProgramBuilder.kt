package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.shader.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.shader.IdentifierGlShader
import cn.coostack.cooparticlesapi.renderer.shader.shader.ShaderType
import cn.coostack.cooparticlesapi.renderer.shader.uniform.GlUniformProvider
import net.minecraft.util.Identifier

class ShaderProgramBuilder {
    private var vertexShader: GlShader? = null
    private var fragmentShader: GlShader? = null
    private var vertexesType: CooVertexFormat = CooVertexFormat.POINT_FORMAT
    private val uniformProviders = ArrayList<GlUniformProvider<*>>()
    private val vertexes = ArrayList<VertexData>()
    fun vertex(id: Identifier): ShaderProgramBuilder {
        vertexShader = IdentifierGlShader(id, ShaderType.VERTEX)
        return this
    }

    fun fragment(id: Identifier): ShaderProgramBuilder {
        fragmentShader = IdentifierGlShader(id, ShaderType.FRAGMENT)
        return this
    }

    fun vertex(shader: GlShader): ShaderProgramBuilder {
        vertexShader = shader
        return this
    }

    fun fragment(shader: GlShader): ShaderProgramBuilder {
        fragmentShader = shader
        return this
    }

    fun vertexType(type: CooVertexFormat): ShaderProgramBuilder {
        vertexesType = type
        return this
    }

    fun uniform(provider: GlUniformProvider<*>): ShaderProgramBuilder {
        uniformProviders.add(provider)
        return this
    }

    fun vertexes(vertexes: Collection<VertexData>): ShaderProgramBuilder {
        this.vertexes.addAll(vertexes)
        return this
    }

    fun assertBaseProperties() {
        assert(vertexShader != null && fragmentShader != null) { "Vertex shader and fragmentShader must not be null" }
    }

    fun build(): CooShaderProgram {
        assertBaseProperties()
        val program = CustomShaderProgram(vertexShader!!, fragmentShader!!, vertexesType)
        program.provides.addAll(uniformProviders)
        program.vertexes.addAll(vertexes)
        return program
    }
}