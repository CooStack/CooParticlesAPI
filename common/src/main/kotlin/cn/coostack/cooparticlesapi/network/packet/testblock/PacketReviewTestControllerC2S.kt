package cn.coostack.cooparticlesapi.network.packet.testblock

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockAccess
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.ServerContext
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

@CooAutoRegister
class PacketReviewTestControllerC2S() : CooPacket() {
    @CodecField var action: String = ""
    @CodecField var blockPos: BlockPos = BlockPos.ZERO
    @CodecField var dimension: String = ""

    constructor(dimension: String, blockPos: BlockPos, action: String) : this() {
        this.dimension = dimension
        this.blockPos = blockPos
        this.action = action
    }

    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "review_test_controller_c2s")
    }

    override fun onServerReceive(context: ServerContext) {
        val sender = context.sender
        if (!sender.isCreative) return
        val result = when {
            action.equals(PASS, ignoreCase = true) -> BlockTestGroup.OptionResult.PASSED
            action.equals(FAIL, ignoreCase = true) -> BlockTestGroup.OptionResult.FAILED
            action.equals(SKIP, ignoreCase = true) -> BlockTestGroup.OptionResult.SKIPPED
            else -> return
        }
        TestControllerBlockAccess.find(sender.server, dimension, blockPos)?.reviewCurrent(result)
    }

    companion object {
        const val PASS = "pass"
        const val FAIL = "fail"
        const val SKIP = "skip"
    }
}
