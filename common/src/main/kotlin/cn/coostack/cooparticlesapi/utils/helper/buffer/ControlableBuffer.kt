package cn.coostack.cooparticlesapi.utils.helper.buffer

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer

/**
 * 注解此类可以快速获得particle buffer
 *
 * 由于kotlin类和java类在 基本数据类型的封装类上有差异
 * 于是可能会导致空指针异常的情况
 *
 * 此模块对基本数据类型进行了kotlin映射处理
 * 但是无法处理基本数据类型对应的数组
 * 不建议在IntArray LongArray等基本数据类型数组上注解此参数 (未测试)
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ControlableBuffer(
    val name: String,
)
