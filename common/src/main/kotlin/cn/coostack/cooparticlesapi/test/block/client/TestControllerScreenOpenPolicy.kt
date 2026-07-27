package cn.coostack.cooparticlesapi.test.block.client

/**
 * Flashback 写入 Replay Viewer 档案的标记名。
 *
 * 示例：档案包含此属性时不执行录像中的测试 GUI 开屏包。
 * 禁止用玩家名称代替该属性判断 Viewer。
 */
private const val FLASHBACK_REPLAY_VIEWER_PROPERTY = "IsReplayViewer"

/**
 * 判断当前客户端档案是否可以响应测试控制器开屏包。
 *
 * 示例：普通玩家或尚未创建档案的客户端返回 `true`。
 * 禁止让带有 Flashback Replay Viewer 标记的档案打开录像中的旧界面。
 *
 * @param profilePropertyNames 当前客户端玩家档案的属性名；玩家尚未创建时为 `null`
 * @return `true` 表示可以打开测试控制器界面
 */
internal fun shouldOpenTestControllerScreen(profilePropertyNames: Set<String>?): Boolean {
    return profilePropertyNames?.contains(FLASHBACK_REPLAY_VIEWER_PROPERTY) != true
}
