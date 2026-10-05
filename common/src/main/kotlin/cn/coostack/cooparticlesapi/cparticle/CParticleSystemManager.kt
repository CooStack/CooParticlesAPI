package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResourceRegistry
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleEmitterBridge
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResource
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.render.CParticleRenderer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGridManager
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import net.minecraft.world.phys.Vec3

/**
 * Manager 内部使用的完整批次键。
 *
 * 示例：相同基础纹理配不同蒙版时会生成两个键。
 * 禁止：不要把该内部键暴露为公共 API，旧 [CParticleSystemKey] 需要保持 JVM 兼容。
 *
 * @property name 调用方使用的逻辑系统名
 * @property mode 系统更新模式
 * @property layer 混合与深度状态
 * @property textureBindingKey 基础纹理 binding
 * @property maskTextureBindingKey 可选蒙版纹理 binding
 */
private data class ManagedCParticleSystemKey(
    val name: String,
    val mode: CParticleSystemMode,
    val layer: CParticleRenderLayer,
    val textureBindingKey: CParticleTextureBindingKey,
    val maskTextureBindingKey: CParticleTextureBindingKey?,
)

/**
 * 普通 emitter 退休后接管 system 时使用的严格兼容键。
 *
 * Command 按候选 system 的原点完成打包后再比较原始位，因此动态中心、selector、Force 参数和
 * 资源槽位都必须一致。数组在构造时复制，避免下一 tick 重用打包缓冲后改变已经登记的键。
 *
 * @property emitterType 发射器注册 ID，同一实现才能接管
 * @property emitterPosition 发射器退休时的位置，新实例必须从同一点继续
 * @property blockCollisionRange 方块碰撞网格范围
 * @property droppedCommands Force Sink 已丢弃的 Command 数量
 * @property commandBits 完整 Command payload 的原始 32-bit 位
 * @property resources 资源型 Force 按首次出现顺序使用的稳定资源键
 */
internal class CParticleSystemReuseKey(
    val emitterType: String,
    val emitterPosition: Vec3,
    val blockCollisionRange: Int,
    val droppedCommands: Int,
    commandBits: IntArray,
    resources: List<CParticleForceResource>,
) {
    private val commandBits = commandBits.copyOf()
    private val resources = resources.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CParticleSystemReuseKey) return false
        return emitterType == other.emitterType &&
            emitterPosition == other.emitterPosition &&
            blockCollisionRange == other.blockCollisionRange &&
            droppedCommands == other.droppedCommands &&
            commandBits.contentEquals(other.commandBits) &&
            resources == other.resources
    }

    override fun hashCode(): Int {
        var result = emitterType.hashCode()
        result = 31 * result + emitterPosition.hashCode()
        result = 31 * result + blockCollisionRange
        result = 31 * result + droppedCommands
        result = 31 * result + commandBits.contentHashCode()
        result = 31 * result + resources.hashCode()
        return result
    }
}

/** Manager 完成键迁移后返回给 emitter bridge 的接管结果。 */
internal data class AdoptedCParticleSystem(
    val system: CParticleSystem,
    val managedName: String,
)

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
    /** Emitter 未覆写时，CParticle 方块碰撞相对 system 原点的保证范围。 */
    const val DEFAULT_BLOCK_COLLISION_RANGE = 24

    /** 单个 emitter 可声明的最大 CParticle 方块碰撞范围，防止误配置创建超大稠密网格。 */
    const val MAX_BLOCK_COLLISION_RANGE = 96

    private val systems = LinkedHashMap<ManagedCParticleSystemKey, CParticleSystem>()

    /** 全局开关 */
    @JvmStatic
    var enabled = true

    /**
     * 所有 CParticle system 合计允许的存活粒子数。
     *
     * 示例：客户端启动时用 `APIConfig.cparticleCountLimit` 配置该值。
     * 禁止：不要把单个 system 的槽位容量当成全局上限。
     */
    @get:JvmStatic
    var particleCountLimit = 3_000_000
        private set

    /**
     * 可选的小型 system CPU 回退阈值。
     *
     * 默认值为 0：GPU compute 可用时所有 CParticle 都在 GPU 上模拟。
     * 只有显式设置为正数时，才允许未切换过 GPU 的小池走 CPU SoA；这不是 GPU 粒子的默认路径。
     */
    @JvmStatic
    var smallSystemCpuThreshold = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /**
     * 多 emitter 合计达到该活跃槽位数后，小 system 也切换到 GPU。
     *
     * 该阈值只影响调用方显式开启小池 CPU 回退的场景；默认值为 0，表示无论总量如何都优先 GPU。
     */
    @JvmStatic
    var smallSystemAggregateGpuThreshold = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    @Volatile
    private var preferGpuForSmallSystems = false

    internal fun shouldPreferGpuForSmallSystems(): Boolean = preferGpuForSmallSystems

    /** 把 emitter 声明限制到碰撞网格支持的安全范围。 */
    internal fun normalizeBlockCollisionRange(range: Int): Int =
        range.coerceIn(0, MAX_BLOCK_COLLISION_RANGE)

    /**
     * 当前所有 CParticle system 中的存活槽位数。
     *
     * 示例：新粒子占用槽位后该值增加，死亡或清空后减少。
     * 禁止：不能从 manager 的 system map 重新求和，否则会漏掉未注册 system。
     */
    private var globalAliveCount = 0

    private var currentTick = 0
    private var fabricParticlePassIndex = 0
    private var renderFrameId = 0L
    private var deferredTerrainForegroundFrame = -1L
    private var deferredTerrainForegroundScenePost = false
    internal val currentRenderFrameId: Long
        get() = renderFrameId

    private val lastNonEmptyTick = HashMap<ManagedCParticleSystemKey, Int>()
    private val autoRelease = HashSet<ManagedCParticleSystemKey>()
    private val terminalAutoRelease = HashSet<ManagedCParticleSystemKey>()
    private val retiredAutoSystemReuseKeys = HashMap<ManagedCParticleSystemKey, CParticleSystemReuseKey>()
    /** 已结束的普通 emitter 空池，按进入顺序淘汰；不含仍绑定发射器的空 system。 */
    private val idleAutoSystems = linkedSetOf<ManagedCParticleSystemKey>()
    /** 空池缓存的槽位总容量，用于限制 CPU 数组和 GPU 缓冲的保留规模。 */
    private var idleAutoSystemSlots = 0
    private var autoSystemNameSequence = 0L
    /** 分批释放已经归零的 emitter system，避免同一 tick 集中 glDeleteBuffers 造成长停顿。 */
    private val pendingAutoRemovals = ArrayDeque<ManagedCParticleSystemKey>()
    private val pendingAutoRemovalSet = HashSet<ManagedCParticleSystemKey>()
    private const val MAX_AUTO_REMOVALS_PER_TICK = 8

    // ------------------------------------------------------------ 系统管理

    /**
     * 更新所有 GPU 粒子系统共享的存活数量上限。
     *
     * 示例：`configureParticleCountLimit(config.cparticleCountLimit)`。
     * 禁止：小于 `1` 的值不会关闭系统，而是按 `1` 处理。
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
     * 示例：新粒子写入槽位前调用本方法。
     * 禁止：该结果只在客户端渲染线程当前调用链内有效，不能跨线程缓存。
     *
     * @return 仍可接收至少一个新 GPU 粒子时返回 `true`
     */
    internal fun hasAvailableParticleCapacity(): Boolean {
        return globalAliveCount < particleCountLimit
    }

    /**
     * 为一个新 GPU 粒子申请全局槽位。
     *
     * 示例：Store 确认本地仍有空槽后调用本方法。
     * 禁止：仅检查 [hasAvailableParticleCapacity] 不能占用额度。
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
     * 示例：Store 清空三个存活粒子后调用 `releaseParticleSlots(3)`。
     * 禁止：不能重复归还同一槽位，也不能传入负数。
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
     * @param autoReleaseWhenEmpty true 时系统空置 200 tick 后自动销毁
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
        null,
    )

    /**
     * 获取或创建一个绑定到指定基础纹理的 system。
     *
     * 示例：end rod 基础纹理可以按方块图集蒙版复用一个 system。
     * 禁止：任一纹理 binding 不同的 system 都不能共享实例槽位。
     *
     * @param name 逻辑系统名
     * @param capacity 最大槽位数
     * @param layer 混合与深度状态
     * @param mode 更新模式
     * @param textureBindingKey 基础纹理或图集 binding
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
    ): CParticleSystem = getOrCreateSystem(
        name,
        capacity,
        layer,
        mode,
        textureBindingKey,
        autoReleaseWhenEmpty,
        null,
    )

    /**
     * 获取或创建同时匹配基础纹理和蒙版纹理的 system。
     *
     * 示例：`maskTextureBindingKey = BLOCK_ATLAS` 会与无蒙版批次分开。
     * 禁止：不能省略 [maskTextureBindingKey] 后期待匹配已有蒙版批次。
     *
     * @param name 逻辑系统名
     * @param capacity 最大槽位数
     * @param layer 混合与深度状态
     * @param mode 更新模式
     * @param textureBindingKey 基础纹理 binding
     * @param autoReleaseWhenEmpty 空置后是否自动销毁
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @return 完整批次键对应的系统
     */
    @JvmStatic
    fun getOrCreateSystem(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        textureBindingKey: CParticleTextureBindingKey,
        autoReleaseWhenEmpty: Boolean,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleSystem {
        val key = ManagedCParticleSystemKey(name, mode, layer, textureBindingKey, maskTextureBindingKey)
        systems[key]?.let {
            // 公共入口显式取回同名池时解除缓存所有权，后续不能再把它交给另一个 emitter。
            claimIdleAutoSystem(key, it)
            if (autoReleaseWhenEmpty) autoRelease.add(key)
            return it
        }
        // 创建大容量 System 的 CPU 峰值先在这一段定位：SoA 数组与空闲栈的分配全部发生在这里。
        val system = CParticlePerfProbe.measure(CParticlePerfProbe.Stage.SYSTEM_ALLOCATE) {
            CParticleSystem(name, capacity, layer, mode, textureBindingKey, maskTextureBindingKey)
        }
        systems[key] = system
        lastNonEmptyTick[key] = currentTick
        if (autoReleaseWhenEmpty) autoRelease.add(key)
        return system
    }

    /**
     * 按名称取回第一个匹配系统；取回已缓存空池时解除缓存所有权，防止再次交给其他 emitter。
     * 只在客户端渲染线程调用；同名多种纹理绑定应使用完整键重载。
     *
     * 示例：`getSystem("custom-effect")?.clearParticles()`。
     *
     * @param name 逻辑系统名
     * @return 第一个同名系统，不存在时返回 `null`
     */
    @JvmStatic
    fun getSystem(name: String): CParticleSystem? {
        val entry = systems.entries.firstOrNull { it.key.name == name } ?: return null
        claimIdleAutoSystem(entry.key, entry.value)
        return entry.value
    }

    /**
     * 按完整系统键查询，不会误取同名的其他 binding 变体。
     *
     * 示例：composition 绑定模板纹理后用此方法检查容量。
     * 禁止：旧的模糊名称查询不适合决定某个精确批次是否存在。
     *
     * @return 完整键匹配的系统，未创建时返回 `null`
     */
    @JvmStatic
    fun getSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem? = getSystem(name, mode, layer, textureBindingKey, null)

    /**
     * 按基础纹理和蒙版纹理的完整批次键查询系统。
     *
     * 示例：`maskTextureBindingKey = null` 只查询无蒙版批次。
     * 禁止：不要用另一张蒙版图集的 binding 查询当前批次。
     *
     * @param name 逻辑系统名
     * @param mode 更新模式
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @return 完整键匹配的系统，未创建时返回 `null`；取回空池时解除其缓存所有权
     */
    @JvmStatic
    fun getSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleSystem? {
        val key = ManagedCParticleSystemKey(name, mode, layer, textureBindingKey, maskTextureBindingKey)
        val system = systems[key] ?: return null
        claimIdleAutoSystem(key, system)
        return system
    }

    /** 显式取得 system 的调用方接管空池，缓存不再拥有该资源或统计其容量。 */
    private fun claimIdleAutoSystem(key: ManagedCParticleSystemKey, system: CParticleSystem) {
        if (idleAutoSystems.remove(key)) idleAutoSystemSlots -= system.capacity
    }

    /**
     * 共享的默认 SIMULATED 系统 (按渲染层区分) — 散粒子直接往这里生成
     */
    @JvmStatic
    fun defaultSystem(layer: CParticleRenderLayer): CParticleSystem =
        defaultSystem(layer, CParticleTextureBindingKey.PARTICLE_ATLAS)

    /**
     * 返回指定渲染层、基础纹理绑定和可选蒙版绑定的共享 SIMULATED 系统。
     *
     * 示例：所有方块图集散粒子共用一个默认系统。
     * 禁止：不要把独立纹理传入方块图集系统。
     *
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理绑定
     * @return 可直接接收已解析实例的共享系统
     */
    @JvmStatic
    fun defaultSystem(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem = defaultSystem(layer, textureBindingKey, null)

    /**
     * 返回指定基础纹理和蒙版纹理 binding 的共享 SIMULATED 系统。
     *
     * 示例：粒子图集基础纹理和方块图集蒙版共用一个匹配批次。
     * 禁止：不同蒙版 binding 不能返回同一个系统。
     *
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @return 可直接接收匹配实例的共享系统
     */
    @JvmStatic
    fun defaultSystem(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleSystem =
        getOrCreateSystem(
            "default/${layer.name.lowercase()}",
            655360,
            layer,
            CParticleSystemMode.SIMULATED,
            textureBindingKey,
            autoReleaseWhenEmpty = false,
            maskTextureBindingKey = maskTextureBindingKey,
        )
            .also {
                // 默认池承载任意位置的散粒子, 不做整池距离剔除
                it.visibleRange = Double.MAX_VALUE
            }

    /** 快速生成一个散粒子到默认系统 (无力场; 需要力场请自建系统) */
    @JvmStatic
    fun spawn(particle: CParticle, layer: CParticleRenderLayer = CParticleRenderLayer.TRANSLUCENT): Int {
        if (!ready()) return -1
        val resolved = particle.resolveTextures(particle.pos)
        if (!resolved.isValid) return -1
        return defaultSystem(layer, resolved.base.bindingKey, resolved.mask?.bindingKey)
            .spawnResolved(particle, resolved)
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
        removeSystem(name, mode, layer, textureBindingKey, null)
    }

    /**
     * 删除一个基础纹理和蒙版纹理都匹配的系统。
     *
     * 示例：删除方块蒙版批次不会影响同名的无蒙版批次。
     * 禁止：不要用模糊名称删除只应移除的单个批次。
     *
     * @param name 逻辑系统名
     * @param mode 更新模式
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     */
    @JvmStatic
    fun removeSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ) {
        removeSystem(ManagedCParticleSystemKey(name, mode, layer, textureBindingKey, maskTextureBindingKey))
    }

    private fun removeSystem(key: ManagedCParticleSystemKey) {
        pendingAutoRemovalSet.remove(key)
        val system = systems.remove(key)
        if (idleAutoSystems.remove(key)) idleAutoSystemSlots -= system?.capacity ?: 0
        system?.release()
        lastNonEmptyTick.remove(key)
        autoRelease.remove(key)
        terminalAutoRelease.remove(key)
        retiredAutoSystemReuseKeys.remove(key)
    }

    /**
     * 把 emitter 已停止写入但仍有存活粒子的 system 登记为可接管状态。
     *
     * 已空的小 system 进入有界缓存；仍有粒子的 system 继续模拟，只允许严格兼容的接管。
     * 归零后缓存小池或释放大池，避免短命 emitter 重复初始化相同的数组和 GL 缓冲。
     *
     * @param system 要停止接收旧 emitter 写入的自动回收 system
     * @param reuseKey 存活粒子接管时新 emitter 必须完整匹配的兼容键；空池不使用此键
     * @return system 已登记为可接管的存活池或空池时返回 `true`
     */
    internal fun retireAutoSystem(
        system: CParticleSystem,
        reuseKey: CParticleSystemReuseKey,
    ): Boolean {
        val key = systems.entries.firstOrNull { it.value === system }?.key ?: return false
        if (key !in autoRelease) return false
        if (system.store.aliveCount == 0) {
            if (cacheIdleAutoSystem(key)) return true
            removeSystem(key)
            return false
        }
        terminalAutoRelease.add(key)
        retiredAutoSystemReuseKeys[key] = reuseKey
        return true
    }

    /**
     * 缓存一个已结束的普通 emitter 空 system。
     * 保留规模同时受池数、总槽位数和既有 200 tick 空闲回收约束；不会缓存大规模特效的峰值容量。
     *
     * @return 空池已保留时返回 `true`，不适合缓存时返回 `false`
     */
    private fun cacheIdleAutoSystem(key: ManagedCParticleSystemKey): Boolean {
        if (key in idleAutoSystems) return true
        val system = systems[key] ?: return false
        val maxCachedSlots = 32_768
        val maxCachedSystems = 32
        if (system.released || system.store.aliveCount != 0 ||
            key.mode != CParticleSystemMode.SIMULATED || system.capacity > maxCachedSlots
        ) return false
        // 淘汰最早进入的空池，缓存只保留近期可复用的小特效资源。
        while (idleAutoSystems.isNotEmpty() &&
            (idleAutoSystems.size >= maxCachedSystems ||
                idleAutoSystemSlots + system.capacity > maxCachedSlots)
        ) removeSystem(idleAutoSystems.first())
        system.resetForEmitterReuse()
        retiredAutoSystemReuseKeys.remove(key)
        terminalAutoRelease.remove(key)
        idleAutoSystems.add(key)
        idleAutoSystemSlots += system.capacity
        lastNonEmptyTick[key] = currentTick
        return true
    }

    /**
     * 取得一个渲染分组一致且兼容键匹配的退休 system，并取消其终止回收状态。
     *
     * 先复用同渲染分组的已重置空池；只有仍有粒子的池才必须匹配原位置和完整力场。
     * 查找只发生在新 emitter 首次创建游标时，不进入逐粒子生成热路径。
     *
     * @param layer 新粒子的渲染层
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @param matches 使用候选 system 原点验证存活粒子与新 emitter 的 Force Command 是否兼容
     * @return 可以继续写入的原 system 及迁移后的 Manager 名称；没有匹配项时返回 `null`
     */
    internal fun takeRetiredAutoSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        matches: (CParticleSystem, CParticleSystemReuseKey) -> Boolean,
    ): AdoptedCParticleSystem? {
        val requestedTargetKey = ManagedCParticleSystemKey(
            name, mode, layer, textureBindingKey, maskTextureBindingKey,
        )
        val idleKey = idleAutoSystems.firstOrNull { key ->
            val candidate = systems[key]
            key.mode == mode && key.layer == layer &&
                key.textureBindingKey == textureBindingKey &&
                key.maskTextureBindingKey == maskTextureBindingKey &&
                candidate?.released == false && candidate.store.aliveCount == 0
        }
        if (idleKey != null) {
            val system = checkNotNull(systems[idleKey])
            // 空池已经清除旧命令、来源绑定和死亡树，不再受原 emitter 位置或类型约束。
            idleAutoSystems.remove(idleKey)
            idleAutoSystemSlots -= system.capacity
            return adoptAutoSystem(idleKey, requestedTargetKey, system)
        }
        val iterator = retiredAutoSystemReuseKeys.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val key = entry.key
            val system = systems[key]
            if (system == null || system.released) {
                iterator.remove()
                terminalAutoRelease.remove(key)
                continue
            }
            if (key.mode != mode ||
                key.layer != layer ||
                key.textureBindingKey != textureBindingKey ||
                key.maskTextureBindingKey != maskTextureBindingKey ||
                !matches(system, entry.value)
            ) {
                continue
            }
            iterator.remove()
            terminalAutoRelease.remove(key)
            return adoptAutoSystem(key, requestedTargetKey, system)
        }
        return null
    }

    /** 将已解除旧 emitter 绑定的 system 迁移到新名称，保留纹理分组及所有 CPU/GL 缓冲。 */
    private fun adoptAutoSystem(
        key: ManagedCParticleSystemKey,
        requestedTargetKey: ManagedCParticleSystemKey,
        system: CParticleSystem,
    ): AdoptedCParticleSystem {
        val targetKey = if (requestedTargetKey == key || requestedTargetKey !in systems) {
            requestedTargetKey
        } else {
            nextAvailableAutoSystemKey(requestedTargetKey)
        }
        if (targetKey != key) {
            systems.remove(key)
            lastNonEmptyTick.remove(key)
            autoRelease.remove(key)
            terminalAutoRelease.remove(key)
            systems[targetKey] = system
            system.renameForManager(targetKey.name)
        }
        lastNonEmptyTick[targetKey] = currentTick
        autoRelease.add(targetKey)
        return AdoptedCParticleSystem(system, targetKey.name)
    }

    /** 返回不会覆盖现有批次的自动 system 名称；只在 emitter 重启冲突时生成后缀。 */
    internal fun availableAutoSystemName(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): String {
        val requestedKey = ManagedCParticleSystemKey(
            name,
            mode,
            layer,
            textureBindingKey,
            maskTextureBindingKey,
        )
        return if (requestedKey !in systems) name else nextAvailableAutoSystemKey(requestedKey).name
    }

    private fun nextAvailableAutoSystemKey(base: ManagedCParticleSystemKey): ManagedCParticleSystemKey {
        while (true) {
            val candidate = base.copy(name = "${base.name}/restart-${++autoSystemNameSequence}")
            if (candidate !in systems) return candidate
        }
    }

    /**
     * 标记名称前缀匹配的自动回收 systems 不再接收新粒子。
     * 已空的 system 当场释放，仍有粒子的 system 在归零后的第一个 tick 释放。
     *
     * @param namePrefix system 名称前缀
     */
    internal fun releaseSystemsWhenEmpty(namePrefix: String) {
        val matchingKeys = systems.keys.filter { key ->
            key in autoRelease && key.name.startsWith(namePrefix)
        }
        for (key in matchingKeys) {
            val system = systems[key] ?: continue
            if (system.store.aliveCount == 0) {
                removeSystem(key)
            } else {
                terminalAutoRelease.add(key)
            }
        }
    }

    /**
     * 更新名称前缀匹配的全部 system 的方块碰撞范围。
     *
     * 示例：emitter 新建 segment 后，同步更新该 emitter 仍存活的旧 segment。
     * 禁止：[namePrefix] 不能为空，避免误改所有 system。
     */
    internal fun updateBlockCollisionRange(namePrefix: String, range: Int) {
        require(namePrefix.isNotEmpty())
        val normalizedRange = normalizeBlockCollisionRange(range)
        for ((key, system) in systems) {
            if (key.name.startsWith(namePrefix)) {
                system.blockCollisionRange = normalizedRange
            }
        }
    }

    /**
     * 判断自动回收 system 当前是否可以释放。
     * terminal system 不再等待空闲阈值，但任何仍有粒子的 system 都不能提前销毁。
     */
    internal fun shouldReleaseAutoSystem(aliveCount: Int, terminal: Boolean, idleTicks: Int): Boolean {
        val autoReleaseIdleTicks = 200
        return aliveCount == 0 && (terminal || idleTicks > autoReleaseIdleTicks)
    }

    /**
     * 返回所有 CParticle system 的总存活粒子数。
     *
     * 示例：调试界面可用 `totalAlive()` 显示当前全局占用。
     * 禁止：不能只把它理解为 manager 内部 map 的存活数。
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
            if (fabricParticlePassIndex++ == 0) CParticleRenderPass.ALL else CParticleRenderPass.NONE
        }
        renderParticlePass(camera, partial, pass)
    }

    /**
     * 在原版粒子阶段绘制当前 pass 覆盖的 GPU 粒子。
     *
     * 示例：Iris MIXED 的第二次回调传入 [CParticleRenderPass.TRANSLUCENT]。
     * 禁止从世界渲染事件重复调用，否则同一帧会绘制两次。
     *
     * @param camera 当前渲染相机
     * @param partial tick 插值
     * @param pass 本次允许绘制的粒子层范围
     */
    @JvmStatic
    fun renderParticlePass(camera: Camera, partial: Float, pass: CParticleRenderPass) {
        if (!ready() || systems.isEmpty() || pass == CParticleRenderPass.NONE) return
        val replayScenePost = CooTerrainPipelineManager.cParticleForegroundReplayScenePost()
        if (replayScenePost != null && PostEffectFrameExecutor.supportsForegroundReplay()) {
            deferredTerrainForegroundFrame = renderFrameId
            deferredTerrainForegroundScenePost = replayScenePost
            return
        }
        CParticleRenderer.render(
            systems.values,
            RenderSystem.getModelViewMatrix(),
            RenderSystem.getProjectionMatrix(),
            camera,
            partial,
            pass
        )
    }

    /** 当前帧是否已把 CParticle 从原粒子 pass 延迟到任一 Mapping 后处理阶段。 */
    internal fun hasDeferredTerrainForeground(): Boolean {
        return deferredTerrainForegroundFrame == renderFrameId
    }

    /** 当前帧是否已把 CParticle 从原粒子 pass 延迟到指定 Mapping 后处理阶段。 */
    internal fun hasDeferredTerrainForeground(scenePost: Boolean): Boolean {
        return hasDeferredTerrainForeground() && deferredTerrainForegroundScenePost == scenePost
    }

    /** 在 Mapping 后按原 layer、混合和 deferred-depth 语义重放当前帧延后的 CParticle。 */
    internal fun renderDeferredTerrainForeground(
        view: Matrix4f,
        proj: Matrix4f,
        partial: Float,
        scenePost: Boolean,
    ) {
        check(hasDeferredTerrainForeground(scenePost)) {
            "No deferred CParticle foreground is scheduled for this post stage"
        }
        CParticleRenderer.renderTerrainForeground(
            systems.values,
            view,
            proj,
            Minecraft.getInstance().gameRenderer.mainCamera,
            partial,
        )
        deferredTerrainForegroundFrame = -1L
    }

    /** 在 Terrain Mapping 合成前按当前帧矩阵生成全部可见 CParticle 的隔离覆盖蒙版。 */
    internal fun renderTerrainCoverageMask(view: Matrix4f, proj: Matrix4f, partial: Float): Boolean {
        if (!ready() || systems.isEmpty()) return true
        return CParticleRenderer.renderTerrainCoverageMask(
            systems.values,
            view,
            proj,
            Minecraft.getInstance().gameRenderer.mainCamera,
            partial,
        )
    }

    /** 每客户端 tick 调用 (渲染线程) */
    @JvmStatic
    fun tick() {
        if (!ready()) return
        currentTick++
        CParticleBlockCollisionGridManager.beginTick(Minecraft.getInstance().level, currentTick)
        try {
            if (systems.isEmpty()) return
            CParticleRespawnEngine.poll()
            // 所有 system 共用同一份路径图层；必须在任一 system 打包路径命令前完成一次同步。
            // 之前每个 system 都重复调用，多个 emitter 时会把无关的路径遍历和 GL 检查放大。
            CParticlePerfProbe.measure(CParticlePerfProbe.Stage.PATH_SYNC) {
                CParticlePathLibrary.syncLayer()
            }
            val toRemove = ArrayList<ManagedCParticleSystemKey>(0)
            // 只有本 tick 至少有一个 system 确实会走 GPU，才读取并维护跨 system 的 GL 状态。
            // 全是阈值以内的小池时，批次本身也会成为额外的固定开销。
            val aggregateSimulatedParticles = systems.values.asSequence()
                .filter { it.mode == CParticleSystemMode.SIMULATED }
                .sumOf { it.store.activeSlotCount }
            preferGpuForSmallSystems =
                aggregateSimulatedParticles >= smallSystemAggregateGpuThreshold
            val gpuBatch = systems.values.any { it.willUseGpuSimulation() }
            if (gpuBatch) CParticleGpuSimulator.beginBatch()
            try {
                for ((key, system) in systems) {
                    // 多 emitter 时每个 system 都会单独 dispatch；把 GPU barrier 延迟到整批
                    // system 完成后，避免几百次小 barrier 让驱动在 CPU/GPU 间反复同步。
                    system.tick(deferGpuMemoryBarrier = gpuBatch)
                    if (system.store.aliveCount > 0) {
                        lastNonEmptyTick[key] = currentTick
                    }
                    val idleTicks = currentTick - (lastNonEmptyTick[key] ?: currentTick)
                    if (key in autoRelease && shouldReleaseAutoSystem(
                            system.store.aliveCount,
                            key in terminalAutoRelease,
                            idleTicks,
                        )
                    ) {
                        toRemove.add(key)
                    }
                }
            } finally {
                if (gpuBatch) CParticleGpuSimulator.endBatch()
            }
            if (gpuBatch) {
                CParticleGpuSimulator.publishMemoryBarrier()
            }
            CParticleRespawnEngine.finishTick()
            for (key in toRemove) {
                if (pendingAutoRemovalSet.add(key)) pendingAutoRemovals.addLast(key)
            }
            drainPendingAutoRemovals()
            CParticleEmitterBridge.flushRespawns()
        } finally {
            preferGpuForSmallSystems = false
            CParticleBlockCollisionGridManager.endTick()
        }
    }

    /**
     * 分批执行 emitter system 的退休清理。
     *
     * glDeleteBuffers、空池重置和大数组回收都可能让渲染线程出现毫秒级尖峰；
     * 归零 system 已经不会参与绘制，因此把清理摊到多个 tick 不改变可见结果。
     */
    private fun drainPendingAutoRemovals() {
        repeat(MAX_AUTO_REMOVALS_PER_TICK) {
            val key = pendingAutoRemovals.removeFirstOrNull() ?: return
            pendingAutoRemovalSet.remove(key)
            if (key !in systems) return@repeat
            // 先完成本 tick 的死亡激活，再重置旧池，避免清理尚未处理的后继链。
            if (key in retiredAutoSystemReuseKeys && cacheIdleAutoSystem(key)) return@repeat
            removeSystem(key)
        }
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
        CParticleRespawnEngine.clear()
        CParticleEmitterBridge.clearPendingRespawns()
        CParticleBlockCollisionGridManager.clear()
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
        terminalAutoRelease.clear()
        retiredAutoSystemReuseKeys.clear()
        idleAutoSystems.clear()
        idleAutoSystemSlots = 0
        pendingAutoRemovals.clear()
        pendingAutoRemovalSet.clear()
        // 路径槽位由调用方持有；换世界只解除 GL 绑定，定义与槽位在调用方释放前保持有效。
        CParticlePathLibrary.unbind()
    }

    /** 资源重载: 图集 UV 会变, 清空贴图缓存; shader 程序由 registry 自动重建 */
    @JvmStatic
    fun onResourceReload() {
        CParticleRespawnEngine.releasePrograms()
        CParticleSprites.clearCache()
        CParticleForceResourceRegistry.clearResolvedBindings()
        CParticleGpuSimulator.release()
        // 路径图层缓冲随 shader 重载一起重建；CPU 侧几何保留，下一次 syncLayer 整段重传。
        CParticlePathLibrary.invalidateGlResources()
    }

    /** 彻底释放全部路径资源；仅在断开连接或完全关闭 CParticle 时调用。 */
    @JvmStatic
    fun releaseAllPaths() {
        CParticlePathLibrary.unbind()
        CParticlePathLibrary.clear()
    }

    /** 完全释放 (退出/调试) */
    @JvmStatic
    fun releaseAll() {
        CParticleRespawnEngine.clear()
        CParticleRespawnEngine.releasePrograms()
        CParticleEmitterBridge.clearPendingRespawns()
        systems.values.forEach { it.release() }
        systems.clear()
        lastNonEmptyTick.clear()
        autoRelease.clear()
        terminalAutoRelease.clear()
        retiredAutoSystemReuseKeys.clear()
        idleAutoSystems.clear()
        idleAutoSystemSlots = 0
        pendingAutoRemovals.clear()
        pendingAutoRemovalSet.clear()
        CParticleRenderer.release()
        CParticleGpuSimulator.release()
        CParticleBlockCollisionGridManager.clear()
        CParticleSprites.release()
        CParticleForceResourceRegistry.clearResolvedBindings()
        releaseAllPaths()
    }
}
