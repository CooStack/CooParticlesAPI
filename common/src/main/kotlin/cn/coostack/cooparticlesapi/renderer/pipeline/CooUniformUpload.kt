package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.shader.api.CooProgramUniformAccess
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * 按 [CooUniformValue] 的 GLSL 类型和形状上传 uniform。
 *
 * 调用前必须已激活 [CooProgramUniformAccess.program] 对应的 OpenGL program。
 *
 * @param key shader 中的 uniform 名称；数组可传基础名称或首元素名称
 * @param value 要上传的值
 */
fun CooProgramUniformAccess.setUniform(key: String, value: CooUniformValue) {
    when (value) {
        is CooUniformValue.BoolValue -> setBoolean(key, value.value)
        is CooUniformValue.IntValue -> setInt(key, value.value)
        is CooUniformValue.UIntValue -> setUInt(key, value.value)
        is CooUniformValue.FloatValue -> setFloat(key, value.value)
        is CooUniformValue.DoubleValue -> setDouble(key, value.value)
        is CooUniformValue.Vec2Value -> setFloat2(key, Vector2f(value.x, value.y))
        is CooUniformValue.Vec3Value -> setFloat3(key, Vector3f(value.x, value.y, value.z))
        is CooUniformValue.Vec4Value -> setFloat4(key, Vector4f(value.x, value.y, value.z, value.w))
        is CooUniformValue.IVecValue -> setIntVector(key, value.components.toIntArray())
        is CooUniformValue.UVecValue -> setUIntVector(key, value.components)
        is CooUniformValue.BVecValue -> setBooleanVector(key, value.components)
        is CooUniformValue.DVecValue -> setDoubleVector(key, value.components.toDoubleArray())
        is CooUniformValue.MatValue -> setFloatMatrix(
            key,
            value.columns,
            value.rows,
            value.components.toFloatArray()
        )
        is CooUniformValue.DMatValue -> setDoubleMatrix(
            key,
            value.columns,
            value.rows,
            value.components.toDoubleArray()
        )
        is CooUniformValue.SamplerValue -> setInt(key, value.textureUnit)
        is CooUniformValue.ImageValue -> setInt(key, value.imageUnit)
        is CooUniformValue.ArrayValue -> setUniformArray(key, value.elements)
    }
}

private fun CooProgramUniformAccess.setUniformArray(key: String, elements: List<CooUniformValue>) {
    when (val first = elements.first()) {
        is CooUniformValue.BoolValue -> setBooleanVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.BoolValue).value }
        )
        is CooUniformValue.IntValue -> setIntVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.IntValue).value }.toIntArray()
        )
        is CooUniformValue.UIntValue -> setUIntVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.UIntValue).value }
        )
        is CooUniformValue.FloatValue -> setFloatVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.FloatValue).value }.toFloatArray()
        )
        is CooUniformValue.DoubleValue -> setDoubleVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.DoubleValue).value }.toDoubleArray()
        )
        is CooUniformValue.Vec2Value -> setFloatVectorArray(
            key,
            2,
            elements.flatMap { listOf((it as CooUniformValue.Vec2Value).x, it.y) }.toFloatArray()
        )
        is CooUniformValue.Vec3Value -> setFloatVectorArray(
            key,
            3,
            elements.flatMap {
                it as CooUniformValue.Vec3Value
                listOf(it.x, it.y, it.z)
            }.toFloatArray()
        )
        is CooUniformValue.Vec4Value -> setFloatVectorArray(
            key,
            4,
            elements.flatMap {
                it as CooUniformValue.Vec4Value
                listOf(it.x, it.y, it.z, it.w)
            }.toFloatArray()
        )
        is CooUniformValue.IVecValue -> setIntVectorArray(
            key,
            first.components.size,
            elements.flatMap { (it as CooUniformValue.IVecValue).components }.toIntArray()
        )
        is CooUniformValue.UVecValue -> setUIntVectorArray(
            key,
            first.components.size,
            elements.flatMap { (it as CooUniformValue.UVecValue).components }
        )
        is CooUniformValue.BVecValue -> setBooleanVectorArray(
            key,
            first.components.size,
            elements.flatMap { (it as CooUniformValue.BVecValue).components }
        )
        is CooUniformValue.DVecValue -> setDoubleVectorArray(
            key,
            first.components.size,
            elements.flatMap { (it as CooUniformValue.DVecValue).components }.toDoubleArray()
        )
        is CooUniformValue.MatValue -> setFloatMatrixArray(
            key,
            first.columns,
            first.rows,
            elements.flatMap { (it as CooUniformValue.MatValue).components }.toFloatArray()
        )
        is CooUniformValue.DMatValue -> setDoubleMatrixArray(
            key,
            first.columns,
            first.rows,
            elements.flatMap { (it as CooUniformValue.DMatValue).components }.toDoubleArray()
        )
        is CooUniformValue.SamplerValue -> setIntVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.SamplerValue).textureUnit }.toIntArray()
        )
        is CooUniformValue.ImageValue -> setIntVectorArray(
            key,
            1,
            elements.map { (it as CooUniformValue.ImageValue).imageUnit }.toIntArray()
        )
        is CooUniformValue.ArrayValue -> error("Nested uniform arrays are not supported by GLSL")
    }
}
