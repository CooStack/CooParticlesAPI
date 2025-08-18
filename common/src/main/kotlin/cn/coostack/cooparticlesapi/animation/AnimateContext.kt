package cn.coostack.cooparticlesapi.animation

import java.util.concurrent.ConcurrentHashMap

class AnimateContext(val animation: StreamAnimation) {
    /**
     * 数据存储
     */
    val dataBuffer = ConcurrentHashMap<String, Any>()

    /**
     * 在当前动画的 beforeDisplay事件中调用此方法
     * 可以设置这个动画的播放延迟 单位tick
     */
    fun setDisplayedDelay(delay: Int): AnimateContext {
        animation.displayDelay = delay
        return this
    }

    fun putString(key: String, value: String): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putBoolean(key: String, value: Boolean): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putByte(key: String, value: Byte): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putShort(key: String, value: Short): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putInt(key: String, value: Int): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putLong(key: String, value: Long): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putFloat(key: String, value: Float): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun putDouble(key: String, value: Double): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun put(key: String, value: Any): AnimateContext {
        dataBuffer[key] = value
        return this
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return runCatching { dataBuffer[key] as? Int }.getOrNull() ?: defaultValue
    }

    fun getLong(key: String, defaultValue: Long = 0): Long {
        return runCatching { dataBuffer[key] as? Long }.getOrNull() ?: defaultValue
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return runCatching { dataBuffer[key] as? Float }.getOrNull() ?: defaultValue
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return runCatching { dataBuffer[key] as? Double }.getOrNull() ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return runCatching { dataBuffer[key] as? Boolean }.getOrNull() ?: defaultValue
    }

    fun getByte(key: String, defaultValue: Byte = 0): Byte {
        return runCatching { dataBuffer[key] as? Byte }.getOrNull() ?: defaultValue
    }

    fun getShort(key: String, defaultValue: Short = 0): Short {
        return runCatching { dataBuffer[key] as? Short }.getOrNull() ?: defaultValue
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return runCatching { dataBuffer[key] as? String }.getOrNull() ?: defaultValue
    }

    inline fun <reified T> get(key: String): T? {
        val res = dataBuffer[key]
        if (res is T) return res
        return null
    }
}