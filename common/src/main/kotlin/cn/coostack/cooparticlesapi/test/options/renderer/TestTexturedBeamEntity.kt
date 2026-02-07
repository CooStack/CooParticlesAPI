package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.*

class TestTexturedBeamEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_textured_beam"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestTexturedBeamEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.width)
                buf.writeFloat(entity.height)
                buf.writeFloat(entity.beamColor.x)
                buf.writeFloat(entity.beamColor.y)
                buf.writeFloat(entity.beamColor.z)
                buf.writeFloat(entity.beamColor.w)
            },
            { buf, entity ->
                entity.width = buf.readFloat()
                entity.height = buf.readFloat()
                entity.beamColor = Vector4f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )

        private val quadBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genSquareUV(
                    Vector3f(-0.5f, -0.5f, 0f),
                    Vector3f(0.5f, -0.5f, 0f),
                    Vector3f(0.5f, 0.5f, 0f),
                    Vector3f(-0.5f, 0.5f, 0f)
                ),
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
        }

        private val beamShader = ShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/beam.fsh")
            .build()

        private val beamTexture = IdentifierTexture(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID,
                "item/test_style.png"
            )
        )

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            quadBuffer.init()
            beamShader.init()
            beamTexture.init()
        }
    }

    var width by tracked(0.6f)
    var height by tracked(3.0f)
    var beamColor by tracked(Vector4f(0.5f, 0.9f, 1.0f, 1.0f))
    private val size = Vector2f()

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
        RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE)
        RenderSystem.depthMask(false)
        beamShader.useOnContext {
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            size.set(width, height)
            setFloat2("size", size)
            setFloat4("color", beamColor)
            setFloat("time", getTime(tickDelta))
            setInt("beamTex", 0)
            glActiveTexture(GL_TEXTURE0)
            beamTexture.useOnCurrent()
            quadBuffer.draw()
            beamTexture.reset()
        }
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }
}
