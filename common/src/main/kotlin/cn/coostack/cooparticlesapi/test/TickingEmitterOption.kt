package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption

/**
 * 在每刻执行判定逻辑的发射器测试项。
 *
 * @param T 发射器的具体类型
 * @property testEmitters 本次测试使用的发射器
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时沿用发射器 ID
 * @property ticking 每刻执行的停止判定
 */
class TickingEmitterOption<T : ParticleEmitters>(
    val testEmitters: T,
    var testingTick: Int = 100,
    private val id: String = "emitter: ${testEmitters.getEmittersID()}",
    val ticking: (T) -> Boolean
) : TestOption<T> {
    /**
     * 保留原有的三参数位置调用。
     *
     * @param testEmitters 本次测试使用的发射器
     * @param testingTick 最长运行时间，`-1` 表示不限时
     * @param ticking 每刻执行的停止判定
     */
    constructor(
        testEmitters: T,
        testingTick: Int,
        ticking: (T) -> Boolean
    ) : this(
        testEmitters,
        testingTick,
        "emitter: ${testEmitters.getEmittersID()}",
        ticking
    )

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
        if (ticking(testEmitters)) {
            stop()
        }
    }
}
