package cn.coostack.cooparticlesapi.annotations

/**
 * 标记需要在客户端自动绑定到 RenderEntity 的 renderer。
 *
 * 被标记的类还必须实现 `RenderEntityRenderer<T>`，
 * 注册器会从泛型参数 `T` 找到对应实体的 id 和 codec。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CooAutoRegisterRenderer
