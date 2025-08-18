package cn.coostack.cooparticlesapi.renderer.shader.api

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import org.joml.Matrix2f
import org.joml.Matrix3f
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import org.joml.Matrix4x3f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*

interface CooShaderProgram {
    var program: Int
    var vertexShader: GlShader
    var fragmentShader: GlShader

    /**
     * 初始化方法
     */
    fun init()

    fun use()

    fun reset()

    fun release()

    /**
     * 在这个环境快捷绘制 (自动使用 自动重置)
     * 在这个环境内绘制vertex buffer
     */
    fun useOnContext(drawMethod: CooShaderProgram.() -> Unit)


    fun setInt(key: String, value: Int) {
        val glLocation = getGlLocation(key) ?: return
        glUniform1i(glLocation, value)
    }

    fun setBoolean(key: String, value: Boolean) {
        setInt(key, if (value) 1 else 0)
    }

    fun setFloat(key: String, value: Float) {
        val glLocation = getGlLocation(key) ?: return
        glUniform1f(glLocation, value)
    }

    fun setFloat2(key: String, value: Vector2f) {
        val glLocation = getGlLocation(key) ?: return
        glUniform2f(glLocation, value.x, value.y)
    }

    fun setFloat3(key: String, value: Vector3f) {
        val glLocation = getGlLocation(key) ?: return
        glUniform3f(glLocation, value.x, value.y, value.z)
    }

    fun setFloat4(key: String, value: Vector4f) {
        val glLocation = getGlLocation(key) ?: return
        glUniform4f(glLocation, value.x, value.y, value.z, value.w)
    }

    fun setMatrix4(key: String, value: Matrix4f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix4fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(16)))
    }

    fun setMatrix4x3(key: String, value: Matrix4x3f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix4x3fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(12)))
    }

    fun setMatrix3x2(key: String, value: Matrix3x2f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix3x2fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(6)))
    }

    fun setMatrix3f(key: String, value: Matrix3f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix3fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(9)))
    }

    fun setMatrix2f(key: String, value: Matrix2f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix2fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(4)))
    }

    private fun getGlLocation(key: String): Int? {
        val location = glGetUniformLocation(program, key)
        if (location == -1) return null
        return location
    }
}