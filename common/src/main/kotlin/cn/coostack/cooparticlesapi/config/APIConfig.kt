package cn.coostack.cooparticlesapi.config

import kotlin.math.max

class APIConfig {
    /**
     * 是否启用ParticleManagerMixin 对粒子数量上限进行修改
     * (对其他插件进行兼容)
     */
    var enabledParticleCountInject = true
    var enabledParticleAsync = true

    /**
     * 粒子数量上限
     * 原版上限为16384
     */
    var particleCountLimit = 65536 * 2
        get() = max(field, 1)

    /**
     * 客户端允许同时存活的 CParticle 总数。
     *
     * Example: `3_000_000` 允许所有 GPU 粒子系统合计存活三百万个粒子。
     * Forbidden: 小于 `1` 的配置会按 `1` 处理，不能用该值关闭 CParticle。
     */
    var cparticleCountLimit = 3_000_000
        get() = max(field, 1)

    /**
     * Status GUI 活跃时请求服务端快照的客户端 tick 间隔。
     *
     * 默认 `20` tick，约一秒；异常配置会限制在 5 至 1200 tick。
     */
    var statusServerRefreshIntervalTicks = 20
        get() = field.coerceIn(5, 1_200)

    /**
     * Math3DUtil的 threadPool最大线程数
     */
    var calculateThreadCount = 16
        get() = max(field, 1)
}
