package cn.coostack.cooparticlesapi.utils.interpolator.data


/**
 * 方便获取值插值
 * 一般用于需要启用 interpolator
 *
 * 避免因为类似
 * ```
 * // 外部调用更新位置， 更新方法会做下面两行操作
 * prev = pos
 * pos = current // 新位置
 * // 某个tick方法内，进行的固定设置（防止闪烁）
 * prev = pos // 导致 prev = current pos = current 无法正确插值
 * ```
 * 按理来说， 防闪烁的prev赋值应该在更新位置之前
 * 但是在外部更新位置是无法决定实际顺序的， 因此设计此类
 * 维护此类时可以固定插值的顺序，防止prev = pos的防闪烁设计影响正确插值结果
 * @param T
 */
interface InterpolatorData<T> {

    fun getWithInterpolator(progress: Number): T


    /**
     * 上传一个数据， 即使在同一个tick设置多次，
     * 也只会按照最后一次的顺序来
     *
     * @param current 当前数值
     */
    fun uploadData(current: T): InterpolatorData<T>

    /**
     * 在tick方法内调用
     *
     * 设计上会修改当前帧 (currentFrame) 作为新 `pos` 然后设置currentFrame = null
     * 当currentFrame不为null时， interpolator会获取到 lastUpload->currentFrame的插值
     *
     * 当currentFrame为null时， lastUpload会被设置为 currentFrame, 插值也只会返回本身
     */
    fun flushFrame()

    /**
     * 获取当前的value， 如果不调用flushFrame，值只会是初始值
     *
     * @return 初始value->你没有调用flushFrame current->调用一次flush后的当前栈值
     */
    fun getCurrent(): T
}