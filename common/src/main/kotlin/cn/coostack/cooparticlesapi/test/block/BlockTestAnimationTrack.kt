package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.cparticle.CParticleBezierMath
import net.minecraft.world.phys.Vec3

/**
 * 按 tick 采样模拟玩家的三维动态值。
 *
 * 示例：位置轨道可用 `track.sample(10.0)` 得到第 10 tick 的相对偏移。
 * 禁止跨线程修改 [keyframes]；控制器和编辑界面各自持有自己的副本。
 *
 * @property durationTicks 从起点到终点的一程时长，最小为 2 tick
 * @property playbackMode 到达轨道末尾后的播放方式
 * @property keyframes 按 tick 排序的关键帧，至少包含一个不可删除帧
 */
class BlockTestAnimationTrack(
    var durationTicks: Int,
    var playbackMode: BlockTestPlaybackMode,
    val keyframes: MutableList<BlockTestAnimationKeyframe>,
) {
    init {
        normalize(Vec3.ZERO)
    }

    /**
     * 返回与当前轨道互不共享关键帧的副本。
     *
     * 示例：编辑界面可修改 `track.copy()` 而不影响方块实体当前配置。
     * 禁止假定 [Vec3] 需要深拷贝；它本身不可变。
     *
     * @return 独立的可编辑轨道
     */
    fun copy(): BlockTestAnimationTrack {
        return BlockTestAnimationTrack(
            durationTicks,
            playbackMode,
            keyframes.mapTo(ArrayList()) { it.copy() }
        )
    }

    /**
     * 修正时长、手柄和关键帧顺序，损坏数据会回退到一个锁定帧。
     *
     * 示例：从 NBT 读取后调用 `normalize(Vec3.ZERO)` 可清理越界 tick。
     * 禁止在运行时每 tick 调用；该操作会排序并可能删除重复帧。
     *
     * @param fallbackValue 没有有效关键帧时使用的值
     */
    fun normalize(fallbackValue: Vec3) {
        durationTicks = durationTicks.coerceIn(MIN_DURATION_TICKS, MAX_DURATION_TICKS)
        if (keyframes.isEmpty()) {
            keyframes += BlockTestAnimationKeyframe(0, fallbackValue, locked = true)
        }
        keyframes.forEach { keyframe ->
            keyframe.tick = keyframe.tick.coerceIn(0, durationTicks)
            if (!keyframe.value.x.isFinite() || !keyframe.value.y.isFinite() || !keyframe.value.z.isFinite()) {
                keyframe.value = fallbackValue
            }
            keyframe.outgoingTime = finiteUnit(keyframe.outgoingTime, 1.0 / 3.0)
            keyframe.outgoingProgress = finiteUnit(keyframe.outgoingProgress, 1.0 / 3.0)
            keyframe.incomingTime = finiteUnit(keyframe.incomingTime, 2.0 / 3.0)
            keyframe.incomingProgress = finiteUnit(keyframe.incomingProgress, 2.0 / 3.0)
        }
        keyframes.sortBy { it.tick }
        val unique = keyframes
            .groupBy { it.tick }
            .values
            .map { sameTick -> sameTick.firstOrNull { it.locked } ?: sameTick.first() }
        keyframes.clear()
        keyframes.addAll(unique.take(MAX_KEYFRAMES))
        if (keyframes.none { it.locked }) {
            val first = keyframes.first()
            keyframes[0] = first.copy(locked = true)
        }
    }

    /**
     * 返回指定关键帧在不越过相邻帧时允许使用的 tick 范围。
     *
     * 示例：相邻帧位于 2 和 10 tick 时，中间帧只能移动到 `3..9`。
     * 禁止传入不存在的索引；编辑器应先确认帧仍在当前轨道中。
     *
     * @param frameIndex 当前关键帧索引
     * @return 包含边界的有效 tick 范围
     * @throws IllegalArgumentException 当 [frameIndex] 不在关键帧列表中时抛出
     */
    internal fun keyframeTickRange(frameIndex: Int): IntRange {
        require(frameIndex in keyframes.indices) { "frameIndex is outside the keyframe list" }
        val minimum = keyframes.getOrNull(frameIndex - 1)?.tick?.plus(1) ?: 0
        val maximum = keyframes.getOrNull(frameIndex + 1)?.tick?.minus(1) ?: durationTicks
        return minimum..maximum
    }

    /**
     * 按播放模式把持续时间映射到轨道时间后采样。
     *
     * 示例：[BlockTestPlaybackMode.ONCE] 下超过总时长的输入会保持终点。
     * 禁止传入 `NaN`；非有限值会按起点处理。
     *
     * @param elapsedTicks 从当前测试项开始后经过的 tick，可包含客户端局部 tick
     * @return 插值后的三维值
     */
    fun sample(elapsedTicks: Double): Vec3 {
        val elapsed = elapsedTicks.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val duration = durationTicks.toDouble()
        val timelineTick = when (playbackMode) {
            BlockTestPlaybackMode.ONCE -> elapsed.coerceAtMost(duration)
            BlockTestPlaybackMode.LOOP -> elapsed % (duration + 1.0)
            BlockTestPlaybackMode.PINGPONG -> {
                val cycle = elapsed % (duration * 2.0)
                if (cycle <= duration) cycle else duration * 2.0 - cycle
            }
        }
        return sampleTimeline(timelineTick)
    }

    /**
     * 忽略播放模式，直接采样时间线上的一个 tick。
     *
     * 示例：曲线编辑器添加关键帧时可用该方法取得插值后的默认值。
     * 禁止用负数或超出总时长的值制造外推；输入会被夹到有效范围。
     *
     * @param timelineTick 时间线 tick
     * @return 对应的三维值
     */
    fun sampleTimeline(timelineTick: Double): Vec3 {
        val frames = keyframes
        if (frames.size == 1) {
            return frames.first().value
        }
        val tick = timelineTick.coerceIn(0.0, durationTicks.toDouble())
        if (tick <= frames.first().tick) {
            return frames.first().value
        }
        if (tick >= frames.last().tick) {
            return frames.last().value
        }
        val rightIndex = frames.indexOfFirst { tick <= it.tick }
        if (rightIndex <= 0) {
            return frames.first().value
        }
        val left = frames[rightIndex - 1]
        val right = frames[rightIndex]
        val span = (right.tick - left.tick).coerceAtLeast(1).toDouble()
        val rawProgress = ((tick - left.tick) / span).coerceIn(0.0, 1.0)
        val progress = segmentProgress(rightIndex - 1, rawProgress)
        return left.value.lerp(right.value, progress)
    }

    /**
     * 采样指定相邻帧之间的归一化进度曲线。
     *
     * 示例：曲线编辑器可用 `segmentProgress(0, 0.5)` 绘制第一段中点。
     * 禁止传入非相邻索引；调用方应先检查段索引范围。
     *
     * @param leftIndex 分段左端关键帧索引
     * @param rawProgress 分段内归一化时间
     * @return 分段内归一化进度
     */
    fun segmentProgress(leftIndex: Int, rawProgress: Double): Double {
        val left = keyframes.getOrNull(leftIndex) ?: return rawProgress.coerceIn(0.0, 1.0)
        val right = keyframes.getOrNull(leftIndex + 1) ?: return rawProgress.coerceIn(0.0, 1.0)
        return when (left.curveToNext) {
            BlockTestCurveType.LINEAR -> rawProgress.coerceIn(0.0, 1.0)
            BlockTestCurveType.BEZIER -> bezierProgress(left, right, rawProgress.coerceIn(0.0, 1.0))
        }
    }

    /**
     * 保存轨道共同使用的边界值和工厂方法。
     *
     * 示例：新位置轨道使用 `BlockTestAnimationTrack.create(Vec3.ZERO)`。
     * 禁止把默认轨道实例放在这里共享，编辑器需要独立可变数据。
     */
    companion object {
        /** 最短的一程播放时长，单位为 tick。 */
        const val MIN_DURATION_TICKS: Int = 2

        /** 最长的一程播放时长，限制异常客户端制造超大时间轴。 */
        const val MAX_DURATION_TICKS: Int = 72_000

        /** 单条轨道允许保存的最大关键帧数。 */
        const val MAX_KEYFRAMES: Int = 128

        /**
         * 创建只含一个锁定关键帧的轨道。
         *
         * 示例：forward 轨道传入 `Vec3(0.0, 0.0, 1.0)`。
         * 禁止传入可变容器；这里只接受不可变 [Vec3]。
         *
         * @param initialValue 起始值
         * @return 默认 40 tick 的单关键帧轨道
         */
        fun create(initialValue: Vec3): BlockTestAnimationTrack {
            return BlockTestAnimationTrack(
                durationTicks = 40,
                playbackMode = BlockTestPlaybackMode.ONCE,
                keyframes = arrayListOf(BlockTestAnimationKeyframe(0, initialValue, locked = true))
            )
        }

        /**
         * 计算单调三次贝塞尔曲线在给定时间上的进度。
         *
         * 示例：默认手柄 `(1/3,1/3)` 与 `(2/3,2/3)` 会得到近似线性进度。
         * 禁止把空间坐标传给此方法；四个手柄值都在归一化曲线坐标中。
         *
         * @param left 分段起点关键帧
         * @param right 分段终点关键帧
         * @param timeProgress 分段内归一化时间
         * @return 分段内归一化移动进度
         */
        private fun bezierProgress(
            left: BlockTestAnimationKeyframe,
            right: BlockTestAnimationKeyframe,
            timeProgress: Double,
        ): Double {
            val p1x = left.outgoingTime.coerceIn(0.0, 1.0)
            val p2x = right.incomingTime.coerceIn(p1x, 1.0)
            val curveParameter = CParticleBezierMath.parameterAt(
                timeProgress.toFloat(),
                0f,
                (p1x * 100.0).toFloat(),
                1f,
                ((p2x - 1.0) * 100.0).toFloat()
            )
            return CParticleBezierMath.cubic(
                curveParameter,
                0f,
                left.outgoingProgress.coerceIn(0.0, 1.0).toFloat(),
                right.incomingProgress.coerceIn(0.0, 1.0).toFloat(),
                1f
            ).toDouble().coerceIn(0.0, 1.0)
        }

        /**
         * 把有限数限制到单位区间，非有限数使用默认值。
         *
         * 示例：来自异常 SNBT 的 `NaN` 会回退为默认手柄坐标。
         * 禁止把该方法用于未归一化的空间坐标。
         *
         * @param value 待校验数值
         * @param fallback 非有限数的回退值
         * @return `0..1` 内的有限数
         */
        private fun finiteUnit(value: Double, fallback: Double): Double {
            return if (value.isFinite()) value.coerceIn(0.0, 1.0) else fallback
        }
    }
}
