package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEnchantmentEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFireworkEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFallingDustEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableSplashEffect
import net.minecraft.world.level.block.Blocks
import java.util.UUID

object ControlableParticleEffectManager {
    // 使用ParticleTypes.PACKET_CODEC 解码 ParticleEffect 产生异常, 解决办法暂时未知
    private val buffer = mutableMapOf<Class<out ControlableParticleEffect>, ControlableParticleEffect>()
    fun register(effect: ControlableParticleEffect) {
        buffer[effect::class.java] = effect.clone()
    }

    fun createWithUUID(uuid: UUID, type: Class<out ControlableParticleEffect>): ControlableParticleEffect {
        return buffer[type]!!.clone().apply {
            this.controlUUID = uuid
        }
    }

    init {
        register(ControlableCloudEffect(UUID.randomUUID()))
        register(ControlableEnchantmentEffect(UUID.randomUUID()))
        register(ControlableFireworkEffect(UUID.randomUUID()))
        register(ControlableFlashEffect(UUID.randomUUID()))
        register(ControlableEndRodEffect(UUID.randomUUID()))
        register(ControlableFallingDustEffect(UUID.randomUUID(), Blocks.AIR.defaultBlockState()))
        register(ControlableSplashEffect(UUID.randomUUID()))
    }

    fun init() {}
}