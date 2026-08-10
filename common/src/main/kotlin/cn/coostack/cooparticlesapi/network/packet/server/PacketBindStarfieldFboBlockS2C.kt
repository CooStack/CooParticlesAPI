package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.ClientContext
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.renderer.pipeline.CooBlockPipelines
import cn.coostack.cooparticlesapi.test.options.renderer.pipeline.RenderPipelineExamples
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * 把方块测试选中的方块类型同步为客户端星空 FBO 临时绑定。
 *
 * BlockState 只用于通过注册表同步方块类型。每次测试都有独立的 [bindingId]，结束时只撤销
 * 自己的规则，之前的永久绑定或其他测试绑定会继续生效。
 */
@CooAutoRegister
class PacketBindStarfieldFboBlockS2C() : CooPacket() {
    /** 用于同步目标方块类型的状态载体。 */
    @CodecField
    var state: BlockState = Blocks.AIR.defaultBlockState()

    /** 当前临时绑定的所有者 ID。 */
    @CodecField
    var bindingId: UUID = UUID(0L, 0L)

    /** `true` 添加临时绑定，`false` 撤销该所有者的绑定。 */
    @CodecField
    var enabled: Boolean = true

    /**
     * @param state 测试位置的方块状态，用于传递方块类型
     * @param bindingId 当前测试的临时绑定 ID
     * @param enabled 是否启用星空 FBO 绑定
     */
    constructor(state: BlockState, bindingId: UUID, enabled: Boolean) : this() {
        this.state = state
        this.bindingId = bindingId
        this.enabled = enabled
    }

    /** @return 星空 FBO 方块类型绑定包的稳定资源 ID。 */
    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, PACKET_ID)
    }

    /**
     * @param context 当前客户端网络上下文
     */
    override fun onClientReceive(context: ClientContext) {
        context.client.execute {
            if (enabled) {
                CooBlockPipelines.bindScoped(bindingId, state.block, RenderPipelineExamples.STARFIELD_BLOCK)
            } else {
                CooBlockPipelines.unbindScoped(bindingId)
            }
        }
    }

    companion object {
        private const val PACKET_ID = "bind_starfield_fbo_block_s2c"
    }
}
