package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.DisplayEntityEmittersData
import cn.coostack.cooparticlesapi.test.options.display.CylinderBoardDisplayEntity
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestDisplayEntityAutoEmitters() : AutoEmitters(Vec3.ZERO, null) {
    constructor(pos: Vec3, world: Level?) : this() {
        this.pos = pos
        this.world = world
    }

    @CodecField
    var ringRadius: Double = 16.0

    @CodecField
    var ringPoints: Int = 12

    @CodecField
    var axisDirection: Vec3 = Vec3(0.0, 1.0, 0.0)

    @CodecField
    var lineWidth: Float = 0.08f

    @CodecField
    var lineLength: Float = 0.3f

    @CodecField
    var inwardSpeed: Double = 0.18

    @CodecField
    var lineLife: Int = 22

    override fun doTick() {
    }

    override fun genControls(lerpProgress: Float): List<Pair<SerializableData, RelativeLocation>> {
        return PointsBuilder()
            .addCircle(ringRadius, ringPoints.coerceAtLeast(3))
            .pointsOnEach { it.y += 1.0 }
            .createWithoutClone()
            .map { offset ->
                DisplayEntityEmittersData(createBoard(offset)) to offset
            }
    }

    override fun singleControlableAction(
        controler: Controlable<*>,
        data: SerializableData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
        val entity = controler.getControlObject() as? DisplayEntity ?: return
        if (entity is CylinderBoardDisplayEntity) {
            entity.direction = if (entity.velocity.lengthSqr() > 1.0E-6) {
                entity.velocity
            } else {
                axisDirection
            }
            entity.lineWidth = lineWidth
            entity.length = lineLength
        }
    }

    private fun createBoard(offset: RelativeLocation = RelativeLocation()): CylinderBoardDisplayEntity {
        val inward = offset.clone().apply { y = 0.0 }.toVector()
        val velocity = if (inward.lengthSqr() > 1.0E-6) {
            inward.normalize().scale(-inwardSpeed)
        } else {
            Vec3.ZERO
        }
        return CylinderBoardDisplayEntity(Vec3.ZERO, null).apply {
            lineWidth = this@TestDisplayEntityAutoEmitters.lineWidth
            length = lineLength
            this.velocity = velocity
            direction = if (velocity.lengthSqr() > 1.0E-6) velocity else axisDirection
            maxAge = lineLife.coerceAtLeast(1)
        }
    }
}
