package cn.coostack.cooparticlesapi.renderer.pipeline

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Matrix2dc
import org.joml.Matrix2fc
import org.joml.Matrix3dc
import org.joml.Matrix3fc
import org.joml.Matrix3x2dc
import org.joml.Matrix3x2fc
import org.joml.Matrix4dc
import org.joml.Matrix4fc
import org.joml.Matrix4x3dc
import org.joml.Matrix4x3fc

/**
 * 可通过 `glUniform*` 上传到 shader 的值。
 *
 * 标量、向量和矩阵类型与 GLSL 基础类型一一对应。[ArrayValue] 用于同形状值的数组。
 * sampler 和 image uniform 只保存已经绑定的纹理单元或 image unit；纹理资源本身仍由渲染后端绑定。
 * uniform block、atomic counter 和 subroutine uniform 使用独立的 OpenGL 绑定 API，不属于本值模型。
 */
sealed interface CooUniformValue {
    companion object {
        private const val FLOAT = 0
        private const val INT = 1
        private const val VEC2 = 2
        private const val VEC3 = 3
        private const val VEC4 = 4
        private const val BOOL = 5
        private const val UINT = 6
        private const val DOUBLE = 7
        private const val IVEC = 8
        private const val UVEC = 9
        private const val BVEC = 10
        private const val DVEC = 11
        private const val MAT = 12
        private const val DMAT = 13
        private const val SAMPLER = 14
        private const val IMAGE = 15
        private const val ARRAY = 16

        /** 支持全部 [CooUniformValue] 实现的多态网络 codec。 */
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CooUniformValue> = StreamCodec.of(::encode, ::decode)

        private fun encode(buffer: FriendlyByteBuf, value: CooUniformValue) {
            when (value) {
                is FloatValue -> {
                    buffer.writeByte(FLOAT)
                    buffer.writeFloat(value.value)
                }
                is IntValue -> {
                    buffer.writeByte(INT)
                    buffer.writeVarInt(zigZag(value.value))
                }
                is Vec2Value -> {
                    buffer.writeByte(VEC2)
                    buffer.writeFloat(value.x)
                    buffer.writeFloat(value.y)
                }
                is Vec3Value -> {
                    buffer.writeByte(VEC3)
                    buffer.writeFloat(value.x)
                    buffer.writeFloat(value.y)
                    buffer.writeFloat(value.z)
                }
                is Vec4Value -> {
                    buffer.writeByte(VEC4)
                    buffer.writeFloat(value.x)
                    buffer.writeFloat(value.y)
                    buffer.writeFloat(value.z)
                    buffer.writeFloat(value.w)
                }
                is BoolValue -> {
                    buffer.writeByte(BOOL)
                    buffer.writeBoolean(value.value)
                }
                is UIntValue -> {
                    buffer.writeByte(UINT)
                    buffer.writeInt(value.value.toInt())
                }
                is DoubleValue -> {
                    buffer.writeByte(DOUBLE)
                    buffer.writeDouble(value.value)
                }
                is IVecValue -> {
                    buffer.writeByte(IVEC)
                    buffer.writeByte(value.components.size)
                    value.components.forEach(buffer::writeInt)
                }
                is UVecValue -> {
                    buffer.writeByte(UVEC)
                    buffer.writeByte(value.components.size)
                    value.components.forEach { buffer.writeInt(it.toInt()) }
                }
                is BVecValue -> {
                    buffer.writeByte(BVEC)
                    buffer.writeByte(value.components.size)
                    value.components.forEach(buffer::writeBoolean)
                }
                is DVecValue -> {
                    buffer.writeByte(DVEC)
                    buffer.writeByte(value.components.size)
                    value.components.forEach(buffer::writeDouble)
                }
                is MatValue -> {
                    buffer.writeByte(MAT)
                    writeMatrixShape(buffer, value.columns, value.rows)
                    value.components.forEach(buffer::writeFloat)
                }
                is DMatValue -> {
                    buffer.writeByte(DMAT)
                    writeMatrixShape(buffer, value.columns, value.rows)
                    value.components.forEach(buffer::writeDouble)
                }
                is SamplerValue -> {
                    buffer.writeByte(SAMPLER)
                    buffer.writeInt(value.textureUnit)
                }
                is ImageValue -> {
                    buffer.writeByte(IMAGE)
                    buffer.writeInt(value.imageUnit)
                }
                is ArrayValue -> {
                    buffer.writeByte(ARRAY)
                    buffer.writeVarInt(value.elements.size)
                    value.elements.forEach { encode(buffer, it) }
                }
            }
        }

        private fun decode(buffer: FriendlyByteBuf): CooUniformValue {
            return when (val type = buffer.readUnsignedByte().toInt()) {
                FLOAT -> FloatValue(buffer.readFloat())
                INT -> IntValue(unZigZag(buffer.readVarInt()))
                VEC2 -> Vec2Value(buffer.readFloat(), buffer.readFloat())
                VEC3 -> Vec3Value(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
                VEC4 -> Vec4Value(
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()
                )
                BOOL -> BoolValue(buffer.readBoolean())
                UINT -> UIntValue(buffer.readInt().toUInt())
                DOUBLE -> DoubleValue(buffer.readDouble())
                IVEC -> IVecValue(readList(buffer) { readInt() })
                UVEC -> UVecValue(readList(buffer) { readInt().toUInt() })
                BVEC -> BVecValue(readList(buffer) { readBoolean() })
                DVEC -> DVecValue(readList(buffer) { readDouble() })
                MAT -> {
                    val (columns, rows) = readMatrixShape(buffer)
                    MatValue(columns, rows, List(columns * rows) { buffer.readFloat() })
                }
                DMAT -> {
                    val (columns, rows) = readMatrixShape(buffer)
                    DMatValue(columns, rows, List(columns * rows) { buffer.readDouble() })
                }
                SAMPLER -> SamplerValue(buffer.readInt())
                IMAGE -> ImageValue(buffer.readInt())
                ARRAY -> ArrayValue(List(buffer.readVarInt()) { decode(buffer) })
                else -> error("Unknown uniform value type: $type")
            }
        }

        private fun writeMatrixShape(buffer: FriendlyByteBuf, columns: Int, rows: Int) {
            buffer.writeByte(columns)
            buffer.writeByte(rows)
        }

        private fun readMatrixShape(buffer: FriendlyByteBuf): Pair<Int, Int> {
            return buffer.readUnsignedByte().toInt() to buffer.readUnsignedByte().toInt()
        }

        private fun <T> readList(buffer: FriendlyByteBuf, readElement: FriendlyByteBuf.() -> T): List<T> {
            return List(buffer.readUnsignedByte().toInt()) { buffer.readElement() }
        }

        private fun zigZag(value: Int): Int = (value shl 1) xor (value shr 31)

        private fun unZigZag(value: Int): Int = (value ushr 1) xor -(value and 1)
    }

    /** GLSL `bool`。 */
    data class BoolValue(val value: Boolean) : CooUniformValue

    /** GLSL `int`。 */
    data class IntValue(val value: Int) : CooUniformValue

    /** GLSL `uint`。 */
    data class UIntValue(val value: UInt) : CooUniformValue

    /** GLSL `float`。 */
    data class FloatValue(val value: Float) : CooUniformValue

    /** GLSL `double`，要求 OpenGL 4.0 或相应扩展。 */
    data class DoubleValue(val value: Double) : CooUniformValue

    /** GLSL `vec2`。 */
    data class Vec2Value(val x: Float, val y: Float) : CooUniformValue

    /** GLSL `vec3`。 */
    data class Vec3Value(val x: Float, val y: Float, val z: Float) : CooUniformValue

    /** GLSL `vec4`。 */
    data class Vec4Value(val x: Float, val y: Float, val z: Float, val w: Float) : CooUniformValue

    /**
     * GLSL `ivec2`、`ivec3` 或 `ivec4`。
     *
     * @property components 按 GLSL 分量顺序排列的 2 到 4 个整数
     */
    class IVecValue(components: List<Int>) : CooUniformValue {
        val components: List<Int> = immutableList(components)

        constructor(vararg components: Int) : this(components.toList())

        init {
            requireVectorSize(this.components.size)
        }

        override fun equals(other: Any?): Boolean = other is IVecValue && components == other.components

        override fun hashCode(): Int = components.hashCode()

        override fun toString(): String = "IVecValue(components=$components)"
    }

    /**
     * GLSL `uvec2`、`uvec3` 或 `uvec4`。
     *
     * @property components 按 GLSL 分量顺序排列的 2 到 4 个无符号整数
     */
    class UVecValue(components: List<UInt>) : CooUniformValue {
        val components: List<UInt> = immutableList(components)

        constructor(x: UInt, y: UInt) : this(listOf(x, y))

        constructor(x: UInt, y: UInt, z: UInt) : this(listOf(x, y, z))

        constructor(x: UInt, y: UInt, z: UInt, w: UInt) : this(listOf(x, y, z, w))

        init {
            requireVectorSize(this.components.size)
        }

        override fun equals(other: Any?): Boolean = other is UVecValue && components == other.components

        override fun hashCode(): Int = components.hashCode()

        override fun toString(): String = "UVecValue(components=$components)"
    }

    /**
     * GLSL `bvec2`、`bvec3` 或 `bvec4`。
     *
     * @property components 按 GLSL 分量顺序排列的 2 到 4 个布尔值
     */
    class BVecValue(components: List<Boolean>) : CooUniformValue {
        val components: List<Boolean> = immutableList(components)

        constructor(vararg components: Boolean) : this(components.toList())

        init {
            requireVectorSize(this.components.size)
        }

        override fun equals(other: Any?): Boolean = other is BVecValue && components == other.components

        override fun hashCode(): Int = components.hashCode()

        override fun toString(): String = "BVecValue(components=$components)"
    }

    /**
     * GLSL `dvec2`、`dvec3` 或 `dvec4`，要求 OpenGL 4.0 或相应扩展。
     *
     * @property components 按 GLSL 分量顺序排列的 2 到 4 个双精度值
     */
    class DVecValue(components: List<Double>) : CooUniformValue {
        val components: List<Double> = immutableList(components)

        constructor(vararg components: Double) : this(components.toList())

        init {
            requireVectorSize(this.components.size)
        }

        override fun equals(other: Any?): Boolean = other is DVecValue && components == other.components

        override fun hashCode(): Int = components.hashCode()

        override fun toString(): String = "DVecValue(components=$components)"
    }

    /**
     * 任意单精度 GLSL 矩阵，覆盖 `mat2` 到 `mat4x3` 的全部 9 种形状。
     *
     * [components] 使用 GLSL/OpenGL 的列优先顺序，数量必须等于 [columns] * [rows]。
     */
    class MatValue(
        val columns: Int,
        val rows: Int,
        components: List<Float>
    ) : CooUniformValue {
        val components: List<Float> = immutableList(components)

        constructor(columns: Int, rows: Int, vararg components: Float) :
            this(columns, rows, components.toList())

        constructor(value: Matrix2fc) : this(2, 2, value.get(FloatArray(4)).toList())

        constructor(value: Matrix3x2fc) : this(3, 2, value.get(FloatArray(6)).toList())

        constructor(value: Matrix3fc) : this(3, 3, value.get(FloatArray(9)).toList())

        constructor(value: Matrix4x3fc) : this(4, 3, value.get(FloatArray(12)).toList())

        constructor(value: Matrix4fc) : this(4, 4, value.get(FloatArray(16)).toList())

        init {
            requireMatrixShape(columns, rows, this.components.size)
        }

        fun copy(
            columns: Int = this.columns,
            rows: Int = this.rows,
            components: List<Float> = this.components
        ): MatValue = MatValue(columns, rows, components)

        override fun equals(other: Any?): Boolean {
            return other is MatValue &&
                columns == other.columns && rows == other.rows && components == other.components
        }

        override fun hashCode(): Int = 31 * (31 * columns + rows) + components.hashCode()

        override fun toString(): String = "MatValue(columns=$columns, rows=$rows, components=$components)"
    }

    /**
     * 任意双精度 GLSL 矩阵，覆盖 `dmat2` 到 `dmat4x3` 的全部 9 种形状。
     *
     * [components] 使用 GLSL/OpenGL 的列优先顺序，要求 OpenGL 4.0 或相应扩展。
     */
    class DMatValue(
        val columns: Int,
        val rows: Int,
        components: List<Double>
    ) : CooUniformValue {
        val components: List<Double> = immutableList(components)

        constructor(columns: Int, rows: Int, vararg components: Double) :
            this(columns, rows, components.toList())

        constructor(value: Matrix2dc) : this(2, 2, value.get(DoubleArray(4)).toList())

        constructor(value: Matrix3x2dc) : this(3, 2, value.get(DoubleArray(6)).toList())

        constructor(value: Matrix3dc) : this(3, 3, value.get(DoubleArray(9)).toList())

        constructor(value: Matrix4x3dc) : this(4, 3, value.get(DoubleArray(12)).toList())

        constructor(value: Matrix4dc) : this(4, 4, value.get(DoubleArray(16)).toList())

        init {
            requireMatrixShape(columns, rows, this.components.size)
        }

        fun copy(
            columns: Int = this.columns,
            rows: Int = this.rows,
            components: List<Double> = this.components
        ): DMatValue = DMatValue(columns, rows, components)

        override fun equals(other: Any?): Boolean {
            return other is DMatValue &&
                columns == other.columns && rows == other.rows && components == other.components
        }

        override fun hashCode(): Int = 31 * (31 * columns + rows) + components.hashCode()

        override fun toString(): String = "DMatValue(columns=$columns, rows=$rows, components=$components)"
    }

    /** sampler uniform 使用的纹理单元编号。 */
    data class SamplerValue(val textureUnit: Int) : CooUniformValue

    /** image uniform 使用的 image unit 编号。 */
    data class ImageValue(val imageUnit: Int) : CooUniformValue

    /**
     * GLSL uniform 数组。
     *
     * 数组不能为空，且所有元素必须具有完全相同的标量类型和形状。GLSL 不允许 uniform 数组嵌套。
     */
    class ArrayValue(elements: List<CooUniformValue>) : CooUniformValue {
        val elements: List<CooUniformValue> = immutableList(elements)

        constructor(vararg elements: CooUniformValue) : this(elements.toList())

        init {
            require(this.elements.isNotEmpty()) { "Uniform array cannot be empty" }
            require(this.elements.none { it is ArrayValue }) { "Nested uniform arrays are not supported by GLSL" }
            val first = this.elements.first()
            require(this.elements.drop(1).all { first.hasSameShape(it) }) {
                "Uniform array elements must have the same type and shape"
            }
        }

        fun copy(elements: List<CooUniformValue> = this.elements): ArrayValue = ArrayValue(elements)

        override fun equals(other: Any?): Boolean = other is ArrayValue && elements == other.elements

        override fun hashCode(): Int = elements.hashCode()

        override fun toString(): String = "ArrayValue(elements=$elements)"
    }
}

private fun <T : Any> immutableList(values: Collection<T>): List<T> = java.util.List.copyOf(values)

private fun requireVectorSize(size: Int) {
    require(size in 2..4) { "GLSL vector size must be between 2 and 4: $size" }
}

private fun requireMatrixShape(columns: Int, rows: Int, componentCount: Int) {
    require(columns in 2..4 && rows in 2..4) {
        "GLSL matrix dimensions must be between 2 and 4: ${columns}x$rows"
    }
    require(componentCount == columns * rows) {
        "GLSL ${columns}x$rows matrix requires ${columns * rows} components: $componentCount"
    }
}

private fun CooUniformValue.hasSameShape(other: CooUniformValue): Boolean {
    return this::class == other::class && when (this) {
        is CooUniformValue.IVecValue if other is CooUniformValue.IVecValue ->
            components.size == other.components.size

        is CooUniformValue.UVecValue if other is CooUniformValue.UVecValue ->
            components.size == other.components.size

        is CooUniformValue.BVecValue if other is CooUniformValue.BVecValue ->
            components.size == other.components.size

        is CooUniformValue.DVecValue if other is CooUniformValue.DVecValue ->
            components.size == other.components.size

        is CooUniformValue.MatValue if other is CooUniformValue.MatValue ->
            columns == other.columns && rows == other.rows

        is CooUniformValue.DMatValue if other is CooUniformValue.DMatValue ->
            columns == other.columns && rows == other.rows

        else -> true
    }
}
