package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GL43

/**
 * OpenGL shader stage 类型枚举。
 */
enum class GlShaderType(val gl: Int) {
    /** 顶点着色器阶段。 */
    VERTEX(GL43.GL_VERTEX_SHADER),
    /** 片元着色器阶段。 */
    FRAGMENT(GL43.GL_FRAGMENT_SHADER),
    /** 几何着色器阶段。 */
    GEOMETRY(GL32.GL_GEOMETRY_SHADER),
    /** 曲面细分控制着色器阶段。 */
    TESSELLATION_CONTROL(GL40.GL_TESS_CONTROL_SHADER),
    /** 曲面细分求值着色器阶段。 */
    TESSELLATION_EVALUATION(GL40.GL_TESS_EVALUATION_SHADER),
    /** 计算着色器阶段。 */
    COMPUTE(GL43.GL_COMPUTE_SHADER),
}
