package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.CooParticleAPIClient
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import kotlin.random.Random

object ClientCameraUtil {
    var currentYawOffset = 0f
    var currentPitchOffset = 0f

    var currentXOffset = 0.0
    var currentYOffset = 0.0
    var currentZOffset = 0.0

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
            currentXOffset = currentAmplitude * random.nextDouble(-0.5, 0.5)
            currentYOffset = currentAmplitude * random.nextDouble(-0.5, 0.5)
            currentZOffset = currentAmplitude * random.nextDouble(-0.5, 0.5)
            currentYawOffset = (currentAmplitude * random.nextDouble(-2.0, 2.0)).toFloat()
            currentPitchOffset = (currentAmplitude * random.nextDouble(-2.0, 2.0)).toFloat()
            currentAmplitude -= decreaseStep
        }.setFinishCallback {
            currentYawOffset = 0f
            currentPitchOffset = 0f
            currentXOffset = 0.0
            currentYOffset = 0.0
            currentZOffset = 0.0
        }
    }
}