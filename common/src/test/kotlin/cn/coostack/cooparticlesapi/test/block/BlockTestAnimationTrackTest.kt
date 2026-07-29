package cn.coostack.cooparticlesapi.test.block

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证动态轨道的时间边界、曲线采样和序列化。
 *
 * 示例：修改播放映射后运行本测试可检查端点是否仍会被采样。
 * 禁止用这些纯数据测试替代游戏内方块实体同步检查。
 */
class BlockTestAnimationTrackTest {
    /**
     * 首关键帧后移时，之前的时间保持该关键帧值。
     *
     * 示例：首帧位于第 10 tick 时，第 5 tick 仍返回首帧值。
     * 禁止把首帧之前的区间外推到下一帧。
     */
    @Test
    fun keepsFirstValueBeforeDelayedKeyframe() {
        val track = track(
            BlockTestAnimationKeyframe(10, Vec3.ZERO, locked = true),
            BlockTestAnimationKeyframe(20, Vec3(10.0, 0.0, 0.0))
        )

        assertEquals(Vec3.ZERO, track.sampleTimeline(5.0))
        assertEquals(5.0, track.sampleTimeline(15.0).x, 1.0E-6)
        assertEquals(10.0, track.sampleTimeline(25.0).x, 1.0E-6)
    }

    /**
     * 循环播放会先采样终点，再在下一 tick 返回起点。
     *
     * 示例：10 tick 轨道的第 10 tick 是终点，第 11 tick 是起点。
     * 禁止提前一个 tick 跳过终点。
     */
    @Test
    fun loopKeepsEndpointForItsArrivalTick() {
        val track = track(
            BlockTestAnimationKeyframe(0, Vec3.ZERO, locked = true),
            BlockTestAnimationKeyframe(10, Vec3(10.0, 0.0, 0.0))
        ).also {
            it.durationTicks = 10
            it.playbackMode = BlockTestPlaybackMode.LOOP
        }

        assertEquals(10.0, track.sample(10.0).x, 1.0E-6)
        assertEquals(0.0, track.sample(11.0).x, 1.0E-6)
    }

    /**
     * 往返播放在一程结束后反向采样。
     *
     * 示例：10 tick 轨道在第 15 tick 回到中点。
     * 禁止在终点直接跳回起点。
     */
    @Test
    fun pingPongReversesAtEndpoint() {
        val track = track(
            BlockTestAnimationKeyframe(0, Vec3.ZERO, locked = true),
            BlockTestAnimationKeyframe(10, Vec3(10.0, 0.0, 0.0))
        ).also {
            it.durationTicks = 10
            it.playbackMode = BlockTestPlaybackMode.PINGPONG
        }

        assertEquals(10.0, track.sample(10.0).x, 1.0E-6)
        assertEquals(5.0, track.sample(15.0).x, 1.0E-6)
        assertEquals(0.0, track.sample(20.0).x, 1.0E-6)
    }

    /**
     * 贝塞尔手柄会改变时间进度，但不会改变两端位置。
     *
     * 示例：低 Y 手柄让中点采样晚于线性轨道。
     * 禁止让曲线结果越过 `[0, 1]` 的进度范围。
     */
    @Test
    fun bezierHandlesControlSegmentProgress() {
        val first = BlockTestAnimationKeyframe(
            0,
            Vec3.ZERO,
            curveToNext = BlockTestCurveType.BEZIER,
            outgoingProgress = 0.0,
            locked = true
        )
        val second = BlockTestAnimationKeyframe(10, Vec3(10.0, 0.0, 0.0), incomingProgress = 0.0)
        val track = track(first, second)

        assertTrue(track.sampleTimeline(5.0).x < 5.0)
        assertEquals(0.0, track.sampleTimeline(0.0).x, 1.0E-6)
        assertEquals(10.0, track.sampleTimeline(10.0).x, 1.0E-6)
    }

    /**
     * SNBT 往返保留播放模式、锁定帧和控制手柄。
     *
     * 示例：数据包字段解码后仍能得到相同的第 8 tick 采样。
     * 禁止依赖对象引用相等判断序列化正确性。
     */
    @Test
    fun codecRoundTripPreservesTrack() {
        val source = track(
            BlockTestAnimationKeyframe(
                4,
                Vec3(1.0, 2.0, 3.0),
                curveToNext = BlockTestCurveType.BEZIER,
                outgoingTime = 0.2,
                outgoingProgress = 0.7,
                locked = true
            ),
            BlockTestAnimationKeyframe(12, Vec3(4.0, 5.0, 6.0), incomingTime = 0.8, incomingProgress = 0.3)
        ).also {
            it.durationTicks = 30
            it.playbackMode = BlockTestPlaybackMode.PINGPONG
        }

        val restored = BlockTestAnimationTrackCodec.decode(
            BlockTestAnimationTrackCodec.encode(source),
            Vec3.ZERO
        )

        assertEquals(30, restored.durationTicks)
        assertEquals(BlockTestPlaybackMode.PINGPONG, restored.playbackMode)
        assertTrue(restored.keyframes.first().locked)
        assertEquals(source.sampleTimeline(8.0).x, restored.sampleTimeline(8.0).x, 1.0E-6)
    }

    /**
     * 空关键帧列表使用调用方提供的静态值创建锁定帧。
     *
     * 示例：旧数据只有时长时，位置仍保留静态偏移 `(3, 4, 5)`。
     * 禁止把损坏数据静默替换为世界原点。
     */
    @Test
    fun emptyCodecTrackUsesFallbackValue() {
        val fallback = Vec3(3.0, 4.0, 5.0)

        val restored = BlockTestAnimationTrackCodec.decode(
            "{durationTicks:40,keyframes:[]}",
            fallback
        )

        assertEquals(fallback, restored.keyframes.single().value)
        assertTrue(restored.keyframes.single().locked)
    }

    /**
     * 关键帧的可编辑范围必须停在相邻帧之前，首帧也不能越过第二帧。
     *
     * 示例：`2, 8, 15` 三帧的范围依次为 `0..7`、`3..14`、`9..20`。
     * 禁止让设置窗口和时间线拖动使用不同的边界规则。
     */
    @Test
    fun keyframeTickRangeStopsBeforeNeighbors() {
        val track = track(
            BlockTestAnimationKeyframe(2, Vec3.ZERO, locked = true),
            BlockTestAnimationKeyframe(8, Vec3(1.0, 0.0, 0.0)),
            BlockTestAnimationKeyframe(15, Vec3(2.0, 0.0, 0.0))
        )

        assertEquals(0..7, track.keyframeTickRange(0))
        assertEquals(3..14, track.keyframeTickRange(1))
        assertEquals(9..20, track.keyframeTickRange(2))
        assertEquals(7, 18.coerceIn(track.keyframeTickRange(0)))
    }

    /**
     * 创建测试使用的 20 tick 轨道。
     *
     * 示例：`track(first, second)` 会复制传入数组到可变列表。
     * 禁止把返回值跨测试共享。
     *
     * @param frames 测试关键帧
     * @return 独立轨道
     */
    private fun track(vararg frames: BlockTestAnimationKeyframe): BlockTestAnimationTrack {
        return BlockTestAnimationTrack(20, BlockTestPlaybackMode.ONCE, frames.toMutableList())
    }
}
