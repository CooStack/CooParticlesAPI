package cn.coostack.cooparticlesapi.cparticle

/**
 * 为外部纹理来源提供有序 UV 帧。资源重载后会再次调用，调用线程是渲染线程。
 */
fun interface CParticleUvProvider {
    fun frames(): List<CParticleSprites.UvRect>

    /** 写入动画 metadata，当前渲染器只消费 PARTICLE_ATLAS。 */
    fun textureChannel(): Int = PARTICLE_ATLAS

    companion object {
        const val PARTICLE_ATLAS = 0
        const val RESERVED_BLOCK_ATLAS = 1
    }
}
