package cn.coostack.cooparticlesapi.test.api

import net.minecraft.world.entity.player.Player
import java.util.function.Supplier

interface TestGroup {

    fun getUser(): Player

    fun appendOption(sup: Supplier<TestOption<*>>): TestGroup

    fun init()

    fun start()

    fun isDone(): Boolean

    fun skipCurrent(): TestOption<*>?

    fun doTick()

    fun onOptionFailure(t: Throwable, option: TestOption<*>)
    fun onOptionSuccess(option: TestOption<*>)

    fun onGroupFinished()

    fun groupID(): String

}
