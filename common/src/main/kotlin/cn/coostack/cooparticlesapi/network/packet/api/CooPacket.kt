package cn.coostack.cooparticlesapi.network.packet.api

import cn.coostack.cooparticlesapi.annotations.packet.CooPacketRegistryHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * # 设计初衷
 * - 简化繁琐的数据包注册流程 采用 [cn.coostack.cooparticlesapi.annotations.CooAutoRegister] 快速注册
 * - 非必须定义包处理器 采用事件/回调方案 (基于 CooEvent)
 * - 支持请求/回报的快捷方案 (基于 [CooServerPacketManager.request] / [CooClientPacketManager.request])
 *
 * # 使用注意
 * - 必须提供空构造参数 (因为 codec 反射构造)
 * - 所有的 CooPacket 类必须使用 [cn.coostack.cooparticlesapi.annotations.CodecField] 标明数据类型
 * - 必须使用 [cn.coostack.cooparticlesapi.annotations.CooAutoRegister] 进行自动注册标识
 *
 * # 默认 timeout
 * - 请求/响应模式默认超时为 60 tick (3秒)
 *
 * # 接收回调
 * - [onClientReceive] / [onServerReceive] 默认空实现
 * - 推荐通过 CooEvent 监听 [cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent]
 *   或在子类内 override 上述方法
 */
abstract class CooPacket {
    /**
     * 数据包ID 必须由子类提供，用于跨端定位包类型
     */
    abstract fun id(): ResourceLocation

    /**
     * 数据包编解码器，默认基于反射 + [cn.coostack.cooparticlesapi.annotations.CodecField] 自动生成
     */
    open fun codec(): StreamCodec<out FriendlyByteBuf, out CooPacket> =
        CooPacketRegistryHelper.generateClassParticleCodec(this::class.java)

    /**
     * 收到自服务器的包时回调 (客户端线程)
     *
     * - 普通包: 直接回调
     * - 请求包: 子类应在此处理请求并通过 [ClientContext.reply] 回复
     * - 响应包: 也会调用本方法 (与 request 注册的回调并存)
     */
    open fun onClientReceive(context: ClientContext) {}

    /**
     * 收到自客户端的包时回调 (服务器线程)
     *
     * - 普通包: 直接回调
     * - 请求包: 子类应在此处理请求并通过 [ServerContext.reply] 回复
     * - 响应包: 也会调用本方法 (与 request 注册的回调并存)
     */
    open fun onServerReceive(context: ServerContext) {}
}
