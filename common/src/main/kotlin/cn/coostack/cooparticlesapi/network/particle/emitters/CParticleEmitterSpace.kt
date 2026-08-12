package cn.coostack.cooparticlesapi.network.particle.emitters

/**
 * 定义 [TransformableCParticleEmitter] 新生成粒子使用的坐标空间。
 *
 * [LOCAL] 把粒子位置和速度保存在发射器局部空间中。粒子生成后仍会跟随发射器平移、
 * [TransformableCParticleEmitter.emitterRotation] 和缩放，
 * 也是默认模式。[WORLD] 使用普通 GPU 发射器的世界坐标语义，已经生成的粒子不再受发射器变换影响。
 * 模式切换只影响后续生成的粒子，已有粒子继续留在各自创建时的空间中。
 *
 * @property networkId 稳定的网络编号，不依赖枚举声明顺序
 */
enum class CParticleEmitterSpace(val networkId: Int) {
    /** 粒子属于发射器局部空间，并持续跟随发射器变换。 */
    LOCAL(0),

    /** 粒子生成后保留在世界空间，不跟随发射器变换。 */
    WORLD(1);

    companion object {
        /**
         * 按稳定网络编号恢复发射空间。
         *
         * @param networkId 网络数据中的空间编号
         * @return 对应的发射空间
         * @throws IllegalArgumentException 编号未知时抛出
         */
        @JvmStatic
        fun fromNetworkId(networkId: Int): CParticleEmitterSpace {
            return entries.firstOrNull { it.networkId == networkId }
                ?: throw IllegalArgumentException("unknown CParticle emitter space: $networkId")
        }
    }
}
