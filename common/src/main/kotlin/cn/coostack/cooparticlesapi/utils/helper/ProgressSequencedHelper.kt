package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.network.particle.style.SequencedParticleStyle
import cn.coostack.cooparticlesapi.particles.Controlable
import kotlin.math.*

abstract class ProgressSequencedHelper(var maxCount: Int, var progressMaxTick: Int) : ParticleHelper {
    var current = 0
        protected set
    protected var step = maxCount.toDouble() / progressMaxTick
    private var remainder = 0.0 // 用于处理非整除情况

    init {
        require(maxCount > 0) { "maxCount 必须大于 0" }
        require(progressMaxTick > 0) { "progressMaxTick 必须大于 0" }
        recalculateStep()
    }

    // 重新计算步长（当参数变化时调用）
    fun recalculateStep(): ProgressSequencedHelper {
        step = maxCount.toDouble() / progressMaxTick
        remainder = 0.0
        return this
    }

    // 直接设置进度百分比（0.0-1.0）
    fun setProgress(percent: Double) {
        val clamped = percent.coerceIn(0.0, 1.0)
        val targetTick = (clamped * progressMaxTick).roundToInt()
        doProgressTo(targetTick)
    }

    // 正向逐步增加粒子（生成）
    fun increaseProgress() {
        if (over()) return

        getLoadedStyle() ?: return
        val actualStep = calculateActualStep(true)

        if (actualStep > 0) {
            addMultiple(actualStep)
            current++
        }
    }

    // 反向逐步减少粒子（回收）
    fun decreaseProgress() {
        if (isZero()) return

        getLoadedStyle() ?: return
        val actualStep = calculateActualStep(false)

        if (actualStep > 0) {
            removeMultiple(actualStep)
            current--
        }
    }

    // 跳转到指定刻度
    fun doProgressTo(targetTick: Int) {
        getLoadedStyle() ?: return
        val target = targetTick.coerceIn(0, progressMaxTick)

        when {
            target > current -> {
                val addCount = calculateTotalStep(current, target)
                addMultiple(addCount)
            }

            target < current -> {
                val removeCount = calculateTotalStep(target, current)
                removeMultiple(removeCount)
            }
        }
        current = target
    }

    // 状态判断
    fun over(): Boolean = current >= progressMaxTick
    fun isZero(): Boolean = current <= 0

    /* 核心计算方法 */
    // 带余数计算的步长处理
    private fun calculateActualStep(isAdding: Boolean): Int {
        val raw = step + remainder
        val integerPart = raw.toInt()
        remainder = raw - integerPart

        return when {
            isAdding -> integerPart
            else -> integerPart
        }.coerceAtLeast(1)
    }

    // 计算两个刻度之间的总粒子数
    private fun calculateTotalStep(from: Int, to: Int): Int {
        val delta = to - from
        return (delta * step).roundToInt()
    }

    abstract fun addMultiple(count: Int)

    abstract fun removeMultiple(count: Int)

    /* 需要子类实现的抽象方法 */
    // 获取关联的粒子样式
    abstract fun getLoadedStyle(): Controlable<*>?

    // 执行批量状态修改（用于精确控制）
    protected abstract fun changeStatusBatch(indexes: IntArray, status: Boolean)
}