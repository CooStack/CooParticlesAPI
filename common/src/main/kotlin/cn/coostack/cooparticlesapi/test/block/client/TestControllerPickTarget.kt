package cn.coostack.cooparticlesapi.test.block.client

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal data class TestControllerPickTarget(
    val point: Vec3,
    val box: AABB,
    val fromBlock: Boolean
)
