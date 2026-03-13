package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.from
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.OutputDepthPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.texture.SupplierTexture
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.function.Supplier
import kotlin.math.max

object ClientWorldLightManager {
    private const val MAX_WORLD_LIGHTS = 8

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()

    private val collectedLights = ArrayList<WorldLight>(MAX_WORLD_LIGHTS * 2)
    private val sortedLights = ArrayList<WorldLight>(MAX_WORLD_LIGHTS)
    private val lightPositionData = FloatArray(MAX_WORLD_LIGHTS * 3)
    private val lightColorData = FloatArray(MAX_WORLD_LIGHTS * 3)
    private val lightNormalData = FloatArray(MAX_WORLD_LIGHTS * 3)
    private val lightData = FloatArray(MAX_WORLD_LIGHTS * 4)

    private var prepared = false
    private var worldLightCount = 0
    private var cameraWorldPos = Vector3f()
    private var projMatrix = Matrix4f()
    private var viewRotationMatrix = Matrix3f()
    private var inverseProjMatrix = Matrix4f()
    private var inverseViewRotationMatrix = Matrix3f()
    private var screenSize = Vector2f(1f, 1f)

    private fun createSceneCopyPipe(): ExternalTextureShaderPipe {
        val sceneCopyTextures = SimpleTextures().apply {
            addTexture(SupplierTexture { minecraft.mainRenderTarget.colorTextureId })
        }
        return ExternalTextureShaderPipe(sceneCopyTextures, Supplier { -1 })
    }

    private val pipeline = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "world_light_composite"
        )
    ).apply {
        enableBlend = false

        val sceneCopyPipe = createSceneCopyPipe()
        val sceneDepthPipe = OutputDepthPipe(Supplier { minecraft.mainRenderTarget.depthTextureId })
        addPipe(sceneCopyPipe)
        addPipe(sceneDepthPipe)
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "world/frag/world_light_composite.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { minecraft.mainRenderTarget.depthTextureId },
                2
            ).addRenderHandler { program ->
                program.setInt("sceneTex", 0)
                program.setInt("sceneDepth", 1)
                program.setInt("worldLightCount", worldLightCount)
                program.setFloat2("screenSize", screenSize)
                program.setFloat3("cameraWorldPos", cameraWorldPos)
                program.setMatrix4("projMat", projMatrix)
                program.setMatrix3f("viewRotationMat", viewRotationMatrix)
                program.setMatrix4("inverseProjMat", inverseProjMatrix)
                program.setMatrix3f("inverseViewRotationMat", inverseViewRotationMatrix)
                program.setFloat3Array("lightPositions", lightPositionData)
                program.setFloat3Array("lightColors", lightColorData)
                program.setFloat3Array("lightNormals", lightNormalData)
                program.setFloat4Array("lightData", lightData)
            }
        )

        beforeRender {
            sceneCopyPipe.capture()
        }
        setLinkerFunc { linker ->
            linker.from(sceneCopyPipe, 0).to(valueOutput!!, 0)
            linker.from(sceneDepthPipe, 0).to(valueOutput!!, 1)
        }
    }

    @JvmStatic
    fun initOnClient() {
        ClientRenderPipelineManager.register(pipeline)
    }

    @JvmStatic
    fun clear() {
        prepared = false
        worldLightCount = 0
        collectedLights.clear()
        sortedLights.clear()
        lightPositionData.fill(0f)
        lightColorData.fill(0f)
        lightNormalData.fill(0f)
        lightData.fill(0f)
    }

    @JvmStatic
    fun render(entities: Collection<RenderEntity>, tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        collectLights(entities, tickDelta, viewMatrix, projMatrix)
        if (!prepared || worldLightCount <= 0) {
            return
        }
        minecraft.mainRenderTarget.bindWrite(false)
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        pipeline.render()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
    }

    private fun collectLights(
        entities: Collection<RenderEntity>,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ) {
        clear()

        val cameraPosition = minecraft.gameRenderer.mainCamera.position
        cameraWorldPos = Vector3f(
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        this.projMatrix = Matrix4f(projMatrix)
        this.viewRotationMatrix = Matrix3f(viewMatrix)
        inverseProjMatrix = Matrix4f(projMatrix).invert()
        inverseViewRotationMatrix = Matrix3f(viewMatrix).invert()
        screenSize = Vector2f(
            minecraft.mainRenderTarget.width.toFloat(),
            minecraft.mainRenderTarget.height.toFloat()
        )

        entities.forEach { entity ->
            if (entity !is WorldLightProvider) {
                return@forEach
            }
            entity.collectWorldLights(tickDelta, collectedLights)
        }

        if (collectedLights.isEmpty()) {
            prepared = false
            return
        }

        sortedLights.addAll(
            collectedLights.sortedBy { light ->
                val distance = Vector3f(light.position).sub(cameraWorldPos).length()
                max(0f, distance - light.radius)
            }.take(MAX_WORLD_LIGHTS)
        )

        worldLightCount = sortedLights.size
        if (worldLightCount <= 0) {
            prepared = false
            return
        }

        sortedLights.forEachIndexed { index, light ->
            val posBase = index * 3
            lightPositionData[posBase] = light.position.x
            lightPositionData[posBase + 1] = light.position.y
            lightPositionData[posBase + 2] = light.position.z

            lightColorData[posBase] = light.color.x
            lightColorData[posBase + 1] = light.color.y
            lightColorData[posBase + 2] = light.color.z

            val normal = Vector3f(light.normal)
            if (normal.lengthSquared() <= 1.0e-6f) {
                normal.set(0f, 1f, 0f)
            } else {
                normal.normalize()
            }
            lightNormalData[posBase] = normal.x
            lightNormalData[posBase + 1] = normal.y
            lightNormalData[posBase + 2] = normal.z

            val dataBase = index * 4
            lightData[dataBase] = light.radius
            lightData[dataBase + 1] = light.intensity
            lightData[dataBase + 2] = light.shape.ordinal.toFloat()
            lightData[dataBase + 3] = light.softness
        }

        prepared = true
    }
}
