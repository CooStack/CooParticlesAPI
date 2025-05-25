package cn.coostack.cooparticlesapi.config

import kotlin.math.max

class APIConfig {
    /**
     * 是否启用ParticleManagerMixin 对粒子数量上限进行修改
     * (对其他插件进行兼容)
     */
    var enabledParticleCountInject = true

    /**
     * 粒子数量上限
     * 原版上限为65536
     */
    var particleCountLimit = 65536
        get() = max(field, 1)
}