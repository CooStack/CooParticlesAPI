package cn.coostack.cooparticlesapi.test.particle.server

import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.test.particle.client.BarrierSwordGroupClient
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

class BarrierSwordGroupServer(
    private val box: HitBox,
    private val filter: Predicate<LivingEntity>,
    var direction: Vec3
) :
    ServerParticleGroup(64.0) {

    override fun tick() {
        val entities = world!!.getEntitiesOfClass(LivingEntity::class.java, box.ofBox(pos), filter)
        var closestEntity: LivingEntity? = null

        for (entity in entities) {
            if (closestEntity == null) {
                closestEntity = entity
                continue
            }
            if (pos.distanceTo(closestEntity.position()) > pos.distanceTo(entity.position())) {
                closestEntity = entity
            }
        }
        if (closestEntity == null) {
            return
        }
        direction = closestEntity.position().subtract(pos).normalize().scale(0.5)
        change({}, mapOf("target_entity_id" to ParticleControlerDataBuffers.int(closestEntity.id)))
    }

    override fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>> {
        return mapOf("direction" to ParticleControlerDataBuffers.vec3d(direction))
    }

    override fun getClientType(): Class<out ControlableParticleGroup> {
        return BarrierSwordGroupClient::class.java
    }
}