package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntityType

object CooBlockEntityTypes {
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    val TEST_CONTROLLER = register("test_controller") {
        BlockEntityType.Builder.of(::TestControllerBlockEntity, CooBlocks.TEST_CONTROLLER.get()).build(null)
    }

    fun registerAll() {
        TEST_CONTROLLER.get()
    }

    private fun register(id: String, supplier: () -> BlockEntityType<*>): CommonDeferredRegistry<BlockEntityType<*>> {
        val location = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        return CooParticlesServices.COO_REGISTRY.register(
            CommonDeferredRegistry(BuiltInRegistries.BLOCK_ENTITY_TYPE, location) { supplier() }
        )
    }
}
