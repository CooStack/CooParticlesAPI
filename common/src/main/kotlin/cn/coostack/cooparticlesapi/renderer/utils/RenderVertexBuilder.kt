package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelLayer
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
 * 用于构建可复用局部网格的 RenderEntity 顶点构建器。
 *
 * API 采用与 PointsBuilder 相近的链式风格，但输出的是可直接渲染的顶点，而不是粒子点。
 * 所有形状都以局部原点为中心生成，并可直接追加到 [RenderEntityModelBuilder]。
 */
class RenderVertexBuilder {
    companion object {
        @JvmStatic
        /**
         * 根据输入和 `RenderVertexBuilder` 当前配置创建 `create` 结果；返回对象保留本次配置的语义。
         *
         * 示例：`create()`。
         *
         * @return 当前构建器或由其配置生成的结果
         */
        fun create(): RenderVertexBuilder = RenderVertexBuilder()

        @JvmStatic
        /**
         * 根据输入和 `RenderVertexBuilder` 当前配置创建 `of` 结果；返回对象保留本次配置的语义。
         *
         * 示例：`of(vertices = vertices, primitiveMode = primitiveMode)`。
         *
         * @param vertices 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
         *
         * @param primitiveMode 顶点的图元组装方式，决定每组顶点形成线、三角形还是四边形
         *
         * @return 当前构建器或由其配置生成的结果
         */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `mode`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`mode(primitiveMode = primitiveMode)`。
     *
     * @param primitiveMode 顶点的图元组装方式，决定每组顶点形成线、三角形还是四边形
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun mode(primitiveMode: RenderEntityModelPrimitiveMode): RenderVertexBuilder {
        defaultPrimitiveMode = primitiveMode
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `color`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`color(color = color)`。
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun color(color: Vector4f): RenderVertexBuilder {
        defaultColor = Vector4f(color)
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `color`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`color(red = red, green = green, blue = blue, alpha = alpha)`。
     *
     * @param red 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param green 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param blue 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param alpha 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun color(red: Number, green: Number, blue: Number, alpha: Number = 1f): RenderVertexBuilder {
        defaultColor = Vector4f(red.f(), green.f(), blue.f(), alpha.f())
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `uv`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`uv(uv = uv)`。
     *
     * @param uv 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun uv(uv: Vector2f): RenderVertexBuilder {
        defaultUv = Vector2f(uv)
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `normal`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`normal(normal = normal)`。
     *
     * @param normal 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun normal(normal: Vector3f): RenderVertexBuilder {
        defaultNormal = safeNormal(normal, Vector3f(0f, 1f, 0f))
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `addVertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addVertex(position = position, color = color, uv = uv, normal = normal, primitiveMode = primitiveMode)`。
     *
     * @param position 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param uv 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param normal 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param primitiveMode 顶点的图元组装方式，决定每组顶点形成线、三角形还是四边形
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addVertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addVertex(x = x, y = y, z = z, color = color, uv = uv, normal = normal, primitiveMode = primitiveMode)`。
     *
     * @param x 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param uv 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param normal 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param primitiveMode 顶点的图元组装方式，决定每组顶点形成线、三角形还是四边形
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addVertices`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addVertices(vertices = vertices, primitiveMode = primitiveMode)`。
     *
     * @param vertices 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @param primitiveMode 顶点的图元组装方式，决定每组顶点形成线、三角形还是四边形
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addVertices(
        vertices: Collection<RenderEntityModelVertex>,
        primitiveMode: RenderEntityModelPrimitiveMode = defaultPrimitiveMode
    ): RenderVertexBuilder {
        val target = batch(primitiveMode).vertices
        vertices.forEach { target += cloneVertex(it) }
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `addLine`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addLine(from = from, to = to, color = color)`。
     *
     * @param from 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param to 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addLine(
        from: Vector3f,
        to: Vector3f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder {
        addVertex(from, color, primitiveMode = RenderEntityModelPrimitiveMode.LINES)
        addVertex(to, color, primitiveMode = RenderEntityModelPrimitiveMode.LINES)
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `addTriangle`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addTriangle(first = first, second = second, third = third)`。
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addTriangle`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addTriangle(first = first, second = second, third = third, color = color)`。
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addQuad`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addQuad(first = first, second = second, third = third, fourth = fourth)`。
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param fourth 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addQuad`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addQuad(first = first, second = second, third = third, fourth = fourth, color = color, uvMin = uvMin, uvMax = uvMax, normal = normal)`。
     *
     * @param first 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param second 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param third 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param fourth 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param uvMin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param uvMax 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param normal 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addQuad`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addQuad(width = width, height = height, z = z, color = color)`。
     *
     * @param width 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param height 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addQuad(
        width: Number,
        height: Number,
        z: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addPlane(width, height, z, color)

    /**
     * 在 `RenderVertexBuilder` 中配置 `addPlane`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addPlane(width = width, height = height, z = z, color = color)`。
     *
     * @param width 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param height 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addDisc`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addDisc(radius = radius, segments = segments, y = y, color = color)`。
     *
     * @param radius 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param segments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addRing`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addRing(innerRadius = innerRadius, outerRadius = outerRadius, segments = segments, y = y, color = color)`。
     *
     * @param innerRadius 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param outerRadius 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param segments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addAnnulus`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addAnnulus(innerRadius = innerRadius, outerRadius = outerRadius, segments = segments, y = y, color = color)`。
     *
     * @param innerRadius 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param outerRadius 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param segments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addAnnulus(
        innerRadius: Number,
        outerRadius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addRing(innerRadius, outerRadius, segments, y, color)

    /**
     * 在 `RenderVertexBuilder` 中配置 `addCircleLine`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addCircleLine(radius = radius, segments = segments, y = y, color = color)`。
     *
     * @param radius 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param segments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addWireCircle`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addWireCircle(radius = radius, segments = segments, y = y, color = color)`。
     *
     * @param radius 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param segments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addWireCircle(
        radius: Number,
        segments: Int = 48,
        y: Number = 0f,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addCircleLine(radius, segments, y, color)

    /**
     * 在 `RenderVertexBuilder` 中配置 `addSphere`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addSphere(radius = radius, latSegments = latSegments, lonSegments = lonSegments, color = color)`。
     *
     * @param radius 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param latSegments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param lonSegments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `addBall`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addBall(radius = radius, latSegments = latSegments, lonSegments = lonSegments, color = color)`。
     *
     * @param radius 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param latSegments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param lonSegments 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addBall(
        radius: Number,
        latSegments: Int = 12,
        lonSegments: Int = 32,
        color: Vector4f = defaultColor
    ): RenderVertexBuilder = addSphere(radius, latSegments, lonSegments, color)

    /**
     * 在 `RenderVertexBuilder` 中配置 `addRibbon`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addRibbon(points = points, width = width, up = up, color = color, closed = closed)`。
     *
     * @param points 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param width 目标尺寸的像素数，必须与相关纹理或 framebuffer 尺寸一致
     *
     * @param up 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param closed 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `translate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`translate(offset = offset)`。
     *
     * @param offset 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun translate(offset: Vector3f): RenderVertexBuilder {
        verticesOnEach { vertex -> vertex.position.add(offset) }
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `translate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`translate(x = x, y = y, z = z)`。
     *
     * @param x 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun translate(x: Number, y: Number, z: Number): RenderVertexBuilder {
        return translate(Vector3f(x.f(), y.f(), z.f()))
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `scale`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`scale(factor = factor)`。
     *
     * @param factor 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun scale(factor: Number): RenderVertexBuilder {
        val scale = factor.f()
        verticesOnEach { vertex -> vertex.position.mul(scale) }
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `scale`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`scale(x = x, y = y, z = z)`。
     *
     * @param x 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `rotateX`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`rotateX(radians = radians, origin = origin)`。
     *
     * @param radians 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param origin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun rotateX(radians: Number, origin: Vector3f = Vector3f(0f, 0f, 0f)): RenderVertexBuilder {
        return rotate(Vector3f(1f, 0f, 0f), radians, origin)
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `rotateY`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`rotateY(radians = radians, origin = origin)`。
     *
     * @param radians 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param origin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun rotateY(radians: Number, origin: Vector3f = Vector3f(0f, 0f, 0f)): RenderVertexBuilder {
        return rotate(Vector3f(0f, 1f, 0f), radians, origin)
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `rotateZ`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`rotateZ(radians = radians, origin = origin)`。
     *
     * @param radians 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param origin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun rotateZ(radians: Number, origin: Vector3f = Vector3f(0f, 0f, 0f)): RenderVertexBuilder {
        return rotate(Vector3f(0f, 0f, 1f), radians, origin)
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `rotate`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`rotate(axis = axis, radians = radians, origin = origin)`。
     *
     * @param axis 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param radians 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param origin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `twist`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`twist(axis = axis, radiansPerUnit = radiansPerUnit, origin = origin)`。
     *
     * @param axis 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param radiansPerUnit 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param origin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `twist`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`twist(axis = axis, totalRadians = totalRadians, minProjection = minProjection, maxProjection = maxProjection, origin = origin)`。
     *
     * @param axis 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param totalRadians 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param minProjection 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param maxProjection 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param origin 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `verticesOnEach`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`verticesOnEach(handler = handler)`。
     *
     * @param handler 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun verticesOnEach(handler: (RenderEntityModelVertex) -> Unit): RenderVertexBuilder {
        batches.forEach { batch -> batch.vertices.forEach(handler) }
        return this
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `clear`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`clear()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun clear(): RenderVertexBuilder {
        batches.clear()
        return this
    }

    /**
     * 根据输入和 `RenderVertexBuilder` 当前配置创建 `create` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`create()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun create(): List<RenderEntityModelVertex> {
        return batches.flatMap { batch -> batch.vertices.map { cloneVertex(it) } }
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `createWithoutClone`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`createWithoutClone()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun createWithoutClone(): List<RenderEntityModelVertex> {
        return batches.flatMap { it.vertices }
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `createVertexData`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`createVertexData()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun createVertexData(): List<VertexData> {
        return create().map { VertexData(it.position, it.color, it.uv) }
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `createPrimitives`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`createPrimitives(layer = layer)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun createPrimitives(layer: RenderEntityModelLayer): List<RenderEntityModelPrimitive> {
        return batches
            .filter { it.vertices.isNotEmpty() }
            .map { batch ->
                RenderEntityModelPrimitive(
                    layer = layer,
                    vertices = batch.vertices.map { cloneVertex(it) },
                    primitiveMode = batch.primitiveMode
                )
            }
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `addTo`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`addTo(model = model, layer = layer)`。
     *
     * @param model 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun addTo(model: RenderEntityModelBuilder, layer: RenderEntityModelLayer): RenderVertexBuilder {
        batches.forEach { batch ->
            batch.vertices.forEach { vertex ->
                model.addVertex(
                    layer = layer,
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

    /**
     * 在 `RenderVertexBuilder` 中配置 `buildModel`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`buildModel(layer = layer)`。
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun buildModel(layer: RenderEntityModelLayer): RenderEntityModel {
        return RenderEntityModel(listOf(layer), createPrimitives(layer))
    }

    /**
     * 在 `RenderVertexBuilder` 中配置 `cloneBuilder`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`cloneBuilder()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
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
