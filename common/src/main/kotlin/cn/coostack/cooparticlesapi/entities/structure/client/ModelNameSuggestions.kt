package cn.coostack.cooparticlesapi.entities.structure.client

import net.minecraft.commands.SharedSuggestionProvider

/** 结构名称的补全状态，沿用原版命令的单词前缀匹配，不依赖游戏窗口。
 * @property names 服务端给出的完整结构 ID 列表，不能由补全凭空生成新 ID
 */
class ModelNameSuggestions(private val names: List<String>) {
    /** 当前候选，按完整 ID 排序并去重。 */
    var matches = emptyList<String>()
        private set
    /** 当前候选下标，没有候选时保持为零。 */
    var selected = 0
        private set
    /** 第一次 Tab 接受当前项，后续 Tab 在同一候选组里循环。 */
    private var completed = false

    /** 输入变化时重新匹配，同时支持完整 ID 和省略命名空间的路径。 */
    fun update(input: String) {
        val prefix = input.lowercase()
        matches = names.asSequence().filter { name ->
            SharedSuggestionProvider.matchesSubStr(prefix, name.lowercase()) ||
                (':' !in prefix && SharedSuggestionProvider.matchesSubStr(prefix, name.substringAfter(':').lowercase()))
        }.distinct().sorted().toList()
        selected = 0
        completed = false
    }

    /** 循环选择候选，空列表无操作。 */
    fun move(delta: Int) {
        if (matches.isEmpty()) return
        selected = (selected + delta).mod(matches.size)
        completed = false
    }

    /** 接受当前候选，连续调用时向前或向后循环；无匹配时返回空。 */
    fun complete(backwards: Boolean): String? {
        if (matches.isEmpty()) return null
        if (completed || backwards) selected = (selected + if (backwards) -1 else 1).mod(matches.size)
        completed = true
        return matches[selected]
    }

    /** 鼠标接受指定行，不触发键盘循环规则。 */
    fun choose(index: Int): String? {
        if (index !in matches.indices) return null
        selected = index
        completed = true
        return matches[index]
    }
}
