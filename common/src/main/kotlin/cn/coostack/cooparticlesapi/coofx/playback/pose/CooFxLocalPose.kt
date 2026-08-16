package cn.coostack.cooparticlesapi.coofx.playback.pose

import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrackSampler
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/** 节点局部 TRS 姿态，构造时复制所有可变 JOML 输入。 */
class CooFxLocalPose(
    translation: Vector3fc = Vector3f(),
    rotation: Quaternionfc = Quaternionf(),
    scale: Vector3fc = Vector3f(1F)
) {
    val translation = Vector3f(translation)
    val rotation = Quaternionf(rotation).normalize()
    val scale = Vector3f(scale)

    init {
        require(this.translation.isFinite && this.rotation.isFinite && this.scale.isFinite) { "节点姿态必须为有限数" }
    }
}

/** 一个节点的 translation、rotation、scale 轨道；缺失通道沿用绑定姿态。 */
class CooFxTransformTrack(
    val nodeIndex: Int,
    val translation: CooFxTrack? = null,
    val rotation: CooFxTrack? = null,
    val scale: CooFxTrack? = null
) {
    val endTimeSeconds: Float = listOfNotNull(translation, rotation, scale).maxOfOrNull(CooFxTrack::endTimeSeconds)
        ?: throw IllegalArgumentException("节点变换轨道至少需要一个通道")

    init {
        require(nodeIndex >= 0) { "节点索引不能为负数" }
        require(translation == null || translation.componentCount == 3 && !translation.quaternion) { "平移轨道必须是三分量向量" }
        require(rotation == null || rotation.componentCount == 4 && rotation.quaternion) { "旋转轨道必须是四元数轨道" }
        require(scale == null || scale.componentCount == 3 && !scale.quaternion) { "缩放轨道必须是三分量向量" }
    }

    fun sample(timeSeconds: Float, bindPose: CooFxLocalPose): CooFxLocalPose {
        val sampledTranslation = translation?.let { track -> Vector3f(CooFxTrackSampler.sample(track, timeSeconds)) }
            ?: bindPose.translation
        val sampledRotation = rotation?.let { track ->
            val value = CooFxTrackSampler.sample(track, timeSeconds)
            Quaternionf(value[0], value[1], value[2], value[3])
        } ?: bindPose.rotation
        val sampledScale = scale?.let { track -> Vector3f(CooFxTrackSampler.sample(track, timeSeconds)) }
            ?: bindPose.scale
        return CooFxLocalPose(sampledTranslation, sampledRotation, sampledScale)
    }
}
