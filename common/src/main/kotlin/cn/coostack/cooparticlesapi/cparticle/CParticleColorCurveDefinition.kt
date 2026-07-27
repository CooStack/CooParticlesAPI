package cn.coostack.cooparticlesapi.cparticle

/**
 * 以外观 descriptor 去重所需的布局保存一条不可变 RGB 曲线。
 *
 * 示例：注册前会从公开曲线复制锚点和控制柄数据。
 * 禁止：此定义成为 Map 的 key 后，不能再修改其中的列表。
 *
 * @property interpolation 相邻关键帧之间的插值方式
 * @property entries 展平后的 `time, red, green, blue` 锚点
 * @property outHandles 展平后的 `outX, outR, outG, outB` 控制柄
 * @property inHandles 展平后的 `inX, inR, inG, inB` 控制柄
 */
internal data class CParticleColorCurveDefinition(
    val interpolation: CParticleCurveInterpolation,
    val entries: List<Float>,
    val outHandles: List<Float>,
    val inHandles: List<Float>,
) {
    /**
     * 返回有效 RGB 关键帧数量。
     *
     * 示例：8 个锚点 float 表示两个颜色关键帧。
     * 禁止：不能把颜色值分量分别计为关键帧。
     */
    val keyCount: Int
        get() = entries.size / ENTRY_SIZE

    /**
     * 定义一个打包 RGB 锚点或控制柄的固定分量数。
     *
     * 示例：每项先保存时间或 X，再保存三个 RGB 分量。
     * 禁止：修改此布局时必须同步修改 shader。
     */
    companion object {
        /** 一个打包 RGB 锚点或控制柄占用的 float 数量。 */
        const val ENTRY_SIZE = 4
    }
}
