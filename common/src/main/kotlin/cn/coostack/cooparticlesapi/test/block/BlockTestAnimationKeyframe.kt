package cn.coostack.cooparticlesapi.test.block

import net.minecraft.world.phys.Vec3

/**
 * 保存动态位置或 forward 轨道中的一个关键帧。
 *
 * 示例：`BlockTestAnimationKeyframe(20, Vec3(1.0, 0.0, 0.0))` 表示第 20 tick 的值。
 * 禁止在未调用轨道归一化的情况下把重复 tick 的关键帧交给运行时采样。
 *
 * @property tick 关键帧所在 tick，最终会被限制在轨道总时长内
 * @property value 位置偏移或 forward 向量
 * @property curveToNext 当前帧到下一帧使用的曲线
 * @property outgoingTime 右手柄在分段时间轴上的归一化 X 坐标
 * @property outgoingProgress 右手柄的归一化进度 Y 坐标
 * @property incomingTime 左手柄在分段时间轴上的归一化 X 坐标
 * @property incomingProgress 左手柄的归一化进度 Y 坐标
 * @property locked 是否为创建轨道时保留的不可删除关键帧
 */
data class BlockTestAnimationKeyframe(
    var tick: Int,
    var value: Vec3,
    var curveToNext: BlockTestCurveType = BlockTestCurveType.LINEAR,
    var outgoingTime: Double = 1.0 / 3.0,
    var outgoingProgress: Double = 1.0 / 3.0,
    var incomingTime: Double = 2.0 / 3.0,
    var incomingProgress: Double = 2.0 / 3.0,
    val locked: Boolean = false,
)
