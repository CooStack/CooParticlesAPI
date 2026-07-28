package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 测试一个粒子样式。
 *
 * @param T 粒子样式的具体类型
 * @property testStyle 本次测试使用的样式
 * @property world 样式生成所在的世界
 * @property pos 样式生成位置
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时沿用原来的类名格式
 */
class SimpleStyleOption<T : ParticleGroupStyle> @JvmOverloads constructor(
    val testStyle: T,
    val world: Level,
    val pos: Vec3,
    var testingTick: Int = 100,
    private val id: String = "style: ${testStyle::class.java}"
) : TestOption<T> {
    override fun paramTarget(): T {
        return testStyle
    }

    override fun start() {
        ParticleStyleManager.spawnStyle(world, pos, testStyle)
    }

    override fun stop() {
        testStyle.remove()
    }

    override fun isValid(): Boolean {
        return testStyle.valid && (testingTick > 0 || testingTick == -1)
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
