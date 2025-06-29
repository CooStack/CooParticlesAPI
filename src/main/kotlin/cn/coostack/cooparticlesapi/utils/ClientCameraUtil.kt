package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.CooParticleAPIClient
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

object ClientCameraUtil {
    internal var shakeYawOffset = 0f
    internal var shakePitchOffset = 0f
    internal var shakeXOffset = 0.0
    internal var shakeYOffset = 0.0
    internal var shakeZOffset = 0.0

    var currentYawOffset = 0f
    var currentPitchOffset = 0f

    var currentXOffset = 0.0
    var currentYOffset = 0.0
    var currentZOffset = 0.0

    fun setOffsetPosition(offset: Vec3d) {
        currentXOffset = offset.x
        currentYOffset = offset.y
        currentZOffset = offset.z
    }

    fun resetPosOffset() {
        currentXOffset = 0.0
        currentYOffset = 0.0
        currentZOffset = 0.0
    }

    fun resetAngleOffset() {
        currentYawOffset = 0f
        currentPitchOffset = 0f
    }

    fun resetOffset() {
        resetAngleOffset()
        resetPosOffset()
    }

    /**
     * @param tick 修改相机的位置
     */
    fun startShakeCamera(
        tick: Int, amplitude: Double
    ) {
        var currentAmplitude = amplitude
        val decreaseStep = amplitude / tick
        val random = Random(System.currentTimeMillis())
        CooParticleAPIClient.scheduler.runTaskTimerMaxTick(tick) {
            shakeXOffset = currentAmplitude * random.nextDouble(-0.5, 0.5)
            shakeYOffset = currentAmplitude * random.nextDouble(-0.5, 0.5)
            shakeZOffset = currentAmplitude * random.nextDouble(-0.5, 0.5)
            shakeYawOffset = (currentAmplitude * random.nextDouble(-2.0, 2.0)).toFloat()
            shakePitchOffset = (currentAmplitude * random.nextDouble(-2.0, 2.0)).toFloat()
            currentAmplitude -= decreaseStep
        }.setFinishCallback {
            shakeYawOffset = 0f
            shakePitchOffset = 0f
            shakeXOffset = 0.0
            shakeYOffset = 0.0
            shakeZOffset = 0.0
        }
    }
}