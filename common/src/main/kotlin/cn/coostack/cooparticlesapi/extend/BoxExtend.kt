package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.barrages.HitBox
import net.minecraft.world.phys.AABB

/**
 * 原参数直接转换为hitBox
 *
 * @return
 */
fun AABB.asHitBox(): HitBox {
    return HitBox.of(maxX - minX, maxY - minY, maxZ - minZ)
}


