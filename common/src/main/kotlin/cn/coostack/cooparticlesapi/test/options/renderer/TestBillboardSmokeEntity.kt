package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
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

class TestBillboardSmokeEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_billboard_smoke"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestBillboardSmokeEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.width)
                buf.writeFloat(entity.height)
                buf.writeFloat(entity.alpha)
                buf.writeFloat(entity.smokeColor.x)
                buf.writeFloat(entity.smokeColor.y)
                buf.writeFloat(entity.smokeColor.z)
            },
            { buf, entity ->
                entity.width = buf.readFloat()
                entity.height = buf.readFloat()
                entity.alpha = buf.readFloat()
                entity.smokeColor = Vector3f(
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

        private val smokeShader = ShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/smoke.fsh")
            .build()

        private val smokeTextures = SimpleTextures().apply {
            addTexture(
                IdentifierTexture(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "test/dirt.png"
                    )
                )
            )
        }

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            quadBuffer.init()
            smokeShader.init()
            smokeTextures.init()
        }

        fun reloadStaticResources() {
            quadBuffer.release()
            smokeShader.release()
            smokeTextures.release()
            initialized = false
        }
    }

    var width by tracked(1.6f)
    var height by tracked(1.2f)
    var alpha by tracked(0.85f)
    var smokeColor by tracked(Vector3f(0.85f, 0.85f, 0.85f))
    private val size = Vector2f()
    private val color = Vector4f()

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
        RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        RenderSystem.depthMask(false)
        smokeShader.useOnContext {
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            size.set(width, height)
            setFloat2("size", size)
            color.set(smokeColor, alpha)
            setFloat4("color", color)
            setFloat("time", getTime(tickDelta))
            setInt("smokeTex", 0)
            smokeTextures.drawWith {
                quadBuffer.draw()
            }
        }
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }
}
