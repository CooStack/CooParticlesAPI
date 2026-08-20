package cn.coostack.cooparticlesapi.network.packet.status

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooPacketKind
import cn.coostack.cooparticlesapi.network.packet.api.ServerContext
import cn.coostack.cooparticlesapi.performance.PerformanceStatusServerRequestGate
import cn.coostack.cooparticlesapi.performance.PerformanceStatusServerSnapshotFactory
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.resources.ResourceLocation

/** 客户端在 Status 活跃期间按配置间隔发送的无参数服务端快照请求。 */
@CooAutoRegister
class PacketPerformanceStatusRequestC2S : CooPacket() {
    /** 返回该业务包的稳定协议 ID。 */
    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, PACKET_ID)
    }

    /** 验证请求类型、命令权限和服务端限流后构造固定大小快照。 */
    override fun onServerReceive(context: ServerContext) {
        if (context.kind != CooPacketKind.REQUEST) return
        val refreshInterval = CooParticlesServices.API_CONFIG_MANAGER.getConfig()
            .statusServerRefreshIntervalTicks
        if (!PerformanceStatusServerRequestGate.tryAcquire(context.sender, refreshInterval)) return
        context.reply(
            PacketPerformanceStatusSnapshotS2C.from(
                PerformanceStatusServerSnapshotFactory.create(context.sender.server)
            )
        )
    }

    /** 协议注册常量。 */
    companion object {
        /** 业务包稳定注册 ID，属于网络协议的一部分。 */
        private const val PACKET_ID = "performance_status_request_c2s"
    }
}
