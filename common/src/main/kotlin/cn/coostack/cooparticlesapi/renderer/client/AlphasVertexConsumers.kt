package cn.coostack.cooparticlesapi.renderer.client

import com.mojang.blaze3d.vertex.VertexConsumer

class AlphasVertexConsumers(var alpha: Int, val consumer: VertexConsumer) : VertexConsumer {
    /**
     * 把输入对象加入 `AlphasVertexConsumers` 的 `addVertex` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`addVertex(x = x, y = y, z = z)`。
     *
     * @param x 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param y 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param z 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun addVertex(
        x: Float,
        y: Float,
        z: Float
    ): VertexConsumer {
        return consumer.addVertex(x, y, z)
    }

    /**
     * 更新 `AlphasVertexConsumers` 的 `setColor` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setColor(red = red, green = green, blue = blue, alpha = alpha)`。
     *
     * @param red 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param green 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param blue 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param alpha 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun setColor(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ): VertexConsumer {
        consumer.setColor(red, green, blue, this.alpha)
        return this
    }

    /**
     * 更新 `AlphasVertexConsumers` 的 `setUv` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setUv(u = u, v = v)`。
     *
     * @param u 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param v 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun setUv(u: Float, v: Float): VertexConsumer {
        consumer.setUv(u, v)
        return this
    }

    /**
     * 更新 `AlphasVertexConsumers` 的 `setUv1` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setUv1(u = u, v = v)`。
     *
     * @param u 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param v 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun setUv1(u: Int, v: Int): VertexConsumer {
        consumer.setUv1(u, v)
        return this
    }

    /**
     * 更新 `AlphasVertexConsumers` 的 `setUv2` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setUv2(u = u, v = v)`。
     *
     * @param u 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @param v 本次计算使用的坐标、颜色、比例、范围或强度分量
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun setUv2(u: Int, v: Int): VertexConsumer {
        consumer.setUv2(u, v)
        return this
    }

    /**
     * 更新 `AlphasVertexConsumers` 的 `setNormal` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`setNormal(normalX = normalX, normalY = normalY, normalZ = normalZ)`。
     *
     * @param normalX 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param normalY 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param normalZ 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun setNormal(
        normalX: Float,
        normalY: Float,
        normalZ: Float
    ): VertexConsumer {
        consumer.setNormal(normalX, normalY, normalZ)
        return this
    }

}