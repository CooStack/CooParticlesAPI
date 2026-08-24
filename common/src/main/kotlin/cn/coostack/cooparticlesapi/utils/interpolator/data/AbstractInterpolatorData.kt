package cn.coostack.cooparticlesapi.utils.interpolator.data

// Codec 不赋值last， 因为实际上服务器不应该传递插值信息给客户端， 这要由客户端自己处理
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