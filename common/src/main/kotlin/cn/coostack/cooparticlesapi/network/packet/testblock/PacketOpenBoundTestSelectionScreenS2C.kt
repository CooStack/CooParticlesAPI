package cn.coostack.cooparticlesapi.network.packet.testblock

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.blocks.BoundControllerEntry
import cn.coostack.cooparticlesapi.network.packet.api.ClientContext
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.test.block.client.TestControllerClientScreens
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

@CooAutoRegister
class PacketOpenBoundTestSelectionScreenS2C() : CooPacket() {
    @CodecField var currentIndices: List<Int> = emptyList()
    @CodecField var dimensions: List<String> = emptyList()
    @CodecField var groupIds: List<String> = emptyList()
    @CodecField var loaded: List<Boolean> = emptyList()
    @CodecField var optionCounts: List<Int> = emptyList()
    @CodecField var positions: List<BlockPos> = emptyList()
    @CodecField var running: List<Boolean> = emptyList()
    @CodecField var statuses: List<String> = emptyList()

    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "open_bound_test_selection_screen_s2c")
    }

    override fun onClientReceive(context: ClientContext) {
        TestControllerClientScreens.openBoundSelection(this)
    }

    companion object {
        fun fromEntries(entries: List<BoundControllerEntry>): PacketOpenBoundTestSelectionScreenS2C {
            return PacketOpenBoundTestSelectionScreenS2C().also {
                it.dimensions = entries.map { entry -> entry.dimension }
                it.positions = entries.map { entry -> entry.pos }
                it.groupIds = entries.map { entry -> entry.groupId }
                it.statuses = entries.map { entry -> entry.status }
                it.currentIndices = entries.map { entry -> entry.currentIndex }
                it.optionCounts = entries.map { entry -> entry.optionCount }
                it.loaded = entries.map { entry -> entry.loaded }
                it.running = entries.map { entry -> entry.running }
            }
        }
    }
}
