package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereConfig
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

@Deprecated("Legacy compatibility demo only. RenderEntity glow should use content-driven MASK_BLOOM.")
@CooAutoRegister
class TestPersistentGlowSphereEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    FramePostRenderEntityRenderer<TestPersistentGlowSphereEntity> {
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

    override fun describeFeatures(entity: TestPersistentGlowSphereEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.FRAME_POST),
            effectTypes = setOf(BuiltinRenderEffectTypes.POST_GLOW_SPHERE),
            localRendererEnabled = false,
            effectGraphEnabled = true
        )
    }

    override fun initialize(instance: RenderEntityInstance<TestPersistentGlowSphereEntity>) {
        PostGlowSphereRenderer.initialize()
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<TestPersistentGlowSphereEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        collector.submit(
            BuiltinRenderEffectDescriptors.postGlowSphere(
                effectId = ID.toString(),
                sourceInstanceId = entity.uuid.toString(),
                entity = entity,
                frameContext = input.frameContext,
                config = entity.createGlowConfig()
            )
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
