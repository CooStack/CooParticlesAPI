package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferCache
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class TestComputeShaderEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    FramePostRenderEntityRenderer<TestComputeShaderEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_compute_shader"
        )

        private val COMPUTE_INPUT = ShaderBufferRegistry.register(
            ShaderBufferLayout.builder<TestComputeShaderEntity>("TestComputeShaderInput")
                .float("time") { entity -> entity.age.toFloat() }
                .build()
        )

        private val PROGRAM = AdvancedShaderProgramBuilder()
            .compute("core/compute/noop.comp")
            .bufferLayout(COMPUTE_INPUT)
            .buildCompute()
    }

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<TestComputeShaderEntity>) {
    }

    override fun describeFeatures(entity: TestComputeShaderEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.FRAME_POST),
            effectTypes = setOf(BuiltinRenderEffectTypes.COMPUTE_DISPATCH),
            localRendererEnabled = false,
            effectGraphEnabled = true
        )
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<TestComputeShaderEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        collector.submit(
            BuiltinRenderEffectDescriptors.computeDispatch(
                effectId = ID.toString(),
                sourceInstanceId = entity.uuid.toString(),
                program = PROGRAM,
                groupX = 1,
                prepare = {
                    val buffer = ShaderBufferCache.getOrCreate(COMPUTE_INPUT)
                    buffer.upload(entity)
                }
            )
        )
    }
}
