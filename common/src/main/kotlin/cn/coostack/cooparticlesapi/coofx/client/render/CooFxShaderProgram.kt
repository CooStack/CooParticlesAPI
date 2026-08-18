package cn.coostack.cooparticlesapi.coofx.client.render

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.CooShaderSourceLoader
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceProvider
import org.lwjgl.opengl.GL33.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL33.GL_FALSE
import org.lwjgl.opengl.GL33.glCompileShader
import org.lwjgl.opengl.GL33.glCreateShader
import org.lwjgl.opengl.GL33.glDeleteShader
import org.lwjgl.opengl.GL33.glGetShaderInfoLog
import org.lwjgl.opengl.GL33.glGetShaderi
import org.lwjgl.opengl.GL33.glShaderSource

internal data class CooFxShaderSourceBundle(
    val vertex: String,
    val fragment: String,
)

internal class CooFxSourceShader(
    private val source: String,
    override val type: GlShaderType,
    private val location: ResourceLocation,
) : GlShader {
    private var id = 0

    override fun shaderID(): Int = id

    override fun compile() {
        id = glCreateShader(type.gl)
        try {
            glShaderSource(id, source)
            glCompileShader(id)
            assertCompiled()
        } catch (error: Throwable) {
            glDeleteShader(id)
            id = 0
            throw error
        }
    }

    override fun assertCompiled() {
        require(glGetShaderi(id, GL_COMPILE_STATUS) != GL_FALSE) {
            "CooFX shader 编译失败：${glGetShaderInfoLog(id)}，资源：$location"
        }
    }

    override fun deleteShader() {
        if (id > 0) {
            glDeleteShader(id)
            id = 0
        }
    }

    override fun sourceLocation(): ResourceLocation = location
}

internal object CooFxShaderProgramFactory {
    private val vertexLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "shaders/coofx/mesh_particle.vsh",
    )
    private val fragmentLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "shaders/coofx/mesh_particle.fsh",
    )
    private val programId = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "coofx/mesh_particle",
    )

    fun loadSources(resources: ResourceProvider): CooFxShaderSourceBundle = CooFxShaderSourceBundle(
        vertex = CooShaderSourceLoader.load(resources, vertexLocation),
        fragment = CooShaderSourceLoader.load(resources, fragmentLocation),
    )

    fun createProgram(sources: CooFxShaderSourceBundle): CooShaderProgram {
        return AdvancedShaderProgramBuilder()
            .vertex(CooFxSourceShader(sources.vertex, GlShaderType.VERTEX, vertexLocation))
            .fragment(CooFxSourceShader(sources.fragment, GlShaderType.FRAGMENT, fragmentLocation))
            .attributeLocation("aPosition", 0)
            .attributeLocation("aNormal", 1)
            .attributeLocation("aTexCoord", 2)
            .attributeLocation("aVertexColor", 3)
            .attributeLocation("aCurrentPositionAge", 4)
            .attributeLocation("aPreviousPositionLifetime", 5)
            .attributeLocation("aCurrentRotation", 6)
            .attributeLocation("aPreviousRotation", 7)
            .attributeLocation("aCurrentScaleLight", 8)
            .attributeLocation("aPreviousScaleMaterial", 9)
            .attributeLocation("aInstanceColor", 10)
            .attributeLocation("aClipPlayback", 11)
            .attributeLocation("aSeedFlagsId", 12)
            .attributeLocation("aNodeWorldRow0", 13)
            .attributeLocation("aNodeWorldRow1", 14)
            .attributeLocation("aNodeWorldRow2", 15)
            .transformFeedbackVaryings(
                "tfEntityPosition",
                "tfEntityColor",
                "tfEntityUv",
                "tfEntityOverlay",
                "tfEntityLight",
                "tfEntityNormal",
            )
            .managedId(programId)
            .build()
    }

    fun releaseProgram(program: CooShaderProgram?) {
        program?.let(ShaderProgramRegistry::unregister)
    }
}
