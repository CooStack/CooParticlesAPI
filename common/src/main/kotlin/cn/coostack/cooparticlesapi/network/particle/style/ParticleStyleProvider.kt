package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import java.util.UUID

interface ParticleStyleProvider {
    /**
     * 创建style
     * 执行此方法的时候不要调用基本参数(因为还没初始化)
     */
    fun createStyle(uuid: UUID, args: Map<String, ParticleControlerDataBuffer<*>>): ParticleGroupStyle
}