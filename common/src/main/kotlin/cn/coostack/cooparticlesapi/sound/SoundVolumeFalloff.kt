package cn.coostack.cooparticlesapi.sound

import kotlin.math.pow

/**
 * 服务端可控音效的距离音量衰减模式。
 *
 * 衰减只影响服务端同步给每个玩家的音量倍率，不改变 [visibleRange] 的可见性筛选职责。
 * [NONE] 保持旧行为；其他模式会把声音中心到可见范围边界映射为 1 到 0 的音量倍率。
 */
enum class SoundVolumeFalloff {
    /**
     * 不按距离额外衰减音量，只使用实例本身的 volume。
     */
    NONE,

    /**
     * 线性衰减：距离 0 为满音量，距离 visibleRange 为静音。
     */
    LINEAR,

    /**
     * 平滑衰减：中心附近下降更缓，边缘更平滑地接近静音。
     */
    SMOOTHSTEP,

    /**
     * 平方衰减：比线性衰减更快，适合能量感较集中的音效。
     */
    QUADRATIC;

    fun factor(distance: Double, range: Double): Float {
        if (this == NONE || range <= 0.0) {
            return 1f
        }
        val progress = (distance / range).coerceIn(0.0, 1.0)
        val remaining = 1.0 - progress
        return when (this) {
            NONE -> 1f
            LINEAR -> remaining
            SMOOTHSTEP -> remaining * remaining * (3.0 - 2.0 * remaining)
            QUADRATIC -> remaining.pow(2.0)
        }.toFloat()
    }
}
