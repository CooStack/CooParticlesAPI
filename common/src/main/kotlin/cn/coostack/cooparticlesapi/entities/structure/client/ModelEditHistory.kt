package cn.coostack.cooparticlesapi.entities.structure.client

/**
 * 当前编辑会话的有界撤销记录，不发送网络请求或撤回已经提交的世界操作。
 * @param T 调用方提供的独立状态快照；加入后不得修改
 */
internal class ModelEditHistory<T> {
    /** 按时间保存的独立快照，最多保留一百步修改和一个初始状态。 */
    private val states = mutableListOf<T>()
    /** 当前快照下标，负一表示尚未初始化。 */
    private var index = -1
    /** 当前快照，仅允许调用方读取，不可原地修改。 */
    val current: T? get() = states.getOrNull(index)

    /** 记录一个完整状态；撤销后继续编辑时删除旧的重做分支。 */
    fun record(state: T) {
        if (current == state) return
        while (states.size > index + 1) states.removeAt(states.lastIndex)
        states.add(state)
        if (states.size > 101) states.removeAt(0)
        index = states.lastIndex
    }

    /** 合并服务端自动补全的中心等派生值，不生成用户未执行的撤销步骤。 */
    fun replaceCurrent(state: T) {
        if (index < 0) record(state) else states[index] = state
    }

    fun undo(): T? {
        if (index <= 0) return null
        return states[--index]
    }

    fun redo(): T? {
        if (index >= states.lastIndex) return null
        return states[++index]
    }
}
