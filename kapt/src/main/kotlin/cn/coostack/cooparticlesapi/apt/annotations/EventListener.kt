package cn.coostack.cooparticlesapi.apt.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
/**
 * 基于对forge EventBusSubscriber的模仿
 * 这里会对生成的类中
 * 里面会对-这种特殊符号进行处理
 * 同时拼接类 MODID会放在后面 (防止数字开头直接干死)
 */
annotation class EventListener(val modID: String = "default")