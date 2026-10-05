package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticleDeathState
import cn.coostack.cooparticlesapi.particles.control.RemoveReason

/** 按需记录槽位的单次死亡事件；先捕获快照，再在模拟完成后执行回调。 */
internal class CParticleDeathTracker(
    private val capture: (Int, RemoveReason) -> CParticleDeathState,
) {
    /** 键为当前槽位号；值为该槽位本次生命对应的回调，回收时立即删除。 */
    private val actions = HashMap<Int, (CParticleDeathState) -> Unit>()

    /** 已与槽位脱离的回调，避免回收过程中重生并覆盖仍在遍历的存储。 */
    private val pending = ArrayDeque<() -> Unit>()

    fun track(slot: Int, action: (CParticleDeathState) -> Unit) {
        actions[slot] = action
    }

    fun record(slot: Int, reason: RemoveReason) {
        val action = actions.remove(slot) ?: return
        if (reason == RemoveReason.QUEUE) return
        val state = capture(slot, reason)
        pending.addLast { action(state) }
    }

    /** 只处理本批事件，回调产生的新事件留待下一轮，避免无限递归。 */
    fun drain() {
        val count = pending.size
        repeat(count) {
            val action = pending.removeFirstOrNull() ?: return
            action()
        }
    }

    fun clear() {
        actions.clear()
        pending.clear()
    }
}
