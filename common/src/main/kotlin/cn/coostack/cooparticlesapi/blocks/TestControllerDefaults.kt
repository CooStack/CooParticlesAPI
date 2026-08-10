package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.test.block.builtin.BlockAPITestGroupBuilder
import net.minecraft.resources.ResourceLocation

internal fun defaultTestControllerGroupId(): ResourceLocation {
    return ofID(CooParticlesConstants.MOD_ID, BlockAPITestGroupBuilder.ID)
}
