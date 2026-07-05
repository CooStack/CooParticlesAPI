package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.randomVec3
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.random.Random

@CooAutoRegister
class TestSpreadPointEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    @CodecField
    var template = ControlableParticleData().apply {
        setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT)
        size = 0.12f
        alpha = 0.92f
        maxAge = 40
        speedLimit = 0.35
        visibleRange = 128f
    }

    @CodecField
    var colorStart = Vector3f(0.20f, 0.72f, 1.00f)

    @CodecField
    var colorEnd = Vector3f(1.00f, 0.36f, 0.12f)

    private val random = Random(System.currentTimeMillis())

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val count = random.nextInt(6, 9)
        return (0 until count).map { index ->
            val progress = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
            val direction = randomVec3(random).normalizeOrUp()
            template.clone().apply {
                color = gradientColor(progress)
                velocity = direction.scale(random.nextDouble(0.055, 0.095))
                size = random.nextDouble(0.08, 0.14).toFloat()
                maxAge = random.nextInt(32, 49)
            } to RelativeLocation()
        }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
    }

    private fun gradientColor(progress: Float): Vector3f {
        val t = progress.coerceIn(0f, 1f)
        return Vector3f(
            colorStart.x + (colorEnd.x - colorStart.x) * t,
            colorStart.y + (colorEnd.y - colorStart.y) * t,
            colorStart.z + (colorEnd.z - colorStart.z) * t
        )
    }

    private fun Vec3.normalizeOrUp(): Vec3 {
        return if (lengthSqr() <= 1.0E-7) Vec3(0.0, 1.0, 0.0) else normalize()
    }
}
