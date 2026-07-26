package cn.coostack.cooparticlesapi.cparticle

enum class CParticleUpdateMode {
    /** 保留源对象，绘制前同步颜色、尺寸、朝向和贴图帧。 */
    DYNAMIC,

    /** 只在生成时写入一次，之后不再读取源对象。 */
    STATIC,
}
