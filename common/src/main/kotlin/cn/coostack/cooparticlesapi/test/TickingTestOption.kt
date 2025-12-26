package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestOption

abstract class TickingTestOption(var testingTick: Int = 100) : TestOption {
    override fun isValid(): Boolean {
        return testingTick > 0 || testingTick == -1
    }


    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}