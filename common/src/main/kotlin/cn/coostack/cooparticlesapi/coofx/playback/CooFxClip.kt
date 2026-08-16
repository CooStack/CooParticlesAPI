package cn.coostack.cooparticlesapi.coofx.playback

import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxTransformTrack

/**
 * 定义播放时间越过 clip 末端时的映射方式。序列化层应使用成员名称，默认值由资源协议显式指定，
 * 运行时不得根据调用位置猜测。
 */
enum class CooFxLoopMode {
    /** 只播放一次；时间到达末端后保持末端姿态，并把播放状态标记为完成。 */
    ONCE,

    /** 正向循环；每次到达 duration 都回到零秒，末端关键帧只在小于 duration 的时间内被采样。 */
    LOOP,

    /** 往返循环；先从零秒到 duration，再反向回到零秒，两个转折端点各只出现一次。 */
    PING_PONG
}

/**
 * 一个按稳定列表顺序保存节点轨道的动画片段。duration 由全部轨道的最大输入时间推导，
 * 空片段和零时长片段应在进入播放模块前被拒绝。
 */
class CooFxClip(
    val name: String,
    transformTracks: List<CooFxTransformTrack>
) {
    val transformTracks: List<CooFxTransformTrack> = transformTracks.toList()
    val durationSeconds: Float = this.transformTracks.maxOfOrNull(CooFxTransformTrack::endTimeSeconds)
        ?: throw IllegalArgumentException("动画片段至少需要一条轨道")

    init {
        require(name.isNotBlank()) { "动画片段名称不能为空" }
        require(durationSeconds > 0F) { "动画片段时长必须为正数" }
        val nodeIndices = this.transformTracks.map(CooFxTransformTrack::nodeIndex)
        require(nodeIndices.distinct().size == nodeIndices.size) { "同一动画片段不能重复声明节点轨道" }
    }
}
