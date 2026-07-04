package cn.coostack.cooparticlesapi.network.packet.testblock

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockAccess
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.ServerContext
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class PacketUpdateTestControllerC2S() : CooPacket() {
    @CodecField var blockPos: BlockPos = BlockPos.ZERO
    @CodecField var boxDepth: Double = 0.6
    @CodecField var boxHeight: Double = 1.8
    @CodecField var boxWidth: Double = 0.6
    @CodecField var dimension: String = ""
    @CodecField var forwardX: Double = 0.0
    @CodecField var forwardY: Double = 0.0
    @CodecField var forwardZ: Double = 1.0
    @CodecField var groupId: String = ""
    @CodecField var mode: String = "sequential"
    @CodecField var offsetX: Double = 0.0
    @CodecField var offsetY: Double = 0.0
    @CodecField var offsetZ: Double = 0.0
    @CodecField var repeatDelayTicks: Int = 0
    @CodecField var repeatIndex: Boolean = false
    @CodecField var reopen: Boolean = false
    @CodecField var selectedIndex: Int = 0

    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "update_test_controller_c2s")
    }

    override fun onServerReceive(context: ServerContext) {
        val sender = context.sender
        if (!sender.isCreative) return
        val blockEntity = TestControllerBlockAccess.find(sender.server, dimension, blockPos) ?: return
        val wasRunning = blockEntity.isRunning()
        val changed = blockEntity.updateConfig(
            groupId = groupId,
            mode = BlockTestMode.fromId(mode),
            selectedIndex = selectedIndex,
            repeatIndex = repeatIndex,
            repeatDelayTicks = repeatDelayTicks,
            playerOffset = Vec3(offsetX, offsetY, offsetZ),
            playerForward = Vec3(forwardX, forwardY, forwardZ),
            playerBoxWidth = boxWidth,
            playerBoxHeight = boxHeight,
            playerBoxDepth = boxDepth
        )
        if (wasRunning && changed) {
            blockEntity.startTest()
        }
        if (reopen) {
            TestControllerBlockAccess.openControllerScreen(sender, blockEntity)
        }
    }
}
