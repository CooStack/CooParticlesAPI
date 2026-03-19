package cn.coostack.cooparticlesapi.test.options.renderer.cases

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloom
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.runtime.CompositeMode
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityReleaseHook
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityUpdateHook
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.test.options.renderer.RenderEntityExampleSupport
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.sin

/**
 * RenderEntity V2 API 总览案例。
 *
 * 推荐把这个类当成“第一份教材”看，因为它把以下 API 串成了一条完整链路：
 * - 实体本体：`createCodec`、`tracked`、`serverTick`、`clientTick`、`loadProfileFromEntity`、`shouldSync`
 * - renderer 侧：`createVisualProfile`、`describeFeatures`、`initialize`、`update`、`renderLocal`
 *   `collectRenderContributions`、`release`
 *
 * 视觉上它只是一个会同步脉冲的能量球，重点是让人一眼看懂每个 API 在什么阶段生效。
 */
@CooAutoRegister
class CaseRenderEntityApiOverviewEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    RenderEntity(world, pos),
    WorldPassRenderEntityRenderer<CaseRenderEntityApiOverviewEntity>,
    FramePostRenderEntityRenderer<CaseRenderEntityApiOverviewEntity>,
    RenderEntityUpdateHook<CaseRenderEntityApiOverviewEntity>,
    RenderEntityReleaseHook<CaseRenderEntityApiOverviewEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "case_render_entity_api_overview"
        )

        private val localSphereShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/showcase_energy_sphere.fsh")
            .build()

        private var shaderReady = false
        private val clientUpdatePulse = mutableMapOf<String, Float>()
        private val clientSpin = mutableMapOf<String, Float>()

        private fun initStatic() {
            if (shaderReady) {
                return
            }
            shaderReady = true
            RenderEntityExampleSupport.sphereBuffer()
            localSphereShader.init()
        }
    }

    /**
     * 标准同步字段：服务端改动后自动 dirty。
     */
    var radius by tracked(0.95f)

    /**
     * 第二个 tracked 字段，用来展示“参数变化直接驱动渲染”的同步场景。
     */
    var emission by tracked(1.75f)

    /**
     * `syncOnce = true` 的例子。
     * 它更像一个“同步脉冲序号”，只负责触发一次性同步。
     */
    private var syncSequence by tracked(0, syncOnce = true)

    /**
     * 颜色改动走手动 `requestSync()`，用来演示 tracked 与手动同步可以同时使用。
     */
    var accentColor: Vector3f = Vector3f(0.36f, 0.82f, 1.10f)

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> {
        return createCodec(
            factory = { CaseRenderEntityApiOverviewEntity() },
            encodeExtra = { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.emission)
                buf.writeInt(entity.syncSequence)
                buf.writeVector3f(entity.accentColor)
            },
            decodeExtra = { buf, entity ->
                entity.radius = buf.readFloat()
                entity.emission = buf.readFloat()
                entity.syncSequence = buf.readInt()
                entity.accentColor = buf.readVector3f()
                entity.clearDirty()
            }
        )
    }

    override fun getRenderID(): ResourceLocation = ID

    override fun serverTick() {
        radius = 0.92f + sin(age * 0.10f).toFloat() * 0.06f
        emission = 1.65f + sin(age * 0.13f).toFloat() * 0.18f

        if (age % 24 == 0) {
            accentColor = Vector3f(
                0.28f + 0.10f * sin(age * 0.17f).toFloat(),
                0.72f + 0.12f * sin(age * 0.11f + 1.2f).toFloat(),
                1.02f + 0.08f * sin(age * 0.09f + 2.4f).toFloat()
            )
            syncSequence += 1
            requestSync()
        }
    }

    override fun clientTick() {
        val key = uuid.toString()
        clientSpin[key] = (clientSpin[key] ?: 0.0f) + 0.025f
    }

    override fun shouldSync(): Boolean {
        return super.shouldSync() || age % 20 == 0
    }

    override fun loadProfileFromEntity(another: RenderEntity) {
        super.loadProfileFromEntity(another)
        another as CaseRenderEntityApiOverviewEntity
        radius = another.radius
        emission = another.emission
        syncSequence = another.syncSequence
        accentColor = Vector3f(another.accentColor)
        clearDirty()
    }

    override fun createVisualProfile(entity: CaseRenderEntityApiOverviewEntity): RenderEntityVisualProfile {
        return RenderEntityVisualProfile(
            compositeMode = CompositeMode.ADDITIVE,
            needsSceneColorCopy = true,
            needsSceneDepth = true,
            frameEffectsEnabled = true,
            renderPriority = 180
        )
    }

    override fun describeFeatures(entity: CaseRenderEntityApiOverviewEntity): RenderEntityFeatureSet {
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
                BuiltinRenderEffectTypes.WORLD_LIGHT
            ),
            localRendererEnabled = true,
            effectGraphEnabled = true
        )
    }

    override fun initialize(instance: RenderEntityInstance<CaseRenderEntityApiOverviewEntity>) {
        initStatic()
        val key = instance.entity.uuid.toString()
        clientUpdatePulse[key] = 0.0f
        clientSpin[key] = 0.0f
    }

    override fun update(
        instance: RenderEntityInstance<CaseRenderEntityApiOverviewEntity>,
        entity: CaseRenderEntityApiOverviewEntity
    ) {
        clientUpdatePulse[entity.uuid.toString()] = 0.55f
    }

    override fun renderLocal(input: LocalRenderInput<CaseRenderEntityApiOverviewEntity>) {
        val entity = input.instance.entity
        val key = entity.uuid.toString()
        val syncFlash = clientUpdatePulse[key] ?: 0.0f
        clientUpdatePulse[key] = (syncFlash - 0.05f).coerceAtLeast(0.0f)
        val spin = (clientSpin[key] ?: 0.0f) + 0.009f
        clientSpin[key] = spin

        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.depthMask(false)
        try {
            localSphereShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.rotateY(spin)
                input.modelMatrix.scale(entity.radius)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat3("color", entity.accentColor)
                setFloat("intensity", entity.emission + syncFlash * 0.6f)
                setFloat("alpha", 0.18f + syncFlash * 0.06f)
                setFloat("rimPower", 2.7f)
                setFloat("fillStrength", 0.82f)
                setFloat("pulseSpeed", 1.2f)
                setFloat("noiseScale", 3.4f)
                RenderEntityExampleSupport.sphereBuffer().draw()
                input.modelMatrix.popMatrix()
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
        input: RenderContributionInput<CaseRenderEntityApiOverviewEntity>,
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
                        color = Vector3f(entity.accentColor),
                        radius = entity.radius * 1.2f,
                        intensity = entity.emission * 0.45f,
                        softness = 0.52f,
                        haloProfile = 0.28f
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
                        color = Vector3f(entity.accentColor),
                        radius = entity.radius * 0.95f,
                        intensity = entity.emission * 0.30f,
                        softness = 0.62f,
                        haloRadiusScale = 2.6f,
                        haloOpacity = 0.78f,
                        blurSigma = 8.0f,
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
                        color = Vector3f(entity.accentColor),
                        radius = entity.radius * 3.0f,
                        intensity = entity.emission * 0.42f
                    )
                )
            }
        )
    }

    override fun release(instance: RenderEntityInstance<CaseRenderEntityApiOverviewEntity>) {
        val key = instance.entity.uuid.toString()
        clientUpdatePulse.remove(key)
        clientSpin.remove(key)
    }
}
