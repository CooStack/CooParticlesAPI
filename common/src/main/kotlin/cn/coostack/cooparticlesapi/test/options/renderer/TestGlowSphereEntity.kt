package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
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
import org.lwjgl.opengl.GL33.*

class TestGlowSphereEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_glow_sphere"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestGlowSphereEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.intensity)
                buf.writeFloat(entity.glowColor.x)
                buf.writeFloat(entity.glowColor.y)
                buf.writeFloat(entity.glowColor.z)
            },
            { buf, entity ->
                entity.radius = buf.readFloat()
                entity.intensity = buf.readFloat()
                entity.glowColor = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )

        private val sphereBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genBall(1.0f, 32, 32),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val glowShader = ShaderProgramBuilder()
            .vertex("test/vtx/glow_sphere.vsh")
            .fragment("test/frag/glow_sphere.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            sphereBuffer.init()
            glowShader.init()
        }
    }

    var radius by tracked(0.8f)
    var intensity by tracked(1.6f)
    var glowColor by tracked(Vector3f(0.7f, 0.9f, 1.0f))

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    override fun release() {
    }

    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GL_ONE, GL_ONE)
        RenderSystem.depthMask(false)
        glowShader.useOnContext {
            matrices.pushMatrix()
            matrices.scale(radius, radius, radius)
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat3("color", glowColor)
            setFloat("intensity", intensity)
            sphereBuffer.draw()
            matrices.popMatrix()
        }
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }
}
