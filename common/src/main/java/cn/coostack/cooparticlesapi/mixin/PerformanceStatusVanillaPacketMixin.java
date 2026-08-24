package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkEndpoint;
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkMetrics;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 统计原版 Connection 实际提交和进入入站处理的 Packet 数量。 */
@Mixin(Connection.class)
public abstract class PerformanceStatusVanillaPacketMixin {
    @Shadow
    @Final
    private PacketFlow receiving;

    /** 在原版入站处理开始前记录一个接收包。 */
    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD")
    )
    private void cooparticlesapi$recordVanillaReceived(
            ChannelHandlerContext context,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        PerformanceStatusNetworkMetrics.INSTANCE.recordVanillaReceived(endpoint());
    }

    /** 在包实际提交给 Netty pipeline 前记录一个发送包。 */
    @Inject(
            method = "doSendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD")
    )
    private void cooparticlesapi$recordVanillaSent(
            Packet<?> packet,
            PacketSendListener listener,
            boolean flush,
            CallbackInfo callbackInfo
    ) {
        PerformanceStatusNetworkMetrics.INSTANCE.recordVanillaSent(endpoint());
    }

    /** 根据 Connection 的接收方向确定当前 JVM 端点。 */
    private PerformanceStatusNetworkEndpoint endpoint() {
        return this.receiving == PacketFlow.CLIENTBOUND
                ? PerformanceStatusNetworkEndpoint.CLIENT
                : PerformanceStatusNetworkEndpoint.SERVER;
    }
}
