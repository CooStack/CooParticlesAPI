package cn.coostack.cooparticlesapi.utils

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

class RotationMatrix private constructor(
    private val matrix: Array<DoubleArray>
) {
    companion object {
        fun fromAxisAngle(axis: RelativeLocation, angle: Double): RotationMatrix {
            val u = axis.normalize()
            val cos = cos(angle)
            val sin = sin(angle)
            val oneMinusCos = 1 - cos

            return RotationMatrix(
                arrayOf(
                    doubleArrayOf(
                        cos + u.x * u.x * oneMinusCos,
                        u.x * u.y * oneMinusCos - u.z * sin,
                        u.x * u.z * oneMinusCos + u.y * sin
                    ),
                    doubleArrayOf(
                        u.y * u.x * oneMinusCos + u.z * sin,
                        cos + u.y * u.y * oneMinusCos,
                        u.y * u.z * oneMinusCos - u.x * sin
                    ),
                    doubleArrayOf(
                        u.z * u.x * oneMinusCos - u.y * sin,
                        u.z * u.y * oneMinusCos + u.x * sin,
                        cos + u.z * u.z * oneMinusCos
                    )
                )
            )
        }
    }

    fun applyToClone(point: RelativeLocation): RelativeLocation {
        return applyTo(point.clone())
    }

    fun applyTo(point: RelativeLocation): RelativeLocation {
        val x = point.x
        val y = point.y
        val z = point.z
        point.x = (matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z)
        point.y = (matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z)
        point.z = (matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z)
        return point
    }

}