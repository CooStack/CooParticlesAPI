package cn.coostack.cooparticlesapi.utils.presets

import cn.coostack.cooparticlesapi.utils.builder.FourierSeriesBuilder

/**
 * 这里的所有预设都需要手动设置count
 */
object FourierPresets {
    /**
     * r = 7
     * 5边形
     */
    fun pentagon(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, 2.0, 0.0)
        .addFourier(1.0, -8.0, 0.0)
        .addFourier(3.0, 2.0, 0.0)

    /**
     * r = 7
     * 三叶草
     */
    fun clover(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, 2.0, 0.0)
        .addFourier(1.0, -8.0, 0.0)
        .addFourier(-3.0, 2.0, 0.0)
        .addFourier(6.0, -2.0, 0.0)

    /**
     * 边角半径为12
     * 回旋彪
     */
    fun boomerang(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, 1.0, 0.0)
        .addFourier(7.0, -2.0, 0.0)
        .addFourier(2.0, 4.0, 0.0)

    /**
     * 半径为12
     * 4角符文
     */
    fun runesOnAllSides(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, -1.0, 0.0)
        .addFourier(7.0, -5.0, 0.0)
        .addFourier(2.0, 11.0, 0.0)

    /**
     * r = 12
     * 中国结
     */
    fun knot(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, -1.0, 0.0)
        .addFourier(0.0, -5.0, 0.0)
        .addFourier(4.0, 11.0, 0.0)
        .addFourier(4.0, -4.0, 0.0)

    /**
     * r = 12
     * 圆内三角
     */
    fun circlesAndTriangles(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, -1.0, 0.0)
        .addFourier(0.0, -5.0, 0.0)
        .addFourier(4.0, 11.0, 0.0)
        .addFourier(5.0, 2.0, 0.0)

    /**
     * 四边是对称的蝴蝶结
     * r = 13
     */
    fun bowsOnAllSides(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(3.0, -1.0, 0.0)
        .addFourier(-3.0, -5.0, 0.0)
        .addFourier(4.0, 11.0, 0.0)
        .addFourier(5.0, 3.0, 0.0)
        .addFourier(-3.0, -5.0, 0.0)
        .addFourier(1.0, 3.0, 0.0)

    /**
     * r = 4
     * 菱形
     */
    fun rhombic(): FourierSeriesBuilder = FourierSeriesBuilder()
        .addFourier(-3.0, 1.0, 0.0)
        .addFourier(-9.0, -3.0, 0.0)
        .addFourier(8.0, -3.0, 0.0)
}