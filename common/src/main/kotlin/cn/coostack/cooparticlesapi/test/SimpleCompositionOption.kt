package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.test.api.TestOption

/**
 * 测试一个粒子组合。
 *
 * @param T 粒子组合的具体类型
 * @property composition 本次测试使用的粒子组合
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时沿用原来的类名格式
 */
class SimpleCompositionOption<T : ParticleComposition> @JvmOverloads constructor(
    val composition: T,
    var testingTick: Int = 100,
    private val id: String = "composition: ${composition::class.java.name}"
) : TestOption<T> {
    override fun paramTarget(): T {
        return composition
    }

    override fun start() {
        ParticleCompositionManager.spawn(composition)
    }

    override fun stop() {
        composition.remove()
    }

    override fun isValid(): Boolean {
        return !composition.canceled && (testingTick > 0 || testingTick == -1)
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
