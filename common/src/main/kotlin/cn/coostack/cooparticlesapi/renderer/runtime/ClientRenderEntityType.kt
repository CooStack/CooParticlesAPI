package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * 客户端识别某个 RenderEntity 类型所需的最小描述。
 *
 * @property codec 收到网络同步数据后，用于恢复该实体实例的解码器
 * @property rendererFactory 为该实体创建 renderer 的工厂；为空时表示客户端只能解码，不能正常进入渲染运行时
 */
data class ClientRenderEntityType(
    val codec: StreamCodec<FriendlyByteBuf, RenderEntity>,
    val rendererFactory: (() -> RenderEntityRenderer<out RenderEntity>)? = null
)
