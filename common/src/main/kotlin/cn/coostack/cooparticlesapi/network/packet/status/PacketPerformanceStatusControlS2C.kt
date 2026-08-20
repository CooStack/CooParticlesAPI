package cn.coostack.cooparticlesapi.network.packet.status

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.ClientContext
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.performance.PerformanceStatusClientBridge
import cn.coostack.cooparticlesapi.performance.PerformanceStatusControlAction
import net.minecraft.resources.ResourceLocation

/** 服务端命令向目标玩家客户端发送的 Status 会话控制包。 */
@CooAutoRegister
class PacketPerformanceStatusControlS2C() : CooPacket() {
    /** 网络动作名称。 */
    @CodecField var action: String = ""

    /** 使用类型安全动作创建控制包。 */
    constructor(action: PerformanceStatusControlAction) : this() {
        this.action = action.name
    }

    /** 返回该业务包的稳定协议 ID。 */
    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, PACKET_ID)
    }

    /** 经纯 common bridge 把动作交给客户端初始化时安装的控制器。 */
    override fun onClientReceive(context: ClientContext) {
        PerformanceStatusControlAction.fromNetworkName(action)?.let(PerformanceStatusClientBridge::handle)
    }

    /** 协议注册常量。 */
    companion object {
        /** 业务包稳定注册 ID，属于网络协议的一部分。 */
        private const val PACKET_ID = "performance_status_control_s2c"
    }
}
