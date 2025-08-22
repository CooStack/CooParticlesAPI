package cn.coostack.cooparticlesapi.utils

class CircularQueue<T>(val capacity: Int) : Iterable<T> {
    private val data = arrayOfNulls<Any>(capacity)
    private var size = 0
    private var head = 0

    class CircularIterator<T>(private val queue: CircularQueue<T>) : Iterator<T> {
        private var current = 0
        override fun next(): T {
            return queue[current++]
        }

        override fun hasNext(): Boolean {
            return queue.size > current
        }

    }

    fun addFirst(value: T) {
        if (size < capacity) {
            data[head++] = value
            size++
            return
        }
        // 这里需要进行位移 head >= capacity
        head %= capacity //会覆盖掉旧的值
        data[head++] = value
    }

    operator fun get(index: Int): T {
        if (index > size) throw IndexOutOfBoundsException("超过你设定的capacity或者你还没输入那么多元素 size: $size, index: $index")
        val i = if (size < capacity) index else (index + head) % capacity
        return data[i] as T
    }

    /**
     * 全是null
     */
    fun empty(): Boolean {
        return notNullSize() == 0
    }

    fun notNullSize(): Int = size
    override fun iterator(): Iterator<T> {
        return CircularIterator(this)
    }

}