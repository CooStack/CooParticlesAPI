package cn.coostack.cooparticlesapi.renderer.shader.api

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

/**
 * 提供 OpenGL uniform 写入能力的基础接口。
 *
 * 任何封装了 `program` id 的 shader program 都可以复用这组默认实现。
 */
interface CooProgramUniformAccess {
    /**
     * 当前 OpenGL program id。
     */
    var program: Int

    /**
     * 设置一个 `int` uniform。
     */
    fun setInt(key: String, value: Int) {
        val glLocation = getGlLocation(key) ?: return
        glUniform1i(glLocation, value)
    }

    /**
     * 设置一个布尔 uniform。
     *
     * 会转换成 `0/1` 的整型写入。
     */
    fun setBoolean(key: String, value: Boolean) {
        setInt(key, if (value) 1 else 0)
    }

    /**
     * 设置一个 `float` uniform。
     */
    fun setFloat(key: String, value: Float) {
        val glLocation = getGlLocation(key) ?: return
        glUniform1f(glLocation, value)
    }

    /**
     * 设置一个 `vec2` uniform。
     */
    fun setFloat2(key: String, value: Vector2f) {
        val glLocation = getGlLocation(key) ?: return
        glUniform2f(glLocation, value.x, value.y)
    }

    /**
     * 设置一个 `vec3` uniform。
     */
    fun setFloat3(key: String, value: Vector3f) {
        val glLocation = getGlLocation(key) ?: return
        glUniform3f(glLocation, value.x, value.y, value.z)
    }

    /**
     * 设置一个 `vec4` uniform。
     */
    fun setFloat4(key: String, value: Vector4f) {
        val glLocation = getGlLocation(key) ?: return
        glUniform4f(glLocation, value.x, value.y, value.z, value.w)
    }

    /**
     * 设置一个 `float[]` uniform 数组。
     */
    fun setFloatArray(key: String, value: FloatArray) {
        val glLocation = getArrayLocation(key) ?: return
        glUniform1fv(glLocation, value)
    }

    /**
     * 设置一个 `vec3[]` uniform 数组。
     *
     * 输入数组需要按 `x,y,z,x,y,z...` 的扁平格式组织。
     */
    fun setFloat3Array(key: String, value: FloatArray) {
        val glLocation = getArrayLocation(key) ?: return
        glUniform3fv(glLocation, value)
    }

    /**
     * 设置一个 `vec4[]` uniform 数组。
     *
     * 输入数组需要按 `x,y,z,w,...` 的扁平格式组织。
     */
    fun setFloat4Array(key: String, value: FloatArray) {
        val glLocation = getArrayLocation(key) ?: return
        glUniform4fv(glLocation, value)
    }

    /**
     * 设置一个 `mat4` uniform。
     */
    fun setMatrix4(key: String, value: Matrix4f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix4fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(16)))
    }

    /**
     * 设置一个 `mat4x3` uniform。
     */
    fun setMatrix4x3(key: String, value: Matrix4x3f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix4x3fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(12)))
    }

    /**
     * 设置一个 `mat3x2` uniform。
     */
    fun setMatrix3x2(key: String, value: Matrix3x2f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix3x2fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(6)))
    }

    /**
     * 设置一个 `mat3` uniform。
     */
    fun setMatrix3f(key: String, value: Matrix3f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix3fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(9)))
    }

    /**
     * 设置一个 `mat2` uniform。
     */
    fun setMatrix2f(key: String, value: Matrix2f) {
        val glLocation = getGlLocation(key) ?: return
        glUniformMatrix2fv(glLocation, false, value.get(BufferUtils.createFloatBuffer(4)))
    }

    private fun getGlLocation(key: String): Int? {
        val location = glGetUniformLocation(program, key)
        if (location == -1) return null
        return location
    }

    private fun getArrayLocation(key: String): Int? {
        return getGlLocation("$key[0]") ?: getGlLocation(key)
    }
}
