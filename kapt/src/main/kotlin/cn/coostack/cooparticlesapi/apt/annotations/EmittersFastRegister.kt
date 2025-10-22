package cn.coostack.cooparticlesapi.apt.annotations

/**
 * 自动注册 ParticleEmitters
 * 在ParticleEmitters的定义处进行注解
 *
 * 需要 空构造方法或者 第一个参数为 Vec3 第二个参数为 Level? 的双参数构造方法
 * ```
 * @FastRegister
 * class YourEmitter: ParticleEmitters{
 *      constructor(pos: Vec3, world: Level?){
 *          // ...
 *      }
 * }
 * ```
 *
 * 在主类调用(load时)
 * ```
 * FastRegisterUtil.loadAllFastLoader(modID) 进行加载
 * ```
 *
 * gradle 设置需要引用kapt
 * ```
 * plugins{
 *  id 'org.jetbrains.kotlin.kapt' version "${kotlin_version}"
 * }
 *
 * dependencies{
 *  // ...
 *  kapt 'cn.coostack:cooparticlesapi-kapt:{version}'
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class EmittersFastRegister(val modID: String = "cooparticlesapi")
