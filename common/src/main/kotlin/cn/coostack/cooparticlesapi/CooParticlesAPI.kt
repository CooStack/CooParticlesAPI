package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.animation.AnimateManager
import cn.coostack.cooparticlesapi.annotations.packet.CooPacketRegistry
import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketClearClientStateS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityAutoRegistry
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import cn.coostack.cooparticlesapi.supports.sound.ServerSoundLoopManager
import cn.coostack.cooparticlesapi.supports.sound.ServerSoundManager
import cn.coostack.cooparticlesapi.test.APITestGroupBuilder
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import com.ezylang.evalex.Expression
import net.minecraft.core.RegistryAccess
import net.minecraft.server.MinecraftServer

object CooParticlesAPI {
    var subTicks = 0.0
    var renderInit = false
    lateinit var server: MinecraftServer
    lateinit var registryAccess: RegistryAccess
    private var activeServer: MinecraftServer? = null
    private var activeRegistryAccess: RegistryAccess? = null

    @get:JvmStatic
    val serverOrNull: MinecraftServer?
        get() = activeServer

    @get:JvmStatic
    val registryAccessOrNull: RegistryAccess?
        get() = activeRegistryAccess

    @JvmField
    val scheduler = CooScheduler()

    @JvmStatic
    fun <T> invokeIfServerNotNull(block: (MinecraftServer) -> T): T? {
        val server = serverOrNull ?: return null
        return block(server)
    }

    @JvmStatic
    fun <T> invokeAsServer(block: (MinecraftServer) -> T): T? {
        return invokeIfServerNotNull(block)
    }

    @JvmStatic
    fun safelyServerInstance(): MinecraftServer? {
        return serverOrNull
    }

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
        ParticleCompositionManager.registerScanner()
        DataHolderManager.registerScanner()
        CooPacketRegistry.registerScanner()
        if (CooParticlesServices.PLATFORM.getDistType() == DistType.CLIENT) {
            DisplayEntityManager.registerScanner()
            RenderEntityAutoRegistry.registerScanner()
        }
    }

    fun onServerStart(server: MinecraftServer) {
        clearServerState()
        this.server = server
        this.registryAccess = server.registryAccess()
        activeServer = server
        activeRegistryAccess = registryAccess
    }

    fun onServerStop() {
        clearServerState()
        activeServer = null
        activeRegistryAccess = null
    }

    fun clearTransientState() {
        clearServerState()
        ServerSoundManager.clear()
        ServerSoundLoopManager.clear()
        scheduler.clear()
        subTicks = 0.0
        invokeIfServerNotNull { server ->
            server.playerList.players.forEach {
                CooParticlesServices.SERVER_NETWORK.send(PacketClearClientStateS2C, it)
            }
        }
    }

    private fun clearServerState() {
        ServerParticleGroupManager.clearServer()
        ParticleStyleManager.clearServer()
        ParticleEmittersManager.clearServer()
        ParticleCompositionManager.clearServer()
        DisplayEntityManager.clearServer()
        ServerRenderEntityManager.clear()
        TestManager.clearServer()
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
        DataHolderManager.tick()
        ServerRenderEntityManager.tick()
        scheduler.doTick()
        ServerSoundLoopManager.tick()
        ServerSoundManager.tick()
        DisplayEntityManager.tickServer()
        ParticleCompositionManager.tickServer()
        AnimateManager.tickServer()
        TestManager.doTickServer()
        CooServerPacketManager.tick()
    }
}
