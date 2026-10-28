package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderSourceLoader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.oit.OitStage
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.GL33.*

/**
 * 编译自定义地形片元的原版 OIT 变体；只拥有临时 shader 对象，不拥有原版 OIT 纹理。
 * @property id 原始地形片元资源 ID
 * @property stage 当前原版 OIT 阶段
 * @property additive 当前地形效果是否为加法贡献
 * @property zeroToOne 当前设备的 NDC 范围
 */
internal class CooTerrainOitFragmentShader(
    private val id: Identifier,
    private val stage: OitStage,
    private val additive: Boolean,
    private val zeroToOne: Boolean
) : GlShader {
    override val type = GlShaderType.FRAGMENT
    private var shader = 0

    override fun shaderID(): Int = shader
    override fun sourceLocation(): Identifier = id

    override fun compile() {
        val resources = Minecraft.getInstance().resourceManager
        val source = CooShaderSourceLoader.load(resources,
            Identifier.fromNamespaceAndPath(id.namespace, "shaders/core/${id.path}.fsh"))
        val oit = CooShaderSourceLoader.load(resources, Identifier.withDefaultNamespace("shaders/include/oit.glsl"))
        shader = glCreateShader(type.gl)
        glShaderSource(shader, CooTerrainOitSource.build(source, oit, stage, additive, zeroToOne))
        glCompileShader(shader)
        assertCompiled()
    }

    override fun assertCompiled() {
        check(glGetShaderi(shader, GL_COMPILE_STATUS) != GL_FALSE) {
            "地形 OIT 片元编译失败: $id/$stage ${glGetShaderInfoLog(shader)}"
        }
    }

    override fun deleteShader() {
        if (shader > 0) glDeleteShader(shader)
        shader = 0
    }
}
