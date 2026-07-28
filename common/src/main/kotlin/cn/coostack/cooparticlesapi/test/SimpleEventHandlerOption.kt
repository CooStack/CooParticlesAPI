package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.test.options.event.TestChildEvent
import net.minecraft.world.entity.player.Player

/**
 * 按固定时长派发测试事件。
 *
 * @property testPlayer 事件关联的玩家
 * @param ticking 最长运行时间，`-1` 表示不限时
 * @property id 测试项 ID；不传时保持原来的类名
 */
class SimpleEventHandlerOption @JvmOverloads constructor(
    val testPlayer: Player,
    ticking: Int,
    private val id: String = "SimpleEventHandlerOption"
) : TickingTestOption<SimpleEventHandlerOption>(ticking) {
    override fun paramTarget(): SimpleEventHandlerOption {
        return this
    }

    override fun start() {
    }

    override fun doTick() {
        super.doTick()
        CooEventBus.call(TestChildEvent(testPlayer, "id $testingTick"))
    }

    override fun stop() {
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return id
    }
}
