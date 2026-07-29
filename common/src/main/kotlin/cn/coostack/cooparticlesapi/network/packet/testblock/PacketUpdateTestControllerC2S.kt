package cn.coostack.cooparticlesapi.network.packet.testblock

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockAccess
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.ServerContext
import cn.coostack.cooparticlesapi.test.api.TestOptionParamCodec
import cn.coostack.cooparticlesapi.test.block.BlockTestAnimationTrackCodec
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
    @CodecField var forwardDynamic: Boolean = false
    @CodecField var forwardTrack: String = ""
    @CodecField var groupId: String = ""
    @CodecField var mode: String = "sequential"
    @CodecField var offsetX: Double = 0.0
    @CodecField var offsetY: Double = 0.0
    @CodecField var offsetZ: Double = 0.0
    @CodecField var optionParamIndex: Int = 0
    @CodecField var optionParamValues: String = ""
    @CodecField var positionDynamic: Boolean = false
    @CodecField var positionTrack: String = ""
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
        val offset = Vec3(offsetX, offsetY, offsetZ)
        val forward = Vec3(forwardX, forwardY, forwardZ)
        val changed = blockEntity.updateConfig(
            groupId = groupId,
            mode = BlockTestMode.fromId(mode),
            selectedIndex = selectedIndex,
            repeatIndex = repeatIndex,
            repeatDelayTicks = repeatDelayTicks,
            playerOffset = offset,
            playerForward = forward,
            playerPositionDynamic = positionDynamic,
            playerForwardDynamic = forwardDynamic,
            playerPositionTrack = BlockTestAnimationTrackCodec.decode(positionTrack, offset),
            playerForwardTrack = BlockTestAnimationTrackCodec.decode(forwardTrack, forward),
            playerBoxWidth = boxWidth,
            playerBoxHeight = boxHeight,
            playerBoxDepth = boxDepth,
            optionParamIndex = optionParamIndex,
            optionParamValues = TestOptionParamCodec.decodeOptionValues(optionParamValues)
        )
        if (wasRunning && changed) {
            blockEntity.startTest()
        }
        if (reopen) {
            TestControllerBlockAccess.openControllerScreen(sender, blockEntity)
        }
    }
}
