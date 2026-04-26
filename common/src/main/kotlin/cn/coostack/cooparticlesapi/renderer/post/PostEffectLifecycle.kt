package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.network.FriendlyByteBuf
import kotlin.math.max
import kotlin.math.min

data class PostEffectLifecycle(
    val durationTicks: Int,
    val ageTicks: Int = 0,
    val warmupTicks: Int = 0,
    val expandTicks: Int = durationTicks,
    val holdTicks: Int = 0,
    val fadeTicks: Int = 0
) {
    val progress: Float
        get() = if (durationTicks <= 0) 1f else (ageTicks.toFloat() / durationTicks.toFloat()).coerceIn(0f, 1f)

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

    val expired: Boolean
        get() = durationTicks >= 0 && ageTicks >= durationTicks

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

enum class PostEffectLifecyclePhase {
    WARMUP,
    EXPAND,
    HOLD,
    FADE,
    DONE
}
