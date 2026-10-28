package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object CooBlocks {
    val TEST_CONTROLLER = register("test_controller") { properties ->
        TestControllerBlock(
            properties
                .mapColor(MapColor.COLOR_GREEN)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
        )
    }

    fun registerAll() {
        CooBlockEntityTypes.registerAll()
    }

    /** 在构造方块前设置注册键，供 26.3 解析掉落表和翻译键。 */
    private fun <T : Block> register(
        id: String,
        supplier: (BlockBehaviour.Properties) -> T
    ): CommonDeferredRegistry<Block> {
        val location = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        return CooParticlesServices.COO_REGISTRY.register(
            CommonDeferredRegistry(BuiltInRegistries.BLOCK, location) {
                supplier(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, location)))
            }
        )
    }
}
