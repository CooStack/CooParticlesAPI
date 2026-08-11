package cn.coostack.cooparticlesapi.renderer.terrain.sodium

import java.util.IdentityHashMap

internal class CooIdentityResourceStore<K : Any, R : Any> {
    private val entries = IdentityHashMap<K, List<R>>()

    @Synchronized
    /**
     * 执行 `CooIdentityResourceStore` 定义的 `replace` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`replace(key = key, resources = resources, release = release)`。
     *
     * @param key 用于查找、绑定或记录目标的名称
     *
     * @param resources 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @param release 在当前生命周期或数据上下文中执行的回调
     */
    fun replace(key: K, resources: List<R>, release: (R) -> Unit) {
        entries.remove(key)?.forEach(release)
        if (resources.isNotEmpty()) {
            entries[key] = resources.toList()
        }
    }

    @Synchronized
    /**
     * 从 `CooIdentityResourceStore` 的 `take` 管理范围移除目标，后续调用不再使用对应绑定或资源。
     *
     * 示例：`take(key = key)`。
     *
     * @param key 用于查找、绑定或记录目标的名称
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun take(key: K): List<R>? {
        return entries.remove(key)
    }

    @Synchronized
    /**
     * 从 `CooIdentityResourceStore` 当前维护的状态中读取 `get` 结果，不创建新的渲染资源。
     *
     * 示例：`get(key = key)`。
     *
     * @param key 用于查找、绑定或记录目标的名称
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun get(key: K): List<R>? {
        return entries[key]?.toList()
    }

    @Synchronized
    /**
     * 释放 `CooIdentityResourceStore` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release(key = key, release = release)`。
     *
     * @param key 用于查找、绑定或记录目标的名称
     *
     * @param release 在当前生命周期或数据上下文中执行的回调
     */
    fun release(key: K, release: (R) -> Unit) {
        entries.remove(key)?.forEach(release)
    }

    @Synchronized
    /**
     * 清理 `CooIdentityResourceStore` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear(release = release)`。
     *
     * @param release 在当前生命周期或数据上下文中执行的回调
     */
    fun clear(release: (R) -> Unit) {
        entries.values.flatten().forEach(release)
        entries.clear()
    }

    @Synchronized
    /**
     * 从 `CooIdentityResourceStore` 当前维护的状态中读取 `size` 结果，不创建新的渲染资源。
     *
     * 示例：`size()`。
     *
     * @return 当前集合、资源或实例的数量
     */
    fun size(): Int {
        return entries.size
    }
}
