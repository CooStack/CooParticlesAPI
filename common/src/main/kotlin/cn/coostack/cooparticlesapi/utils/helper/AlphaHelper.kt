package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.particles.Controlable
import kotlin.math.*

abstract class AlphaHelper(var minAlpha: Double, var maxAlpha: Double, var alphaTick: Int) : ParticleHelper {
    var current = 0
        protected set
    protected var step = abs(maxAlpha - minAlpha) / alphaTick

    init {
        // 确保数值范围正确
        val temp = min(minAlpha, maxAlpha)
        maxAlpha = max(minAlpha, maxAlpha)
        minAlpha = temp
    }

    // 重新计算步长（当参数变化时调用）
    fun recalculateStep(): AlphaHelper {
        val temp = min(minAlpha, maxAlpha)
        maxAlpha = max(minAlpha, maxAlpha)
        minAlpha = temp
        step = abs(maxAlpha - minAlpha) / alphaTick
        return this
    }

    // 直接设置透明度时同步更新状态
    fun toggleAlpha(alpha: Double) {
        when {
            alpha <= minAlpha -> resetAlphaMin()
            alpha >= maxAlpha -> {
                current = alphaTick
                resetAlphaMax()
            }

            else -> {
                val point = alpha - minAlpha
                val tick = (point / step).roundToInt()
                current = tick
                doAlphaTo(current)
            }
        }
    }

    // 重置到最小透明度
    fun resetAlphaMin() {
        getLoadedGroup()?.let {
            current = 0
            setAlpha(minAlpha)
        }
    }

    // 重置到最大透明度
    fun resetAlphaMax() {
        getLoadedGroup()?.let {
            current = alphaTick
            setAlpha(maxAlpha)
        }
    }

    // 按刻度步进设置透明度
    fun doAlphaTo(current: Int) {
        getLoadedGroup()?.let {
            val enter = current.coerceAtLeast(0)
            this.current = enter
            when {
                current >= alphaTick -> resetAlphaMax()
                current <= 0 -> resetAlphaMin()
                else -> setAlpha(minAlpha + step * enter)
            }
        }
    }

    // 正向逐步增加透明度（更不透明）
    fun increaseAlpha() {
        getLoadedGroup()?.takeUnless { over() }?.let {
            current++
            setAlpha(minAlpha + step * current)
        }
    }

    // 反向逐步降低透明度（更透明）
    fun decreaseAlpha() {
        getLoadedGroup()?.takeUnless { isZero() }?.let {
            current--
            setAlpha(minAlpha + step * current)
        }
    }

    // 状态判断
    fun over(): Boolean = alphaTick <= current
    fun isZero(): Boolean = current <= 0

    /* 需要子类实现的抽象方法 */
    // 获取关联的可控对象
    abstract fun getLoadedGroup(): Controlable<*>?

    // 获取当前实际透明度值
    abstract fun getCurrentAlpha(): Double

    // 执行透明度设置操作
    abstract fun setAlpha(alpha: Double)
}