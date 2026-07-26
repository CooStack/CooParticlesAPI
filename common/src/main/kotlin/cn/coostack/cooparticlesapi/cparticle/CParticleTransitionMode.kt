package cn.coostack.cooparticlesapi.cparticle

enum class CParticleTransitionMode {
    /** 播放一次，完成后保留最后一帧。 */
    HOLD_END,

    /** 播放一次，完成后恢复粒子原本的视觉状态。 */
    RESET,

    /** 按给定时长循环播放。 */
    LOOP,
}
