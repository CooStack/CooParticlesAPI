package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.animation.Animate
import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption

/**
 * 测试一个服务端动画实例。
 *
 * @property animate 本次测试使用的动画
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时保持原来的 `animate`
 */
class SimpleAnimateOption @JvmOverloads constructor(
    val animate: Animate,
    var testingTick: Int = 100,
    private val id: String = "animate"
) : TestOption<Animate> {
    override fun paramTarget(): Animate {
        return animate
    }

    override fun start() {
        AnimateManager.displayAnimateServer(animate)
    }

    override fun stop() {
        animate.cancel()
    }

    override fun isValid(): Boolean {
        return !animate.done && (testingTick > 0 || testingTick == -1)
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return id
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
