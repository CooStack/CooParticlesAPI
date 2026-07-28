package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption

/**
 * 测试一个粒子发射器。
 *
 * @param T 发射器的具体类型
 * @property testEmitters 本次测试使用的发射器
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时沿用发射器 ID
 */
class SimpleEmitterOption<T : ParticleEmitters> @JvmOverloads constructor(
    val testEmitters: T,
    var testingTick: Int = 100,
    private val id: String = "emitter: ${testEmitters.getEmittersID()}"
) : TestOption<T> {
    /**
     * 每刻在倒计时更新后执行的附加逻辑。
     *
     * 可以用它更新测试状态；不要在这里再次生成同一个发射器。
     */
    var ticking: SimpleEmitterOption<T>.() -> Unit = {}

    override fun paramTarget(): T {
        return testEmitters
    }

    override fun start() {
        ParticleEmittersManager.spawnEmitters(testEmitters)
    }

    override fun stop() {
        testEmitters.canceled = true
    }

    override fun isValid(): Boolean {
        return !testEmitters.canceled && (testingTick > 0 || testingTick == -1)
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
        ticking()
    }
}
