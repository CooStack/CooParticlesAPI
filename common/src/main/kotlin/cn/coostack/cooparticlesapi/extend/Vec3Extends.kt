package cn.coostack.cooparticlesapi.extend

import net.minecraft.world.phys.Vec3


fun Vec3.relativize(target: Vec3): Vec3 {
    return target.subtract(this)
}


fun Vec3.multiply(scaled: Number): Vec3 {
    return this.scale(scaled.toDouble())
}