package cn.coostack.cooparticlesapi.extend

import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3

fun Vec3i.asVec3(): Vec3 {
    return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
}