package cn.coostack.cooparticlesapi.test.options.renderer.combat

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloom
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.test.options.renderer.RenderEntityExampleSupport
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.max

data class CombatExplosionShockwaveRequest(
    val entity: CombatExplosionEntity,
    val frameContext: RenderFrameContext
)

/**
 * 实战案例：爆炸。
 *
 * 这个类把爆炸拆成了三层：
 * - world pass：一个快速膨胀、快速衰减的能量球；
 * - frame-post descriptor：screen glow + bloom + world light；
 * - 自定义 descriptor：屏幕空间冲击波扭曲。
 *
 * 对照代码读的话，很容易看出 V2 里“局部几何 / descriptor 图 / 自定义 executor”三层是怎么组合的。
 */
@CooAutoRegister
class CombatExplosionEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    WorldPassRenderEntityRenderer<CombatExplosionEntity>,
    FramePostRenderEntityRenderer<CombatExplosionEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "combat_explosion"
        )
        val SHOCKWAVE_EFFECT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "effect/combat_explosion_shockwave"
        )
        private val SHOCKWAVE_CAPABILITIES = setOf(
            RenderBackendCapability.FINAL_FRAME_POST,
            RenderBackendCapability.SCENE_COLOR_COPY,
            RenderBackendCapability.SCENE_DEPTH_READ
        )

        private val localSphereShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/showcase_energy_sphere.fsh")
            .build()
        private val smokeShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/explosion_smoke.fsh")
            .build()
        private val shockwaveShader = AdvancedShaderProgramBuilder()
            .vertex("pipe/vertexes/screen.vsh")
            .fragment("test/frag/shockwave_ring_composite.fsh")
            .build()

        private var shadersReady = false

        private fun initStatic() {
            if (shadersReady) {
                return
            }
            shadersReady = true
            RenderEntityExampleSupport.sphereBuffer()
            RenderEntityExampleSupport.billboardBuffer()
            RenderEntityExampleSupport.screenBuffer()
            localSphereShader.init()
            smokeShader.init()
            shockwaveShader.init()
        }

        fun renderRequests(requests: List<CombatExplosionShockwaveRequest>) {
            requests.forEach { request ->
                request.entity.renderShockwave(request.frameContext)
            }
        }
    }

    @field:CodecField
    var blastRadius: Float = 0.8f

    @field:CodecField
    var shockRadius: Float = 1.4f

    @field:CodecField
    var brightness: Float = 5.0f

    @field:CodecField
    var blastColor: Vector3f = Vector3f(1.24f, 0.62f, 0.20f)

    override fun getRenderID(): ResourceLocation = ID

    override fun serverTick() {
        blastRadius += 0.18f
        shockRadius += 0.34f
        brightness = max(0.35f, brightness - 0.16f)
        if (age % 2 == 0) {
            requestSync()
        }
        if (age > 28) {
            remove()
        }
    }

    override fun initialize(instance: RenderEntityInstance<CombatExplosionEntity>) {
        initStatic()
    }

    override fun describeFeatures(entity: CombatExplosionEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS, RenderFrameStage.FRAME_POST),
            requestedSceneTargets = setOf(
                RenderSceneTargets.POST,
                RenderSceneTargets.SCENE_COLOR,
                RenderSceneTargets.SCENE_DEPTH,
                RenderSceneTargets.LIGHT
            ),
            effectTypes = setOf(
                BuiltinRenderEffectTypes.SCREEN_GLOW,
                BuiltinRenderEffectTypes.PERSISTENT_BLOOM,
                BuiltinRenderEffectTypes.WORLD_LIGHT,
                SHOCKWAVE_EFFECT
            ),
            localRendererEnabled = true,
            effectGraphEnabled = true
        )
    }

    override fun renderLocal(input: LocalRenderInput<CombatExplosionEntity>) {
        val entity = input.instance.entity
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE
        )
        RenderSystem.depthMask(false)
        try {
            localSphereShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.scale(entity.blastRadius)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat3("color", entity.blastColor)
                setFloat("intensity", entity.brightness * 0.82f)
                setFloat("alpha", 0.20f)
                setFloat("rimPower", 2.0f)
                setFloat("fillStrength", 0.76f)
                setFloat("pulseSpeed", 3.8f)
                setFloat("noiseScale", 4.8f)
                RenderEntityExampleSupport.sphereBuffer().draw()
                input.modelMatrix.popMatrix()
            }

            RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            )
            val dustOffsets = arrayOf(
                Vector3f(-0.72f, 0.10f, 0.12f),
                Vector3f(0.64f, 0.18f, 0.22f),
                Vector3f(0.22f, -0.12f, -0.74f),
                Vector3f(-0.18f, 0.22f, 0.68f),
                Vector3f(0.76f, -0.08f, -0.18f),
                Vector3f(-0.60f, 0.16f, -0.34f)
            )
            smokeShader.useOnContext {
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat("alpha", (entity.brightness / 5.0f).coerceAtMost(1.0f) * 0.42f)
                setFloat3("smokeColor", Vector3f(0.42f, 0.38f, 0.34f))
                for (offset in dustOffsets) {
                    input.modelMatrix.pushMatrix()
                    input.modelMatrix.translate(
                        offset.x * entity.blastRadius,
                        offset.y * entity.blastRadius,
                        offset.z * entity.blastRadius
                    )
                    val dustSize = entity.blastRadius * 0.95f + 0.65f
                    setMatrix4("transMat", input.modelMatrix)
                    setFloat2("size", org.joml.Vector2f(dustSize, dustSize))
                    RenderEntityExampleSupport.billboardBuffer().draw()
                    input.modelMatrix.popMatrix()
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<CombatExplosionEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        collector.submit(
            BuiltinRenderEffectDescriptors.screenGlow(
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext
            ) { _, output ->
                output.add(
                    ScreenGlow(
                        position = Vector3f(entity.pos.x.toFloat(), entity.pos.y.toFloat(), entity.pos.z.toFloat()),
                        color = Vector3f(entity.blastColor),
                        radius = entity.blastRadius * 2.2f,
                        intensity = entity.brightness * 0.85f,
                        softness = 0.48f,
                        haloProfile = 0.42f
                    )
                )
            }
        )
        collector.submit(
            BuiltinRenderEffectDescriptors.persistentBloom(
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext
            ) { _, output ->
                output.add(
                    PersistentBloom(
                        position = Vector3f(entity.pos.x.toFloat(), entity.pos.y.toFloat(), entity.pos.z.toFloat()),
                        color = Vector3f(entity.blastColor),
                        radius = entity.blastRadius * 1.6f,
                        intensity = entity.brightness * 0.50f,
                        softness = 0.56f,
                        haloRadiusScale = 2.8f,
                        haloOpacity = 0.80f,
                        blurSigma = 7.6f,
                        blurRange = 5.4f
                    )
                )
            }
        )
        collector.submit(
            BuiltinRenderEffectDescriptors.worldLight(
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext
            ) { output ->
                output.add(
                    WorldLight(
                        position = Vector3f(entity.pos.x.toFloat(), entity.pos.y.toFloat(), entity.pos.z.toFloat()),
                        color = Vector3f(entity.blastColor),
                        radius = entity.shockRadius * 2.5f,
                        intensity = entity.brightness * 0.55f
                    )
                )
            }
        )
        collector.submit(
            RenderEffectDescriptor(
                effectType = SHOCKWAVE_EFFECT,
                effectId = "${ID}_shockwave",
                sourceInstanceId = entity.uuid.toString(),
                requiredCapabilities = SHOCKWAVE_CAPABILITIES,
                payload = CombatExplosionShockwaveRequest(entity, input.frameContext)
            )
        )
    }

    private fun renderShockwave(frameContext: RenderFrameContext) {
        val projection = RenderEntityExampleSupport.projectSphere(
            worldPos = pos,
            radius = shockRadius,
            viewMatrix = frameContext.viewMatrix,
            projMatrix = frameContext.projMatrix
        )
        if (!projection.visible) {
            return
        }

        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.disableBlend()
        RenderSystem.depthMask(false)
        try {
            RenderEntityExampleSupport.withSceneTextures { colorSlot, depthSlot ->
                shockwaveShader.useOnContext {
                    setInt("sceneTex", colorSlot)
                    setInt("sceneDepth", depthSlot)
                    setFloat2("screenSize", RenderEntityExampleSupport.mainRenderSize())
                    setFloat2("effectCenterUv", projection.centerUv)
                    setFloat("radiusPx", projection.radiusPx)
                    setFloat("thicknessPx", projection.radiusPx * 0.22f)
                    setFloat("centerDepth01", projection.depth01)
                    setFloat3("tint", blastColor)
                    setFloat("distortionStrength", 12.0f)
                    RenderEntityExampleSupport.screenBuffer().draw()
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
        }
    }
}
