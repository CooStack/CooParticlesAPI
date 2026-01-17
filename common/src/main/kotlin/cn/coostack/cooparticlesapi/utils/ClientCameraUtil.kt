package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object ClientCameraUtil {
    var shakeYawOffset = 0f
    var shakePitchOffset = 0f
    var shakeXOffset = 0.0
    var shakeYOffset = 0.0
    var shakeZOffset = 0.0

    var currentYawOffset = 0f
    var currentPitchOffset = 0f

    var currentXOffset = 0.0
    var currentYOffset = 0.0
    var currentZOffset = 0.0

    var tick = 0
    var ampStep = 0.0
    var amp = 0.0

    fun setOffsetPosition(offset: Vec3) {
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
        amp = amplitude
        ampStep = amp / tick
        this.tick = tick
    }

    fun tick() {
        if (tick > 0) {
            shakeXOffset = amp * Random.nextDouble(-0.5, 0.5)
            shakeYOffset = amp * Random.nextDouble(-0.5, 0.5)
            shakeZOffset = amp * Random.nextDouble(-0.5, 0.5)
            shakeYawOffset = (amp * Random.nextDouble(-2.0, 2.0)).toFloat()
            shakePitchOffset = (amp * Random.nextDouble(-2.0, 2.0)).toFloat()
            amp -= ampStep
            tick--
        }
    }

}