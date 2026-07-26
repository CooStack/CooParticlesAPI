package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.render.CParticleGlBuffer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector3fc
import kotlin.math.abs

/**
 * # CParticleSystem — 一个 GPU 粒子池
 *
 * 一个系统 = 一份固定容量的 SoA 存储 + 一个 GL 实例缓冲 + 一个渲染层 + 一组力场.
 * 每帧一次 instanced draw; 每 tick 一次模拟 (GL43 compute 或 CPU 并行回退).
 *
 * 两种模式:
 * - [CParticleSystemMode.SIMULATED]: 发射器语义 — 粒子生成后由力场驱动, 不可单独控制
 *   (对应 "emitter 是数据源")
 * - [CParticleSystemMode.SCRIPTED]: composition 语义 — CPU 持有位置权威,
 *   通过句柄 teleport/rotate, 或整组 groupTransform 变换 (对应 "composition 是显示")
 */
class CParticleSystem(
    val name: String,
    val capacity: Int,
    val layer: CParticleRenderLayer,
    val mode: CParticleSystemMode,
) {
    val store = CParticleStore(capacity)
    val glBuffer = CParticleGlBuffer(capacity)

    /**
     * 系统原点 (双精度): 粒子位置以它为基准存 float,
     * 避免远坐标 float 精度抖动. 池为空时可自动重定位.
     */
    var origin: Vec3 = Vec3.ZERO
        private set

    /** 力场列表 (SIMULATED 模式生效) */
    val forces = ArrayList<CParticleForce>()

    /** emitter 桥接: 上次力场同步的 emitter tick (避免同 tick 重复重建) */
    var forcesSyncTick = Int.MIN_VALUE

    /** 速度上限 (对应 ControlableParticleData.speedLimit) */
    var speedLimit = 32f

    /** 透明度生命周期曲线 (null = 恒定) */
    var alphaCurve: CParticleCurve? = null

    /** 尺寸生命周期曲线 (null = 恒定) */
    var sizeCurve: CParticleCurve? = null

    /** 大于 0 时，alpha/size 曲线按系统 tick 循环，不再使用粒子生命周期进度。 */
    var curveCycleTicks = 0f

    /** 大于 0 时，颜色在指定 tick 周期内完成一次色相循环。 */
    var colorCycleTicks = 0f

    /** 颜色沿粒子绕系统原点的角度分布多少个循环。 */
    var colorCycleSpatialScale = 0f

    internal var visualTransition: CParticleVisualTransition? = null
        private set

    /**
     * 整组变换 (SCRIPTED 模式): 施加于粒子的原点相对坐标.
     * 用它做整组旋转/缩放是零 per-particle CPU 开销的.
     */
    val groupTransform = Matrix4f()

    /** 渲染用的前后 tick 整组变换，由 shader 按 partial tick 插值。 */
    internal val previousGroupTransform = Matrix4f()
    internal val currentGroupTransform = Matrix4f()

    /** 可见范围 (以 origin 为球心的粗剔除; 只影响绘制, 不影响模拟) */
    var visibleRange = 256.0

    /** 本系统经历的 tick 数 */
    var tickCount = 0
        private set

    /** scripted 模式: prev 收敛计数 (写入后需 1 tick 让 prev==cur) */
    private var settleTicks = 0

    private val packedForces = FloatArray(CParticleGpuSimulator.PACKED_SIZE)

    internal var lastDynamicPrepareFrame = Long.MIN_VALUE
        private set

    var released = false
        private set

    // ------------------------------------------------------------ spawn

    /**
     * 生成一个粒子 (客户端渲染线程).
     * @param uvOverride 指定 UV (为 null 时按 [CParticle.sprite] 解析)
     * @return 槽位, -1 = 池满
     */
    fun spawn(p: CParticle, uvOverride: CParticleSprites.UvRect? = null): Int {
        if (released) return -1
        if (store.aliveCount == 0) snapGroupTransform()
        rebaseIfNeeded(p.pos)
        val uv = uvOverride ?: CParticleSprites.resolve(p, p.age, p.maxAge)
        var block = p.light
        var sky = p.light
        if (p.light < 0) {
            // 采样世界光照 (生成时一次)
            val world = Minecraft.getInstance().level
            if (world != null) {
                val packedLight = LevelRenderer.getLightColor(world, BlockPos.containing(p.pos))
                block = (packedLight shr 4) and 15
                sky = (packedLight shr 20) and 15
            } else {
                block = 15; sky = 15
            }
        }
        return store.spawn(p, origin, uv, block, sky)
    }

    private fun rebaseIfNeeded(pos: Vec3) {
        if (store.aliveCount == 0 && pos.distanceToSqr(origin) > 1024.0 * 1024.0) {
            origin = pos
        }
    }

    /** 手动设置原点 (仅在池为空时生效) */
    fun setOriginIfEmpty(pos: Vec3) {
        if (store.aliveCount == 0) origin = pos
    }

    /**
     * 从当前 system tick 开始播放一次 GPU 视觉过渡。
     *
     * alpha 和 size 曲线作为粒子原始值的倍率；同时提供 [colorFrom]、[colorTo]
     * 时，颜色在两者之间插值。相同配置的重复调用不会重置进度；需要重播时传入 [restart]。
     * 该操作不会改写粒子实例缓冲。
     */
    @JvmOverloads
    fun playVisualTransition(
        durationTicks: Float,
        alphaCurve: CParticleCurve? = null,
        sizeCurve: CParticleCurve? = null,
        colorFrom: Vector3fc? = null,
        colorTo: Vector3fc? = null,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
        restart: Boolean = false,
    ): CParticleSystem {
        require(durationTicks.isFinite() && durationTicks > 0f) {
            "durationTicks must be finite and greater than zero"
        }
        require((colorFrom == null) == (colorTo == null)) {
            "colorFrom and colorTo must both be set or both be null"
        }
        require(alphaCurve != null || sizeCurve != null || colorFrom != null) {
            "visual transition requires an alpha curve, size curve, or color range"
        }
        if (!restart && visualTransition?.matches(
                durationTicks,
                alphaCurve,
                sizeCurve,
                colorFrom,
                colorTo,
                mode,
            ) == true
        ) {
            return this
        }
        visualTransition = CParticleVisualTransition(
            startTick = tickCount.toFloat(),
            durationTicks = durationTicks,
            alphaCurve = alphaCurve,
            sizeCurve = sizeCurve,
            colorFrom = colorFrom,
            colorTo = colorTo,
            mode = mode,
        )
        return this
    }

    /**
     * 停止当前过渡。[reset] 为 true 时恢复原始状态，否则立即停在最终状态。
     */
    @JvmOverloads
    fun stopVisualTransition(reset: Boolean = false): CParticleSystem {
        val current = visualTransition ?: return this
        visualTransition = if (reset) {
            null
        } else {
            CParticleVisualTransition(
                startTick = tickCount.toFloat() - current.durationTicks,
                durationTicks = current.durationTicks,
                alphaCurve = current.alphaCurve,
                sizeCurve = current.sizeCurve,
                colorFrom = current.colorFrom.takeIf { current.hasColor },
                colorTo = current.colorTo.takeIf { current.hasColor },
                mode = CParticleTransitionMode.HOLD_END,
            )
        }
        return this
    }

    // ------------------------------------------------------------ scripted 句柄写入

    fun checkHandle(slot: Int, generation: Int): Boolean =
        !released && store.isAlive(slot) && store.generations[slot] == generation

    /** scripted: 写位置 (世界坐标); 同 tick 首次写入自动滚动 prev 实现插值 */
    fun scriptedSetPos(slot: Int, generation: Int, pos: Vec3) {
        if (!checkHandle(slot, generation)) return
        val relativeX = (pos.x - origin.x).toFloat()
        val relativeY = (pos.y - origin.y).toFloat()
        val relativeZ = (pos.z - origin.z).toFloat()
        val transformed = if (hasIdentityGroupTransform()) {
            null
        } else {
            val inverse = inverseGroupTransform() ?: return
            inverse.transformPosition(Vector3f(relativeX, relativeY, relativeZ))
        }
        val base = slot * CParticleStore.STRIDE
        val d = store.data
        if (store.writeTicks[slot] != tickCount) {
            d[base + CParticleStore.OFF_PREV] = d[base]
            d[base + CParticleStore.OFF_PREV + 1] = d[base + 1]
            d[base + CParticleStore.OFF_PREV + 2] = d[base + 2]
            store.writeTicks[slot] = tickCount
        }
        d[base] = transformed?.x ?: relativeX
        d[base + 1] = transformed?.y ?: relativeY
        d[base + 2] = transformed?.z ?: relativeZ
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedSetColor(slot: Int, generation: Int, r: Float, g: Float, b: Float) {
        if (!checkHandle(slot, generation)) return
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR
        store.data[base] = r; store.data[base + 1] = g; store.data[base + 2] = b
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedSetAlpha(slot: Int, generation: Int, alpha: Float) {
        if (!checkHandle(slot, generation)) return
        store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR + 3] = alpha
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedSetSize(slot: Int, generation: Int, w: Float, h: Float) {
        if (!checkHandle(slot, generation)) return
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_SIZE
        store.data[base] = w; store.data[base + 1] = h
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedSetRotation(slot: Int, generation: Int, yaw: Float, pitch: Float, roll: Float) {
        if (!checkHandle(slot, generation)) return
        val base = slot * CParticleStore.STRIDE
        store.data[base + CParticleStore.OFF_SIZE + 2] = yaw
        store.data[base + CParticleStore.OFF_SIZE + 3] = pitch
        store.data[base + CParticleStore.OFF_ROLL] = roll
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedSetVelocity(slot: Int, generation: Int, velocity: Vec3) {
        if (!checkHandle(slot, generation)) return
        val localVelocity = if (hasIdentityGroupTransform()) {
            null
        } else {
            val inverse = inverseGroupTransform() ?: return
            inverse.transformDirection(
                Vector3f(velocity.x.toFloat(), velocity.y.toFloat(), velocity.z.toFloat())
            )
        }
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_VEL
        store.data[base] = localVelocity?.x ?: velocity.x.toFloat()
        store.data[base + 1] = localVelocity?.y ?: velocity.y.toFloat()
        store.data[base + 2] = localVelocity?.z ?: velocity.z.toFloat()
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedGetPos(slot: Int, generation: Int): Vec3? {
        if (!checkHandle(slot, generation)) return null
        val base = slot * CParticleStore.STRIDE
        if (hasIdentityGroupTransform()) {
            return Vec3(
                store.data[base] + origin.x,
                store.data[base + 1] + origin.y,
                store.data[base + 2] + origin.z,
            )
        }
        val transformed = groupTransform.transformPosition(
            Vector3f(store.data[base], store.data[base + 1], store.data[base + 2])
        )
        return Vec3(
            transformed.x + origin.x,
            transformed.y + origin.y,
            transformed.z + origin.z,
        )
    }

    fun scriptedGetVelocity(slot: Int, generation: Int): Vec3? {
        if (!checkHandle(slot, generation)) return null
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_VEL
        if (hasIdentityGroupTransform()) {
            return Vec3(
                store.data[base].toDouble(),
                store.data[base + 1].toDouble(),
                store.data[base + 2].toDouble(),
            )
        }
        val transformed = groupTransform.transformDirection(
            Vector3f(store.data[base], store.data[base + 1], store.data[base + 2])
        )
        return Vec3(
            transformed.x.toDouble(),
            transformed.y.toDouble(),
            transformed.z.toDouble(),
        )
    }

    private fun inverseGroupTransform(): Matrix4f? {
        val determinant = groupTransform.determinant3x3()
        if (!determinant.isFinite() || abs(determinant) <= 1e-8f) return null
        return Matrix4f(groupTransform).invertAffine()
    }

    private fun hasIdentityGroupTransform(): Boolean {
        return groupTransform.properties() and Matrix4fc.PROPERTY_IDENTITY.toInt() != 0
    }

    fun kill(slot: Int, generation: Int) {
        if (!checkHandle(slot, generation)) return
        store.kill(slot)
        settleTicks = 2
    }

    // ------------------------------------------------------------ tick

    /**
     * 每客户端 tick 调用 (渲染线程, 持有 GL 上下文).
     *
     * tickCount 在**末尾**自增: scripted 写入发生在本方法之前 (composition tick 早于 manager tick),
     * 写入盖章用的是当前值, [rollPrevForUnwritten] 必须用同一个值比较 —
     * 否则本 tick 刚写入的槽位会被误滚动 prev, 破坏插值.
     */
    fun tick() {
        if (released) return
        previousGroupTransform.set(currentGroupTransform)
        currentGroupTransform.set(groupTransform)
        try {
            clearFinishedResetTransition()
            if (store.aliveCount == 0 && store.spawnedCount == 0 && store.killedCount == 0) {
                store.clearDirty()
                return
            }
            ensureGl()
            when (mode) {
                CParticleSystemMode.SIMULATED -> tickSimulated()
                CParticleSystemMode.SCRIPTED -> tickScripted()
            }
        } finally {
            tickCount++
        }
    }

    private fun clearFinishedResetTransition() {
        val transition = visualTransition ?: return
        if (transition.mode == CParticleTransitionMode.RESET &&
            tickCount - transition.startTick >= transition.durationTicks
        ) {
            visualTransition = null
        }
    }

    private fun tickSimulated() {
        val forceCount = packForces()
        val useGpu = CParticleCapabilities.useGpuSimulation()
        if (useGpu) {
            // compute 必须先看到本 tick 的死亡和新生成槽位，否则 CPU age 会领先 GPU 一 tick。
            if (store.killedCount > 0) {
                glBuffer.patchFlags(store.data, store.killedSlots, store.killedCount)
            }
            if (store.spawnedCount > 0) {
                glBuffer.uploadSlots(store.data, store.spawnedSlots, store.spawnedCount)
            }
        }
        if (useGpu &&
            CParticleGpuSimulator.simulate(this, packedForces, forceCount)
        ) {
            store.tickAges(writeBufferAge = false)
            store.publishDynamicAges()
            store.clearSpawned()
            store.clearKilled()
            store.clearDirty()
        } else {
            // CPU: 模拟写回 SoA → 整段上传
            CParticleCpuSimulator.simulate(
                store, packedForces, forceCount,
                origin.x, origin.y, origin.z, speedLimit
            )
            store.tickAges(writeBufferAge = true)
            store.publishDynamicAges()
            uploadDirty()
            store.clearSpawned()
            store.clearKilled()
        }
    }

    private fun tickScripted() {
        val hasLifecycleCurves = curveCycleTicks <= 0f && (alphaCurve != null || sizeCurve != null)
        // 未被写入的槽位滚动 prev=cur (静止粒子收敛, 避免重复插值)
        if (settleTicks > 0) {
            rollPrevForUnwritten()
            settleTicks--
            store.markAllAliveDirty()
        }
        store.tickAges(writeBufferAge = hasLifecycleCurves)
        store.publishDynamicAges()
        if (hasLifecycleCurves) store.markAllAliveDirty()
        uploadDirty()
        store.clearSpawned()
        store.clearKilled()
    }

    private fun rollPrevForUnwritten() {
        val d = store.data
        val bits = store.aliveBits
        val words = (store.highWater + 63) ushr 6
        for (w in 0 until words) {
            var b = bits[w]
            while (b != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(b)
                b = b and (b - 1)
                val slot = (w shl 6) + bit
                if (store.writeTicks[slot] != tickCount) {
                    val base = slot * CParticleStore.STRIDE
                    d[base + CParticleStore.OFF_PREV] = d[base]
                    d[base + CParticleStore.OFF_PREV + 1] = d[base + 1]
                    d[base + CParticleStore.OFF_PREV + 2] = d[base + 2]
                }
            }
        }
    }

    private fun uploadDirty() {
        if (store.dirtyMin >= 0) {
            glBuffer.uploadRange(store.data, store.dirtyMin, store.dirtyMax)
        }
        store.clearDirty()
    }

    /** 每个渲染帧只补写一次 DYNAMIC 粒子的渲染字段。 */
    internal fun prepareDynamicVisuals(frameId: Long) {
        if (released || lastDynamicPrepareFrame == frameId) return
        lastDynamicPrepareFrame = frameId
        val dirtyCount = store.prepareDynamicVisuals(CParticleSprites::resolve)
        if (dirtyCount > 0) {
            glBuffer.patchDynamicVisuals(store.data, store.dynamicDirtySlots, dirtyCount)
        }
    }

    private fun packForces(): Int {
        val count = forces.size.coerceAtMost(CParticleForce.MAX_FORCES)
        java.util.Arrays.fill(packedForces, 0f)
        for (i in 0 until count) {
            forces[i].pack(packedForces, i * CParticleForce.STRIDE, origin)
        }
        return count
    }

    // ------------------------------------------------------------ 生命周期

    /** 首次使用时创建 GL 资源 (渲染线程) */
    fun ensureGl() {
        if (!glBuffer.initialized) {
            glBuffer.init()
            // 新缓冲: 把当前 CPU 侧数据整体上传 (含 shader 重载后重建的场景)
            if (store.highWater > 0) {
                glBuffer.uploadRange(store.data, 0, store.highWater - 1)
            }
        }
    }

    /** 清空全部粒子 (保留 GL 资源) */
    fun clearParticles() {
        val prevHigh = store.highWater
        store.clear()
        snapGroupTransform()
        settleTicks = 0
        lastDynamicPrepareFrame = Long.MIN_VALUE
        if (glBuffer.initialized && prevHigh > 0) {
            // 同步清掉 GPU 侧 alive 位
            glBuffer.uploadRange(store.data, 0, prevHigh - 1)
        }
    }

    /** 仅释放 GL 资源 (shader/资源重载时; CPU 数据保留, 下次 ensureGl 重传) */
    fun releaseGl() {
        glBuffer.release()
    }

    /** 彻底销毁 */
    fun release() {
        released = true
        store.clear()
        lastDynamicPrepareFrame = Long.MIN_VALUE
        glBuffer.release()
    }

    /** 粗可见性: 相机到 origin 距离 */
    fun isVisible(cameraPos: Vec3): Boolean {
        if (store.aliveCount == 0) return false
        val r = visibleRange + 64.0
        return cameraPos.distanceToSqr(origin) <= r * r
    }

    internal fun snapGroupTransform() {
        previousGroupTransform.set(groupTransform)
        currentGroupTransform.set(groupTransform)
    }
}

enum class CParticleSystemMode {
    /** 发射器语义: 力场驱动, fire-and-forget */
    SIMULATED,

    /** composition 语义: CPU 位置权威 + 句柄控制 + 整组变换 */
    SCRIPTED
}
