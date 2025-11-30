package cn.coostack.cooparticlesapi.utils.interpolator.data

/**
 * @author CooStack
 * @date 2025/11/30
 */
interface DataInterpolator<T> {
    /**
     * 通过给定的progres获取 a -> b 的插值值
     *
     * @param progress 进度
     * @return 插值进度
     */
    fun get(progress: Double): T

    /**
     * 上传一次数值 方便 progress的设置
     *
     * @param value
     */
    fun uploadValue(value: T)

    /**
     * 获取最近上传的值
     *
     */
    fun current(): T

}