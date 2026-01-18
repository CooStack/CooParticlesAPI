package cn.coostack.cooparticlesapi.utils.interpolator.data


/**
 * 方便获取值插值
 * 一般用于需要启用 interpolator
 *
 * @param T
 */
interface InterpolatorData<T> {

    fun update(current: T): InterpolatorData<T>

    fun getWithInterpolator(progress: Number): T

    fun getCurrent(): T
}