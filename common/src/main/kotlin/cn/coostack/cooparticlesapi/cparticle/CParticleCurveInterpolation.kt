package cn.coostack.cooparticlesapi.cparticle

/**
 * 指定 CParticle 曲线在相邻关键帧之间使用的插值方式。
 *
 * 示例：[CUBIC_BEZIER] 会保留控制柄，并在 CPU 与 GPU 上按相同规则求值。
 * 禁止：不要持久化 [ordinal]；网络数据使用稳定的 [wireId]。
 *
 * @property wireId 曲线 codec 与 GPU descriptor 共用的稳定编号
 */
enum class CParticleCurveInterpolation(internal val wireId: Int) {
    /**
     * 用直线连接相邻关键帧。
     *
     * 示例：`CParticleCurve.linear(0f, 1f)` 使用此模式。
     * 禁止：控制柄不会影响线性曲线。
     */
    LINEAR(0),

    /**
     * 将相邻关键帧作为三次贝塞尔曲线段求值。
     *
     * 示例：`CParticleCurve.bezier(...)` 使用此模式。
     * 禁止：时间控制柄不能交叉，因为 GPU 求值要求 X 单调。
     */
    CUBIC_BEZIER(1),
    ;

    /**
     * 根据稳定编号解析曲线插值方式。
     *
     * 示例：codec 读取扩展曲线 payload 后调用 [fromWireId]。
     * 禁止：未知编号不能回退成线性插值。
     */
    companion object {
        /**
         * 返回序列化编号对应的插值方式。
         *
         * 示例：`fromWireId(1)` 返回 [CUBIC_BEZIER]。
         * 禁止：未知编号会抛出异常，不会猜测插值方式。
         *
         * @param wireId 序列化后的插值编号
         * @return 对应的插值方式
         * @throws IllegalArgumentException 如果 [wireId] 未定义
         */
        internal fun fromWireId(wireId: Int): CParticleCurveInterpolation =
            entries.firstOrNull { it.wireId == wireId }
                ?: throw IllegalArgumentException("unknown CParticle curve interpolation: $wireId")
    }
}
