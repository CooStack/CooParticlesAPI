package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestOption

/**
 * 提供倒计时有效性判断的测试项基类。
 *
 * @param T 参数应用目标的类型
 * @property testingTick 剩余运行时间，`-1` 表示不限时
 */
abstract class TickingTestOption<T : Any>(var testingTick: Int = 100) : TestOption<T> {
    override fun isValid(): Boolean {
        return testingTick > 0 || testingTick == -1
    }


    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
