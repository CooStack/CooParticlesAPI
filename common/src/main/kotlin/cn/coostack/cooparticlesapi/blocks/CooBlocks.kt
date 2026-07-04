package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object CooBlocks {
    val TEST_CONTROLLER = register("test_controller") {
        TestControllerBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GREEN)
                .strength(2.0f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
        )
    }

    fun registerAll() {
        CooBlockEntityTypes.registerAll()
    }

    private fun <T : Block> register(id: String, supplier: () -> T): CommonDeferredRegistry<Block> {
        val location = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        return CooParticlesServices.COO_REGISTRY.register(
            CommonDeferredRegistry(BuiltInRegistries.BLOCK, location) { supplier() }
        )
    }
}
