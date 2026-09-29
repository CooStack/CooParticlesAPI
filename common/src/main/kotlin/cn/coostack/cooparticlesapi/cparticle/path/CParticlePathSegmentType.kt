package cn.coostack.cooparticlesapi.cparticle.path

/**
 * # 路径段类型
 *
 * 一条路径的所有段共用同一种类型，避免同一资源内混合线性段与贝塞尔段时，控制柄语义出现歧义。
 * [wireValue] 属于 GPU/CPU 共用的路径图层 ABI，写入图层表头，不能按枚举顺序重新编号。
 */
enum class CParticlePathSegmentType(
    /** 写入路径图层表头的数值标识。 */
    val wireValue: Int,
) {
    /**
     * 分段线性。
     *
     * 每段是控制点之间的直线，忽略入/出控制柄；折线拐角处的切线由相邻段的平均方向决定，
     * 不会因为单侧差分产生瞬时翻转。
     */
    LINEAR(0),

    /**
     * 空间三次贝塞尔。
     *
     * 第 `i` 段使用 `P(i)` 作为起点、`P(i) + outHandle(i)` 作为第一控制点、
     * `P(i+1) + inHandle(i+1)` 作为第二控制点、`P(i+1)` 作为终点。
     * 两侧控制柄共线且等长时该点是平滑连接点，不共线时形成尖角。
     */
    BEZIER(1),
}
