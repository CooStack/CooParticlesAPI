package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectCollector
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectInput
import cn.coostack.cooparticlesapi.renderer.effects.FrameEffectSubmission
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4fStack
import org.joml.Vector3f

@CooAutoRegister
class TestBlackHoleEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos) {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_black_hole"
        )
    }

    @field:CodecField
    var radius: Float = 2.8f

    @field:CodecField
    var coreRadius: Float = 0.32f

    @field:CodecField
    var distortionStrength: Float = 0.28f

    @field:CodecField
    var diskNormal: Vector3f = Vector3f(0f, 1f, 0f)

    @field:CodecField
    var diskThickness: Float = 0.12f

    @field:CodecField
    var diskWidth: Float = 0.42f

    @field:CodecField
    var spinSpeed: Float = 1.2f

    @field:CodecField
    var ringColor: Vector3f = Vector3f(1.0f, 0.58f, 0.18f)

    override fun getRenderID(): ResourceLocation = ID
}

class TestBlackHoleEntityRenderer : RenderEntityRenderer<TestBlackHoleEntity> {
    companion object {
        private val sphereBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genBall(1f, 96, 144),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val blackHoleShader = ShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/black_hole_mask.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            sphereBuffer.init()
            blackHoleShader.init()
        }
    }

    override fun initialize(instance: RenderEntityInstance<TestBlackHoleEntity>) {
        initStatic()
    }

    override fun collectFrameEffects(input: FrameEffectInput<TestBlackHoleEntity>, collector: FrameEffectCollector) {
        val entity = input.instance.entity
        collector.submit(
            FrameEffectSubmission(
                effectId = TestBlackHoleEntity.ID.toString(),
                sourceInstanceId = entity.uuid.toString()
            ) {
                renderFrameEffect(entity, input)
            }
        )
    }

    private fun renderFrameEffect(entity: TestBlackHoleEntity, input: FrameEffectInput<TestBlackHoleEntity>) {
        val matrices = Matrix4fStack(16)
        RenderUtil.setRenderStackWithEntity(matrices, entity, input.frameContext.tickDelta)

        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.depthMask(false)
        try {
            blackHoleShader.useOnContext {
                matrices.pushMatrix()
                matrices.scale(entity.radius, entity.radius, entity.radius)
                setMatrix4("projMat", input.frameContext.projMatrix)
                setMatrix4("viewMat", input.frameContext.viewMatrix)
                setMatrix4("transMat", matrices)
                setFloat("time", entity.getTime(input.frameContext.tickDelta))
                setFloat("coreRadius", entity.coreRadius)
                setFloat("distortionStrength", entity.distortionStrength)
                setFloat3("diskNormal", entity.diskNormal)
                setFloat("diskThickness", entity.diskThickness)
                setFloat("diskWidth", entity.diskWidth)
                setFloat("spinSpeed", entity.spinSpeed)
                setFloat3("ringColor", entity.ringColor)
                sphereBuffer.draw()
                matrices.popMatrix()
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
        }
    }
}
