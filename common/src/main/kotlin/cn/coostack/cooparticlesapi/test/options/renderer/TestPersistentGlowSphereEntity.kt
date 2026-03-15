package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectInput
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereConfig
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

@CooAutoRegister
class TestPersistentGlowSphereEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<TestPersistentGlowSphereEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_persistent_glow_sphere"
        )
    }

    @field:CodecField
    var radius: Float = 4.1f

    @field:CodecField
    var intensity: Float = 6.8f

    @field:CodecField
    var haloIntensity: Float = 3.4f

    @field:CodecField
    var haloRadiusScale: Float = 1.72f

    @field:CodecField
    var fresnelStrength: Float = 1.38f

    @field:CodecField
    var animationSpeed: Float = 1.08f

    @field:CodecField
    var overbrightClamp: Float = 6.6f

    @field:CodecField
    var glowColor: Vector3f = Vector3f(0.58f, 0.88f, 1.30f)

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<TestPersistentGlowSphereEntity>) {
        PostGlowSphereRenderer.initialize()
    }

    override fun collectFrameEffects(
        input: FrameEffectInput<TestPersistentGlowSphereEntity>,
        collector: FrameEffectCollector
    ) {
        val entity = input.instance.entity
        PostGlowSphereRenderer.submit(
            collector = collector,
            effectId = ID.toString(),
            sourceInstanceId = entity.uuid.toString(),
            entity = entity,
            frameContext = input.frameContext,
            config = entity.createGlowConfig()
        )
    }

    private fun createGlowConfig(): PostGlowSphereConfig {
        return PostGlowSphereConfig(
            radius = radius,
            intensity = intensity,
            haloIntensity = haloIntensity,
            haloRadiusScale = haloRadiusScale,
            fresnelStrength = fresnelStrength,
            animationSpeed = animationSpeed,
            overbrightClamp = overbrightClamp,
            glowColor = Vector3f(glowColor)
        )
    }
}
