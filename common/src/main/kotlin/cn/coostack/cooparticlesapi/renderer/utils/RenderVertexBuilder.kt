package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPipe
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitive
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitiveMode
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Render-entity vertex builder for reusable local mesh construction.
 *
 * It mirrors the fluent style of PointsBuilder, but exports render-ready vertices instead of particle points.
 * Shapes are generated around the local origin and can be appended to [RenderEntityModelBuilder] directly.
 */
class RenderVertexBuilder {
    companion object {
        @JvmStatic
        fun create(): RenderVertexBuilder = RenderVertexBuilder()

        @JvmStatic
        fun of(
            vertices: Collection<RenderEntityModelVertex>,
            primitiveMode: RenderEntityModelPrimitiveMode = RenderEntityModelPrimitiveMode.TRIANGLES
        ): RenderVertexBuilder {
            return RenderVertexBuilder().addVertices(vertices, primitiveMode)
        }
    }

    private data class VertexBatch(
        val primitiveMode: RenderEntityModelPrimitiveMode,
        val vertices: MutableList<RenderEntityModelVertex> = mutableListOf()
    )

    private val batches = ArrayList<VertexBatch>()
    private var defaultPrimitiveMode = RenderEntityModelPrimitiveMode.TRIANGLES
    private var defaultColor = Vector4f(1f, 1f, 1f, 1f)
    private var defaultUv = Vector2f(0f, 0f)
    private var defaultNormal = Vector3f(0f, 1f, 0f)

    fun mode(primitiveMode: RenderEntityModelPrimitiveMode): RenderVertexBuilder {
        defaultPrimitiveMode = primitiveMode
        return this
    }

    fun color(color: Vector4f): RenderVertexBuilder {
        defaultColor = Vector4f(color)
        return this
    }

    fun color(red: Number, green: Number, blue: Number, alpha: Number = 1f): RenderVertexBuilder {
        defaultColor = Vector4f(red.f(), green.f(), blue.f(), alpha.f())
        return this
    }

    fun uv(uv: Vector2f): RenderVertexBuilder {
        defaultUv = Vector2f(uv)
        return this
    }

    fun normal(normal: Vector3f): RenderVertexBuilder {
        defaultNormal = safeNormal(normal, Vector3f(0f, 1f, 0f))
        return this
    }

    fun addVertex(
        position: Vector3f,
        color: Vector4f = defaultColor,
        uv: Vector2f = defaultUv,
        normal: Vector3f = defaultNormal,
        primitiveMode: RenderEntityModelPrimitiveMode = defaultPrimitiveMode
    ): RenderVertexBuilder {
        batch(primitiveMode).vertices += vertex(position, color, uv, normal)
        return this
    }

    fun addVertex(
        x: Number,
        y: Number,
        z: Number,
        color: Vector4f = defaultColor,
        uv: Vector2f = defaultUv,
        normal: Vector3f = defaultNormal,
        primitiveMode: RenderEntityModelPrimitiveMode = defaultPrimitiveMode
    ): RenderVertexBuilder {
        return addVertex(Vector3f(x.f(), y.f(), z.f()), color, uv, normal, primitiveMode)
    }

    fun addVertices(
        vertices: Collection<RenderEntityModelVertex>,
        primitiveMode: RenderEntityModelPrimitiveMode = defaultPrimitiveMode
    ): RenderVertexBuilder {
        val target = batch(primitiveMode).vertices
        vertices.forEach { target += cloneVertex(it) }
        return this
    }

    fun addLine(
        from: Vector3f,
        to: Vector3f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        addVertex(from, color, primitiveMode = RenderEntityModelPrimitiveMode.LINES)
        addVertex(to, color, primitiveMode = RenderEntityModelPrimitiveMode.LINES)
        return this
    }

    fun addTriangle(
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex
    ): RenderVertexBuilder {
        val vertices = batch(RenderEntityModelPrimitiveMode.TRIANGLES).vertices
        vertices += cloneVertex(first)
        vertices += cloneVertex(second)
        vertices += cloneVertex(third)
        return this
    }

    fun addTriangle(
        first: Vector3f,
        second: Vector3f,
        third: Vector3f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        val normal = faceNormal(first, second, third)
        return addTriangle(
            vertex(first, color, defaultUv, normal),
            vertex(second, color, defaultUv, normal),
            vertex(third, color, defaultUv, normal)
        )
    }

    fun addQuad(
        first: RenderEntityModelVertex,
        second: RenderEntityModelVertex,
        third: RenderEntityModelVertex,
        fourth: RenderEntityModelVertex
    ): RenderVertexBuilder {
        addTriangle(first, second, third)
        addTriangle(first, third, fourth)
        return this
    }

    fun addQuad(
        first: Vector3f,
        second: Vector3f,
        third: Vector3f,
        fourth: Vector3f,
        color: Vector4f = defaultColor,
        uvMin: Vector2f = Vector2f(0f, 0f),
        uvMax: Vector2f = Vector2f(1f, 1f),
        normal: Vector3f? = null
    ): RenderVertexBuilder {
        val quadNormal = normal?.let { safeNormal(it, defaultNormal) } ?: faceNormal(first, second, third)
        return addQuad(
            vertex(first, color, Vector2f(uvMin.x, uvMin.y), quadNormal),
            vertex(second, color, Vector2f(uvMax.x, uvMin.y), quadNormal),
            vertex(third, color, Vector2f(uvMax.x, uvMax.y), quadNormal),
            vertex(fourth, color, Vector2f(uvMin.x, uvMax.y), quadNormal)
        )
    }

    fun addQuad(
        width: Number,
        height: Number,
        z: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addPlane(width, height, z, color)

    fun addPlane(
        width: Number,
        height: Number,
        z: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        val halfWidth = width.f() / 2f
        val halfHeight = height.f() / 2f
        val planeZ = z.f()
        return addQuad(
            Vector3f(-halfWidth, -halfHeight, planeZ),
            Vector3f(halfWidth, -halfHeight, planeZ),
            Vector3f(halfWidth, halfHeight, planeZ),
            Vector3f(-halfWidth, halfHeight, planeZ),
            color,
            normal = Vector3f(0f, 0f, 1f)
        )
    }

    fun addDisc(
        radius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        require(segments >= 3) { "segments must be at least 3" }
        val r = radius.f()
        val centerY = y.f()
        val center = vertex(Vector3f(0f, centerY, 0f), color, Vector2f(0.5f, 0.5f), Vector3f(0f, 1f, 0f))
        repeat(segments) { index ->
            val u0 = index.toFloat() / segments.toFloat()
            val u1 = (index + 1).toFloat() / segments.toFloat()
            val a = TWO_PI * u0
            val b = TWO_PI * u1
            val first = ringVertex(r, centerY, a, color, Vector2f(0.5f + cos(a.toDouble()).toFloat() * 0.5f, 0.5f + sin(a.toDouble()).toFloat() * 0.5f))
            val second = ringVertex(r, centerY, b, color, Vector2f(0.5f + cos(b.toDouble()).toFloat() * 0.5f, 0.5f + sin(b.toDouble()).toFloat() * 0.5f))
            addTriangle(center, second, first)
        }
        return this
    }

    fun addRing(
        innerRadius: Number,
        outerRadius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        require(segments >= 3) { "segments must be at least 3" }
        val inner = innerRadius.f()
        val outer = outerRadius.f()
        require(inner >= 0f && outer > inner) { "outerRadius must be greater than innerRadius" }
        val centerY = y.f()
        repeat(segments) { index ->
            val u0 = index.toFloat() / segments.toFloat()
            val u1 = (index + 1).toFloat() / segments.toFloat()
            val a = TWO_PI * u0
            val b = TWO_PI * u1
            addQuad(
                ringVertex(inner, centerY, a, color, Vector2f(u0, 0f)),
                ringVertex(inner, centerY, b, color, Vector2f(u1, 0f)),
                ringVertex(outer, centerY, b, color, Vector2f(u1, 1f)),
                ringVertex(outer, centerY, a, color, Vector2f(u0, 1f))
            )
        }
        return this
    }

    fun addAnnulus(
        innerRadius: Number,
        outerRadius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addRing(innerRadius, outerRadius, segments, y, color)

    fun addCircleLine(
        radius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        require(segments >= 3) { "segments must be at least 3" }
        val r = radius.f()
        val centerY = y.f()
        repeat(segments) { index ->
            val a = TWO_PI * index.toFloat() / segments.toFloat()
            val b = TWO_PI * (index + 1).toFloat() / segments.toFloat()
            addLine(
                Vector3f(cos(a.toDouble()).toFloat() * r, centerY, sin(a.toDouble()).toFloat() * r),
                Vector3f(cos(b.toDouble()).toFloat() * r, centerY, sin(b.toDouble()).toFloat() * r),
                color
            )
        }
        return this
    }

    fun addWireCircle(
        radius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addCircleLine(radius, segments, y, color)

    fun addSphere(
        radius: Number,
        latSegments: Int = 12,
        lonSegments: Int = 32,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        require(latSegments >= 2) { "latSegments must be at least 2" }
        require(lonSegments >= 3) { "lonSegments must be at least 3" }
        val r = radius.f()
        for (lat in 0 until latSegments) {
            val v0 = lat.toFloat() / latSegments.toFloat()
            val v1 = (lat + 1).toFloat() / latSegments.toFloat()
            val theta0 = (-PI / 2.0 + PI * v0).toFloat()
            val theta1 = (-PI / 2.0 + PI * v1).toFloat()
            for (lon in 0 until lonSegments) {
                val u0 = lon.toFloat() / lonSegments.toFloat()
                val u1 = (lon + 1).toFloat() / lonSegments.toFloat()
                val phi0 = TWO_PI * u0
                val phi1 = TWO_PI * u1
                addQuad(
                    sphereVertex(r, theta0, phi0, u0, v0, color),
                    sphereVertex(r, theta1, phi0, u0, v1, color),
                    sphereVertex(r, theta1, phi1, u1, v1, color),
                    sphereVertex(r, theta0, phi1, u1, v0, color)
                )
            }
        }
        return this
    }

    fun addBall(
        radius: Number,
        latSegments: Int = 12,
        lonSegments: Int = 32,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addSphere(radius, latSegments, lonSegments, color)

    fun addRibbon(
        points: List<Vector3f>,
        width: Number,
        up: Vector3f = Vector3f(0f, 1f, 0f),
        color: Vector4f = defaultColor,
        closed: Boolean = false
    ): RenderVertexBuilder {
        if (points.size < 2) {
            return this
        }
        val halfWidth = width.f() / 2f
        val upVector = safeNormal(up, Vector3f(0f, 1f, 0f))
        val segmentCount = if (closed) points.size else points.size - 1
        repeat(segmentCount) { index ->
            val from = points[index]
            val to = points[(index + 1) % points.size]
            if (Vector3f(to).sub(from).lengthSquared() <= EPSILON) {
                return@repeat
            }
            val side = sideVector(from, to, upVector).mul(halfWidth)
            val u0 = index.toFloat() / segmentCount.toFloat()
            val u1 = (index + 1).toFloat() / segmentCount.toFloat()
            addQuad(
                vertex(Vector3f(from).sub(side), color, Vector2f(u0, 0f), upVector),
                vertex(Vector3f(to).sub(side), color, Vector2f(u1, 0f), upVector),
                vertex(Vector3f(to).add(side), color, Vector2f(u1, 1f), upVector),
                vertex(Vector3f(from).add(side), color, Vector2f(u0, 1f), upVector)
            )
        }
        return this
    }

    fun translate(offset: Vector3f): RenderVertexBuilder {
        verticesOnEach { vertex -> vertex.position.add(offset) }
        return this
    }

    fun translate(x: Number, y: Number, z: Number): RenderVertexBuilder {
        return translate(Vector3f(x.f(), y.f(), z.f()))
    }

    fun scale(factor: Number): RenderVertexBuilder {
        val scale = factor.f()
        verticesOnEach { vertex -> vertex.position.mul(scale) }
        return this
    }

    fun scale(x: Number, y: Number, z: Number): RenderVertexBuilder {
        val sx = x.f()
        val sy = y.f()
        val sz = z.f()
        verticesOnEach { vertex ->
            vertex.position.set(vertex.position.x * sx, vertex.position.y * sy, vertex.position.z * sz)
            vertex.normal.normalizeSafe(defaultNormal)
        }
        return this
    }

    fun rotateX(radians: Number, origin: Vector3f = Vector3f(0f, 0f, 0f)): RenderVertexBuilder {
        return rotate(Vector3f(1f, 0f, 0f), radians, origin)
    }

    fun rotateY(radians: Number, origin: Vector3f = Vector3f(0f, 0f, 0f)): RenderVertexBuilder {
        return rotate(Vector3f(0f, 1f, 0f), radians, origin)
    }

    fun rotateZ(radians: Number, origin: Vector3f = Vector3f(0f, 0f, 0f)): RenderVertexBuilder {
        return rotate(Vector3f(0f, 0f, 1f), radians, origin)
    }

    fun rotate(
        axis: Vector3f,
        radians: Number,
        origin: Vector3f = Vector3f(0f, 0f, 0f)
    ): RenderVertexBuilder {
        val safeAxis = safeNormal(axis, Vector3f(0f, 1f, 0f))
        val angle = radians.toDouble()
        verticesOnEach { vertex ->
            vertex.position.set(rotatePosition(vertex.position, safeAxis, angle, origin))
            vertex.normal.set(rotateVector(vertex.normal, safeAxis, angle).normalizeSafe(defaultNormal))
        }
        return this
    }

    fun twist(
        axis: Vector3f = Vector3f(0f, 1f, 0f),
        radiansPerUnit: Number,
        origin: Vector3f = Vector3f(0f, 0f, 0f)
    ): RenderVertexBuilder {
        val safeAxis = safeNormal(axis, Vector3f(0f, 1f, 0f))
        val amount = radiansPerUnit.toDouble()
        verticesOnEach { vertex ->
            val projection = Vector3f(vertex.position).sub(origin).dot(safeAxis)
            val angle = projection * amount
            vertex.position.set(rotatePosition(vertex.position, safeAxis, angle.toDouble(), origin))
            vertex.normal.set(rotateVector(vertex.normal, safeAxis, angle.toDouble()).normalizeSafe(defaultNormal))
        }
        return this
    }

    fun twist(
        axis: Vector3f,
        totalRadians: Number,
        minProjection: Number,
        maxProjection: Number,
        origin: Vector3f = Vector3f(0f, 0f, 0f)
    ): RenderVertexBuilder {
        val min = minProjection.f()
        val max = maxProjection.f()
        if (abs(max - min) <= EPSILON) {
            return this
        }
        val safeAxis = safeNormal(axis, Vector3f(0f, 1f, 0f))
        val total = totalRadians.toDouble()
        verticesOnEach { vertex ->
            val projection = Vector3f(vertex.position).sub(origin).dot(safeAxis)
            val t = ((projection - min) / (max - min)).coerceIn(0f, 1f)
            val angle = total * t.toDouble()
            vertex.position.set(rotatePosition(vertex.position, safeAxis, angle, origin))
            vertex.normal.set(rotateVector(vertex.normal, safeAxis, angle).normalizeSafe(defaultNormal))
        }
        return this
    }

    fun verticesOnEach(handler: (RenderEntityModelVertex) -> Unit): RenderVertexBuilder {
        batches.forEach { batch -> batch.vertices.forEach(handler) }
        return this
    }

    fun clear(): RenderVertexBuilder {
        batches.clear()
        return this
    }

    fun create(): List<RenderEntityModelVertex> {
        return batches.flatMap { batch -> batch.vertices.map { cloneVertex(it) } }
    }

    fun createWithoutClone(): List<RenderEntityModelVertex> {
        return batches.flatMap { it.vertices }
    }

    fun createVertexData(): List<VertexData> {
        return create().map { VertexData(it.position, it.color, it.uv) }
    }

    fun createPrimitives(pipe: RenderEntityModelPipe): List<RenderEntityModelPrimitive> {
        return batches
            .filter { it.vertices.isNotEmpty() }
            .map { batch ->
                RenderEntityModelPrimitive(
                    pipe = pipe,
                    vertices = batch.vertices.map { cloneVertex(it) },
                    primitiveMode = batch.primitiveMode
                )
            }
    }

    fun addTo(model: RenderEntityModelBuilder, pipe: RenderEntityModelPipe): RenderVertexBuilder {
        batches.forEach { batch ->
            batch.vertices.forEach { vertex ->
                model.addVertex(
                    pipe = pipe,
                    position = Vector3f(vertex.position),
                    color = Vector4f(vertex.color),
                    uv = Vector2f(vertex.uv),
                    normal = Vector3f(vertex.normal),
                    primitiveMode = batch.primitiveMode
                )
            }
        }
        return this
    }

    fun buildModel(pipe: RenderEntityModelPipe): RenderEntityModel {
        return RenderEntityModel(listOf(pipe), createPrimitives(pipe))
    }

    fun cloneBuilder(): RenderVertexBuilder {
        val clone = RenderVertexBuilder()
            .mode(defaultPrimitiveMode)
            .color(defaultColor)
            .uv(defaultUv)
            .normal(defaultNormal)
        batches.forEach { batch -> clone.addVertices(batch.vertices, batch.primitiveMode) }
        return clone
    }

    private fun batch(primitiveMode: RenderEntityModelPrimitiveMode): VertexBatch {
        val current = batches.lastOrNull()
        if (current?.primitiveMode == primitiveMode) {
            return current
        }
        return VertexBatch(primitiveMode).also { batches += it }
    }

    private fun ringVertex(
        radius: Float,
        y: Float,
        angle: Float,
        color: Vector4f,
        uv: Vector2f
    ): RenderEntityModelVertex {
        return vertex(
            Vector3f(cos(angle.toDouble()).toFloat() * radius, y, sin(angle.toDouble()).toFloat() * radius),
            color,
            uv,
            Vector3f(0f, 1f, 0f)
        )
    }

    private fun sphereVertex(
        radius: Float,
        theta: Float,
        phi: Float,
        u: Float,
        v: Float,
        color: Vector4f
    ): RenderEntityModelVertex {
        val ringRadius = cos(theta.toDouble()).toFloat() * radius
        val position = Vector3f(
            cos(phi.toDouble()).toFloat() * ringRadius,
            sin(theta.toDouble()).toFloat() * radius,
            sin(phi.toDouble()).toFloat() * ringRadius
        )
        return vertex(position, color, Vector2f(u, v), safeNormal(position, Vector3f(0f, 1f, 0f)))
    }

    private fun sideVector(from: Vector3f, to: Vector3f, up: Vector3f): Vector3f {
        val tangent = Vector3f(to).sub(from).normalizeSafe(Vector3f(1f, 0f, 0f))
        val side = Vector3f(tangent).cross(up)
        if (side.lengthSquared() > EPSILON) {
            return side.normalize()
        }
        val fallback = if (abs(tangent.y) < 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
        return Vector3f(tangent).cross(fallback).normalizeSafe(Vector3f(0f, 0f, 1f))
    }

    private fun vertex(position: Vector3f, color: Vector4f, uv: Vector2f, normal: Vector3f): RenderEntityModelVertex {
        return RenderEntityModelVertex(
            position = Vector3f(position),
            color = Vector4f(color),
            uv = Vector2f(uv),
            normal = safeNormal(normal, defaultNormal)
        )
    }

    private fun cloneVertex(vertex: RenderEntityModelVertex): RenderEntityModelVertex {
        return RenderEntityModelVertex(
            position = Vector3f(vertex.position),
            color = Vector4f(vertex.color),
            uv = Vector2f(vertex.uv),
            normal = Vector3f(vertex.normal)
        )
    }

    private fun faceNormal(first: Vector3f, second: Vector3f, third: Vector3f): Vector3f {
        val edgeA = Vector3f(second).sub(first)
        val edgeB = Vector3f(third).sub(first)
        return edgeA.cross(edgeB).normalizeSafe(defaultNormal)
    }

    private fun safeNormal(normal: Vector3f, fallback: Vector3f): Vector3f {
        return Vector3f(normal).normalizeSafe(fallback)
    }

    private fun rotatePosition(position: Vector3f, axis: Vector3f, radians: Double, origin: Vector3f): Vector3f {
        val relative = Vector3f(position).sub(origin)
        return rotateVector(relative, axis, radians).add(origin)
    }

    private fun rotateVector(vector: Vector3f, axis: Vector3f, radians: Double): Vector3f {
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val dot = vector.dot(axis)
        val cross = Vector3f(
            axis.y * vector.z - axis.z * vector.y,
            axis.z * vector.x - axis.x * vector.z,
            axis.x * vector.y - axis.y * vector.x
        )
        return Vector3f(
            vector.x * c + cross.x * s + axis.x * dot * (1f - c),
            vector.y * c + cross.y * s + axis.y * dot * (1f - c),
            vector.z * c + cross.z * s + axis.z * dot * (1f - c)
        )
    }

    private fun Vector3f.normalizeSafe(fallback: Vector3f): Vector3f {
        if (lengthSquared() <= EPSILON) {
            return set(fallback)
        }
        return normalize()
    }

    private fun Number.f(): Float = toFloat()
}

private const val EPSILON = 1.0E-6f
private val TWO_PI = (PI * 2.0).toFloat()
