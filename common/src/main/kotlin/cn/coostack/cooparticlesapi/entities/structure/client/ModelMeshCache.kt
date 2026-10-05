package cn.coostack.cooparticlesapi.entities.structure.client

import java.util.IdentityHashMap

/**
 * 按对象身份管理可关闭的网格资源，以字节数和条目数限制缓存，替换及淘汰时立即释放。
 * 仅由渲染线程调用；键不会按实体数字 ID 合并。通过 [put] 移交资源所有权。
 * @param maxBytes 所有条目权重之和的上限，单位为字节
 * @param maxEntries 可同时保留的条目数上限
 */
internal class ModelMeshCache<K : Any, V : AutoCloseable>(private val maxBytes: Long, private val maxEntries: Int) {
    /** 按对象身份索引的独占资源，不使用弱引用代替显式关闭。 */
    private val entries = IdentityHashMap<K, Entry<V>>()
    /** 单调递增的访问序号，用于在容量不足时淘汰最久未访问的条目。 */
    private var sequence = 0L
    /** 当前资源权重之和，不包含驱动内部或共享索引缓冲开销。 */
    var bytes = 0L
        private set

    init {
        require(maxBytes > 0 && maxEntries > 0)
    }

    /** 查找并更新最近访问顺序；例如 `cache[entity]`，未命中返回空。 */
    operator fun get(key: K): V? = entries[key]?.also { it.access = ++sequence }?.value

    /**
     * 接管新资源，先关闭旧值及超预算的最旧条目；无法容纳的新资源也会关闭。
     * 示例：`cache.put(entity, mesh, mesh.bytes)`。
     * @param key 资源所属对象，按身份区分
     * @param value 所有权交给缓存的独立资源，不得重复插入同一个资源实例
     * @param weight 该资源占用的字节数，不得为负
     * @return 是否成功保留资源
     */
    fun put(key: K, value: V, weight: Long): Boolean {
        require(weight >= 0)
        remove(key)
        if (weight > maxBytes) {
            value.close()
            return false
        }
        while (entries.size >= maxEntries || bytes + weight > maxBytes) {
            remove(entries.entries.minBy { it.value.access }.key)
        }
        entries[key] = Entry(value, weight, ++sequence)
        bytes += weight
        return true
    }

    /** 删除并关闭指定对象的资源；例如实体卸载时调用 `cache.remove(entity)`，重复删除无副作用。 */
    fun remove(key: K) {
        val removed = entries.remove(key) ?: return
        bytes -= removed.weight
        removed.value.close()
    }

    /**
     * 访问当前条目，不改变最近访问顺序；回调不得增删本缓存。
     * 示例：`cache.forEach { _, mesh -> mesh.dirty = true }`。
     * @param action 接收所属对象与独占资源的同步操作
     */
    fun forEach(action: (K, V) -> Unit) {
        entries.forEach { (key, entry) -> action(key, entry.value) }
    }

    /**
     * 关闭满足条件的条目，适用于世界切换或长时间未使用的资源。
     * 示例：`cache.removeIf { entity, _ -> entity.isRemoved }`。
     * @param predicate 返回真时移除对应资源，不得在回调中增删本缓存
     */
    fun removeIf(predicate: (K, V) -> Boolean) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val owned = entry.value
            if (!predicate(entry.key, owned.value)) continue
            iterator.remove()
            bytes -= owned.weight
            owned.value.close()
        }
    }

    /** 关闭所有资源并清空计数；例如退出世界时调用 `cache.clear()`，可以重复调用。 */
    fun clear() = removeIf { _, _ -> true }

    /**
     * 单个缓存资源及其淘汰元数据。
     * @property value 独占的可关闭资源
     * @property weight 资源字节权重
     * @property access 最近访问序号
     */
    private class Entry<V>(val value: V, val weight: Long, var access: Long)
}
