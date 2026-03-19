package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.display.CooRenderTypeResourceRegistry
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.backend.IrisSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.client.ClientPersistentBloomManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.ClientScreenGlowManager
import cn.coostack.cooparticlesapi.renderer.client.ClientWorldLightManager
import cn.coostack.cooparticlesapi.renderer.client.ClientMaskBloomManager
import cn.coostack.cooparticlesapi.renderer.client.ShaderPipeManagers
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectRegistry
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import cn.coostack.cooparticlesapi.test.TestControlKeyBindings
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.options.display.TestDisplayerStyle
import cn.coostack.cooparticlesapi.test.options.particle.client.BarrierSwordGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.client.ScaleCircleGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.client.SequencedMagicCircleClient
import cn.coostack.cooparticlesapi.test.options.particle.client.TestGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.style.*
import cn.coostack.cooparticlesapi.test.options.renderer.RenderEntityExampleEffectRegistry
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

    @JvmStatic
    fun init() {
        TestControlKeyBindings.register()
        initGroup()
        initStyle()
        initParticleType()
        initRender()
        irisLoaded = CooParticlesServices.PLATFORM.isModLoaded("iris")
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

    @JvmStatic
    fun initShaderPrograms() {
        if (renderInit) return
        renderInit = true
        ShaderPipeManagers.init()
        ClientRenderPipelineManager.init()
        ClientRenderPipelineManager.setActiveBackend(selectedRenderBackend)
        ClientRenderEntityManager.init()
        ShaderProgramRegistry.reinitializeAll()
        CooParticlesConstants.logger.info("初始化渲染管线")
    }

    @JvmStatic
    fun reloadShaderPrograms() {
        renderInit = false
        ClientRenderPipelineManager.release()
        ClientMaskBloomManager.clear()
        ClientRenderEntityManager.onShaderReload()
        initShaderPrograms()
    }

    private fun initRender() {
        CooRenderTypeResourceRegistry.reloadFromClasspath()
        BuiltinRenderEffectRegistry.initOnClient()
        RenderEntityExampleEffectRegistry.initOnClient()
        ClientWorldLightManager.initOnClient()
        ClientPersistentBloomManager.initOnClient()
        ClientMaskBloomManager.initOnClient()
        ClientScreenGlowManager.initOnClient()
        CooParticleTextureSheet.init()
    }

    fun onDisconnect() {
        Minecraft.getInstance().execute {
            onDisconnectInternal()
        }
    }

    private fun onDisconnectInternal() {
        ParticleEmittersManager.clientEmitters.clear()
        ParticleStyleManager.clearAllVisible()
        ClientRenderEntityManager.clear()
        ClientWorldLightManager.clear()
        ClientMaskBloomManager.clear()
        ClientParticleGroupManager.clearAllVisible()
        ParticleCompositionManager.clearClient()
        TestManager.clearClient()
    }

    fun afterClientWorldChange() {
        Minecraft.getInstance().execute {
            afterClientWorldChangeInternal()
        }
    }

    private fun afterClientWorldChangeInternal() {
        ParticleEmittersManager.clientEmitters.clear()
        ParticleStyleManager.clearAllVisible()
        ClientParticleGroupManager.clearAllVisible()
        ClientRenderEntityManager.clear()
        ClientWorldLightManager.clear()
        ClientMaskBloomManager.clear()
        ParticleCompositionManager.clearClient()
        TestManager.clearClient()

        DisplayEntityManager.clearClient()
    }

    var subTicks = 0.0
    fun tickClient(world: ClientLevel) {

        if (!::access.isInitialized) {
            access = world.registryAccess()
        }

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
                DisplayEntityManager.tickClient()
                ClientCameraUtil.tick()
                ParticleCompositionManager.tickClient()
                TestManager.doTickClient()
                AnimateManager.tickClient()
            }
        }
    }
}
