package cn.coostack.cooparticlesapi.particles.impl.particles

import cn.coostack.cooparticlesapi.cparticle.CParticleBlockAppearanceResolver
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.impl.ControlableFallingDustEffect
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * 使用方块模型 particle icon 的可控制 FallingDust 粒子。
 *
 * 方块外观由 CParticle 通用解析器共用的 helper 计算。
 * 示例：[Factory] 会为普通可见 BlockState 创建实例。
 * 禁止：空气和不可见模型不会由 [Factory] 创建。
 */
class ControlableFallingDustParticle(
    world: ClientLevel,
    pos: Vec3,
    velocity: Vec3,
    controlUUID: UUID,
    faceToCamera: Boolean,
    state: BlockState
) :
    ControlableParticle(world, pos, velocity, controlUUID, faceToCamera) {
    var uo = 0F
    var vo = 0F

    init {
        textureSheet = CooParticleTextureSheet.TERRAIN_SHEET
        val pos = BlockPos.containing(x, y, z)
        val appearance = requireNotNull(
            CParticleBlockAppearanceResolver.resolve(
                state,
                world,
                pos,
                applyTint = true,
                applyBrightness = true,
            )
        ) {
            "ControlableFallingDustParticle requires a visible, non-air BlockState"
        }
        setSprite(appearance.sprite)
        this.color = appearance.colorMultiplier
        this.quadSize /= 2
        this.uo = this.random.nextFloat() * 3.0F
        this.vo = this.random.nextFloat() * 3.0F
    }

    override fun getU0(): Float {
        return this.sprite.getU((this.uo + 1.0F) / 4.0F)
    }

    override fun getU1(): Float {
        return this.sprite.getU(this.uo / 4.0F)
    }

    override fun getV0(): Float {
        return this.sprite.getV(this.vo / 4.0F)
    }

    override fun getV1(): Float {
        return this.sprite.getV((this.vo + 1.0F) / 4.0F)
    }

    /** 校验方块可见性后创建使用方块图集的受控落尘粒子。 */
    class Factory : ParticleProvider<ControlableFallingDustEffect> {
        /** 接受 26.3 工厂签名，仅为可见方块状态创建粒子。 */
        override fun createParticle(
            parameters: ControlableFallingDustEffect,
            world: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            velocityX: Double,
            velocityY: Double,
            velocityZ: Double,
            random: RandomSource,
        ): Particle? {
            val state = parameters.state
            if (!CParticleBlockAppearanceResolver.isRenderable(state)) return null
            return ControlableFallingDustParticle(
                world,
                Vec3(x, y, z),
                Vec3(velocityX, velocityY, velocityZ),
                parameters.controlUUID,
                parameters.faceToPlayer,
                state,
            )
        }
    }

}
