package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.render.CParticleRenderer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.compat.IrisCompat
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import org.joml.Matrix4f

/**
 * # CParticleSystemManager — GPU 粒子系统客户端总管
 *
 * 生命周期挂载点:
 * - tick: `CooParticlesAPIClient.tickClient` (支持 tickrate 补偿)
 * - render: `ParticleEngine.render` 粒子阶段 (fabric/neoforge mixin)
 * - clear: 断线/换世界 (`clearTransientClientState`)
 * - reload: 资源重载 (`reloadShaderPrograms`)
 *
 * 所有方法都只应在客户端渲染线程调用 (MC 客户端 tick 与渲染同线程).
 */
object CParticleSystemManager {

    private val systems = LinkedHashMap<String, CParticleSystem>()

    /** 全局开关 */
    @JvmStatic
    var enabled = true

    private var currentTick = 0
    private var fabricParticlePassIndex = 0
    private var renderFrameId = 0L
    internal val currentRenderFrameId: Long
        get() = renderFrameId

    /** 空系统自动回收阈值 (tick) */
    private const val AUTO_RELEASE_IDLE_TICKS = 200

    private val lastNonEmptyTick = HashMap<String, Int>()
    private val autoRelease = HashSet<String>()

    // ------------------------------------------------------------ 系统管理

    /**
     * 获取或创建一个粒子系统.
     *
     * @param autoReleaseWhenEmpty true 时系统空置 [AUTO_RELEASE_IDLE_TICKS] tick 后自动销毁
     *   (emitter 绑定的系统用)
     */
    @JvmStatic
    fun getOrCreateSystem(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        autoReleaseWhenEmpty: Boolean = false,
    ): CParticleSystem {
        systems[name]?.let { return it }
        val system = CParticleSystem(name, capacity, layer, mode)
        systems[name] = system
        lastNonEmptyTick[name] = currentTick
        if (autoReleaseWhenEmpty) autoRelease.add(name)
        return system
    }

    @JvmStatic
    fun getSystem(name: String): CParticleSystem? = systems[name]

    /**
     * 共享的默认 SIMULATED 系统 (按渲染层区分) — 散粒子直接往这里生成
     */
    @JvmStatic
    fun defaultSystem(layer: CParticleRenderLayer): CParticleSystem =
        getOrCreateSystem("default/${layer.name.lowercase()}", 65536, layer, CParticleSystemMode.SIMULATED)
            .also {
                // 默认池承载任意位置的散粒子, 不做整池距离剔除
                it.visibleRange = Double.MAX_VALUE
            }

    /** 快速生成一个散粒子到默认系统 (无力场; 需要力场请自建系统) */
    @JvmStatic
    fun spawn(particle: CParticle, layer: CParticleRenderLayer = CParticleRenderLayer.TRANSLUCENT): Int {
        if (!ready()) return -1
        return defaultSystem(layer).spawn(particle)
    }

    @JvmStatic
    fun removeSystem(name: String) {
        systems.remove(name)?.release()
        lastNonEmptyTick.remove(name)
        autoRelease.remove(name)
    }

    /** 当前总存活粒子数 (调试/统计) */
    @JvmStatic
    fun totalAlive(): Int = systems.values.sumOf { it.store.aliveCount }

    @JvmStatic
    fun systemCount(): Int = systems.size

    private fun ready(): Boolean {
        if (!enabled) return false
        CParticleCapabilities.detect()
        return CParticleCapabilities.instancingSupported
    }

    // ------------------------------------------------------------ 生命周期

    /** 每次 LevelRenderer 帧开始时重置 Fabric Iris MIXED 分流计数。 */
    @JvmStatic
    fun beginRenderFrame() {
        fabricParticlePassIndex = 0
        renderFrameId++
    }

    /** Fabric 的 Iris MIXED 模式会调用两次 ParticleEngine。 */
    @JvmStatic
    fun renderFabricParticlePass(camera: Camera, partial: Float) {
        val pass = if (IrisCompat.usesMixedParticleRendering()) {
            when (fabricParticlePassIndex++) {
                0 -> CParticleRenderPass.OPAQUE
                1 -> CParticleRenderPass.TRANSLUCENT
                else -> CParticleRenderPass.NONE
            }
        } else {
            CParticleRenderPass.ALL
        }
        renderParticlePass(camera, partial, pass)
    }

    /** Iris 下重新应用对应粒子 shader 后绘制，使 raw GPU draw 进入其 gbuffer FBO。 */
    @JvmStatic
    fun renderParticlePass(camera: Camera, partial: Float, pass: CParticleRenderPass) {
        if (!ready() || systems.isEmpty() || pass == CParticleRenderPass.NONE) return
        IrisCompat.runWithParticleShader(pass) {
            CParticleRenderer.render(
                systems.values,
                RenderSystem.getModelViewMatrix(),
                RenderSystem.getProjectionMatrix(),
                camera,
                partial,
                pass
            )
        }
    }

    /** 每客户端 tick 调用 (渲染线程) */
    @JvmStatic
    fun tick() {
        if (!ready()) return
        currentTick++
        if (systems.isEmpty()) return
        val toRemove = ArrayList<String>(0)
        for ((name, system) in systems) {
            system.tick()
            if (system.store.aliveCount > 0) {
                lastNonEmptyTick[name] = currentTick
            } else if (name in autoRelease &&
                currentTick - (lastNonEmptyTick[name] ?: currentTick) > AUTO_RELEASE_IDLE_TICKS
            ) {
                toRemove.add(name)
            }
        }
        toRemove.forEach { removeSystem(it) }
    }

    /** 保留给手动世界渲染调用；平台默认路径使用 [renderParticlePass]。 */
    @JvmStatic
    fun renderWorld(view: Matrix4f, proj: Matrix4f, camera: Camera, delta: DeltaTracker) {
        if (!ready() || systems.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        renderFrameId++
        val partial = delta.getGameTimeDeltaPartialTick(!level.tickRateManager().runsNormally())
        CParticleRenderer.render(systems.values, view, proj, camera, partial)
    }

    /** 断线 / 换世界: 清空所有粒子与动态系统 */
    @JvmStatic
    fun clear() {
        val it = systems.entries.iterator()
        while (it.hasNext()) {
            val (name, system) = it.next()
            if (name in autoRelease) {
                system.release()
                it.remove()
                lastNonEmptyTick.remove(name)
            } else {
                system.clearParticles()
            }
        }
        autoRelease.removeAll { it !in systems.keys }
    }

    /** 资源重载: 图集 UV 会变, 清空贴图缓存; shader 程序由 registry 自动重建 */
    @JvmStatic
    fun onResourceReload() {
        CParticleSprites.clearCache()
    }

    /** 完全释放 (退出/调试) */
    @JvmStatic
    fun releaseAll() {
        systems.values.forEach { it.release() }
        systems.clear()
        lastNonEmptyTick.clear()
        autoRelease.clear()
        CParticleRenderer.release()
        CParticleGpuSimulator.release()
    }
}
