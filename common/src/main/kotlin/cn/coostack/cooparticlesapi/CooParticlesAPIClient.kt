package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.coofx.client.CooFXClient
import cn.coostack.cooparticlesapi.coofx.client.CooFxSceneClientRegistry
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.render.CParticleRenderer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.display.CooRenderTypeResourceRegistry
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.backend.IrisSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.model.OpenGlRenderEntityModelExecutor
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.post.PostEffectRuntimeRegistry
import cn.coostack.cooparticlesapi.renderer.pipeline.CooBlockPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineRuntimeEffect
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectRegistry
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegistry
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundManager
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundLoopManager
import cn.coostack.cooparticlesapi.test.TestControlKeyBindings
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.block.client.TestControllerPickClient
import cn.coostack.cooparticlesapi.test.options.display.TestDisplayerStyle
import cn.coostack.cooparticlesapi.test.options.renderer.PostEffectDemoOptions
import cn.coostack.cooparticlesapi.test.options.renderer.pipeline.RenderPipelineExamples
import cn.coostack.cooparticlesapi.test.options.particle.client.BarrierSwordGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.client.ScaleCircleGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.client.SequencedMagicCircleClient
import cn.coostack.cooparticlesapi.test.options.particle.client.TestGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.style.*
import cn.coostack.cooparticlesapi.utils.ClientCameraUtil
import net.irisshaders.iris.api.v0.IrisApi
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.RegistryAccess

object CooParticlesAPIClient {
    @JvmField
    val scheduler = CooScheduler()

    @JvmField
    var irisLoaded = false
    private var selectedRenderBackend: RenderBackend = VanillaSafeRenderBackend
    lateinit var access: RegistryAccess

    /**
     * 初始化客户端注册项、CParticle 图形 program，并应用当前配置中的粒子总量上限。
     *
     * 示例：客户端入口在 common 初始化完成后调用一次 `init()`。
     *
     * 禁止：此处只注册 program，不能执行依赖 GL 上下文的编译；专用服务端也不能调用本方法。
     */
    @JvmStatic
    fun init() {
        irisLoaded = CooParticlesServices.PLATFORM.isModLoaded("iris")
        CParticleSystemManager.configureParticleCountLimit(
            CooParticlesServices.API_CONFIG_MANAGER.getConfig().cparticleCountLimit
        )
        CParticleRenderer.registerProgram()
        TestControlKeyBindings.register()
        initGroup()
        initStyle()
        initParticleType()
        initRender()
        CooFXClient.init()
    }

    @JvmStatic
    fun checkIrisShaderPackUsed(): Boolean {
        return irisLoaded && IrisApi.getInstance().isShaderPackInUse
    }

    @JvmStatic
    fun syncRenderBackend(): RenderBackend {
        selectedRenderBackend = if (checkIrisShaderPackUsed()) {
            IrisSafeRenderBackend
        } else {
            VanillaSafeRenderBackend
        }
        ClientRenderPipelineManager.setActiveBackend(selectedRenderBackend)
        return selectedRenderBackend
    }

    private fun initParticleType() {
        ParticleEmittersManager.init()
        CooModParticles.reg()
    }

    private fun initGroup() {
        ClientParticleGroupManager.register(
            TestGroupClient::class.java,
            TestGroupClient.Provider()
        )
        ClientParticleGroupManager.register(
            ScaleCircleGroupClient::class.java,
            ScaleCircleGroupClient.Provider()
        )
        ClientParticleGroupManager.register(
            BarrierSwordGroupClient::class.java,
            BarrierSwordGroupClient.Provider()
        )
        ClientParticleGroupManager.register(
            SequencedMagicCircleClient::class.java,
            SequencedMagicCircleClient.Provider()
        )
    }

    private fun initStyle() {
        ParticleStyleManager.register(
            ExampleStyle::class.java,
            ExampleStyle.Provider()
        )
        ParticleStyleManager.register(
            TestDisplayerStyle::class.java,
            TestDisplayerStyle.Provider()
        )
        ParticleStyleManager.register(
            ExampleSequencedStyle::class.java,
            ExampleSequencedStyle.Provider()
        )
        ParticleStyleManager.register(
            RomaMagicTestStyle::class.java,
            RomaMagicTestStyle.Provider()
        )
        ParticleStyleManager.register(
            RotateTestStyle::class.java,
            RotateTestStyle.Provider()
        )
        ParticleStyleManager.register(
            TestShapeUtilStyle::class.java,
            TestShapeUtilStyle.Provider()
        )
        ParticleStyleManager.register(
            PointStyle::class.java,
            PointStyle.Provider()
        )
    }

    @JvmStatic
    private var renderInit = false

    /**
     * 在渲染线程准备客户端管线，并编译启动阶段已经注册的 shader program。
     *
     * 示例：首次资源加载或世界渲染时调用，CParticle 不再承担 program 首次编译。
     *
     * 禁止：loader 的普通客户端注册回调没有可用 GL 上下文时不能直接调用。
     */
    @JvmStatic
    fun initShaderPrograms() {
        if (renderInit) return
        renderInit = true
        ClientRenderPipelineManager.init()
        ClientRenderPipelineManager.setActiveBackend(selectedRenderBackend)
        RenderEntityModelExecutors.install(OpenGlRenderEntityModelExecutor)
        PostEffectFrameExecutor.installBackend(OpenGlPostEffectExecutionBackend)
        CParticleCapabilities.detect()
        CParticleGpuSimulator.initializeProgramIfSupported()
        ShaderProgramRegistry.reinitializeAll()
        CooFXClient.prepareResourcesIfNeeded(Minecraft.getInstance().resourceManager)
        CooFXClient.onRenderPipelineReady()
        CooParticlesConstants.logger.info("初始化渲染管线")
    }

    @JvmStatic
    fun reloadShaderPrograms() {
        renderInit = false
        CParticleSystemManager.onResourceReload()
        OpenGlRenderEntityModelExecutor.release()
        RenderEntityModelExecutors.reset()
        PostEffectFrameExecutor.releaseBackendResources()
        PostEffectFrameExecutor.resetBackend()
        CooFXClient.releaseRenderResources()
        ClientRenderPipelineManager.release()
        ClientRenderEntityManager.onShaderReload()
        initShaderPrograms()
    }

    private fun initRender() {
        CooTerrainPipelineManager.initialize()
        PostEffectDemoOptions.init()
        CooRenderTypeResourceRegistry.reloadFromClasspath()
        PostEffectRuntimeRegistry.initOnClient()
        CooPipelineRuntimeEffect.initOnClient()
        CooParticleTextureSheet.init()
    }

    fun onDisconnect() {
        Minecraft.getInstance().execute {
            onDisconnectInternal()
        }
    }

    private fun onDisconnectInternal() {
        clearTransientClientState()
    }

    fun afterClientWorldChange() {
        Minecraft.getInstance().execute {
            afterClientWorldChangeInternal()
        }
    }

    private fun afterClientWorldChangeInternal() {
        clearTransientClientState()
    }

    fun clearTransientClientState() {
        ParticleEmittersManager.clearAllVisible()
        ParticleStyleManager.clearAllVisible()
        ClientParticleGroupManager.clearAllVisible()
        ParticleCompositionManager.clearClient()
        DisplayEntityManager.clearClient()
        ControlParticleManager.clearClient()
        CParticleSystemManager.clear()
        CooFxSceneClientRegistry.clear()
        CooFXClient.clearTransientWorldState()
        ClientRenderEntityManager.clear()
        CooTerrainEffectRegistry.clear()
        CooTerrainMappingRegistry.clear()
        CooBlockPipelines.clearScopedBindings()
        CooTerrainPipelineManager.releaseResources()
        CooPostEffects.client.clear()
        DataHolderManager.clearClient()
        TestManager.clearClient()
        TestControllerPickClient.cancel()
        ClientSoundManager.clear()
        ClientSoundLoopManager.clear()
        scheduler.clear()
        subTicks = 0.0
    }

    var subTicks = 0.0
    fun tickClient(world: ClientLevel) {

        if (!::access.isInitialized) {
            access = world.registryAccess()
        }
        RenderPipelineExamples.ensureStarfieldFbo()

        val tickManager = world.tickRateManager()
        if (!tickManager.runsNormally()) {
            return
        }
        val rate = tickManager.tickrate()
        val preInvokeTimes = rate / 20.0
        subTicks += preInvokeTimes
        if (subTicks >= 1) {
            val toInt = subTicks.toInt()
            subTicks -= toInt
            repeat(toInt) {
                scheduler.doTick()
                ClientParticleGroupManager.doClientTick()
                ParticleStyleManager.doTickClient()
                ParticleEmittersManager.doTickClient()
                ClientRenderEntityManager.tick()
                CooFxSceneClientRegistry.tick()
                DisplayEntityManager.tickClient()
                DataHolderManager.tick()
                ClientCameraUtil.tick()
                ParticleCompositionManager.tickClient()
                CParticleSystemManager.tick()
                CooFXClient.tickClient()
                TestManager.doTickClient()
                AnimateManager.tickClient()
                CooClientPacketManager.tick()
                TestControllerPickClient.tick()
            }
        }
    }
}
