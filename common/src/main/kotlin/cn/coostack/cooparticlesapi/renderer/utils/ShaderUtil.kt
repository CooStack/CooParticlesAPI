package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ShaderUtil {
    fun vertexBuilder(block: RenderVertexBuilder.() -> Unit = {}): RenderVertexBuilder {
        return RenderVertexBuilder().apply(block)
    }

    fun genVertexData(block: RenderVertexBuilder.() -> Unit): List<VertexData> {
        return vertexBuilder(block).createVertexData()
    }

    fun genModelVertices(block: RenderVertexBuilder.() -> Unit): List<RenderEntityModelVertex> {
        return vertexBuilder(block).create()
    }

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

    /**
     * 生成圆柱顶点 (三角形)
     * 不包括上下两面的底面
     * @param tessellate 细分程度 (至少为2)
     */
    fun genCylinder(r: Float, tessellate: Float, height: Float): List<VertexData> {
        val res = mutableListOf<VertexData>()
        require(height > 0 && r > 0 && tessellate >= 2)
        val step = 2 * PI.toFloat() / tessellate
        var current = 0f
        while (current < 2 * PI) {
            val x1 = r * cos(current)
            val x2 = r * cos(current + step)
            val z1 = r * sin(current)
            val z2 = r * sin(current + step)
            res.addAll(
                genSquare(
                    Vector3f(x1, height, z1),
                    Vector3f(x2, height, z2),
                    Vector3f(x2, 0f, z2),
                    Vector3f(x1, 0f, z1),
                )
            )
            current += step
        }

        return res
    }

    fun genSquareUV(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> {
        val res = mutableListOf<VertexData>()
        res.add(VertexData(p1, Vector2f(0f, 0f)))
        res.add(VertexData(p2, Vector2f(1f, 0f)))
        res.add(VertexData(p4, Vector2f(0f, 1f)))

        res.add(VertexData(p2, Vector2f(1f, 0f)))
        res.add(VertexData(p3, Vector2f(1f, 1f)))
        res.add(VertexData(p4, Vector2f(0f, 1f)))
        return res
    }

    fun genSquareUVScreen(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> {
        val res = mutableListOf<VertexData>()
        res.add(VertexData(p1, Vector2f(0f, 1f)))
        res.add(VertexData(p2, Vector2f(1f, 1f)))
        res.add(VertexData(p4, Vector2f(0f, 0f)))

        res.add(VertexData(p2, Vector2f(1f, 1f)))
        res.add(VertexData(p3, Vector2f(1f, 0f)))
        res.add(VertexData(p4, Vector2f(0f, 0f)))
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

    fun genBox(): List<VertexData> {
        val up = genSquareUV(
            Vector3f(-0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, -0.5f),
            Vector3f(-0.5f, 0.5f, -0.5f)
        )
        val down = genSquareUV(
            Vector3f(-0.5f, -0.5f, 0.5f),
            Vector3f(0.5f, -0.5f, 0.5f),
            Vector3f(0.5f, -0.5f, -0.5f),
            Vector3f(-0.5f, -0.5f, -0.5f)
        )
        val left = genSquareUV(
            Vector3f(-0.5f, -0.5f, 0.5f),
            Vector3f(-0.5f, 0.5f, 0.5f),
            Vector3f(-0.5f, 0.5f, -0.5f),
            Vector3f(-0.5f, -0.5f, -0.5f),
        )
        val right = genSquareUV(
            Vector3f(0.5f, -0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, -0.5f),
            Vector3f(0.5f, -0.5f, -0.5f),
        )
        val front = genSquareUV(
            Vector3f(-0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, -0.5f, 0.5f),
            Vector3f(-0.5f, -0.5f, 0.5f),
        )
        val back = genSquareUV(
            Vector3f(-0.5f, 0.5f, -0.5f),
            Vector3f(0.5f, 0.5f, -0.5f),
            Vector3f(0.5f, -0.5f, -0.5f),
            Vector3f(-0.5f, -0.5f, -0.5f),
        )
        val res = ArrayList<VertexData>()
        res.also {
            it.addAll(up)
            it.addAll(down)
            it.addAll(left)
            it.addAll(right)
            it.addAll(front)
            it.addAll(back)
        }
        return res
    }

    /**
     * @param r 球的半径
     * @param slices 经度细分
     * @param stacks 纬度细分
     */
    fun genBall(r: Float, stacks: Int, slices: Int): List<VertexData> {
        val res = mutableListOf<VertexData>()

        for (i in 0 until stacks) {
            val phi1 = i * PI.toFloat() / stacks
            val phi2 = (i + 1) * PI.toFloat() / stacks
            val r1 = r * sin(phi1)
            val r2 = r * sin(phi2)
            val y1 = r * cos(phi1)
            val y2 = r * cos(phi2)

            for (j in 0 until slices) {
                val theta1 = j * 2 * PI.toFloat() / slices
                val theta2 = (j + 1) * 2 * PI.toFloat() / slices
                val p1 = Vector3f(r1 * cos(theta1), y1, r1 * sin(theta1))
                val p2 = Vector3f(r1 * cos(theta2), y1, r1 * sin(theta2))
                val p3 = Vector3f(r2 * cos(theta1), y2, r2 * sin(theta1))
                val p4 = Vector3f(r2 * cos(theta2), y2, r2 * sin(theta2))
                res.addAll(genQuad(p1, p2, p4, p3))
            }
        }

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
            res.add(VertexData(northPole))
            res.add(VertexData(p1))
            res.add(VertexData(p2))
        }

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
