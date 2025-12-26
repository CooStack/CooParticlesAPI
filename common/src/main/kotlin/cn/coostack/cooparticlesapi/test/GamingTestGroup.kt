package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestOption
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.function.Supplier

class GamingTestGroup(val testPlayer: Player, val id: String) : TestGroup {
    val options = ArrayList<Supplier<TestOption>>()
    var currentOption: TestOption? = null
    var testingIndex = 0
    override fun appendOption(sup: Supplier<TestOption>): GamingTestGroup {
        options.add(sup)
        return this
    }

    override fun init() {
        testingIndex = 0
    }

    override fun start() {
        currentOption = options.getOrNull(testingIndex++)?.get()
        currentOption?.start()
    }

    override fun isDone(): Boolean {
        return testingIndex >= options.size && !(currentOption?.isValid() ?: true)
    }

    override fun doTick() {
        currentOption ?: return
        val option = currentOption as TestOption
        try {
            option.doTick()
        } catch (e: Exception) {
            onOptionFailure(e, option)
            currentOption = options.getOrNull(testingIndex++)?.get()
            return
        }

        if (!option.isValid()) {
            onOptionSuccess(option)
            option.stop()
            currentOption = options.getOrNull(testingIndex++)?.get()
            currentOption?.start()
            if (isDone()) {
                onGroupFinished()
            }
        }


    }

    override fun onOptionFailure(t: Throwable, option: TestOption) {
        testPlayer.sendSystemMessage(
            Component.literal(
                """
                    测试项: ${option.optionID()} 在进行tick操作时发生了异常
                    异常: ${t.stackTraceToString()}
                """.trimIndent()
            )
        )
    }

    override fun onOptionSuccess(option: TestOption) {
        testPlayer.sendSystemMessage(
            Component.literal(
                """
                测试项: ${option.optionID()} 执行完成
            """.trimIndent()
            )
        )
    }

    override fun onGroupFinished() {
        // done
        testPlayer.sendSystemMessage(
            Component.literal(
                "测试 $id 已经全部完成"
            )
        )
    }


    override fun groupID(): String {
        return id
    }
}