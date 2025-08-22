package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.ShaderPipeManagers
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import cn.coostack.cooparticlesapi.test.particle.client.BarrierSwordGroupClient
import cn.coostack.cooparticlesapi.test.particle.client.ScaleCircleGroupClient
import cn.coostack.cooparticlesapi.test.particle.client.SequencedMagicCircleClient
import cn.coostack.cooparticlesapi.test.particle.client.TestGroupClient
import cn.coostack.cooparticlesapi.test.particle.style.ExampleSequencedStyle
import cn.coostack.cooparticlesapi.test.particle.style.ExampleStyle
import cn.coostack.cooparticlesapi.test.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.particle.style.RotateTestStyle
import cn.coostack.cooparticlesapi.test.particle.style.TestShapeUtilStyle
import cn.coostack.cooparticlesapi.test.renderer.TestRendererEntity
import net.minecraft.client.multiplayer.ClientLevel

object CooParticlesAPIClient {
    @JvmField
    val scheduler = CooScheduler()

    @JvmStatic
    fun init() {
        initGroup()
        initStyle()
        initParticleType()
        initRender()
    }

    private fun initParticleType() {
        ParticleEmittersManager.init()
        CooModParticles.reg()
    }


    private fun initGroup() {
        ClientParticleGroupManager.register(TestGroupClient::class.java, TestGroupClient.Provider())
        ClientParticleGroupManager.register(ScaleCircleGroupClient::class.java, ScaleCircleGroupClient.Provider())
        ClientParticleGroupManager.register(BarrierSwordGroupClient::class.java, BarrierSwordGroupClient.Provider())
        ClientParticleGroupManager.register(
            SequencedMagicCircleClient::class.java,
            SequencedMagicCircleClient.Provider()
        )
    }


    private fun initStyle() {
        ParticleStyleManager.register(ExampleStyle::class.java, ExampleStyle.Provider())
        ParticleStyleManager.register(ExampleSequencedStyle::class.java, ExampleSequencedStyle.Provider())
        ParticleStyleManager.register(RomaMagicTestStyle::class.java, RomaMagicTestStyle.Provider())
        ParticleStyleManager.register(RotateTestStyle::class.java, RotateTestStyle.Provider())
        ParticleStyleManager.register(TestShapeUtilStyle::class.java, TestShapeUtilStyle.Provider())
    }

    private fun initRender() {
        ClientRenderEntityManager.register(TestRendererEntity.id, TestRendererEntity.codec)
        ClientRenderEntityManager.bindEntityRenderPipe(TestRendererEntity.id, ShaderPipeManagers.simpleBloom.pipeID)
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
    }

    var subTicks = 0.0
    var renderInit = false
    fun tickClient(world: ClientLevel) {
        if (!renderInit) {
            // 初始化ClientRenderer
            ShaderPipeManagers.init() // 注册到pipeline
            ClientRenderPipelineManager.init() // 把注册的pipeline进行一个初始化
            renderInit = true
            CooParticlesConstants.logger.info("初始化渲染管线")
        }
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
                AnimateManager.tickClient()
                ClientRenderEntityManager.tick()
            }
        }
    }
}