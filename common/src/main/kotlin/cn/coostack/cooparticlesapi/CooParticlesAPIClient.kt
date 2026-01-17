package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.ShaderPipeManagers
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.options.display.TestDisplayerStyle
import cn.coostack.cooparticlesapi.test.options.particle.client.BarrierSwordGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.client.ScaleCircleGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.client.SequencedMagicCircleClient
import cn.coostack.cooparticlesapi.test.options.particle.client.TestGroupClient
import cn.coostack.cooparticlesapi.test.options.particle.style.*
import cn.coostack.cooparticlesapi.test.options.renderer.TestRendererEntity
import cn.coostack.cooparticlesapi.utils.ClientCameraUtil
import net.irisshaders.iris.api.v0.IrisApi
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.RenderType

object CooParticlesAPIClient {
    @JvmField
    val scheduler = CooScheduler()

    @JvmField
    var irisLoaded = false

    @JvmStatic
    fun init() {
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

    /**
     * 在 render 第一次执行时初始化
     */
    @JvmStatic
    private var renderInit = false

    @JvmStatic
    fun initShaderPrograms() {
        if (renderInit) return
        renderInit = true
        ShaderPipeManagers.init() // 注册到pipeline
        ClientRenderPipelineManager.init() // 把注册的pipeline进行一个初始化
        ClientRenderEntityManager.init()
        CooParticlesConstants.logger.info("初始化渲染管线")
    }

    private fun initRender() {
        ClientRenderEntityManager.register(TestRendererEntity.id, TestRendererEntity.codec)
        ClientRenderEntityManager.bindEntityRenderPipe(TestRendererEntity.id, ShaderPipeManagers.simpleBloom.pipeID)
        CooParticleTextureSheet.init()
    }

    fun onDisconnect() {
        ParticleEmittersManager.clientEmitters.clear()
        ParticleStyleManager.clearAllVisible()
        ClientRenderEntityManager.clear()
        ClientParticleGroupManager.clearAllVisible()
    }


    fun afterClientWorldChange() {
        ParticleEmittersManager.clientEmitters.clear()
        ParticleStyleManager.clearAllVisible()
        ClientParticleGroupManager.clearAllVisible()
        ClientRenderEntityManager.clear()

        DisplayEntityManager.clearClient()

    }

    var subTicks = 0.0
    fun tickClient(world: ClientLevel) {
        // resize test
        val tickManager = world.tickRateManager()
        if (!tickManager.runsNormally()) {
            return
        }
        val rate = tickManager.tickrate()
        val preInvokeTimes = rate / 20.0 // 平均每 tick 执行的次数
        subTicks += preInvokeTimes
        if (subTicks >= 1) {
            val toInt = subTicks.toInt()
            subTicks -= toInt
            repeat(toInt) {
                // 这里要同步应用上tick rate
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