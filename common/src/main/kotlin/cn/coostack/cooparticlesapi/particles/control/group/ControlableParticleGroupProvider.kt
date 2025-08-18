package cn.coostack.cooparticlesapi.particles.control.group

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import java.util.*

/**
 * ServerParticleGroupManager在发送Group包后
 * 由ClientParticleGroupManager处理此包并且通过该对象创建ControlableParticleGroupBuilder
 * 这些参数代表发包时(create particle group) 用户输入的其余参数
 * 自行解码构建
 */
interface ControlableParticleGroupProvider {
    /**
     * @param args 接收到的所有参数
     * @see ParticleControlerDataBuffer.loadedValue 所有参数都解码在这里
     */
    fun createGroup(uuid: UUID, args: Map<String, ParticleControlerDataBuffer<*>>): ControlableParticleGroup

    /**
     * 当ServerParticleGroup执行了 change方法时 会调用此方法
     * 使用此方法修改对被操作的group进行同步
     * @param group uuid和 ServerParticleGroup 相同的ClientGroup
     * @param args 更改的内容
     *
     * 位于 PacketParticleGroupS2C.PacketArgsType 下的修改也会在args内
     */
    fun changeGroup(group: ControlableParticleGroup, args: Map<String, ParticleControlerDataBuffer<*>>)
}