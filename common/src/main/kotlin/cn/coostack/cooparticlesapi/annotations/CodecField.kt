package cn.coostack.cooparticlesapi.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
/**
 * 当你使用了对应的register之后
 *
 * 如果目标实例的成员属性是 可修改的 且在CodecHelper内注册了Codec
 *
 * 则能够自动对目标实例进行注入更新
 */
annotation class CodecField