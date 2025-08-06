package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ShaderUtil {
    fun genSquare(w: Float, h: Float): List<VertexData> {
        val p1 = Vector3f(-w / 2, -h / 2, 0f)
        val p2 = Vector3f(w / 2, h / 2, 0f)
        return genSquare(p1, p2)
    }

    fun genSquare(p1: Vector3f, p2: Vector3f): List<VertexData> {
        val res = mutableListOf<VertexData>()
        val p3 = Vector3f(p2.x, p1.y, p1.z)
        val p4 = Vector3f(p1.x, p2.y, p2.z)
        res.add(VertexData(p1))
        res.add(VertexData(p3))
        res.add(VertexData(p2))

        res.add(VertexData(p1))
        res.add(VertexData(p2))
        res.add(VertexData(p4))
        return res
    }

    fun genSquare(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> {
        val res = mutableListOf<VertexData>()
        res.add(VertexData(p1))
        res.add(VertexData(p2))
        res.add(VertexData(p4))

        res.add(VertexData(p2))
        res.add(VertexData(p3))
        res.add(VertexData(p4))
        return res
    }

    /**
     * @param r 球的半径
     * @param slices 经度细分
     * @param stacks 纬度细分
     */
    fun genBall(r: Float, stacks: Int, slices: Int): List<VertexData> {
        val res = mutableListOf<VertexData>()

        // 纬度从0到π（北极到南极），不包括两极
        for (i in 0 until stacks) {
            val phi1 = i * PI.toFloat() / stacks       // 当前纬度
            val phi2 = (i + 1) * PI.toFloat() / stacks // 下一纬度

            // 计算当前纬度圆和下一纬度圆的半径
            val r1 = r * sin(phi1)
            val r2 = r * sin(phi2)

            // 计算当前纬度圆和下一纬度圆的y坐标
            val y1 = r * cos(phi1)
            val y2 = r * cos(phi2)

            // 经度从0到2π
            for (j in 0 until slices) {
                val theta1 = j * 2 * PI.toFloat() / slices      // 当前经度
                val theta2 = (j + 1) * 2 * PI.toFloat() / slices // 下一经度

                // 计算四个顶点 (修正坐标计算)
                val p1 = Vector3f(
                    r1 * cos(theta1),
                    y1,
                    r1 * sin(theta1)
                )
                val p2 = Vector3f(
                    r1 * cos(theta2),
                    y1,
                    r1 * sin(theta2)
                )
                val p3 = Vector3f(
                    r2 * cos(theta1),
                    y2,
                    r2 * sin(theta1)
                )
                val p4 = Vector3f(
                    r2 * cos(theta2),
                    y2,
                    r2 * sin(theta2)
                )

                // 修正四边形生成顺序（关键修复）
                res.addAll(genQuad(p1, p2, p4, p3))
            }
        }

        // 添加北极点（修正）
        for (j in 0 until slices) {
            val theta1 = j * 2 * PI.toFloat() / slices
            val theta2 = (j + 1) * 2 * PI.toFloat() / slices

            val northPole = Vector3f(0f, r, 0f)
            val p1 = Vector3f(
                r * sin(0f) * cos(theta1),
                r * cos(0f),
                r * sin(0f) * sin(theta1)
            )
            val p2 = Vector3f(
                r * sin(0f) * cos(theta2),
                r * cos(0f),
                r * sin(0f) * sin(theta2)
            )

            // 修正北极点连接顺序
            res.add(VertexData(northPole))
            res.add(VertexData(p1))
            res.add(VertexData(p2))
        }

        // 添加南极点（修正）
        for (j in 0 until slices) {
            val theta1 = j * 2 * PI.toFloat() / slices
            val theta2 = (j + 1) * 2 * PI.toFloat() / slices

            val southPole = Vector3f(0f, -r, 0f)
            val p1 = Vector3f(
                r * sin(PI.toFloat()) * cos(theta1),
                r * cos(PI.toFloat()),
                r * sin(PI.toFloat()) * sin(theta1)
            )
            val p2 = Vector3f(
                r * sin(PI.toFloat()) * cos(theta2),
                r * cos(PI.toFloat()),
                r * sin(PI.toFloat()) * sin(theta2)
            )

            // 修正南极点连接顺序
            res.add(VertexData(southPole))
            res.add(VertexData(p2))
            res.add(VertexData(p1))
        }

        return res
    }

    private fun genQuad(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> {
        return listOf(
            VertexData(p1),
            VertexData(p2),
            VertexData(p3),

            VertexData(p1),
            VertexData(p3),
            VertexData(p4)
        )
    }
}