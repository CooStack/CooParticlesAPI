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
class DemoLightOrbRenderEntity() : AutoRenderEntity(null, Vec3.ZERO), DemoWorldRenderEffectSpec {
    constructor(world: Level?, pos: Vec3, durationTicks: Int = 120) : this() {
        this.world = world
        this.pos = pos
        this.durationTicks = durationTicks
    }

    @CodecField
    override var radius: Float = 1.1f

    @CodecField
    override var intensity: Float = 1.15f

    @CodecField
    override var durationTicks: Int = 120

    override val modelPipeKey: String = "light_orb"
    override val displayName: String = "render_entity/light_orb"
    override val effectColor: Vector4f = Vector4f(0.48f, 0.88f, 1.0f, 0.7f)

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
            "demo_light_orb_render_entity"
        )
    }
}
