package cn.coostack.cooparticlesapi.network.packet.testblock

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockAccess
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.ServerContext
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

@CooAutoRegister
class PacketSelectBoundTestControllerC2S() : CooPacket() {
    @CodecField var blockPos: BlockPos = BlockPos.ZERO
    @CodecField var dimension: String = ""

    constructor(dimension: String, blockPos: BlockPos) : this() {
        this.dimension = dimension
        this.blockPos = blockPos
    }

    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "select_bound_test_controller_c2s")
    }

    override fun onServerReceive(context: ServerContext) {
        val sender = context.sender
        val blockEntity = TestControllerBlockAccess.find(sender.server, dimension, blockPos) ?: return
        TestControllerBlockAccess.openControllerScreen(sender, blockEntity)
    }
}
