package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.extend.ofID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 检查测试控制器按 MOD_ID 分阶段提供 path 的规则。
 */
class TestControllerGroupIdCompletionTest {
    /** 两个 MOD 注册的三个测试组 ID。 */
    private val registeredIds = listOf(
        ofID("alpha", "first"),
        ofID("alpha", "second"),
        ofID("beta", "only")
    )

    @Test
    fun `mod ids preserve registration order without duplicates`() {
        assertEquals(listOf("alpha", "beta"), testControllerModIds(registeredIds))
    }

    @Test
    fun `paths remain unavailable until mod id is complete`() {
        assertEquals(emptyList(), testControllerPaths(registeredIds, "alp"))
        assertEquals(listOf("first", "second"), testControllerPaths(registeredIds, "alpha"))
    }

    @Test
    fun `complete mod id exposes its single path`() {
        assertNull(testControllerSinglePath(registeredIds, "bet"))
        assertNull(testControllerSinglePath(registeredIds, "alpha"))
        assertEquals("only", testControllerSinglePath(registeredIds, "BETA"))
    }

    @Test
    fun `exact mod id stops longer namespace completion`() {
        val ids = registeredIds + ofID("alpha_addon", "extra")

        assertEquals(emptyList(), testControllerModIdSuggestions(ids, "alpha"))
        assertEquals(listOf("alpha", "alpha_addon"), testControllerModIdSuggestions(ids, "alph"))
    }

    @Test
    fun `valid unregistered group id is preserved`() {
        assertTrue(isTestControllerModIdValid("removed_mod"))
        assertFalse(isTestControllerModIdValid("RemovedMod"))
        assertEquals(
            ofID("removed_mod", "kept_path"),
            testControllerGroupId(registeredIds, "removed_mod", "kept_path")
        )
        assertNull(testControllerGroupId(registeredIds, "RemovedMod", "kept_path"))
    }
}
