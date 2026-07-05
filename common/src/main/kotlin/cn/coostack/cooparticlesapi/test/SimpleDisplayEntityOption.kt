package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.test.api.TestOption

class SimpleDisplayEntityOption(val testDisplayEntity: DisplayEntity, var testingTick: Int = 100) : TestOption {
    override fun paramTarget(): Any {
        return testDisplayEntity
    }

    override fun start() {
        DisplayEntityManager.spawn(testDisplayEntity)
    }

    override fun stop() {
        testDisplayEntity.remove()
    }

    override fun isValid(): Boolean {
        return testDisplayEntity.isValid() && (testingTick > 0 || testingTick == -1)
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return "displayer: ${testDisplayEntity.controlUUID()} : ${testDisplayEntity::class.java.name}"
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
