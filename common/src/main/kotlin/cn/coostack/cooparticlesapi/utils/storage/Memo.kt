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

    fun setMemoValue(memo: T): Memo<T> {
        this.memo = memo
        return this
    }

    fun resetMemo(): Memo<T> {
        memo = supplier.get()
        return this
    }

}