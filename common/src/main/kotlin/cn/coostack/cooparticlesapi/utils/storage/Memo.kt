package cn.coostack.cooparticlesapi.utils.storage

import java.util.function.Supplier

/**
 * 备忘录
 */
class Memo<T>(val supplier: Supplier<T>) {
    private var memo: T? = null
    fun get(): T {
        if (memo == null) {
            resetMemo()
        }
        return memo!!
    }

    /**
     * 读取已缓存的值，不触发 [supplier] 计算。
     *
     * 调试渲染这类旁路读取只能观察逻辑线程算好的结果，绝不能在渲染线程触发计算并写回缓存。
     */
    fun peek(): T? = memo

    fun setMemoValue(memo: T): Memo<T> {
        this.memo = memo
        return this
    }

    fun resetMemo(): Memo<T> {
        memo = supplier.get()
        return this
    }

}