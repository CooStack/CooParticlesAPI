package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenBoundTestSelectionScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import net.minecraft.client.Minecraft

/**
 * 在客户端打开测试控制器相关界面。
 *
 * 示例：服务器向点击方块的玩家发送开屏包后调用 [openController]。
 * 禁止绕过这里直接打开界面，否则 Flashback Viewer 会执行录像中的旧开屏包。
 */
object TestControllerClientScreens {
    /**
     * 为当前玩家打开测试控制器主界面。
     *
     * 示例：普通玩家收到 [PacketOpenTestControllerScreenS2C] 后进入配置页。
     * 禁止在 Flashback Replay Viewer 上构造或设置此界面。
     *
     * @param packet 服务端发送的控制器状态
     */
    fun openController(packet: PacketOpenTestControllerScreenS2C) {
        val client = Minecraft.getInstance()
        if (!shouldOpenTestControllerScreen(client.player?.gameProfile?.properties?.keySet())) return
        client.setScreen(TestControllerScreen(packet, TestControllerPickClient.consumeOpenParamPage()))
    }

    /**
     * 为当前玩家打开已绑定测试控制器的选择界面。
     *
     * 示例：绑定器关联多个控制器时展示 [BoundTestControllerSelectionScreen]。
     * 禁止在 Flashback Replay Viewer 上构造或设置此界面。
     *
     * @param packet 服务端发送的绑定控制器列表
     */
    fun openBoundSelection(packet: PacketOpenBoundTestSelectionScreenS2C) {
        val client = Minecraft.getInstance()
        if (!shouldOpenTestControllerScreen(client.player?.gameProfile?.properties?.keySet())) return
        client.setScreen(BoundTestControllerSelectionScreen(packet))
    }
}
