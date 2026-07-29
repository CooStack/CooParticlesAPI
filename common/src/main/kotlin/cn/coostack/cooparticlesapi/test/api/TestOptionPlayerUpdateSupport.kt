package cn.coostack.cooparticlesapi.test.api

import net.minecraft.world.entity.player.Player
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * 保存并派发 [TestOption.onPlayerUpdate] 注册的可选回调。
 *
 * 示例：BlockTest 调度器在更新模拟玩家姿态后调用 [dispatch]。
 * 禁止把这里的弱引用状态用作 Option 生命周期或运行状态来源。
 */
internal object TestOptionPlayerUpdateSupport {
    /**
     * 按 Option 弱引用保存已注册回调，避免延长一次性测试项的生命周期。
     *
     * 示例：同一 Option 可以按注册顺序保存多个玩家更新回调。
     * 禁止在回调值中强引用作为键的 Option。
     */
    private val callbacks = Collections.synchronizedMap(
        WeakHashMap<TestOption<*>, MutableList<(Player, Any) -> Unit>>()
    )

    /**
     * 为测试项追加一个强类型玩家更新回调。
     *
     * 示例：`register(option) { player, target -> target.moveTo(player.position()) }`。
     * 禁止传入与 [TestOption.paramTarget] 返回类型不一致的回调。
     *
     * @param T Option 参数目标类型
     * @param option 注册回调的测试项
     * @param action 接收玩家与参数目标的处理逻辑
     * @return 原测试项
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> register(
        option: TestOption<T>,
        action: TestOption<T>.(Player, T) -> Unit,
    ): TestOption<T> {
        val optionReference = WeakReference(option)
        callbacks.getOrPut(option) { ArrayList() }.add { player, target ->
            optionReference.get()?.let { registeredOption ->
                registeredOption.action(player, target as T)
            }
        }
        return option
    }

    /**
     * 使用一次 [TestOption.paramTarget] 查询依次派发当前 Option 的全部回调。
     *
     * 示例：BlockTest 的活动 Option 每 tick 在 `doTick()` 前调用一次。
     * 禁止为待复核或已结束的 Option 调用本方法。
     *
     * @param option 当前活动测试项
     * @param player 已完成姿态更新的模拟玩家
     */
    fun dispatch(option: TestOption<*>, player: Player) {
        val registeredCallbacks = callbacks[option]?.toList().orEmpty()
        if (registeredCallbacks.isEmpty()) return
        val target = option.paramTarget()
        registeredCallbacks.forEach { action -> action(player, target) }
    }
}
