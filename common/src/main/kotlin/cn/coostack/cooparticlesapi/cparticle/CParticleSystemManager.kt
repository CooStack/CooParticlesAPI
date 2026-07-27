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

    private val systems = LinkedHashMap<CParticleSystemKey, CParticleSystem>()

    /** 全局开关 */
    @JvmStatic
    var enabled = true

    /**
     * 所有 CParticle system 合计允许的存活粒子数。
     *
     * Example: 客户端启动时用 `APIConfig.cparticleCountLimit` 配置该值。
     * Forbidden: 不要把单个 system 的槽位容量当成全局上限。
     */
    @get:JvmStatic
    var particleCountLimit = 3_000_000
        private set

    /**
     * 当前所有 CParticle system 中的存活槽位数。
     *
     * Example: 新粒子占用槽位后该值增加，死亡或清空后减少。
     * Forbidden: 不能从 manager 的 system map 重新求和，否则会漏掉未注册 system。
     */
    private var globalAliveCount = 0

    private var currentTick = 0
    private var fabricParticlePassIndex = 0
    private var renderFrameId = 0L
    internal val currentRenderFrameId: Long
        get() = renderFrameId

    /** 空系统自动回收阈值 (tick) */
    private const val AUTO_RELEASE_IDLE_TICKS = 200

    private val lastNonEmptyTick = HashMap<CParticleSystemKey, Int>()
    private val autoRelease = HashSet<CParticleSystemKey>()

    // ------------------------------------------------------------ 系统管理

    /**
     * 更新所有 GPU 粒子系统共享的存活数量上限。
     *
     * Example: `configureParticleCountLimit(config.cparticleCountLimit)`。
     * Forbidden: 小于 `1` 的值不会关闭系统，而是按 `1` 处理。
     *
     * @param limit 新的全局存活粒子数上限
     */
    @JvmStatic
    fun configureParticleCountLimit(limit: Int) {
        particleCountLimit = limit.coerceAtLeast(1)
    }

    /**
     * 检查所有 CParticle system 的当前存活总数是否仍低于全局上限。
     *
     * Example: 新粒子写入槽位前调用本方法。
     * Forbidden: 该结果只在客户端渲染线程当前调用链内有效，不能跨线程缓存。
     *
     * @return 仍可接收至少一个新 GPU 粒子时返回 `true`
     */
    internal fun hasAvailableParticleCapacity(): Boolean {
        return globalAliveCount < particleCountLimit
    }

    /**
     * 为一个新 GPU 粒子申请全局槽位。
     *
     * Example: Store 确认本地仍有空槽后调用本方法。
     * Forbidden: 仅检查 [hasAvailableParticleCapacity] 不能占用额度。
     *
     * @return 申请成功时返回 `true`；达到配置上限时返回 `false`
     */
    internal fun tryAcquireParticleSlot(): Boolean {
        if (!hasAvailableParticleCapacity()) return false
        globalAliveCount++
        return true
    }

    /**
     * 归还已经释放的全局 GPU 粒子槽位。
     *
     * Example: Store 清空三个存活粒子后调用 `releaseParticleSlots(3)`。
     * Forbidden: 不能重复归还同一槽位，也不能传入负数。
     *
     * @param count 本次释放的存活槽位数
     */
    internal fun releaseParticleSlots(count: Int) {
        require(count in 0..globalAliveCount) {
            "Cannot release $count CParticle slots while only $globalAliveCount are alive"
        }
        globalAliveCount -= count
    }

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
    ): CParticleSystem = getOrCreateSystem(
        name,
        capacity,
        layer,
        mode,
        CParticleTextureBindingKey.PARTICLE_ATLAS,
        autoReleaseWhenEmpty,
    )

    /**
     * 获取或创建一个绑定到指定主纹理的系统。
     *
     * Example: 同一方块图集系统可以混合多种 BlockState descriptor。
     * Forbidden: [textureBindingKey] 不同的系统不能共享实例槽位。
     *
     * @param name 逻辑系统名
     * @param capacity 最大槽位数
     * @param layer 混合与深度状态
     * @param mode 更新模式
     * @param textureBindingKey 主纹理或图集绑定
     * @param autoReleaseWhenEmpty 空置后是否自动销毁
     * @return 完整 key 对应的系统
     */
    @JvmStatic
    fun getOrCreateSystem(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        textureBindingKey: CParticleTextureBindingKey,
        autoReleaseWhenEmpty: Boolean = false,
    ): CParticleSystem {
        val key = CParticleSystemKey(name, mode, layer, textureBindingKey)
        systems[key]?.let {
            if (autoReleaseWhenEmpty) autoRelease.add(key)
            return it
        }
        val system = CParticleSystem(name, capacity, layer, mode, textureBindingKey)
        systems[key] = system
        lastNonEmptyTick[key] = currentTick
        if (autoReleaseWhenEmpty) autoRelease.add(key)
        return system
    }

    @JvmStatic
    fun getSystem(name: String): CParticleSystem? =
        systems.entries.firstOrNull { it.key.name == name }?.value

    /**
     * 按完整系统键查询，不会误取同名的其他 binding 变体。
     *
     * Example: composition 绑定模板纹理后用此方法检查容量。
     * Forbidden: 旧的模糊名称查询不适合决定某个精确批次是否存在。
     *
     * @return 完整键匹配的系统，未创建时返回 `null`
     */
    @JvmStatic
    fun getSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem? = systems[CParticleSystemKey(name, mode, layer, textureBindingKey)]

    /**
     * 共享的默认 SIMULATED 系统 (按渲染层区分) — 散粒子直接往这里生成
     */
    @JvmStatic
    fun defaultSystem(layer: CParticleRenderLayer): CParticleSystem =
        defaultSystem(layer, CParticleTextureBindingKey.PARTICLE_ATLAS)

    /**
     * 返回指定渲染层和主纹理绑定的共享 SIMULATED 系统。
     *
     * Example: 所有方块图集散粒子共用一个默认系统。
     * Forbidden: 不要把独立纹理传入方块图集系统。
     *
     * @param layer 混合与深度状态
     * @param textureBindingKey 主纹理绑定
     * @return 可直接接收已解析实例的共享系统
     */
    @JvmStatic
    fun defaultSystem(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem =
        getOrCreateSystem(
            "default/${layer.name.lowercase()}",
            655360,
            layer,
            CParticleSystemMode.SIMULATED,
            textureBindingKey,
        )
            .also {
                // 默认池承载任意位置的散粒子, 不做整池距离剔除
                it.visibleRange = Double.MAX_VALUE
            }

    /** 快速生成一个散粒子到默认系统 (无力场; 需要力场请自建系统) */
    @JvmStatic
    fun spawn(particle: CParticle, layer: CParticleRenderLayer = CParticleRenderLayer.TRANSLUCENT): Int {
        if (!ready()) return -1
        val source = particle.effectiveTextureSource()
        val resolved = CParticleTextureResolver.resolve(source, particle.pos)
        if (!resolved.isValid) return -1
        return defaultSystem(layer, resolved.bindingKey).spawnResolved(
            particle,
            resolved,
            randomQuarterUv = (source as? CParticleTextureSource.Block)?.randomCrop == true,
        )
    }

    @JvmStatic
    fun removeSystem(name: String) {
        val matchingKeys = systems.keys.filter { it.name == name }
        matchingKeys.forEach(::removeSystem)
    }

    /** 删除一个完整键对应的系统，不影响同名的其他 binding 变体。 */
    @JvmStatic
    fun removeSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ) {
        removeSystem(CParticleSystemKey(name, mode, layer, textureBindingKey))
    }

    private fun removeSystem(key: CParticleSystemKey) {
        systems.remove(key)?.release()
        lastNonEmptyTick.remove(key)
        autoRelease.remove(key)
    }

    /**
     * 返回所有 CParticle system 的总存活粒子数。
     *
     * Example: 调试界面可用 `totalAlive()` 显示当前全局占用。
     * Forbidden: 不能只把它理解为 manager 内部 map 的存活数。
     *
     * @return 当前占用全局额度的粒子数
     */
    @JvmStatic
    fun totalAlive(): Int = globalAliveCount

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
        val toRemove = ArrayList<CParticleSystemKey>(0)
        for ((key, system) in systems) {
            system.tick()
            if (system.store.aliveCount > 0) {
                lastNonEmptyTick[key] = currentTick
            } else if (key in autoRelease &&
                currentTick - (lastNonEmptyTick[key] ?: currentTick) > AUTO_RELEASE_IDLE_TICKS
            ) {
                toRemove.add(key)
            }
        }
        toRemove.forEach(::removeSystem)
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
            val (key, system) = it.next()
            if (key in autoRelease) {
                system.release()
                it.remove()
                lastNonEmptyTick.remove(key)
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
        CParticleSprites.release()
    }
}
