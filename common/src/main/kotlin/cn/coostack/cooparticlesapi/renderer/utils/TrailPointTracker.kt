package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * 拖尾采样点。
 *
 * @property localPosition 相对当前渲染原点的局部坐标，
 * 与 [cn.coostack.cooparticlesapi.renderer.client.RenderUtil.buildModelMatrix] 的原点一致，
 * 可直接作为模型顶点使用
 * @property progress 归一化寿命进度，0 = 头部（最新点），1 = 即将过期的尾部
 */
data class TrailSamplePoint(
    val localPosition: Vector3f,
    val progress: Float
)

/**
 * 一次拖尾采样结果。
 *
 * @property origin 本次采样使用的世界原点（通常是实体的插值渲染位置）
 * @property points 头 -> 尾 排列的局部采样点
 */
data class TrailSample(
    val origin: Vec3,
    val points: List<TrailSamplePoint>
)

/**
 * RenderEntity 拖尾位置历史追踪器。
 *
 * 记录目标最近一段时间经过的世界坐标，并在渲染时转换为
 * “以插值渲染原点为原点”的局部点列，供 [TrailModelBuilder] 每帧生成动态拖尾模型。
 *
 * renderer 每帧重建模型，模型顶点由 DynamicVertexBuffer 重传，
 * 用它构建的拖尾天然就是“点在不断变化的动态模型”。
 *
 * 典型用法（在客户端 renderer 中持有一个实例）：
 * ```kotlin
 * private val tracker = TrailPointTracker(maxPoints = 96, maxAgeTicks = 14f)
 *
 * override fun buildModel(entity: FooEntity, tickDelta: Float): RenderEntityModel {
 *     // 记录当前拖尾发射点（可以是实体位置，也可以是任意动画位置）
 *     tracker.record(emitterWorldPos, entity.age + tickDelta)
 *     val sample = tracker.sample(entity, tickDelta)
 *     val model = RenderEntityModelBuilder()
 *     val layer = model.layer("trail")
 *     TrailModelBuilder.buildRibbon(model, layer, sample, TrailRibbonStyle(...))
 *     return model.build()
 * }
 * ```
 *
 * 该类只在客户端使用，不参与网络同步；是否启用拖尾完全由调用方决定。
 */
class TrailPointTracker(
    /** 最多保留的历史点数量，超出后丢弃最旧的点。 */
    var maxPoints: Int = 64,
    /** 历史点的最大寿命（tick），超龄的点会被丢弃。 */
    var maxAgeTicks: Float = 20.0f,
    /** 新点与最近一个点的最小间距（格），低于该间距的新点会被忽略，避免顶点堆积。 */
    var minPointDistance: Double = 0.02
) {
    private class TrackedPoint(val position: Vec3, val ageTicks: Float)

    private val points = ArrayDeque<TrackedPoint>()

    /**
     * 记录一个世界坐标历史点。
     *
     * 既可以在 `clientTick` 里以 `age.toFloat()` 每 tick 调用，
     * 也可以在 `buildModel` 里以 `entity.age + tickDelta` 每帧调用。
     * 时间戳不递增或与上个点距离过近的调用会被自动忽略，
     * 因此同一帧多次构建模型（例如 world pass 与 glow mask 各一次）是安全的。
     */
    fun record(position: Vec3, ageTicks: Float) {
        val newest = points.lastOrNull()
        if (newest != null) {
            if (ageTicks <= newest.ageTicks) return
            if (newest.position.distanceTo(position) < minPointDistance) return
        }
        points.addLast(TrackedPoint(position, ageTicks))
        trim(ageTicks)
    }

    /** 清空全部历史点。 */
    fun clear() {
        points.clear()
    }

    /** 当前保留的历史点数量。 */
    fun size(): Int = points.size

    /**
     * 以实体当前插值渲染位置为原点采样拖尾点列。
     *
     * 结果从头部（progress = 0）到尾部（最旧点）排列。
     * 局部坐标系与 [cn.coostack.cooparticlesapi.renderer.client.RenderUtil.buildModelMatrix]
     * 生成的模型矩阵一致，可直接写入模型顶点。
     *
     * @param includeHead 为 true 时在头部插入局部原点 (0,0,0)，
     * 让拖尾始终连接到实体当前插值位置；如果调用方按帧 record 了发射点，可设为 false
     */
    fun sample(entity: RenderEntity, tickDelta: Float, includeHead: Boolean = true): TrailSample {
        val origin = entity.lastRenderPos.lerp(entity.pos, tickDelta.toDouble())
        return sample(origin, entity.age + tickDelta, includeHead)
    }

    /**
     * 使用自定义世界原点与当前时间采样。
     */
    fun sample(origin: Vec3, currentAgeTicks: Float, includeHead: Boolean = true): TrailSample {
        trim(currentAgeTicks)
        val result = ArrayList<TrailSamplePoint>(points.size + 1)
        if (includeHead) {
            result += TrailSamplePoint(Vector3f(0f, 0f, 0f), 0f)
        }
        val maxAge = maxAgeTicks.coerceAtLeast(1.0E-3f)
        // points 按时间递增存储，倒序遍历得到 新 -> 旧
        for (index in points.indices.reversed()) {
            val point = points[index]
            val age = currentAgeTicks - point.ageTicks
            if (age < 0f) continue
            val progress = (age / maxAge).coerceIn(0f, 1f)
            result += TrailSamplePoint(
                Vector3f(
                    (point.position.x - origin.x).toFloat(),
                    (point.position.y - origin.y).toFloat(),
                    (point.position.z - origin.z).toFloat()
                ),
                progress
            )
        }
        return TrailSample(origin, result)
    }

    private fun trim(currentAgeTicks: Float) {
        val limit = maxPoints.coerceAtLeast(1)
        while (points.size > limit) {
            points.removeFirst()
        }
        while (points.isNotEmpty() && currentAgeTicks - points.first().ageTicks > maxAgeTicks) {
            points.removeFirst()
        }
    }
}
