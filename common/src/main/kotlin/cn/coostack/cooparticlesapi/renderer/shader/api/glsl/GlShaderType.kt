package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

import org.lwjgl.opengl.GL33

enum class GlShaderType(val gl: Int) {
    VERTEX(GL33.GL_VERTEX_SHADER),
    FRAGMENT(GL33.GL_FRAGMENT_SHADER),
}