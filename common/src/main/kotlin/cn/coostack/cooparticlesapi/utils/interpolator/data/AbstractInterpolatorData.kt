package cn.coostack.cooparticlesapi.utils.interpolator.data

abstract class AbstractInterpolatorData<T>(protected var value: T) : InterpolatorData<T> {
    protected var currentFrame: T? = null
    protected var last: T = value

    override fun uploadData(current: T): AbstractInterpolatorData<T> {
        currentFrame = current
        return this
    }


    override fun flushFrame() {
        // 设置插值
        if (currentFrame == null) {
            last = value
        } else {
            last = value
            value = currentFrame!!
            currentFrame = null
        }
    }

}