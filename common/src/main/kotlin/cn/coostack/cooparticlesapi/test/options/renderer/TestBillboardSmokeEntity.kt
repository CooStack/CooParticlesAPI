package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA

@CooAutoRegister
class TestBillboardSmokeEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<TestBillboardSmokeEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_billboard_smoke"
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
    }

    @field:CodecField
    var width: Float = 1.6f

    @field:CodecField
    var height: Float = 1.2f

    @field:CodecField
    var alpha: Float = 0.85f

    @field:CodecField
    var smokeColor: Vector3f = Vector3f(0.85f, 0.85f, 0.85f)

    private val size = Vector2f()
    private val color = Vector4f()

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<TestBillboardSmokeEntity>) {
        initStatic()
    }

    override fun renderLocal(input: LocalRenderInput<TestBillboardSmokeEntity>) {
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        RenderSystem.depthMask(false)
        try {
            smokeShader.useOnContext {
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                size.set(width, height)
                setFloat2("size", size)
                color.set(smokeColor, alpha)
                setFloat4("color", color)
                setFloat("time", getTime(input.tickDelta))
                setInt("smokeTex", 0)
                smokeTextures.drawWith {
                    quadBuffer.draw()
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableDepthTest()
            RenderSystem.disableCull()
        }
    }
}
