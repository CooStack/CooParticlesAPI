package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import net.minecraft.world.phys.Vec3

/**
 * # SoA 粒子存储
 *
 * 每个粒子占 [STRIDE] = 28 个 float (112 字节, 7 x vec4), 布局与 GPU 端
 * (instanced attribute / std430 SSBO) 完全一致, 上传时整段 memcpy:
 *
 * ```
 * vec4 0: pos.xyz         age
 * vec4 1: prevPos.xyz     maxAge
 * vec4 2: velocity.xyz    flags(位打包整数, 以精确 float 值存储)
 * vec4 3: sizeW sizeH     yaw   pitch
 * vec4 4: axis.xyz        roll
 * vec4 5: u0 v0 u1 v1
 * vec4 6: r g b a
 * ```
 *
 * flags 位: bit0 alive; bit1..2 cameraMode(0=BILLBOARD 1=AXIS 2=ROTATION);
 * bit3..6 blockLight; bit7..10 skyLight
 *
 * 槽位管理: 空闲栈 + 存活位图 + 世代计数(句柄失效检测).
 * 死槽位不压缩 — 渲染端对非 alive 实例输出退化三角形, 代价可忽略.
 */
class CParticleStore(val capacity: Int) {
    companion object {
        const val STRIDE = 28
        const val BYTE_STRIDE = STRIDE * 4

        const val OFF_AGE = 3
        const val OFF_PREV = 4
        const val OFF_MAX_AGE = 7
        const val OFF_VEL = 8
        const val OFF_FLAGS = 11
        const val OFF_SIZE = 12
        const val OFF_AXIS = 16
        const val OFF_ROLL = 19
        const val OFF_UV = 20
        const val OFF_COLOR = 24

        const val FLAG_ALIVE = 1
        const val CAMERA_SHIFT = 1
        const val BLOCK_LIGHT_SHIFT = 3
        const val SKY_LIGHT_SHIFT = 7

        private const val SNAP_SIZE_W = 0
        private const val SNAP_SIZE_H = 1
        private const val SNAP_YAW = 2
        private const val SNAP_PITCH = 3
        private const val SNAP_AXIS_X = 4
        private const val SNAP_AXIS_Y = 5
        private const val SNAP_AXIS_Z = 6
        private const val SNAP_ROLL = 7
        private const val SNAP_COLOR_R = 8
        private const val SNAP_COLOR_G = 9
        private const val SNAP_COLOR_B = 10
        private const val SNAP_ALPHA = 11
        private const val SNAPSHOT_STRIDE = 12
        private val EMPTY_SLOTS = IntArray(0)

        @JvmStatic
        fun packFlags(alive: Boolean, cameraMode: Int, blockLight: Int, skyLight: Int): Int {
            var f = if (alive) FLAG_ALIVE else 0
            f = f or ((cameraMode and 3) shl CAMERA_SHIFT)
            f = f or ((blockLight and 15) shl BLOCK_LIGHT_SHIFT)
            f = f or ((skyLight and 15) shl SKY_LIGHT_SHIFT)
            return f
        }
    }

    /** 交错主数据 (与 GPU 缓冲 1:1) */
    val data = FloatArray(capacity * STRIDE)

    /** CPU 侧生命周期账本 (槽位回收依据; GPU 模式下缓冲内 age 由 kernel 自增) */
    val ages = IntArray(capacity)
    val maxAges = IntArray(capacity)

    /** 存活位图 */
    val aliveBits = LongArray((capacity + 63) ushr 6)

    /** 需要推进生命周期的槽位。持久 composition 粒子不进入此位图。 */
    private val agingBits = LongArray((capacity + 63) ushr 6)
    private var agingCount = 0

    /** 槽位世代 (句柄安全性: 槽位复用后旧句柄立即失效) */
    val generations = IntArray(capacity)

    private val freeStack = IntArray(capacity) { capacity - 1 - it }
    private var freeTop = capacity

    /** 已用高水位 (draw instanceCount) */
    var highWater = 0
        private set

    var aliveCount = 0
        private set

    /** 本 tick 新生成的槽位 (供 GPU 模式做增量上传) */
    val spawnedSlots = IntArray(capacity)
    var spawnedCount = 0
        private set

    /** 脏区间 (供 CPU/scripted 模式做范围上传), -1 表示无 */
    var dirtyMin = -1
        private set
    var dirtyMax = -1
        private set

    /** scripted 模式: 每槽位最后写入的 tick (用于同 tick 首次写入时滚动 prev) */
    val writeTicks = IntArray(capacity) { Int.MIN_VALUE }

    private var dynamicState: DynamicState? = null
    private var killedState: KilledState? = null

    internal val dynamicDirtySlots: IntArray
        get() = dynamicState?.dirtySlots ?: EMPTY_SLOTS
    internal val dynamicSourceCount: Int
        get() = dynamicState?.sourceCount ?: 0
    internal val hasDynamicStorage: Boolean
        get() = dynamicState != null
    internal val killedSlots: IntArray
        get() = killedState?.slots ?: EMPTY_SLOTS
    internal val killedCount: Int
        get() = killedState?.count ?: 0

    fun isFull(): Boolean = freeTop <= 0

    fun isAlive(slot: Int): Boolean =
        slot in 0 until capacity && (aliveBits[slot ushr 6] and (1L shl (slot and 63))) != 0L

    /**
     * 生成一个粒子, 返回槽位 (-1 = 池满).
     *
     * @param origin 系统原点 (位置写入为原点相对 float)
     * @param blockLight 0..15 (light==-1 时由调用方先采样世界光照)
     */
    fun spawn(p: CParticle, origin: Vec3, uv: CParticleSprites.UvRect, blockLight: Int, skyLight: Int): Int {
        if (freeTop <= 0) return -1
        val slot = freeStack[--freeTop]
        val base = slot * STRIDE

        val rx = (p.pos.x - origin.x).toFloat()
        val ry = (p.pos.y - origin.y).toFloat()
        val rz = (p.pos.z - origin.z).toFloat()
        val maxAge = p.maxAge.coerceAtLeast(1)

        data[base] = rx; data[base + 1] = ry; data[base + 2] = rz
        data[base + OFF_AGE] = p.age.toFloat()
        data[base + OFF_PREV] = rx; data[base + OFF_PREV + 1] = ry; data[base + OFF_PREV + 2] = rz
        data[base + OFF_MAX_AGE] = maxAge.toFloat()
        data[base + OFF_VEL] = p.velocity.x.toFloat()
        data[base + OFF_VEL + 1] = p.velocity.y.toFloat()
        data[base + OFF_VEL + 2] = p.velocity.z.toFloat()
        data[base + OFF_FLAGS] = packFlags(true, p.cameraOption.ordinal, blockLight, skyLight).toFloat()
        data[base + OFF_SIZE] = p.weightSize
        data[base + OFF_SIZE + 1] = p.heightSize
        data[base + OFF_SIZE + 2] = p.yaw
        data[base + OFF_SIZE + 3] = p.pitch
        data[base + OFF_AXIS] = p.axis.x.toFloat()
        data[base + OFF_AXIS + 1] = p.axis.y.toFloat()
        data[base + OFF_AXIS + 2] = p.axis.z.toFloat()
        data[base + OFF_ROLL] = p.roll
        data[base + OFF_UV] = uv.u0; data[base + OFF_UV + 1] = uv.v0
        data[base + OFF_UV + 2] = uv.u1; data[base + OFF_UV + 3] = uv.v1
        data[base + OFF_COLOR] = p.color.x
        data[base + OFF_COLOR + 1] = p.color.y
        data[base + OFF_COLOR + 2] = p.color.z
        data[base + OFF_COLOR + 3] = p.alpha

        ages[slot] = p.age
        maxAges[slot] = maxAge
        aliveBits[slot ushr 6] = aliveBits[slot ushr 6] or (1L shl (slot and 63))
        if (maxAge < Int.MAX_VALUE) {
            agingBits[slot ushr 6] = agingBits[slot ushr 6] or (1L shl (slot and 63))
            agingCount++
        }
        writeTicks[slot] = Int.MIN_VALUE
        aliveCount++
        if (slot + 1 > highWater) highWater = slot + 1
        if (spawnedCount < spawnedSlots.size) {
            spawnedSlots[spawnedCount++] = slot
        }
        releaseDynamicSource(slot)
        if (p.updateMode == CParticleUpdateMode.DYNAMIC) {
            val state = dynamicState ?: DynamicState(capacity).also { dynamicState = it }
            state.sources[slot] = p
            state.sourceCount++
            snapshotDynamicSource(state, slot, p)
        }
        markDirty(slot)
        return slot
    }

    /** 释放槽位 (清 alive 位, 世代自增, 渲染端立即隐藏) */
    fun kill(slot: Int, queueGpuFlag: Boolean = true) {
        if (!isAlive(slot)) return
        aliveBits[slot ushr 6] = aliveBits[slot ushr 6] and (1L shl (slot and 63)).inv()
        val agingMask = 1L shl (slot and 63)
        val agingWord = slot ushr 6
        if ((agingBits[agingWord] and agingMask) != 0L) {
            agingBits[agingWord] = agingBits[agingWord] and agingMask.inv()
            agingCount--
        }
        releaseDynamicSource(slot)
        generations[slot]++
        freeStack[freeTop++] = slot
        aliveCount--
        // 缓冲内清掉 alive 位, 让渲染端隐藏
        val base = slot * STRIDE
        val flags = data[base + OFF_FLAGS].toInt()
        data[base + OFF_FLAGS] = (flags and FLAG_ALIVE.inv()).toFloat()
        if (queueGpuFlag) queueKilled(slot)
        markDirty(slot)
        if (slot + 1 == highWater) {
            while (highWater > 0 && !isAlive(highWater - 1)) {
                highWater--
            }
        }
    }

    /**
     * 推进所有存活粒子的 CPU 年龄账本; 到期的槽位被回收.
     *
     * @param writeBufferAge true = 同步写缓冲内 age (CPU/scripted 模式);
     *                       GPU 模式传 false (kernel 自己自增)
     * @return 有粒子到期时返回 true
     */
    fun tickAges(writeBufferAge: Boolean): Boolean {
        if (agingCount == 0) return false
        var anyDead = false
        val words = (highWater + 63) ushr 6
        for (w in 0 until words) {
            var bits = agingBits[w]
            while (bits != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                val slot = (w shl 6) + bit
                val newAge = ++ages[slot]
                if (writeBufferAge) {
                    data[slot * STRIDE + OFF_AGE] = newAge.toFloat()
                }
                if (newAge >= maxAges[slot]) {
                    kill(slot, queueGpuFlag = false)
                    anyDead = true
                }
            }
        }
        return anyDead
    }

    fun getAge(slot: Int): Int = ages[slot]

    fun setAge(slot: Int, age: Int) {
        val safeAge = age.coerceIn(0, maxAges[slot])
        ages[slot] = safeAge
        data[slot * STRIDE + OFF_AGE] = safeAge.toFloat()
        dynamicState?.sources?.get(slot)?.age = safeAge
        markDirty(slot)
    }

    /** 清空全部粒子 */
    fun clear() {
        java.util.Arrays.fill(aliveBits, 0L)
        java.util.Arrays.fill(agingBits, 0L)
        for (i in 0 until capacity) {
            generations[i]++
            freeStack[i] = capacity - 1 - i
            writeTicks[i] = Int.MIN_VALUE
        }
        freeTop = capacity
        aliveCount = 0
        agingCount = 0
        highWater = 0
        spawnedCount = 0
        dynamicState = null
        killedState = null
        dirtyMin = -1
        dirtyMax = -1
        // flags 清零即可 (渲染端只看 alive 位)
        var base = OFF_FLAGS
        while (base < data.size) {
            data[base] = 0f
            base += STRIDE
        }
    }

    fun markDirty(slot: Int) {
        if (dirtyMin == -1 || slot < dirtyMin) dirtyMin = slot
        if (slot > dirtyMax) dirtyMax = slot
    }

    fun markAllAliveDirty() {
        if (highWater > 0) {
            dirtyMin = 0
            dirtyMax = highWater - 1
        }
    }

    fun clearDirty() {
        dirtyMin = -1
        dirtyMax = -1
    }

    fun clearSpawned() {
        spawnedCount = 0
    }

    internal fun clearKilled() {
        val state = killedState ?: return
        for (i in 0 until state.count) {
            val slot = state.slots[i]
            val word = slot ushr 6
            state.queuedBits[word] = state.queuedBits[word] and (1L shl (slot and 63)).inv()
        }
        state.count = 0
    }

    internal fun dynamicSource(slot: Int): CParticle? = dynamicState?.sources?.getOrNull(slot)

    internal fun publishDynamicAges() {
        val state = dynamicState ?: return
        if (state.sourceCount == 0) return
        for (slot in 0 until highWater) {
            state.sources[slot]?.age = ages[slot]
        }
    }

    /**
     * 把 DYNAMIC 源对象中确实发生变化的渲染字段写回 CPU 镜像。
     * 位置、速度和 age 不在这里同步，避免覆盖模拟器的状态。
     */
    internal fun prepareDynamicVisuals(
        resolveUv: (CParticle, age: Int, maxAge: Int) -> CParticleSprites.UvRect,
    ): Int {
        val state = dynamicState ?: return 0
        if (state.sourceCount == 0) return 0
        var dirtyCount = 0
        for (slot in 0 until highWater) {
            val source = state.sources[slot] ?: continue
            if (!isAlive(slot)) continue
            val base = slot * STRIDE
            val snapshot = slot * SNAPSHOT_STRIDE
            var changed = false

            changed = syncDynamicFloat(state, base + OFF_SIZE, snapshot + SNAP_SIZE_W, source.weightSize) || changed
            changed = syncDynamicFloat(state, base + OFF_SIZE + 1, snapshot + SNAP_SIZE_H, source.heightSize) || changed
            changed = syncDynamicFloat(state, base + OFF_SIZE + 2, snapshot + SNAP_YAW, source.yaw) || changed
            changed = syncDynamicFloat(state, base + OFF_SIZE + 3, snapshot + SNAP_PITCH, source.pitch) || changed
            changed = syncDynamicFloat(state, base + OFF_AXIS, snapshot + SNAP_AXIS_X, source.axis.x.toFloat()) || changed
            changed = syncDynamicFloat(state, base + OFF_AXIS + 1, snapshot + SNAP_AXIS_Y, source.axis.y.toFloat()) || changed
            changed = syncDynamicFloat(state, base + OFF_AXIS + 2, snapshot + SNAP_AXIS_Z, source.axis.z.toFloat()) || changed
            changed = syncDynamicFloat(state, base + OFF_ROLL, snapshot + SNAP_ROLL, source.roll) || changed
            changed = syncDynamicFloat(state, base + OFF_COLOR, snapshot + SNAP_COLOR_R, source.color.x) || changed
            changed = syncDynamicFloat(state, base + OFF_COLOR + 1, snapshot + SNAP_COLOR_G, source.color.y) || changed
            changed = syncDynamicFloat(state, base + OFF_COLOR + 2, snapshot + SNAP_COLOR_B, source.color.z) || changed
            changed = syncDynamicFloat(state, base + OFF_COLOR + 3, snapshot + SNAP_ALPHA, source.alpha) || changed

            val cameraMode = source.cameraOption.ordinal
            val light = source.light
            if (cameraMode != state.cameraModes[slot]) {
                var flags = data[base + OFF_FLAGS].toInt()
                flags = (flags and (3 shl CAMERA_SHIFT).inv()) or ((cameraMode and 3) shl CAMERA_SHIFT)
                data[base + OFF_FLAGS] = flags.toFloat()
                state.cameraModes[slot] = cameraMode
                changed = true
            }
            if (light != state.lights[slot]) {
                var flags = data[base + OFF_FLAGS].toInt()
                if (light >= 0) {
                    flags = flags and (15 shl BLOCK_LIGHT_SHIFT).inv()
                    flags = flags and (15 shl SKY_LIGHT_SHIFT).inv()
                    flags = flags or ((light.coerceIn(0, 15)) shl BLOCK_LIGHT_SHIFT)
                    flags = flags or ((light.coerceIn(0, 15)) shl SKY_LIGHT_SHIFT)
                }
                data[base + OFF_FLAGS] = flags.toFloat()
                state.lights[slot] = light
                changed = true
            }

            val uv = resolveUv(source, ages[slot], maxAges[slot])
            if (data[base + OFF_UV] != uv.u0 || data[base + OFF_UV + 1] != uv.v0 ||
                data[base + OFF_UV + 2] != uv.u1 || data[base + OFF_UV + 3] != uv.v1
            ) {
                data[base + OFF_UV] = uv.u0
                data[base + OFF_UV + 1] = uv.v0
                data[base + OFF_UV + 2] = uv.u1
                data[base + OFF_UV + 3] = uv.v1
                changed = true
            }

            if (changed) state.dirtySlots[dirtyCount++] = slot
        }
        return dirtyCount
    }

    private fun syncDynamicFloat(
        state: DynamicState,
        dataOffset: Int,
        snapshotOffset: Int,
        value: Float,
    ): Boolean {
        if (value == state.snapshots[snapshotOffset]) return false
        data[dataOffset] = value
        state.snapshots[snapshotOffset] = value
        return true
    }

    private fun snapshotDynamicSource(state: DynamicState, slot: Int, source: CParticle) {
        val snapshot = slot * SNAPSHOT_STRIDE
        state.snapshots[snapshot + SNAP_SIZE_W] = source.weightSize
        state.snapshots[snapshot + SNAP_SIZE_H] = source.heightSize
        state.snapshots[snapshot + SNAP_YAW] = source.yaw
        state.snapshots[snapshot + SNAP_PITCH] = source.pitch
        state.snapshots[snapshot + SNAP_AXIS_X] = source.axis.x.toFloat()
        state.snapshots[snapshot + SNAP_AXIS_Y] = source.axis.y.toFloat()
        state.snapshots[snapshot + SNAP_AXIS_Z] = source.axis.z.toFloat()
        state.snapshots[snapshot + SNAP_ROLL] = source.roll
        state.snapshots[snapshot + SNAP_COLOR_R] = source.color.x
        state.snapshots[snapshot + SNAP_COLOR_G] = source.color.y
        state.snapshots[snapshot + SNAP_COLOR_B] = source.color.z
        state.snapshots[snapshot + SNAP_ALPHA] = source.alpha
        state.cameraModes[slot] = source.cameraOption.ordinal
        state.lights[slot] = source.light
    }

    private fun releaseDynamicSource(slot: Int) {
        val state = dynamicState ?: return
        if (state.sources[slot] != null) {
            state.sources[slot] = null
            state.sourceCount--
        }
    }

    private fun queueKilled(slot: Int) {
        val state = killedState ?: KilledState(capacity).also { killedState = it }
        val word = slot ushr 6
        val mask = 1L shl (slot and 63)
        if (state.queuedBits[word] and mask != 0L) return
        state.queuedBits[word] = state.queuedBits[word] or mask
        state.slots[state.count++] = slot
    }

    private class DynamicState(capacity: Int) {
        val sources = arrayOfNulls<CParticle>(capacity)
        val snapshots = FloatArray(capacity * SNAPSHOT_STRIDE)
        val cameraModes = IntArray(capacity)
        val lights = IntArray(capacity)
        val dirtySlots = IntArray(capacity)
        var sourceCount = 0
    }

    private class KilledState(capacity: Int) {
        val slots = IntArray(capacity)
        val queuedBits = LongArray((capacity + 63) ushr 6)
        var count = 0
    }
}
