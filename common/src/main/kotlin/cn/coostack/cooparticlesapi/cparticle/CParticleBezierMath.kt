package cn.coostack.cooparticlesapi.cparticle

/**
 * 保持 CPU 贝塞尔求值与顶点着色器的固定迭代算法一致。
 *
 * 示例：标量曲线和 RGB 曲线都会先调用 [parameterAt]，再计算对应值。
 * 禁止：不要传入交叉的时间控制柄；应先通过 [requireMonotonicSegment] 校验。
 */
internal object CParticleBezierMath {
    /**
     * 一个标量关键帧的出入控制柄所占的 float 数量。
     *
     * 示例：顺序为 `outX, outValue, inX, inValue`。
     * 禁止：修改此值时必须同步修改 codec 和 shader 布局。
     */
    const val SCALAR_HANDLE_COMPONENTS = 4

    /**
     * CPU 与顶点着色器共用的二分迭代次数。
     *
     * 示例：10 次迭代可把贝塞尔参数定位到约 `1 / 1024` 的范围。
     * 禁止：CPU 和 GPU 的迭代次数不能不同。
     */
    const val SOLVE_ITERATIONS = 10

    /**
     * 检查一个曲线段是否具有单值且递增的时间映射。
     *
     * 示例：在 `0..1` 曲线段中，`outX=25`、`inX=-25` 是有效控制柄。
     * 禁止：控制柄交叉或越出曲线段会让 GPU 无法唯一选根。
     *
     * @param startTime 曲线段起点的归一化时间
     * @param outX 出控制柄的 X 偏移，单位为百分比点
     * @param endTime 曲线段终点的归一化时间
     * @param inX 入控制柄的 X 偏移，单位为百分比点
     * @throws IllegalArgumentException 如果曲线段或控制柄不单调
     */
    fun requireMonotonicSegment(startTime: Float, outX: Float, endTime: Float, inX: Float) {
        val firstControl = startTime + outX / 100f
        val secondControl = endTime + inX / 100f
        require(startTime < endTime) { "Bezier curve key times must be strictly increasing" }
        require(firstControl in startTime..endTime && secondControl in startTime..endTime) {
            "Bezier curve time handles must stay inside their segment"
        }
        require(firstControl <= secondControl) { "Bezier curve time handles must not cross" }
    }

    /**
     * 求出 X 坐标与 [time] 对应的贝塞尔参数。
     *
     * 示例：X 控制柄对称时，曲线段中点会得到约 `0.5` 的参数。
     * 禁止：接收用户提供的控制柄前必须调用 [requireMonotonicSegment]。
     *
     * @param time 目标归一化时间
     * @param startTime 曲线段起点时间
     * @param outX 出控制柄的 X 偏移，单位为百分比点
     * @param endTime 曲线段终点时间
     * @param inX 入控制柄的 X 偏移，单位为百分比点
     * @return `0..1` 范围内的三次贝塞尔参数
     */
    fun parameterAt(time: Float, startTime: Float, outX: Float, endTime: Float, inX: Float): Float {
        if (time <= startTime) return 0f
        if (time >= endTime) return 1f
        val firstControl = startTime + outX / 100f
        val secondControl = endTime + inX / 100f
        var low = 0f
        var high = 1f
        repeat(SOLVE_ITERATIONS) {
            val middle = (low + high) * 0.5f
            if (cubic(middle, startTime, firstControl, secondControl, endTime) < time) {
                low = middle
            } else {
                high = middle
            }
        }
        return (low + high) * 0.5f
    }

    /**
     * 计算三次贝塞尔曲线的一个分量。
     *
     * 示例：两个中间控制值相等时，可得到淡入淡出常用的平滑拱形。
     * 禁止：[parameter] 是贝塞尔参数，不是粒子的归一化年龄。
     *
     * @param parameter `0..1` 范围内的贝塞尔参数
     * @param first 第一个锚点分量
     * @param firstControl 出控制点分量
     * @param secondControl 入控制点分量
     * @param second 第二个锚点分量
     * @return 求值后的分量
     */
    fun cubic(
        parameter: Float,
        first: Float,
        firstControl: Float,
        secondControl: Float,
        second: Float,
    ): Float {
        val inverse = 1f - parameter
        val inverseSquared = inverse * inverse
        val parameterSquared = parameter * parameter
        return inverseSquared * inverse * first +
                3f * inverseSquared * parameter * firstControl +
                3f * inverse * parameterSquared * secondControl +
                parameterSquared * parameter * second
    }
}
