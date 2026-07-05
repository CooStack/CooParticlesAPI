package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class SimpleStyleOption(
    val testStyle: ParticleGroupStyle,
    val world: Level,
    val pos: Vec3,
    var testingTick: Int = 100
) : TestOption {
    override fun paramTarget(): Any {
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
        return "style: ${testStyle::class.java}"
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
