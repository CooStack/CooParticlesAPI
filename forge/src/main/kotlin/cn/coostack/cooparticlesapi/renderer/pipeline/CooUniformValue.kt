package cn.coostack.cooparticlesapi.renderer.pipeline

import net.minecraft.network.PacketByteBuf
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

        val STREAM_CODEC: ForgeStreamCodec<CooUniformValue> = ForgeStreamCodec.of(::encode, ::decode)

        private fun encode(buffer: PacketByteBuf, value: CooUniformValue) {
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

        private fun decode(buffer: PacketByteBuf): CooUniformValue {
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

        private fun writeMatrixShape(buffer: PacketByteBuf, columns: Int, rows: Int) {
            buffer.writeByte(columns)
            buffer.writeByte(rows)
        }

        private fun readMatrixShape(buffer: PacketByteBuf): Pair<Int, Int> {
            return buffer.readUnsignedByte().toInt() to buffer.readUnsignedByte().toInt()
        }

        private fun <T> readList(buffer: PacketByteBuf, readElement: PacketByteBuf.() -> T): List<T> {
            return List(buffer.readUnsignedByte().toInt()) { buffer.readElement() }
        }

        private fun zigZag(value: Int): Int = (value shl 1) xor (value shr 31)
        private fun unZigZag(value: Int): Int = (value ushr 1) xor -(value and 1)
    }

    data class BoolValue(val value: Boolean) : CooUniformValue
    data class IntValue(val value: Int) : CooUniformValue
    data class UIntValue(val value: UInt) : CooUniformValue
    data class FloatValue(val value: Float) : CooUniformValue
    data class DoubleValue(val value: Double) : CooUniformValue
    data class Vec2Value(val x: Float, val y: Float) : CooUniformValue
    data class Vec3Value(val x: Float, val y: Float, val z: Float) : CooUniformValue
    data class Vec4Value(val x: Float, val y: Float, val z: Float, val w: Float) : CooUniformValue

    class IVecValue(components: List<Int>) : CooUniformValue {
        val components: List<Int> = immutableList(components)
        constructor(vararg components: Int) : this(components.toList())
        init { requireVectorSize(this.components.size) }
    }

    class UVecValue(components: List<UInt>) : CooUniformValue {
        val components: List<UInt> = immutableList(components)
        constructor(x: UInt, y: UInt) : this(listOf(x, y))
        constructor(x: UInt, y: UInt, z: UInt) : this(listOf(x, y, z))
        constructor(x: UInt, y: UInt, z: UInt, w: UInt) : this(listOf(x, y, z, w))
        init { requireVectorSize(this.components.size) }
    }

    class BVecValue(components: List<Boolean>) : CooUniformValue {
        val components: List<Boolean> = immutableList(components)
        constructor(vararg components: Boolean) : this(components.toList())
        init { requireVectorSize(this.components.size) }
    }

    class DVecValue(components: List<Double>) : CooUniformValue {
        val components: List<Double> = immutableList(components)
        constructor(vararg components: Double) : this(components.toList())
        init { requireVectorSize(this.components.size) }
    }

    class MatValue(
        val columns: Int,
        val rows: Int,
        components: List<Float>
    ) : CooUniformValue {
        val components: List<Float> = immutableList(components)
        constructor(columns: Int, rows: Int, vararg components: Float) : this(columns, rows, components.toList())
        constructor(value: Matrix2fc) : this(2, 2, value.get(FloatArray(4)).toList())
        constructor(value: Matrix3x2fc) : this(3, 2, value.get(FloatArray(6)).toList())
        constructor(value: Matrix3fc) : this(3, 3, value.get(FloatArray(9)).toList())
        constructor(value: Matrix4x3fc) : this(4, 3, value.get(FloatArray(12)).toList())
        constructor(value: Matrix4fc) : this(4, 4, value.get(FloatArray(16)).toList())
        init { requireMatrixShape(columns, rows, this.components.size) }
    }

    class DMatValue(
        val columns: Int,
        val rows: Int,
        components: List<Double>
    ) : CooUniformValue {
        val components: List<Double> = immutableList(components)
        constructor(columns: Int, rows: Int, vararg components: Double) : this(columns, rows, components.toList())
        constructor(value: Matrix2dc) : this(2, 2, value.get(DoubleArray(4)).toList())
        constructor(value: Matrix3x2dc) : this(3, 2, value.get(DoubleArray(6)).toList())
        constructor(value: Matrix3dc) : this(3, 3, value.get(DoubleArray(9)).toList())
        constructor(value: Matrix4x3dc) : this(4, 3, value.get(DoubleArray(12)).toList())
        constructor(value: Matrix4dc) : this(4, 4, value.get(DoubleArray(16)).toList())
        init { requireMatrixShape(columns, rows, this.components.size) }
    }

    data class SamplerValue(val textureUnit: Int) : CooUniformValue
    data class ImageValue(val imageUnit: Int) : CooUniformValue

    class ArrayValue(elements: List<CooUniformValue>) : CooUniformValue {
        val elements: List<CooUniformValue> = immutableList(elements)
        constructor(vararg elements: CooUniformValue) : this(elements.toList())
        init {
            require(this.elements.isNotEmpty()) { "Uniform array cannot be empty" }
            require(this.elements.none { it is ArrayValue }) { "Nested uniform arrays are not supported by GLSL" }
        }
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
