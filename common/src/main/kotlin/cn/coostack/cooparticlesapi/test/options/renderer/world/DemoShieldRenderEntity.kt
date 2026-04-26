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
class DemoShieldRenderEntity() : AutoRenderEntity(null, Vec3.ZERO), DemoWorldRenderEffectSpec {
    constructor(world: Level?, pos: Vec3, durationTicks: Int = 120) : this() {
        this.world = world
        this.pos = pos
        this.durationTicks = durationTicks
    }

    @CodecField
    override var radius: Float = 2.25f

    @CodecField
    override var intensity: Float = 0.78f

    @CodecField
    override var durationTicks: Int = 120

    override val modelPipeKey: String = "shield"
    override val displayName: String = "render_entity/shield"
    override val effectColor: Vector4f = Vector4f(0.20f, 0.72f, 1.0f, 0.55f)

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
            "demo_shield_render_entity"
        )
    }
}
