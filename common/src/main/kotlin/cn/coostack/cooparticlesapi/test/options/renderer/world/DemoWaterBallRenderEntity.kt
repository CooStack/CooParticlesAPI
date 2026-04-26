package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector4f

@CooAutoRegister
class DemoWaterBallRenderEntity() : AutoRenderEntity(null, Vec3.ZERO), DemoWorldRenderEffectSpec {
    constructor(world: Level?, pos: Vec3, durationTicks: Int = 160) : this() {
        this.world = world
        this.pos = pos
        this.durationTicks = durationTicks
    }

    @CodecField
    override var radius: Float = 1.45f

    @CodecField
    override var intensity: Float = 0.92f

    @CodecField
    override var durationTicks: Int = 160

    override val modelPipeKey: String = "water_ball"
    override val displayName: String = "render_entity/water_ball"
    override val effectColor: Vector4f = Vector4f(0.025f, 0.22f, 0.34f, 0.50f)

    override fun serverTick() {
        if (durationTicks in 1..age) remove()
    }

    override fun clientTick() {
        if (durationTicks in 1..age) remove()
    }

    override fun getRenderID(): ResourceLocation = ID

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "demo_water_ball_render_entity"
        )
    }
}
