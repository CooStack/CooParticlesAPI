package cn.coostack.cooparticlesapi.test.block.client

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 覆盖测试控制器补全候选的筛选顺序。
 *
 * 示例：输入 `leaf` 时，前缀匹配排在普通包含匹配之前。
 * 禁止用这组测试推断 GUI 绘制行为；这里只检查候选列表。
 */
class TestControllerSuggestionSearchTest {
    /**
     * 验证搜索忽略大小写，并保留前缀匹配的最高优先级。
     *
     * 示例：`LeafBurst` 应排在 `SmokeLeaf` 前面。
     * 禁止把与输入完全相同的候选再次显示为补全项。
     */
    @Test
    fun `prefix matches appear before contains matches`() {
        val candidates = listOf("SmokeLeaf", "LeafBurst", "LeafTrail", "DarkLeaf", "Leaf", "Smoke")

        assertEquals(
            listOf("LeafBurst", "LeafTrail", "SmokeLeaf", "DarkLeaf"),
            searchTestControllerSuggestions(candidates, "LEAF")
        )
    }

    /**
     * 验证空输入仍按注册顺序展示全部候选。
     *
     * 示例：`Alpha, Beta` 应原样返回。
     * 禁止在用户尚未输入时擅自重排候选。
     */
    @Test
    fun `blank input preserves candidate order`() {
        val candidates = listOf("Beta", "Alpha")

        assertEquals(candidates, searchTestControllerSuggestions(candidates, "  "))
    }
}
