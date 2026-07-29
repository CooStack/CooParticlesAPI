package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证动态姿态重置发生在 Option supplier 构造之前。
 *
 * 示例：需要读取玩家位置的 Option 会在构造时看到起点姿态。
 * 禁止把该测试当作网络同步或客户端渲染测试。
 */
class BlockTestGroupOptionStartTest {
    /**
     * 检查重置、构造、启动的顺序。
     *
     * 示例：事件序列应为 `reset -> construct -> start`。
     * 禁止将重置延后到下一次 `doTick`。
     */
    @Test
    fun resetsBeforeSupplierCreatesOption() {
        val events = ArrayList<String>()
        val group = BlockTestGroup(uninitializedBlockTestPlayer(), "order").also {
            it.announceGroupFinished = false
            it.optionStartListener = { _, _ -> events += "reset" }
        }.appendOption {
            events += "construct"
            object : TestOption<Any> {
                override fun start() {
                    events += "start"
                }

                override fun stop() = Unit

                override fun isValid(): Boolean = true

                override fun onFailed() = Unit

                override fun onSuccess() = Unit

                override fun optionID(): String = "order"

                override fun doTick() = Unit

                override fun reviewMode(): TestReviewMode = TestReviewMode.AUTO

                override fun paramTarget(): Any = this
            }
        }

        group.start()

        assertEquals(listOf("reset", "construct", "start"), events)
    }

    /**
     * 在没有真实服务端世界时创建测试玩家占位对象。
     *
     * 示例：纯调度测试只访问玩家引用，不调用其世界方法。
     * 禁止把该占位对象用于真实游戏 tick。
     *
     * @return 未初始化的测试玩家
     */
    private fun uninitializedBlockTestPlayer(): BlockTestPlayer {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as Unsafe).allocateInstance(BlockTestPlayer::class.java) as BlockTestPlayer
    }
}
