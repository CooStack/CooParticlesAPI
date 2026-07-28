package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.test.api.TestOption

/**
 * 测试一个显示实体。
 *
 * @param T 显示实体的具体类型
 * @property testDisplayEntity 本次测试使用的显示实体
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时沿用原来的类名格式
 */
class SimpleDisplayEntityOption<T : DisplayEntity> @JvmOverloads constructor(
    val testDisplayEntity: T,
    var testingTick: Int = 100,
    private val id: String = "displayer:  ${testDisplayEntity::class.java.name}"
) : TestOption<T> {
    override fun paramTarget(): T {
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
        return id
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}
