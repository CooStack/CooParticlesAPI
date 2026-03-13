package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityRenderPass
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector3f

class TestBlackHoleEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_black_hole"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestBlackHoleEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.coreRadius)
                buf.writeFloat(entity.distortionStrength)
                buf.writeFloat(entity.diskNormal.x)
                buf.writeFloat(entity.diskNormal.y)
                buf.writeFloat(entity.diskNormal.z)
                buf.writeFloat(entity.diskThickness)
                buf.writeFloat(entity.diskWidth)
                buf.writeFloat(entity.spinSpeed)
                buf.writeFloat(entity.ringColor.x)
                buf.writeFloat(entity.ringColor.y)
                buf.writeFloat(entity.ringColor.z)
            },
            { buf, entity ->
                entity.radius = buf.readFloat()
                entity.coreRadius = buf.readFloat()
                entity.distortionStrength = buf.readFloat()
                entity.diskNormal = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
                entity.diskThickness = buf.readFloat()
                entity.diskWidth = buf.readFloat()
                entity.spinSpeed = buf.readFloat()
                entity.ringColor = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )

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

        fun reloadStaticResources() {
            sphereBuffer.release()
            blackHoleShader.release()
            initialized = false
        }
    }

    var radius by tracked(2.8f)
    var coreRadius by tracked(0.32f)
    var distortionStrength by tracked(0.28f)
    var diskNormal by tracked(Vector3f(0f, 1f, 0f))
    var diskThickness by tracked(0.12f)
    var diskWidth by tracked(0.42f)
    var spinSpeed by tracked(1.2f)
    var ringColor by tracked(Vector3f(1.0f, 0.58f, 0.18f))

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    override fun getRenderPass(): RenderEntityRenderPass {
        return RenderEntityRenderPass.POST_PROCESS
    }

    override fun release() {
    }

    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.depthMask(false)
        blackHoleShader.useOnContext {
            matrices.pushMatrix()
            matrices.scale(radius, radius, radius)
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat("time", getTime(tickDelta))
            setFloat("coreRadius", coreRadius)
            setFloat("distortionStrength", distortionStrength)
            setFloat3("diskNormal", diskNormal)
            setFloat("diskThickness", diskThickness)
            setFloat("diskWidth", diskWidth)
            setFloat("spinSpeed", spinSpeed)
            setFloat3("ringColor", ringColor)
            sphereBuffer.draw()
            matrices.popMatrix()
        }
        RenderSystem.depthMask(true)
    }
}
