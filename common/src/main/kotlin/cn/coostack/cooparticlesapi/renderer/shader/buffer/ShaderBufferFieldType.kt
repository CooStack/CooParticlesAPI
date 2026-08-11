package cn.coostack.cooparticlesapi.renderer.shader.buffer

/**
 * shader buffer 字段类型枚举。
 *
 * `glslName` 表示生成 GLSL 声明时对应的类型关键字。
 *
 * @property glslName 该字段类型在 GLSL 中使用的名称。
 */
enum class ShaderBufferFieldType(val glslName: String) {
    /** 单精度浮点标量。 */
    FLOAT("float"),
    /** 有符号整型标量。 */
    INT("int"),
    /** 无符号整型标量。 */
    UINT("uint"),
    /** 双分量浮点向量。 */
    VEC2("vec2"),
    /** 三分量浮点向量。 */
    VEC3("vec3"),
    /** 四分量浮点向量。 */
    VEC4("vec4"),
    /** 3x3 浮点矩阵。 */
    MAT3("mat3"),
    /** 4x4 浮点矩阵。 */
    MAT4("mat4");

    /**
     * 返回该字段类型在指定内存布局下的基础对齐字节数。
     */
    fun alignment(layout: ShaderBufferMemoryLayout): Int {
        return when (this) {
            FLOAT, INT, UINT -> 4
            VEC2 -> 8
            VEC3, VEC4, MAT3, MAT4 -> 16
        }
    }

    /**
     * 返回该字段类型在指定内存布局下占用的字节数。
     *
     * 这里返回的是按布局规则补齐后的大小，而不是数学意义上的裸大小。
     */
    fun byteSize(layout: ShaderBufferMemoryLayout): Int {
        return when (this) {
            FLOAT, INT, UINT -> 4
            VEC2 -> 8
            VEC3 -> 16
            VEC4 -> 16
            MAT3 -> 48
            MAT4 -> 64
        }
    }
}
