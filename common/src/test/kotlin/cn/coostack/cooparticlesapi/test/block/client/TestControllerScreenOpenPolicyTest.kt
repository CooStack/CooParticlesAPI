package cn.coostack.cooparticlesapi.test.block.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 覆盖测试控制器界面的客户端开屏门禁。
 *
 * 示例：普通玩家收到定向开屏包时可以打开界面。
 * 禁止 Flashback Replay Viewer 执行录像中的旧开屏包。
 */
class TestControllerScreenOpenPolicyTest {
    /**
     * 验证 Flashback 标记过的 Viewer 不会打开测试界面。
     *
     * 示例：带有 `IsReplayViewer` 属性的档案应返回 `false`。
     * 禁止仅凭玩家名称判断 Viewer，以免误伤同名普通玩家。
     */
    @Test
    fun `flashback replay viewer cannot open controller screens`() {
        assertFalse(shouldOpenTestControllerScreen(setOf("IsReplayViewer")))
    }

    /**
     * 验证普通玩家档案不受 Flashback 门禁影响。
     *
     * 示例：没有 Replay Viewer 属性的玩家应返回 `true`。
     * 禁止因为客户端档案暂时为空而拦截正常开屏路径。
     */
    @Test
    fun `ordinary player can open controller screens`() {
        assertTrue(shouldOpenTestControllerScreen(emptySet()))
        assertTrue(shouldOpenTestControllerScreen(null))
    }
}
