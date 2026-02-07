package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Random
import org.joml.Vector3f

class TestRendererEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = StreamCodec.of<FriendlyByteBuf, RenderEntity>(
            { buf, data ->
                encodeBase(buf, data)
            }, {
                val instance = TestRendererEntity(null)
                decodeBase(it, instance)
                instance
            }
        )
        val ballBuffer = SimpleVertexBuffer().apply {
            setVertexes(ShaderUtil.genBall(5f, 64, 64), CooVertexFormat.POINT_TEXTURE_UV_FORMAT)
        }
        val ballShader = ShaderProgramBuilder()
            .vertex("core/vertex/point.vsh")
            .fragment("core/fragment/color.fsh")
            .build()
        var initialized = false
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_shader")
        fun initStatic() {
            if (initialized) {
                return
            }
            initialized = true
            ballBuffer.init()
            ballShader.init()
        }
    }

    val random = Random()
    val randomColor = Vector3f(
        random.nextFloat(),
        random.nextFloat(),
        random.nextFloat(),
    )

    override fun initialize() {
        initStatic()
    }


    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> {
        return codec
    }

    override fun getRenderID(): ResourceLocation {
        return id
    }

    override fun release() {
    }

    override fun render(
        matrices: Matrix4fStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    ) {
        RenderSystem.disableCull()
        val stack = PoseStack()
        ballShader.useOnContext {
            matrices.pushMatrix()
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat3("color", randomColor)
            ballBuffer.draw()
            matrices.popMatrix()
        }
    }
}