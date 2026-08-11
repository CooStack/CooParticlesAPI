package cn.coostack.cooparticlesapi.renderer.shader.api

import org.joml.Matrix2f
import org.joml.Matrix3f
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import org.joml.Matrix4x3f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL40.*

/**
 * 提供 OpenGL 普通 uniform 写入能力的基础接口。
 *
 * 这里覆盖 `glUniform*` 能直接写入的标量、向量、矩阵和数组。纹理、image、uniform block、
 * atomic counter 等资源的实际绑定仍由对应的 OpenGL API 负责。
 */
interface CooProgramUniformAccess {
    /** 当前 OpenGL program id。 */
    var program: Int

    /** 设置一个 GLSL `int` uniform。 */
    fun setInt(key: String, value: Int) {
        val location = getUniformLocation(key) ?: return
        glUniform1i(location, value)
    }

    /** 设置一个 GLSL `uint` uniform。 */
    fun setUInt(key: String, value: UInt) {
        val location = getUniformLocation(key) ?: return
        glUniform1ui(location, value.toInt())
    }

    /** 设置一个 GLSL `bool` uniform。 */
    fun setBoolean(key: String, value: Boolean) {
        setInt(key, if (value) 1 else 0)
    }

    /** 设置一个 GLSL `float` uniform。 */
    fun setFloat(key: String, value: Float) {
        val location = getUniformLocation(key) ?: return
        glUniform1f(location, value)
    }

    /** 设置一个 GLSL `double` uniform。 */
    fun setDouble(key: String, value: Double) {
        val location = getUniformLocation(key) ?: return
        glUniform1d(location, value)
    }

    /** 设置一个 `vec2` uniform。 */
    fun setFloat2(key: String, value: Vector2f) {
        val location = getUniformLocation(key) ?: return
        glUniform2f(location, value.x, value.y)
    }

    /** 设置一个 `vec3` uniform。 */
    fun setFloat3(key: String, value: Vector3f) {
        val location = getUniformLocation(key) ?: return
        glUniform3f(location, value.x, value.y, value.z)
    }

    /** 设置一个 `vec4` uniform。 */
    fun setFloat4(key: String, value: Vector4f) {
        val location = getUniformLocation(key) ?: return
        glUniform4f(location, value.x, value.y, value.z, value.w)
    }

    /** 设置一个 `ivec*` uniform，数组长度决定 2 到 4 的向量宽度。 */
    fun setIntVector(key: String, value: IntArray) {
        requireVectorWidth(value.size)
        uploadIntVector(getUniformLocation(key) ?: return, value.size, value)
    }

    /** 设置一个 `uvec*` uniform，列表长度决定 2 到 4 的向量宽度。 */
    fun setUIntVector(key: String, value: List<UInt>) {
        requireVectorWidth(value.size)
        uploadUIntVector(getUniformLocation(key) ?: return, value.size, value.toRawIntArray())
    }

    /** 设置一个 `bvec*` uniform，列表长度决定 2 到 4 的向量宽度。 */
    fun setBooleanVector(key: String, value: List<Boolean>) {
        requireVectorWidth(value.size)
        uploadIntVector(getUniformLocation(key) ?: return, value.size, value.toBooleanIntArray())
    }

    /** 设置一个 `vec*` uniform，数组长度决定 2 到 4 的向量宽度。 */
    fun setFloatVector(key: String, value: FloatArray) {
        requireVectorWidth(value.size)
        uploadFloatVector(getUniformLocation(key) ?: return, value.size, value)
    }

    /** 设置一个 `dvec*` uniform，数组长度决定 2 到 4 的向量宽度。 */
    fun setDoubleVector(key: String, value: DoubleArray) {
        requireVectorWidth(value.size)
        uploadDoubleVector(getUniformLocation(key) ?: return, value.size, value)
    }

    /** 设置标量或向量组成的 `int`/`ivec*` uniform 数组。 */
    fun setIntVectorArray(key: String, componentCount: Int, value: IntArray) {
        requireArrayShape(componentCount, value.size)
        uploadIntVector(getArrayLocation(key) ?: return, componentCount, value)
    }

    /** 设置标量或向量组成的 `uint`/`uvec*` uniform 数组。 */
    fun setUIntVectorArray(key: String, componentCount: Int, value: List<UInt>) {
        requireArrayShape(componentCount, value.size)
        uploadUIntVector(getArrayLocation(key) ?: return, componentCount, value.toRawIntArray())
    }

    /** 设置标量或向量组成的 `bool`/`bvec*` uniform 数组。 */
    fun setBooleanVectorArray(key: String, componentCount: Int, value: List<Boolean>) {
        requireArrayShape(componentCount, value.size)
        uploadIntVector(getArrayLocation(key) ?: return, componentCount, value.toBooleanIntArray())
    }

    /** 设置标量或向量组成的 `float`/`vec*` uniform 数组。 */
    fun setFloatVectorArray(key: String, componentCount: Int, value: FloatArray) {
        requireArrayShape(componentCount, value.size)
        uploadFloatVector(getArrayLocation(key) ?: return, componentCount, value)
    }

    /** 设置标量或向量组成的 `double`/`dvec*` uniform 数组。 */
    fun setDoubleVectorArray(key: String, componentCount: Int, value: DoubleArray) {
        requireArrayShape(componentCount, value.size)
        uploadDoubleVector(getArrayLocation(key) ?: return, componentCount, value)
    }

    /** 设置一个 `float[]` uniform。 */
    fun setFloatArray(key: String, value: FloatArray) {
        setFloatVectorArray(key, 1, value)
    }

    /** 设置一个扁平排列的 `vec2[]` uniform。 */
    fun setFloat2Array(key: String, value: FloatArray) {
        setFloatVectorArray(key, 2, value)
    }

    /** 设置一个扁平排列的 `vec3[]` uniform。 */
    fun setFloat3Array(key: String, value: FloatArray) {
        setFloatVectorArray(key, 3, value)
    }

    /** 设置一个扁平排列的 `vec4[]` uniform。 */
    fun setFloat4Array(key: String, value: FloatArray) {
        setFloatVectorArray(key, 4, value)
    }

    /** 设置任意 2 到 4 列、2 到 4 行的单精度矩阵。 */
    fun setFloatMatrix(key: String, columns: Int, rows: Int, value: FloatArray) {
        requireMatrixShape(columns, rows, value.size, false)
        uploadFloatMatrix(getUniformLocation(key) ?: return, columns, rows, value)
    }

    /** 设置任意单精度矩阵组成的 uniform 数组。 */
    fun setFloatMatrixArray(key: String, columns: Int, rows: Int, value: FloatArray) {
        requireMatrixShape(columns, rows, value.size, true)
        uploadFloatMatrix(getArrayLocation(key) ?: return, columns, rows, value)
    }

    /** 设置任意 2 到 4 列、2 到 4 行的双精度矩阵。 */
    fun setDoubleMatrix(key: String, columns: Int, rows: Int, value: DoubleArray) {
        requireMatrixShape(columns, rows, value.size, false)
        uploadDoubleMatrix(getUniformLocation(key) ?: return, columns, rows, value)
    }

    /** 设置任意双精度矩阵组成的 uniform 数组。 */
    fun setDoubleMatrixArray(key: String, columns: Int, rows: Int, value: DoubleArray) {
        requireMatrixShape(columns, rows, value.size, true)
        uploadDoubleMatrix(getArrayLocation(key) ?: return, columns, rows, value)
    }

    /** 设置一个 `mat4` uniform。 */
    fun setMatrix4(key: String, value: Matrix4f) {
        setFloatMatrix(key, 4, 4, value.get(FloatArray(16)))
    }

    /** 设置一个 `mat4x3` uniform。 */
    fun setMatrix4x3(key: String, value: Matrix4x3f) {
        setFloatMatrix(key, 4, 3, value.get(FloatArray(12)))
    }

    /** 设置一个 `mat3x2` uniform。 */
    fun setMatrix3x2(key: String, value: Matrix3x2f) {
        setFloatMatrix(key, 3, 2, value.get(FloatArray(6)))
    }

    /** 设置一个 `mat3` uniform。 */
    fun setMatrix3f(key: String, value: Matrix3f) {
        setFloatMatrix(key, 3, 3, value.get(FloatArray(9)))
    }

    /** 设置一个 `mat2` uniform。 */
    fun setMatrix2f(key: String, value: Matrix2f) {
        setFloatMatrix(key, 2, 2, value.get(FloatArray(4)))
    }

    private fun getUniformLocation(key: String): Int? {
        val location = glGetUniformLocation(program, key)
        return location.takeUnless { it == -1 }
    }

    private fun getArrayLocation(key: String): Int? {
        return getUniformLocation("$key[0]") ?: getUniformLocation(key)
    }

    private fun uploadIntVector(location: Int, componentCount: Int, value: IntArray) {
        when (componentCount) {
            1 -> glUniform1iv(location, value)
            2 -> glUniform2iv(location, value)
            3 -> glUniform3iv(location, value)
            4 -> glUniform4iv(location, value)
        }
    }

    private fun uploadUIntVector(location: Int, componentCount: Int, value: IntArray) {
        when (componentCount) {
            1 -> glUniform1uiv(location, value)
            2 -> glUniform2uiv(location, value)
            3 -> glUniform3uiv(location, value)
            4 -> glUniform4uiv(location, value)
        }
    }

    private fun uploadFloatVector(location: Int, componentCount: Int, value: FloatArray) {
        when (componentCount) {
            1 -> glUniform1fv(location, value)
            2 -> glUniform2fv(location, value)
            3 -> glUniform3fv(location, value)
            4 -> glUniform4fv(location, value)
        }
    }

    private fun uploadDoubleVector(location: Int, componentCount: Int, value: DoubleArray) {
        when (componentCount) {
            1 -> glUniform1dv(location, value)
            2 -> glUniform2dv(location, value)
            3 -> glUniform3dv(location, value)
            4 -> glUniform4dv(location, value)
        }
    }

    private fun uploadFloatMatrix(location: Int, columns: Int, rows: Int, value: FloatArray) {
        when (columns to rows) {
            2 to 2 -> glUniformMatrix2fv(location, false, value)
            2 to 3 -> glUniformMatrix2x3fv(location, false, value)
            2 to 4 -> glUniformMatrix2x4fv(location, false, value)
            3 to 2 -> glUniformMatrix3x2fv(location, false, value)
            3 to 3 -> glUniformMatrix3fv(location, false, value)
            3 to 4 -> glUniformMatrix3x4fv(location, false, value)
            4 to 2 -> glUniformMatrix4x2fv(location, false, value)
            4 to 3 -> glUniformMatrix4x3fv(location, false, value)
            4 to 4 -> glUniformMatrix4fv(location, false, value)
        }
    }

    private fun uploadDoubleMatrix(location: Int, columns: Int, rows: Int, value: DoubleArray) {
        when (columns to rows) {
            2 to 2 -> glUniformMatrix2dv(location, false, value)
            2 to 3 -> glUniformMatrix2x3dv(location, false, value)
            2 to 4 -> glUniformMatrix2x4dv(location, false, value)
            3 to 2 -> glUniformMatrix3x2dv(location, false, value)
            3 to 3 -> glUniformMatrix3dv(location, false, value)
            3 to 4 -> glUniformMatrix3x4dv(location, false, value)
            4 to 2 -> glUniformMatrix4x2dv(location, false, value)
            4 to 3 -> glUniformMatrix4x3dv(location, false, value)
            4 to 4 -> glUniformMatrix4dv(location, false, value)
        }
    }

    private fun requireVectorWidth(componentCount: Int) {
        require(componentCount in 2..4) { "GLSL vector size must be between 2 and 4: $componentCount" }
    }

    private fun requireArrayShape(componentCount: Int, valueCount: Int) {
        require(componentCount in 1..4) { "GLSL scalar/vector component count must be between 1 and 4" }
        require(valueCount > 0 && valueCount % componentCount == 0) {
            "Uniform array value count must be a positive multiple of $componentCount: $valueCount"
        }
    }

    private fun requireMatrixShape(columns: Int, rows: Int, valueCount: Int, array: Boolean) {
        require(columns in 2..4 && rows in 2..4) {
            "GLSL matrix dimensions must be between 2 and 4: ${columns}x$rows"
        }
        val matrixSize = columns * rows
        require(if (array) valueCount > 0 && valueCount % matrixSize == 0 else valueCount == matrixSize) {
            "Uniform matrix value count does not match ${columns}x$rows: $valueCount"
        }
    }

    private fun List<UInt>.toRawIntArray(): IntArray = IntArray(size) { this[it].toInt() }

    private fun List<Boolean>.toBooleanIntArray(): IntArray = IntArray(size) { if (this[it]) 1 else 0 }
}
