package cn.coostack.cooparticlesapi.utils.interpolator.data

import cn.coostack.cooparticlesapi.utils.GraphMathHelper

import org.joml.Vector3f

class InterpolatorVector3f(value: Vector3f) : AbstractInterpolatorData<Vector3f>(value) {

    companion object {
        @JvmStatic
        val CODEC = ForgeStreamCodec.of(InterpolatorVector3f>(
            { buf, data ->
                buf.writeFloat(data.value.x())
                buf.writeFloat(data.value.y())
                buf.writeFloat(data.value.z())
            }, {
                val x = it.readFloat()
                val y = it.readFloat()
                val z = it.readFloat()
                InterpolatorVector3f(Vector3f(x, y, z))
            }
        )
    }

    override fun getWithInterpolator(progress: Number): Vector3f {
        return GraphMathHelper.lerp(progress.toFloat(), last, value)
    }

    override fun getCurrent(): Vector3f {
        return value
    }

    operator fun plus(vec: Vector3f): InterpolatorVector3f {
        uploadData(Vector3f(value).add(vec))
        return this
    }

    operator fun minus(vec: Vector3f): InterpolatorVector3f {
        uploadData(Vector3f(value).sub(vec))
        return this
    }

    operator fun times(float: Float): InterpolatorVector3f {
        uploadData(Vector3f(value).mul(float))
        return this
    }

    operator fun div(float: Float): InterpolatorVector3f {
        require(float != 0f) { "Division by zero" }
        uploadData(Vector3f(value).mul(1f / float))
        return this
    }

    operator fun unaryMinus(): InterpolatorVector3f {
        uploadData(Vector3f(value).mul(-1f))
        return this
    }
}
