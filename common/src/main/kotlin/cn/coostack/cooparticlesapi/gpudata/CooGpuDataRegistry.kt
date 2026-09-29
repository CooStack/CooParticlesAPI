package cn.coostack.cooparticlesapi.gpudata

import java.util.concurrent.atomic.AtomicInteger

/**
 * # GPU 数据图层注册表
 *
 * 统一负责图层的稳定逻辑 ID、世代、有效性检查与引用计数。使用端只持有 [CooGpuDataHandle]，
 * 每次解析都要经过 [layerOf]，因此资源释放后旧句柄不会静默指向被复用的新资源。
 *
 * ## 为什么需要引用计数
 * 同一个图层可以被多个 Command、多个 emitter 和多个 RenderEntity 同时引用。只要还有引用者，
 * 图层就必须保持有效；最后一个引用者释放后才允许回收并让 ID 可被复用。
 *
 * ## 线程约定
 * - 声明式入口（[allocate]、[acquire]、[release]）只做 CPU 数据结构操作，服务端与客户端都可调用。
 * - 图层内容（[CooGpuDataLayer]）只在客户端渲染线程写入，服务端不得写入图层数据。
 * - 本对象**不持有**任何 GL 句柄；绑定与上传由 [CooGpuDataGlBuffer] 在渲染线程完成。
 */
object CooGpuDataRegistry {
    private val nextId = AtomicInteger(1)

    private val entries = LinkedHashMap<Int, Entry>()

    /**
     * 可复用的逻辑 ID。
     *
     * ID 只有在引用计数归零后才回到这里，因此复用不会发生在仍有使用者的图层上。
     */
    private val freeIds = ArrayDeque<Int>()

    /**
     * 每个逻辑 ID 最近一次分配的世代号。
     *
     * 条目被回收后本表仍然保留，新的 [allocate] 才能给出递增的世代，
     * 让 [layerOf] 的世代比较真正区分“旧句柄”与“复用后的新资源”。
     * 只保留已释放 ID 的世代；正在使用的世代由 [Entry.handle] 提供。
     */
    private val releasedGenerations = HashMap<Int, Int>()

    private class Entry(
        val handle: CooGpuDataHandle,
        val layer: CooGpuDataLayer,
    ) {
        var references: Int = 0
    }

    /**
     * 创建一个新的图层并返回引用计数为 1 的句柄。
     *
     * 示例：新建路径时调用一次，随后把它交给命令或 RenderEntity 使用。
     * 禁止：调用方不得重复创建同一份语义资源；共享路径应把同一个句柄传给多个使用者。
     *
     * @param layout 图层布局
     * @param initialElements 初始元素容量，0 表示按需扩容
     * @return 新的稳定句柄，初始引用计数为 1
     */
    fun allocate(layout: CooGpuDataLayout, initialElements: Int = 0): CooGpuDataHandle {
        val id = freeIds.removeLastOrNull() ?: nextId.getAndIncrement()
        val previousGeneration = entries[id]?.handle?.generation ?: releasedGenerations.remove(id) ?: -1
        val handle = CooGpuDataHandle(id, previousGeneration + 1)
        // 新图层从“被调用方持有一次引用”开始：否则 layerOf 会认为它无效，
        // 而且第一次 release 会变成空操作，调用方无法回收自己创建的资源。
        entries[id] = Entry(handle, CooGpuDataLayer(layout, initialElements)).also { it.references = 1 }
        return handle
    }

    /**
     * 解析句柄对应的图层，并做世代与有效性检查。
     *
     * @param handle 调用方持有的句柄
     * @return 仍然有效的图层；句柄过期或资源已释放时返回 `null`
     */
    fun layerOf(handle: CooGpuDataHandle): CooGpuDataLayer? {
        val entry = entries[handle.id] ?: return null
        if (entry.handle != handle) return null
        if (entry.references <= 0) return null
        return entry.layer
    }

    /**
     * 解析句柄对应的图层，缺失时抛出可用于定位的错误。
     *
     * 示例：命令在打包阶段解析自己引用的路径图层。
     * 禁止：不要在热路径逐粒子调用；应在每次 tick 打包时解析一次并复用结果。
     *
     * @param handle 调用方持有的句柄
     * @return 仍然有效的图层
     * @throws IllegalStateException 句柄过期或资源已释放时抛出
     */
    fun requireLayer(handle: CooGpuDataHandle): CooGpuDataLayer =
        layerOf(handle) ?: error("Gpu data layer handle is stale or released: $handle")

    /** 句柄当前是否仍然有效。 */
    fun isAlive(handle: CooGpuDataHandle): Boolean = layerOf(handle) != null

    /**
     * 为一个新使用者增加引用。
     *
     * 示例：第二个 RenderEntity 复用同一条路径时调用。
     * 禁止：同一个使用者重复调用而没有对应的 [release]，会造成永久泄漏。
     *
     * @param handle 要增加的引用
     * @return 引用成功时返回 `true`；句柄已失效时返回 `false`
     */
    fun acquire(handle: CooGpuDataHandle): Boolean {
        val entry = entries[handle.id] ?: return false
        if (entry.handle != handle) return false
        if (entry.references <= 0) return false
        entry.references++
        return true
    }

    /**
     * 释放一个使用者持有的引用。
     *
     * 引用计数归零时图层数据被清空，句柄世代被记录下来等待 ID 复用；同一世代下再次
     * [acquire] 会失败，必须重新 [allocate] 取得新世代。
     *
     * @param handle 要释放的引用
     * @return 本次释放让引用计数归零时返回 `true`
     */
    fun release(handle: CooGpuDataHandle): Boolean {
        val entry = entries[handle.id] ?: return false
        if (entry.handle != handle) return false
        if (entry.references <= 0) return false
        entry.references--
        if (entry.references > 0) return false
        entry.layer.release()
        releasedGenerations[handle.id] = handle.generation
        entries.remove(handle.id)
        freeIds.addLast(handle.id)
        return true
    }

    /** 当前仍在使用的图层数量。 */
    fun liveLayerCount(): Int = entries.size

    /**
     * 强制释放全部图层。
     *
     * 示例：客户端断开连接、切换世界或资源完全重载时调用。
     * 禁止：不要在有存活命令仍可能解析旧句柄时调用；释放后这些句柄会解析为 `null`。
     */
    fun clear() {
        entries.values.forEach { it.layer.release() }
        entries.clear()
        freeIds.clear()
        releasedGenerations.clear()
    }

    /** 供诊断使用的当前活跃句柄快照。 */
    internal fun activeHandles(): List<CooGpuDataHandle> = entries.values.map { it.handle }
}
