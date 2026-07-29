package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import net.minecraft.world.phys.Vec3
import kotlin.math.round

/**
 * 保存一次客户端世界取点请求及其完成回调。
 *
 * 示例：主界面取点请求会在完成后修改 [packet] 并发送更新包。
 * 禁止把关键帧取点回调当作服务端确认；它只更新本地编辑草稿。
 */
internal data class TestControllerPickRequest(
    val screenPacket: PacketOpenTestControllerScreenS2C,
    val packet: PacketUpdateTestControllerC2S,
    val kind: TestControllerPickKind,
    val precisionUnlocked: Boolean,
    val paramOptionIndex: Int = 0,
    val paramId: String = "",
    val paramComponentCount: Int = 3,
    val paramAbsolute: Boolean = false,
    val history: TestControllerUndoHistory<TestControllerPacketDrafts.TestControllerConfigSnapshot>? = null,
    /** 成功取点时接收目标位置、目标来源和玩家朝向。 */
    val onPicked: ((Vec3, Boolean, Vec3) -> Unit)? = null,
    /** 取消取点时调用；普通主界面请求保持为空。 */
    val onCancelled: (() -> Unit)? = null,
) {
    fun format(value: Double): Double {
        if (precisionUnlocked) {
            return value
        }
        val scaled = round(value * 1_000_000.0)
        return scaled / 1_000_000.0
    }
}
