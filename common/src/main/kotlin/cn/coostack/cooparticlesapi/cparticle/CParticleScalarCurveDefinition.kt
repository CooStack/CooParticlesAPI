package cn.coostack.cooparticlesapi.cparticle

/**
 * 以外观 descriptor 去重所需的布局保存一条不可变标量曲线。
 *
 * 示例：锚点和控制柄相同的两个粒子可复用同一个 descriptor ID。
 * 禁止：此定义成为 Map 的 key 后，不能再修改其中的列表。
 *
 * @property interpolation 相邻关键帧之间的插值方式
 * @property times 归一化关键帧时间
 * @property values 每个关键帧的标量值
 * @property handles 展平后的 `outX, outValue, inX, inValue`
 */
internal data class CParticleScalarCurveDefinition(
    val interpolation: CParticleCurveInterpolation,
    val times: List<Float>,
    val values: List<Float>,
    val handles: List<Float>,
) {
    /**
     * 返回有效标量关键帧数量。
     *
     * 示例：两个时间点描述一个曲线段。
     * 禁止：不能把控制柄分量数量当作关键帧数量。
     */
    val keyCount: Int
        get() = times.size
}
