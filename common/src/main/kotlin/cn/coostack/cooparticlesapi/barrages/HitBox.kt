package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

data class HitBox(var x1: Double, var y1: Double, var z1: Double, var x2: Double, var y2: Double, var z2: Double) {

    init {
        replacePoint()
    }

    companion object {
        fun of(dx: Double, dy: Double, dz: Double): HitBox {
            return HitBox(-dx / 2, -dy / 2, -dz / 2, dx / 2, dy / 2, dz / 2)
        }
    }

    fun ofBox(center: Vec3): AABB {
        return AABB(
            x1 + center.x,
            y1 + center.y,
            z1 + center.z,
            x2 + center.x,
            y2 + center.y,
            z2 + center.z
        )
    }

    /**
     * 旋转的其实是 x1,y1,z1 -> x2,y2,z2 这两个点
     * 旋转后BOX规则仍然不变 (还是长方体)
     */
    fun rotateTo(axis: RelativeLocation, to: RelativeLocation) {
        val points = listOf(
            RelativeLocation(x1, y1, z1),
            RelativeLocation(x2, y2, z2),
        )
        Math3DUtil.rotatePointsToPoint(points, axis, to)
        val p1 = points[0]
        val p2 = points[1]
        x1 = p1.x
        x2 = p2.x
        y1 = p1.y
        y2 = p2.y
        z1 = p1.z
        z2 = p2.z
        replacePoint()
    }

    /**
     * 让x1 y1 z1 的值永远小于 x2 y2 z2
     */
    private fun replacePoint() {
        val tx = x1
        x1 = min(x1, x2)
        x2 = max(tx, x2)
        val ty = y1
        y1 = min(y1, y2)
        y2 = max(ty, y2)
        val tz = z1
        z1 = min(z1, z2)
        z2 = max(tz, z2)
    }
}