package cn.coostack.cooparticlesapi.test.renderer

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.shader.CustomShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.shader.IdentifierGlShader
import cn.coostack.cooparticlesapi.renderer.shader.shader.ShaderType
import cn.coostack.cooparticlesapi.renderer.shader.uniform.GlFloatUnformProvider
import cn.coostack.cooparticlesapi.renderer.shader.uniform.GlMatrix4fvUnformProvider
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.joml.Matrix4f
import kotlin.apply

class TestRendererEntity(world: World?) : RenderEntity(world) {
    val shader = CustomShaderProgram(
        IdentifierGlShader(Identifier.of(CooParticleAPI.MOD_ID, "shaders/test/test_vertex.vsh"), ShaderType.VERTEX),
        IdentifierGlShader(Identifier.of(CooParticleAPI.MOD_ID, "shaders/test/test_frag.fsh"), ShaderType.FRAGMENT),
        CooVertexFormat.POINT_FORMAT
    )
    val solidBallShader = CustomShaderProgram(
        IdentifierGlShader(Identifier.of(CooParticleAPI.MOD_ID, "shaders/test/solid_vertex.vsh"), ShaderType.VERTEX),
        IdentifierGlShader(Identifier.of(CooParticleAPI.MOD_ID, "shaders/test/solid_frag.fsh"), ShaderType.FRAGMENT),
        CooVertexFormat.POINT_FORMAT
    )
    val projProvider = GlMatrix4fvUnformProvider("projMat", Matrix4f())
    val viewProvider = GlMatrix4fvUnformProvider("viewMat", Matrix4f())
    val transProvider = GlMatrix4fvUnformProvider("transMat", Matrix4f())

    val timeProvider = GlFloatUnformProvider("u_time", 0f)

    companion object {
        val codec: PacketCodec<PacketByteBuf, RenderEntity> = PacketCodec.ofStatic<PacketByteBuf, RenderEntity>(
            { buf, data ->
                encodeBase(buf, data)
            }, {
                val instance = TestRendererEntity(null)
                decodeBase(it, instance)
                instance
            }
        )
        val id: Identifier = Identifier.of(CooParticleAPI.MOD_ID, "test_shader")
    }

    override fun initialize() {
        shader.vertexes.addAll(
            ShaderUtil.genBall(64f, 64, 64)
        )
        solidBallShader.vertexes.addAll(
            ShaderUtil.genBall(60f, 64, 64)
        )
        shader.init()
        solidBallShader.init()
        shader.provides.apply {
            add(projProvider)
            add(viewProvider)
            add(transProvider)
            add(timeProvider)
        }
        solidBallShader.provides.apply {
            add(projProvider)
            add(viewProvider)
            add(transProvider)
            add(timeProvider)
        }
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, RenderEntity> {
        return codec
    }

    override fun getRenderID(): Identifier {
        return id
    }

    override fun release() {
        shader.release()
    }

    override fun render(
        matrices: MatrixStack, viewMatrix: Matrix4f,
        projMatrix: Matrix4f, tickDelta: Float
    ) {
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        projProvider.setValue(projMatrix)
        viewProvider.setValue(viewMatrix)
        transProvider.setValue(matrices.peek().positionMatrix)
        timeProvider.setValue(getTime(tickDelta))
        solidBallShader.draw()
        RenderSystem.depthMask(false)
        shader.draw()
        RenderSystem.disableBlend()
        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
    }
}