package cn.coostack.cooparticlesapi.network.animation

import cn.coostack.cooparticlesapi.network.animation.api.PathMotion
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.util.UUID

/**
 * 服务器专属
 */
object PathMotionManager {
    val motions = HashSet<PathMotion>()

    fun applyMotion(motion: PathMotion) {
        motions.add(motion)
    }

    fun tick() {
        val iterator = motions.iterator()
        while (iterator.hasNext()) {
            val motion = iterator.next()
            if (!motion.checkValid()) {
                iterator.remove()
                continue
            }
            motion.apply(motion.next().add(motion.origin))
        }
    }
}