package cn.coostack.cooparticlesapi.particles.impl

import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.*

class ControlableFallingDustParticle(
    world: ClientLevel,
    pos: Vec3,
    velocity: Vec3,
    controlUUID: UUID,
    faceToCamera: Boolean,
    state: BlockState
) :
    ControlableParticle(world, pos, velocity, controlUUID, faceToCamera) {
    var uo = 0f
    var vo = 0f

    init {
        val pos = BlockPos.containing(x, y, z)
        val sprite = Minecraft.getInstance().blockRenderer.blockModelShaper.getParticleIcon(state)
        var finalColor = Vector3f(0.6f)
        if (!state.`is`(Blocks.GRASS_BLOCK)) {
            val i = Minecraft.getInstance().blockColors.getColor(state, world, pos, 0)
            val x = 0.6f * (i shr 16 and 255).toFloat() / 255.0f
            val y = 0.6f * (i shr 8 and 255).toFloat() / 255.0f
            val z = 0.6f * (i and 255).toFloat() / 255.0f
            finalColor = Vector3f(x, y, z)
        }
        setSprite(sprite)
        this.color = finalColor
        this.quadSize /= 2
        this.uo = this.random.nextFloat() * 3.0f
        this.vo = this.random.nextFloat() * 3.0f
    }

    override fun getRenderType(): ParticleRenderType {
        return ParticleRenderType.TERRAIN_SHEET
    }

    override fun getU0(): Float {
        return this.sprite.getU((this.uo + 1.0f) / 4.0f)
    }

    override fun getU1(): Float {
        return this.sprite.getU(this.uo / 4.0f)
    }

    override fun getV0(): Float {
        return this.sprite.getV(this.vo / 4.0f)
    }

    override fun getV1(): Float {
        return this.sprite.getV((this.vo + 1.0f) / 4.0f)
    }

    class Factory : ParticleProvider<ControlableFallingDustEffect> {
        override fun createParticle(
            parameters: ControlableFallingDustEffect,
            world: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            velocityX: Double,
            velocityY: Double,
            velocityZ: Double
        ): Particle? {
            val state = parameters.state
            if (!state.isAir && state.renderShape == RenderShape.INVISIBLE) return null
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