package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPipe
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import net.minecraft.client.Minecraft
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.pow

/**
 * 拖尾条带的外观参数。
 *
 * 颜色与宽度都按采样点的 `progress`（0 = 头部，1 = 尾部）插值，
 * `widthEase` / `fadeEase` 是插值指数，1 表示线性。
 */
data class TrailRibbonStyle(
    /** 头部宽度（格）。 */
    val headWidth: Float = 0.24f,
    /** 尾部宽度（格）。 */
    val tailWidth: Float = 0.02f,
    /** 头部颜色（RGBA）。 */
    val headColor: Vector4f = Vector4f(1f, 1f, 1f, 0.9f),
    /** 尾部颜色（RGBA），通常 alpha 为 0 让尾部淡出。 */
    val tailColor: Vector4f = Vector4f(1f, 1f, 1f, 0f),
    /** 宽度衰减指数；>1 时前段更饱满、尾段收缩更快。 */
    val widthEase: Float = 1.0f,
    /** 颜色 / 透明度衰减指数；>1 时前段更亮、尾段更快淡出。 */
    val fadeEase: Float = 1.0f
)

/**
 * 把 [TrailPointTracker] 的采样结果转换为可渲染的动态拖尾模型。
 *
 * 生成的是一条始终朝向相机的三角形条带（billboard ribbon）：
 * 每个采样点根据行进方向与相机方向计算横向扩展轴，
 * 宽度与颜色随 progress 衰减，UV 的 u 分量沿拖尾方向为 progress、v 分量横跨条带 0..1，
 * 因此同一个 pipe 也可以配自定义 shader 做流动贴图。
 *
 * 顶点每帧由调用方重建（buildModel 每帧调用 + DynamicVertexBuffer 每帧上传），
 * 天然支持“点在不断变化”的拖尾模型；是否启用完全由调用方决定。
 */
object TrailModelBuilder {

    /**
     * 将拖尾条带写入模型构建器（TRIANGLES 图元）。
     *
     * 相机位置自动取当前客户端主相机，并转换到 [TrailSample.origin] 局部空间。
     */
    @JvmStatic
    fun buildRibbon(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        sample: TrailSample,
        style: TrailRibbonStyle = TrailRibbonStyle()
    ) {
        buildRibbon(model, pipe, sample.points, cameraLocalPos(sample), style)
    }

    /**
     * 以显式局部相机位置写入拖尾条带。
     */
    @JvmStatic
    fun buildRibbon(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        points: List<TrailSamplePoint>,
        cameraLocalPos: Vector3f,
        style: TrailRibbonStyle = TrailRibbonStyle()
    ) {
        val strip = buildRibbonVertices(points, cameraLocalPos, style)
        var index = 0
        while (index + 3 < strip.size) {
            // strip 排列为 [L0, R0, L1, R1, ...]，按周向 L0 -> R0 -> R1 -> L1 组四边形
            model.addQuad(pipe, strip[index], strip[index + 1], strip[index + 3], strip[index + 2])
            index += 2
        }
    }

    /**
     * 生成条带顶点，成对排列：每个采样点扩展出左右两个顶点 `[L0, R0, L1, R1, ...]`。
     *
     * 供需要自行组织 primitive（例如 RenderType 路线或自定义批处理）的调用方使用；
     * 点数不足或全部退化时返回空列表。
     */
    @JvmStatic
    fun buildRibbonVertices(
        points: List<TrailSamplePoint>,
        cameraLocalPos: Vector3f,
        style: TrailRibbonStyle = TrailRibbonStyle()
    ): List<RenderEntityModelVertex> {
        val filtered = dropDegeneratePoints(points)
        if (filtered.size < 2) {
            return emptyList()
        }
        val result = ArrayList<RenderEntityModelVertex>(filtered.size * 2)
        for (index in filtered.indices) {
            val current = filtered[index]
            val previous = if (index > 0) filtered[index - 1].localPosition else null
            val next = if (index < filtered.size - 1) filtered[index + 1].localPosition else null
            val direction = segmentDirection(previous, current.localPosition, next)
            val side = sideAxis(direction, current.localPosition, cameraLocalPos)
            val widthProgress = ease(current.progress, style.widthEase)
            val fadeProgress = ease(current.progress, style.fadeEase)
            val halfWidth = (lerp(style.headWidth, style.tailWidth, widthProgress) * 0.5f).coerceAtLeast(0f)
            val color = Vector4f(style.headColor).lerp(style.tailColor, fadeProgress)
            val offset = Vector3f(side).mul(halfWidth)
            val u = current.progress
            result += vertex(Vector3f(current.localPosition).add(offset), color, Vector2f(u, 0f), direction)
            result += vertex(Vector3f(current.localPosition).sub(offset), color, Vector2f(u, 1f), direction)
        }
        return result
    }

    /**
     * 在指定局部位置追加一片始终面向相机的方形面片（TRIANGLES）。
     *
     * 常用于拖尾头部的亮核或独立光点。
     */
    @JvmStatic
    fun buildCameraFacingQuad(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        centerLocal: Vector3f,
        size: Float,
        color: Vector4f,
        cameraLocalPos: Vector3f
    ) {
        val forward = Vector3f(cameraLocalPos).sub(centerLocal)
        if (forward.lengthSquared() <= EPSILON) {
            forward.set(0f, 0f, 1f)
        } else {
            forward.normalize()
        }
        val right = Vector3f(forward).cross(UP)
        if (right.lengthSquared() <= EPSILON) {
            right.set(1f, 0f, 0f)
        } else {
            right.normalize()
        }
        val up = Vector3f(right).cross(forward).normalize()
        val half = size * 0.5f
        right.mul(half)
        up.mul(half)
        model.addQuad(
            pipe,
            vertex(Vector3f(centerLocal).sub(right).sub(up), color, Vector2f(0f, 0f), forward),
            vertex(Vector3f(centerLocal).add(right).sub(up), color, Vector2f(1f, 0f), forward),
            vertex(Vector3f(centerLocal).add(right).add(up), color, Vector2f(1f, 1f), forward),
            vertex(Vector3f(centerLocal).sub(right).add(up), color, Vector2f(0f, 1f), forward)
        )
    }

    /**
     * 计算当前客户端主相机在 [TrailSample.origin] 局部空间下的位置。
     */
    @JvmStatic
    fun cameraLocalPos(sample: TrailSample): Vector3f {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        return Vector3f(
            (camera.x - sample.origin.x).toFloat(),
            (camera.y - sample.origin.y).toFloat(),
            (camera.z - sample.origin.z).toFloat()
        )
    }

    private fun dropDegeneratePoints(points: List<TrailSamplePoint>): List<TrailSamplePoint> {
        if (points.isEmpty()) {
            return points
        }
        val result = ArrayList<TrailSamplePoint>(points.size)
        result += points.first()
        for (index in 1 until points.size) {
            val candidate = points[index]
            if (candidate.localPosition.distanceSquared(result.last().localPosition) > EPSILON) {
                result += candidate
            }
        }
        return result
    }

    private fun segmentDirection(previous: Vector3f?, current: Vector3f, next: Vector3f?): Vector3f {
        val direction = Vector3f()
        if (next != null) {
            direction.add(Vector3f(next).sub(current))
        }
        if (previous != null) {
            direction.add(Vector3f(current).sub(previous))
        }
        if (direction.lengthSquared() <= EPSILON) {
            direction.set(1f, 0f, 0f)
        } else {
            direction.normalize()
        }
        return direction
    }

    private fun sideAxis(direction: Vector3f, position: Vector3f, cameraLocalPos: Vector3f): Vector3f {
        val toCamera = Vector3f(cameraLocalPos).sub(position)
        val side = Vector3f(direction).cross(toCamera)
        if (side.lengthSquared() > EPSILON) {
            return side.normalize()
        }
        val fallback = Vector3f(direction).cross(UP)
        if (fallback.lengthSquared() > EPSILON) {
            return fallback.normalize()
        }
        return Vector3f(direction).cross(1f, 0f, 0f).normalize()
    }

    private fun ease(progress: Float, exponent: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        if (exponent == 1.0f) {
            return clamped
        }
        return clamped.pow(exponent.coerceAtLeast(1.0E-3f))
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float {
        return from + (to - from) * progress
    }

    private fun vertex(
        position: Vector3f,
        color: Vector4f,
        uv: Vector2f,
        normal: Vector3f
    ): RenderEntityModelVertex {
        return RenderEntityModelVertex(
            position = position,
            color = Vector4f(color),
            uv = uv,
            normal = Vector3f(normal)
        )
    }

    private val UP = Vector3f(0f, 1f, 0f)
    private const val EPSILON = 1.0E-6f
}
