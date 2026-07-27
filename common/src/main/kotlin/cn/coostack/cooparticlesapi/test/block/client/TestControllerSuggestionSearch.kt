package cn.coostack.cooparticlesapi.test.block.client

/**
 * 返回与输入匹配的测试控制器补全项，前缀匹配优先于普通包含匹配。
 *
 * 示例：输入 `leaf` 时，`LeafBurst` 会排在 `SmokeLeaf` 前面。
 * 禁止用此函数做模糊纠错；不包含输入文本的候选不会返回。
 *
 * @param candidates 保持注册顺序的原始候选
 * @param input 用户输入，匹配前会去除首尾空白
 * @return 已过滤并按匹配优先级排列的候选
 */
internal fun searchTestControllerSuggestions(candidates: List<String>, input: String): List<String> {
    val query = input.trim()
    if (query.isBlank()) {
        return candidates
    }
    return candidates.asSequence()
        .filterNot { candidate -> candidate.equals(query, ignoreCase = true) }
        .filter { candidate -> candidate.contains(query, ignoreCase = true) }
        .sortedBy { candidate -> if (candidate.startsWith(query, ignoreCase = true)) 0 else 1 }
        .toList()
}
