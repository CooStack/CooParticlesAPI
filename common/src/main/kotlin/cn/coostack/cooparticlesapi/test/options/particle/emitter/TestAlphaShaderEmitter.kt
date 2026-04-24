package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestAlphaShaderEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    override fun doTick() {

    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return listOf(
            ControlableParticleData().apply {
                maxAge = 20
                alpha = 0.08f
                setTextureSheet(TextureSheetsEnum.PARTICLE_SHEET_TRANSLUCENT)
            } to RelativeLocation(),

            ControlableParticleData().apply {
                maxAge = 20
                alpha = 0.2f
                setTextureSheet(TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT)
            } to RelativeLocation(0,2,0),
        )
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
        data.size = 2f
    }
}