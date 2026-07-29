package cn.coostack.cooparticlesapi.test.block.client

/**
 * 客户端测试方块界面使用的有界撤回历史。
 *
 * 历史只保存不可变快照；相同快照不会重复入栈，新的修改会清空重做栈。
 */
internal class TestControllerUndoHistory<T>(
    private val capacity: Int = DEFAULT_CAPACITY,
    initial: T? = null,
) {
    private val undoStates = ArrayDeque<T>()
    private val redoStates = ArrayDeque<T>()
    private var currentState: T? = initial

    val canUndo: Boolean
        get() = undoStates.isNotEmpty()

    val canRedo: Boolean
        get() = redoStates.isNotEmpty()

    fun seed(state: T) {
        if (currentState == null) currentState = state
    }

    fun record(state: T): Boolean {
        val current = currentState
        if (current == null) {
            currentState = state
            return false
        }
        if (current == state) return false
        undoStates.addLast(current)
        while (undoStates.size > capacity.coerceAtLeast(1)) undoStates.removeFirst()
        currentState = state
        redoStates.clear()
        return true
    }

    fun undo(): T? {
        if (undoStates.isEmpty()) return null
        val current = currentState ?: return null
        redoStates.addLast(current)
        currentState = undoStates.removeLast()
        return currentState
    }

    fun redo(): T? {
        if (redoStates.isEmpty()) return null
        val current = currentState ?: return null
        undoStates.addLast(current)
        while (undoStates.size > capacity.coerceAtLeast(1)) undoStates.removeFirst()
        currentState = redoStates.removeLast()
        return currentState
    }

    companion object {
        private const val DEFAULT_CAPACITY = 128
    }
}
