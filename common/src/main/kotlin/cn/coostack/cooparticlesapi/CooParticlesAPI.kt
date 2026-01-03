package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.network.animation.PathMotionManager
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffectManager
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import cn.coostack.cooparticlesapi.test.APITestGroupBuilder
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import com.ezylang.evalex.Expression
import net.minecraft.server.MinecraftServer

object CooParticlesAPI {
    var subTicks = 0.0
    var renderInit = false
    lateinit var server: MinecraftServer

    @JvmField
    val scheduler = CooScheduler()

    @JvmStatic
    fun init() {
        if (CooParticlesServices.PLATFORM.isModLoaded(CooParticlesConstants.MOD_ID)) {
            CooParticlesConstants.logger.info("Hello to CooParticlesAPI")
        }
        CooParticlesConstants.logger.info("current :${CooParticlesServices.PLATFORM.getPlatformName()}")
        val builder = Expression("1+SQRT(x)")
            .with("x", 4.0)
            .evaluate()
        CooParticlesConstants.logger.info("eval api {}", builder.value)
        CooParticlesServices.API_CONFIG_MANAGER.loadConfig()
        ControlableParticleEffectManager.init()
        WindDirections.init()
        ParticleEventHandlerManager.register(TestCollideEventHandler)
        registerTest()
        CooAPIScanner.registerPacket("cn.coostack")
    }


    fun loadScannerPackages() {
        CooEventBus.scanListeners()
        CooEventBus.initListeners()

        ParticleEventHandlerManager.registerScanner()
        ParticleEmittersManager.registerScanner()
        DisplayEntityManager.registerScanner()
    }

    fun onServerStart(server: MinecraftServer) {
        this.server = server
    }


    fun registerTest() {
        TestManager.register(APITestGroupBuilder.ID) {
            APITestGroupBuilder(it)
        }
    }

    fun tickServer(server: MinecraftServer) {
        val tickManager = server.tickRateManager()
        if (!tickManager.runsNormally()) {
            return
        }
        ServerParticleGroupManager.upgrade()
        ParticleStyleManager.doTickServer()
        ParticleEmittersManager.doTickServer()
        BarrageManager.doTick()
        PathMotionManager.tick()
        ServerRenderEntityManager.tick()
        scheduler.doTick()
        DisplayEntityManager.tickServer()
        TestManager.doTickServer()
    }
}