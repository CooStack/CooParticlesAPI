package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.network.FriendlyByteBuf
import kotlin.math.max
import kotlin.math.min

/**
 * post effect 实例的生命周期。
 *
 * [durationTicks] 决定实例何时过期，[progress] 是 `ageTicks / durationTicks` 的 0..1 值，
 * 会作为内置 uniform `progress` 上传给 shader。
 *
 * `warmup/expand/hold/fade` 用于描述阶段，而不是自动改变输出。shader 可以读取 `progress`
 * 或业务方通过参数决定不同阶段的行为。当前阶段可通过 [phase] 查询。
 *
 * 复杂例子：
 *
 * ```kotlin
 * PostEffectLifecycle(
 *     durationTicks = 80,
 *     warmupTicks = 5,
 *     expandTicks = 25,
 *     holdTicks = 30,
 *     fadeTicks = 20
 * )
 * ```
 *
 * 这个类替代调用方手动维护 age、过期判断、progress 计算和网络同步字段。
 */
data class PostEffectLifecycle(
    val durationTicks: Int,
    val ageTicks: Int = 0,
    val warmupTicks: Int = 0,
    val expandTicks: Int = durationTicks,
    val holdTicks: Int = 0,
    val fadeTicks: Int = 0
) {
    /** 当前生命周期进度，范围 0..1。duration <= 0 时固定为 1。 */
    val progress: Float
        get() = if (durationTicks <= 0) 1f else (ageTicks.toFloat() / durationTicks.toFloat()).coerceIn(0f, 1f)

    /** 当前阶段，用于业务逻辑或自定义 executor 判断。 */
    val phase: PostEffectLifecyclePhase
        get() {
            var cursor = max(0, warmupTicks)
            if (ageTicks < cursor) return PostEffectLifecyclePhase.WARMUP
            cursor += max(0, expandTicks)
            if (ageTicks < cursor) return PostEffectLifecyclePhase.EXPAND
            cursor += max(0, holdTicks)
            if (ageTicks < cursor) return PostEffectLifecyclePhase.HOLD
            cursor += max(0, fadeTicks)
            if (ageTicks < cursor) return PostEffectLifecyclePhase.FADE
            return PostEffectLifecyclePhase.DONE
        }

    /** 是否已达到总时长。durationTicks < 0 可表达“不按 duration 自动过期”。 */
    val expired: Boolean
        get() = durationTicks >= 0 && ageTicks >= durationTicks

    /** 推进一 tick，避免 age 溢出到 Int.MAX_VALUE。 */
    fun tick(): PostEffectLifecycle = copy(ageTicks = min(Int.MAX_VALUE - 1, ageTicks + 1))

    fun write(buf: FriendlyByteBuf) {
        buf.writeInt(durationTicks)
        buf.writeInt(ageTicks)
        buf.writeInt(warmupTicks)
        buf.writeInt(expandTicks)
        buf.writeInt(holdTicks)
        buf.writeInt(fadeTicks)
    }

    companion object {
        fun read(buf: FriendlyByteBuf): PostEffectLifecycle {
            return PostEffectLifecycle(
                durationTicks = buf.readInt(),
                ageTicks = buf.readInt(),
                warmupTicks = buf.readInt(),
                expandTicks = buf.readInt(),
                holdTicks = buf.readInt(),
                fadeTicks = buf.readInt()
            )
        }
    }
}

/** 生命周期阶段。阶段只表达语义，不会自动改变 shader；需要 shader 或业务代码读取后自行使用。 */
enum class PostEffectLifecyclePhase {
    /** 预热期，适合做淡入、预采样或延迟触发。 */
    WARMUP,
    /** 展开期，适合半径、强度、mask 范围从小到大变化。 */
    EXPAND,
    /** 保持期，适合稳定显示。 */
    HOLD,
    /** 消退期，适合透明度或强度降低。 */
    FADE,
    /** 已完成，通常实例会被清理或不再提交。 */
    DONE
}
