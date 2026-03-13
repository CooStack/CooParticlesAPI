package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityRenderPass
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE7
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.glActiveTexture
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glGetInteger

class TestAccretionDiskEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_accretion_disk"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestAccretionDiskEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.schwarzschildRadius)
                buf.writeFloat(entity.diskInnerRadius)
                buf.writeFloat(entity.diskOuterRadius)
                buf.writeFloat(entity.diskHalfThickness)
                buf.writeFloat(entity.diskTemperatureScale)
                buf.writeFloat(entity.lensingStrength)
                buf.writeFloat(entity.spinSpeed)
                buf.writeInt(entity.stepCount)
                buf.writeFloat(entity.maxDistance)
                buf.writeFloat(entity.diskDensity)
                buf.writeFloat(entity.diskEmissionStrength)
                buf.writeFloat(entity.diskNormal.x)
                buf.writeFloat(entity.diskNormal.y)
                buf.writeFloat(entity.diskNormal.z)
                buf.writeFloat(entity.diskColor.x)
                buf.writeFloat(entity.diskColor.y)
                buf.writeFloat(entity.diskColor.z)
            },
            { buf, entity ->
                entity.radius = buf.readFloat()
                entity.schwarzschildRadius = buf.readFloat()
                entity.diskInnerRadius = buf.readFloat()
                entity.diskOuterRadius = buf.readFloat()
                entity.diskHalfThickness = buf.readFloat()
                entity.diskTemperatureScale = buf.readFloat()
                entity.lensingStrength = buf.readFloat()
                entity.spinSpeed = buf.readFloat()
                entity.stepCount = buf.readInt()
                entity.maxDistance = buf.readFloat()
                entity.diskDensity = buf.readFloat()
                entity.diskEmissionStrength = buf.readFloat()
                entity.diskNormal = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
                entity.diskColor = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )

        private val screenBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genSquareUVScreen(
                    Vector3f(-1f, 1f, 0f),
                    Vector3f(1f, 1f, 0f),
                    Vector3f(1f, -1f, 0f),
                    Vector3f(-1f, -1f, 0f)
                ),
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
        }

        private val sceneShader = ShaderProgramBuilder()
            .vertex("pipe/vertexes/screen.vsh")
            .fragment("test/frag/accretion_disk.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            screenBuffer.init()
            sceneShader.init()
        }

        fun reloadStaticResources() {
            screenBuffer.release()
            sceneShader.release()
            initialized = false
        }
    }

    /**
     * 世界单位，既是 RenderEntity 影响域包围球半径，也是 shader 的积分裁剪边界。
     */
    var radius by tracked(12.0f)
    /**
     * Schwarzschild 事件视界半径 Rs，单位与世界坐标一致。
     * shader 内约定 G = c = 1。
     */
    var schwarzschildRadius by tracked(0.92f)
    var diskInnerRadius by tracked(2.9f)
    var diskOuterRadius by tracked(7.8f)
    var diskHalfThickness by tracked(0.30f)
    /**
     * 以 Kelvin 近似标定盘内缘温度。
     */
    var diskTemperatureScale by tracked(9600.0f)
    var lensingStrength by tracked(1.08f)
    var spinSpeed by tracked(0.92f)
    var stepCount by tracked(168)
    var maxDistance by tracked(34.0f)
    var diskDensity by tracked(4.6f)
    var diskEmissionStrength by tracked(2.35f)
    var diskNormal by tracked(Vector3f(0f, 1f, 0f))
    /**
     * 盘的轻度色调偏置，不取代物理温度映射。
     */
    var diskColor by tracked(Vector3f(1.18f, 1.06f, 0.78f))

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    override fun getRenderPass(): RenderEntityRenderPass = RenderEntityRenderPass.POST_PROCESS

    override fun release() {
    }

    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        val minecraft = Minecraft.getInstance()
        val cameraPosition = minecraft.gameRenderer.mainCamera.position
        val cameraWorldPos = Vector3f(
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        val blackHoleWorldPos = Vector3f(
            pos.x.toFloat(),
            pos.y.toFloat(),
            pos.z.toFloat()
        )
        val blackHoleCameraRelativePos = Vector3f(blackHoleWorldPos).sub(cameraWorldPos)
        val normalizedDiskNormal = Vector3f(diskNormal).normalize()
        val inverseProjMatrix = Matrix4f(projMatrix).invert()
        val viewRotationMatrix = Matrix3f(viewMatrix)
        val inverseViewRotationMatrix = Matrix3f(viewRotationMatrix).invert()
        val effectRadiusWorld = maxOf(
            radius,
            diskOuterRadius + schwarzschildRadius * 4.5f,
            schwarzschildRadius * 6.0f
        )
        val lightingInfluenceWorld = maxOf(
            radius * 1.55f,
            diskOuterRadius * 2.15f + diskEmissionStrength * 3.40f,
            effectRadiusWorld * 1.30f
        )
        val traceDistanceWorld = maxOf(maxDistance, effectRadiusWorld * 2.35f)
        val screenSize = Vector2f(
            minecraft.mainRenderTarget.width.toFloat(),
            minecraft.mainRenderTarget.height.toFloat()
        )

        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        sceneShader.useOnContext {
            setMatrix4("projMat", projMatrix)
            setMatrix4("inverseProjMat", inverseProjMatrix)
            setMatrix3f("viewRotationMat", viewRotationMatrix)
            setMatrix3f("inverseViewRotationMat", inverseViewRotationMatrix)
            setFloat("time", getTime(tickDelta))
            setFloat2("screenSize", screenSize)
            setFloat3("blackHoleWorldPos", blackHoleWorldPos)
            setFloat3("blackHoleCameraRelativePos", blackHoleCameraRelativePos)
            setFloat("effectRadiusWorld", effectRadiusWorld)
            setFloat("lightingInfluenceWorld", lightingInfluenceWorld)
            setFloat("schwarzschildRadius", schwarzschildRadius)
            setFloat("diskInnerRadius", diskInnerRadius)
            setFloat("diskOuterRadius", diskOuterRadius)
            setFloat("diskHalfThickness", diskHalfThickness)
            setFloat("diskTemperatureScale", diskTemperatureScale)
            setFloat("lensingStrength", lensingStrength)
            setFloat("spinSpeed", spinSpeed)
            setInt("stepCount", stepCount.coerceIn(48, 192))
            setFloat("maxDistance", traceDistanceWorld)
            setFloat("diskDensity", diskDensity)
            setFloat("diskEmissionStrength", diskEmissionStrength)
            setFloat3("diskColor", diskColor)
            setFloat3("diskNormal", normalizedDiskNormal)
            val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
            glActiveTexture(GL_TEXTURE7)
            val previousDepthBinding = glGetInteger(GL_TEXTURE_BINDING_2D)
            glBindTexture(GL_TEXTURE_2D, minecraft.mainRenderTarget.depthTextureId)
            setInt("sceneDepth", 7)
            screenBuffer.draw()
            glBindTexture(GL_TEXTURE_2D, previousDepthBinding)
            glActiveTexture(previousActiveTexture)
        }
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
    }
}
