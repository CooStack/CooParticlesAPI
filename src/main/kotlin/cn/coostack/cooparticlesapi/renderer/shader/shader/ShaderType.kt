package cn.coostack.cooparticlesapi.renderer.shader.shader

import org.lwjgl.opengl.GL33

enum class ShaderType(val glID: Int) {
    VERTEX(GL33.GL_VERTEX_SHADER),
    FRAGMENT(GL33.GL_FRAGMENT_SHADER),
}