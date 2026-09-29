package cn.coostack.cooparticlesapi.gpudata

import java.util.concurrent.ConcurrentHashMap

/**
 * # 共享图层读取视图
 *
 * RenderEntity 的 shader 与 CParticle 的 compute 读取**同一份** GL 缓冲。图层是逻辑资源，
 * 底层可能是一张显存缓冲，也可能被上层协议切成多段（例如路径图层把全部路径打包进一张图层）。
 * 本对象负责把“第几个元素 / 第几个 float”换算为稳定的读取下标，并给出 shader 需要的绑定点。
 *
 * ## 使用方式
 * shader 侧声明与 [binding] 相同的绑定点：
 * ```glsl
 * layout(std430, binding = 4) readonly buffer CooLayerBuffer { float data[]; };
 * ```
 * 然后按 [elementOffset] 之类的入口换算出的下标读取。
 *
 * ## 线程与生命周期
 * - [binding] 与全部查询入口都只是 CPU 侧的数值计算，可在任意线程调用。
 * - 真正的 GL 缓冲创建与数据写入只在客户端渲染线程发生。
 * - 视图不持有图层，也不增加引用计数；调用方释放在先会让 [isAlive] 变为 `false`。
 */
class CooGpuDataLayerView internal constructor(
    private val handle: CooGpuDataHandle,
    /** 该图层在共享 GL 缓冲中的着色器存储绑定点。 */
    val binding: Int,
    /** 图层布局；用于把元素下标换算为 float 下标。 */
    val layout: CooGpuDataLayout,
) {
    /** 逻辑图层句柄；可用于诊断与日志。 */
    val layerHandle: CooGpuDataHandle
        get() = handle

    /** 逻辑图层 ID；用于比较两个视图是否指向同一类资源。 */
    val layoutId: String
        get() = layout.id

    /** 图层当前是否仍然有效。 */
    val isAlive: Boolean
        get() = CooGpuDataRegistry.isAlive(handle)

    /** 图层当前有效元素个数；图层失效时返回 0。 */
    val elementCount: Int
        get() = CooGpuDataRegistry.layerOf(handle)?.elementCount ?: 0

    /**
     * 把元素下标换算成共享缓冲中的 float 下标。
     *
     * 图层布局的成分为 1 时（例如路径图层）该值与元素下标相同；其他布局会乘以成分数。
     *
     * @param element 元素下标
     * @return 共享缓冲中的 float 下标
     * @throws IllegalArgumentException 元素下标为负时抛出
     */
    fun elementOffset(element: Int): Int {
        require(element >= 0) { "Gpu data layer element index must be non-negative: $element" }
        return element * layout.componentCount
    }

    /**
     * 读取一个 float 成分。
     *
     * @param element 元素下标
     * @param component 成分下标
     * @return 该成分当前值；图层失效或下标越界时返回 `null`
     */
    fun readFloat(element: Int, component: Int): Float? {
        if (element < 0 || component < 0 || component >= layout.componentCount) return null
        val layer = CooGpuDataRegistry.layerOf(handle) ?: return null
        if (element >= layer.elementCount) return null
        return layer.data[layerOffset(layer, element, component)]
    }

    /**
     * 读取一个元素的前三个 float 成分，作为三维向量。
     *
     * @param element 元素下标
     * @return 三维向量；图层失效、越界或成分不足时返回 `null`
     */
    fun readVec3(element: Int): CooLayerVec3? {
        if (layout.componentCount < 3) return null
        val x = readFloat(element, 0) ?: return null
        val y = readFloat(element, 1) ?: return null
        val z = readFloat(element, 2) ?: return null
        return CooLayerVec3(x, y, z)
    }

    /**
     * 读取一个元素的前四个 float 成分。
     *
     * @param element 元素下标
     * @return 四维值；图层失效、越界或成分不足时返回 `null`
     */
    fun readVec4(element: Int): CooLayerVec4? {
        if (layout.componentCount < 4) return null
        val x = readFloat(element, 0) ?: return null
        val y = readFloat(element, 1) ?: return null
        val z = readFloat(element, 2) ?: return null
        val w = readFloat(element, 3) ?: return null
        return CooLayerVec4(x, y, z, w)
    }

    private fun layerOffset(layer: CooGpuDataLayer, element: Int, component: Int): Int =
        layer.layout.floatsFor(element) + component * (layout.componentCount / layer.layout.componentCount)

    companion object {
        private val views = ConcurrentHashMap<CooGpuDataHandle, CooGpuDataLayerView>()

        /**
         * 为一个已经注册的图层创建或取得读取视图。
         *
         * @param handle 图层句柄
         * @param binding 该图层所在的着色器存储绑定点
         * @return 读取视图；句柄已失效时返回 `null`
         */
        @JvmStatic
        fun of(handle: CooGpuDataHandle, binding: Int): CooGpuDataLayerView? {
            val layer = CooGpuDataRegistry.layerOf(handle) ?: return null
            return views.computeIfAbsent(handle) { CooGpuDataLayerView(handle, binding, layer.layout) }
        }

        /**
         * 直接按布局建视图，用于不经过注册表的只读访问。
         *
         * @param handle 图层句柄
         * @param binding 着色器存储绑定点
         * @param layout 图层布局
         * @return 读取视图
         */
        @JvmStatic
        fun of(handle: CooGpuDataHandle, binding: Int, layout: CooGpuDataLayout): CooGpuDataLayerView =
            CooGpuDataLayerView(handle, binding, layout)

        /** 清除缓存视图；图层释放或资源重载后调用。 */
        @JvmStatic
        fun clearCache() {
            views.clear()
        }
    }
}

/**
 * 图层中的三维值快照。
 *
 * @property x 第一个成分
 * @property y 第二个成分
 * @property z 第三个成分
 */
data class CooLayerVec3(val x: Float, val y: Float, val z: Float)

/**
 * 图层中的四维值快照。
 *
 * @property x 第一个成分
 * @property y 第二个成分
 * @property z 第三个成分
 * @property w 第四个成分
 */
data class CooLayerVec4(val x: Float, val y: Float, val z: Float, val w: Float)
