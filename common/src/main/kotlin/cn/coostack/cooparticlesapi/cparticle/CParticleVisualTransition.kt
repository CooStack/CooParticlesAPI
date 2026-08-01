package cn.coostack.cooparticlesapi.cparticle

import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * 保存一段系统级 GPU 外观过渡及其起始时间。
 *
 * 示例：renderer 通过 [progressAt] 取得当前 alpha、scale 和颜色插值进度。
 * 禁止：不要把 [scaleCurve] 当作粒子的基础尺寸。
 *
 * @property startTick 开始播放的系统 tick
 * @property durationTicks 过渡时长，单位 tick
 * @property alphaCurve 不透明度曲线；视觉过渡将其作为倍率，独立 alpha 过渡将其作为实例 alpha 覆盖值
 * @property scaleCurve 等比缩放倍率曲线
 * @property mode 过渡结束后的行为
 */
internal class CParticleVisualTransition(
    val startTick: Float,
    val durationTicks: Float,
    val alphaCurve: CParticleCurve?,
    val scaleCurve: CParticleCurve?,
    colorFrom: Vector3fc?,
    colorTo: Vector3fc?,
    val mode: CParticleTransitionMode,
) {
    val hasColor = colorFrom != null
    val colorFrom = colorFrom?.let(::Vector3f) ?: Vector3f()
    val colorTo = colorTo?.let(::Vector3f) ?: Vector3f()

    /**
     * 判断一组参数是否与当前过渡完全一致。
     *
     * 示例：重复提交相同参数时可保持现有播放进度。
     * 禁止：不要只比较曲线对象引用，等值曲线也应匹配。
     *
     * @param durationTicks 候选时长
     * @param alphaCurve 候选不透明度曲线
     * @param scaleCurve 候选等比缩放倍率曲线
     * @param colorFrom 候选起始颜色
     * @param colorTo 候选结束颜色
     * @param mode 候选结束行为
     * @return 全部参数相同时返回 `true`
     */
    fun matches(
        durationTicks: Float,
        alphaCurve: CParticleCurve?,
        scaleCurve: CParticleCurve?,
        colorFrom: Vector3fc?,
        colorTo: Vector3fc?,
        mode: CParticleTransitionMode,
    ): Boolean {
        if (this.durationTicks != durationTicks || this.mode != mode) return false
        if (!sameCurve(this.alphaCurve, alphaCurve) || !sameCurve(this.scaleCurve, scaleCurve)) return false
        if (hasColor != (colorFrom != null)) return false
        if (!hasColor) return true
        return sameColor(this.colorFrom, colorFrom!!) && sameColor(this.colorTo, colorTo!!)
    }

    fun progressAt(systemTime: Float): Float? {
        val normalized = (systemTime - startTick) / durationTicks
        return when (mode) {
            CParticleTransitionMode.HOLD_END -> normalized.coerceIn(0f, 1f)
            CParticleTransitionMode.RESET -> normalized.takeIf { it < 1f }?.coerceAtLeast(0f)
            CParticleTransitionMode.LOOP -> normalized.coerceAtLeast(0f) % 1f
        }
    }

    private fun sameCurve(first: CParticleCurve?, second: CParticleCurve?): Boolean {
        if (first === second) return true
        if (first == null || second == null || first.keyCount != second.keyCount) return false
        return first.interpolation == second.interpolation &&
                first.packedData.contentEquals(second.packedData) &&
                first.packedHandleData.contentEquals(second.packedHandleData)
    }

    private fun sameColor(first: Vector3fc, second: Vector3fc): Boolean {
        return first.x() == second.x() && first.y() == second.y() && first.z() == second.z()
    }
}
