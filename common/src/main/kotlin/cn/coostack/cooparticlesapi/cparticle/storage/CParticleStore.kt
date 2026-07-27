package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleAppearanceDescriptors
import cn.coostack.cooparticlesapi.cparticle.CParticleGpuMath
import cn.coostack.cooparticlesapi.cparticle.CParticleInstanceFlags
import cn.coostack.cooparticlesapi.cparticle.CParticleResolvedTexture
import cn.coostack.cooparticlesapi.cparticle.CParticleResolvedTextures
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureDescriptors
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.roundToInt

/**
 * # SoA 粒子存储
 *
 * 每个粒子占 [STRIDE] = 36 个 float (144 字节, 9 x vec4), 布局与 GPU 端
 * (instanced attribute / std430 SSBO) 完全一致, 上传时整段 memcpy:
 *
 * ```
 * vec4 0: pos.xyz         age
 * vec4 1: prevPos.xyz     maxAge
 * vec4 2: velocity.xyz    flags(位打包整数, 以精确 float 值存储)
 * vec4 3: sizeW sizeH     yaw   pitch
 * vec4 4: axis.xyz        roll
 * vec4 5: animationId visualAgeBase seedLow16 seedHigh16
 * vec4 6: r g b a
 * vec4 7: angularVelocity(pitch,yaw,roll) epochTick
 * vec4 8: appearanceDescriptorId speedLimit/systemSentinel maskDescriptorId packedMaskRgb8
 * ```
 *
 * flags 位: bit0 alive; bit1..2 cameraMode(0=BILLBOARD 1=AXIS 2=ROTATION);
 * bit3..6 blockLight; bit7..10 skyLight; bit11 randomAge; bit12 rotationDirection;
 * bit13 randomQuarterUv; bit14 randomMaskQuarterUv; bit15 blockCollision
 *
 * 槽位管理: 空闲栈 + 存活位图 + 世代计数(句柄失效检测).
 * 死槽位不压缩 — 渲染端对非 alive 实例输出退化三角形, 代价可忽略.
 */
class CParticleStore(val capacity: Int) {
    companion object {
        /**
         * 创建受 CParticle 全局数量上限约束的存储。
         *
         * Example: [cn.coostack.cooparticlesapi.cparticle.CParticleSystem] 用它创建 GPU 粒子池。
         * Forbidden: 独立的 CPU 数据测试不应使用该入口占用全局额度。
         *
         * @param capacity 槽位容量
         * @return 接入全局数量统计的存储
         */
        internal fun globallyCounted(capacity: Int): CParticleStore =
            CParticleStore(capacity).also { it.countsTowardGlobalLimit = true }

        const val STRIDE = 36
        const val BYTE_STRIDE = STRIDE * 4

        const val OFF_AGE = 3
        const val OFF_PREV = 4
        const val OFF_MAX_AGE = 7
        const val OFF_VEL = 8
        const val OFF_FLAGS = 11
        const val OFF_SIZE = 12
        const val OFF_AXIS = 16
        const val OFF_ROLL = 19
        const val OFF_ANIMATION = 20
        @Deprecated("The UV slot now stores GPU animation metadata")
        const val OFF_UV = OFF_ANIMATION
        const val OFF_COLOR = 24
        const val OFF_ANGULAR_VELOCITY = 28
        const val OFF_EPOCH_TICK = 31
        const val OFF_APPEARANCE = 32
        const val OFF_SPEED_LIMIT = OFF_APPEARANCE + 1
        const val OFF_MASK_ANIMATION = OFF_APPEARANCE + 2

        /** 蒙版 RGB8 倍率的 24-bit 打包值。Example: 白色为 `0xFFFFFF`。Forbidden: 不要写入超过 24 bit 的值。 */
        const val OFF_MASK_COLOR = OFF_APPEARANCE + 3
        private const val SYSTEM_SPEED_LIMIT_SENTINEL = -1f

        const val FLAG_ALIVE = CParticleInstanceFlags.ALIVE
        const val CAMERA_SHIFT = CParticleInstanceFlags.CAMERA_SHIFT
        const val BLOCK_LIGHT_SHIFT = CParticleInstanceFlags.BLOCK_LIGHT_SHIFT
        const val SKY_LIGHT_SHIFT = CParticleInstanceFlags.SKY_LIGHT_SHIFT
        const val FLAG_RANDOM_AGE = CParticleInstanceFlags.RANDOM_AGE
        const val FLAG_ROTATION_DIRECTION = CParticleInstanceFlags.ROTATION_DIRECTION
        const val FLAG_RANDOM_QUARTER_UV = CParticleInstanceFlags.RANDOM_QUARTER_UV
        const val FLAG_MASK_RANDOM_QUARTER_UV = CParticleInstanceFlags.MASK_RANDOM_QUARTER_UV
        const val FLAG_BLOCK_COLLISION = CParticleInstanceFlags.BLOCK_COLLISION

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
        private const val SNAP_ANGULAR_PITCH = 12
        private const val SNAP_ANGULAR_YAW = 13
        private const val SNAP_ANGULAR_ROLL = 14
        private const val SNAP_SPEED_LIMIT = 15
        private const val SNAPSHOT_STRIDE = 16
        private val EMPTY_SLOTS = IntArray(0)

        /**
         * 按旧布局打包基础实例 flags。
         *
         * Example: `packFlags(true, 0, 15, 15)` 创建一个存活 billboard 粒子。
         * Forbidden: 该兼容入口不会设置蒙版裁剪位。
         */
        @JvmStatic
        fun packFlags(
            alive: Boolean,
            cameraMode: Int,
            blockLight: Int,
            skyLight: Int,
            randomAge: Boolean = false,
            rotationDirection: Boolean = false,
            randomQuarterUv: Boolean = false,
            blockCollision: Boolean = false,
        ): Int = CParticleInstanceFlags.pack(
            alive,
            cameraMode,
            blockLight,
            skyLight,
            randomAge,
            rotationDirection,
            randomQuarterUv,
            blockCollision,
        )

        /**
         * 打包包含蒙版裁剪位的实例 flags。
         *
         * Example: 方块蒙版开启随机裁剪时把 [randomMaskQuarterUv] 设为 `true`。
         * Forbidden: 不要把蒙版裁剪位写进基础裁剪参数。
         */
        internal fun packFlagsWithMask(
            alive: Boolean,
            cameraMode: Int,
            blockLight: Int,
            skyLight: Int,
            randomAge: Boolean,
            rotationDirection: Boolean,
            randomQuarterUv: Boolean,
            randomMaskQuarterUv: Boolean,
            blockCollision: Boolean = false,
        ): Int = CParticleInstanceFlags.packWithMask(
            alive,
            cameraMode,
            blockLight,
            skyLight,
            randomAge,
            rotationDirection,
            randomQuarterUv,
            randomMaskQuarterUv,
            blockCollision,
        )

        /**
         * 把蒙版 RGB 倍率量化为 float 可精确保存的 24-bit 整数。
         *
         * Example: `(1, 0.5, 0)` 会打包为 `0x0080FF`。
         * Forbidden: 不要用它保存 HDR 或负颜色，输入会限制到 `0..1`。
         *
         * @param color 蒙版 RGB 倍率；`null` 表示白色
         * @return 按低位到高位排列的 R、G、B 8-bit 通道
         */
        internal fun packRgb8(color: Vector3f?): Int {
            val red = ((color?.x ?: 1f).coerceIn(0f, 1f) * 255f).roundToInt()
            val green = ((color?.y ?: 1f).coerceIn(0f, 1f) * 255f).roundToInt()
            val blue = ((color?.z ?: 1f).coerceIn(0f, 1f) * 255f).roundToInt()
            return red or (green shl 8) or (blue shl 16)
        }
    }

    /** 交错主数据 (与 GPU 缓冲 1:1) */
    val data = FloatArray(capacity * STRIDE)

    /**
     * 当前开启方块占用网格碰撞的存活粒子数。
     *
     * Example: system 只在本值大于零时构建和绑定碰撞网格。
     * Forbidden: 调用方不能直接修改；spawn、DYNAMIC 更新和 kill 会维护计数。
     */
    var blockCollisionCount = 0
        private set

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

    /**
     * 该存储中的存活槽位是否计入 CParticle 全局上限。
     *
     * Example: CParticleSystem 创建的 store 将该值设为 `true`。
     * Forbidden: 普通 [CParticleStore] 数据容器不能占用 GPU 粒子额度。
     */
    private var countsTowardGlobalLimit = false

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
     * 使用基础纹理描述符生成一个粒子。
     *
     * Example: `spawn(particle, origin, animationId, 15, 15)` 占用一个空槽位。
     * Forbidden: 该兼容入口不接受额外蒙版；蒙版由 system 的解析路径写入。
     *
     * @param p 粒子数据
     * @param origin 系统原点
     * @param animationId GPU 动画描述符
     * @param blockLight 0..15
     * @param skyLight 0..15
     * @param epochTick 粒子生成时的 system tick
     * @param randomSeed GPU 随机种子
     * @param colorMultiplier 基础纹理颜色倍率
     * @param randomQuarterUv 是否随机裁剪基础纹理
     * @param textureBindingKey 粒子所属的基础纹理 binding
     * @param textureGeneration 纹理缓存代数
     * @param appearanceDescriptorId GPU 外观描述符
     * @return 分配的槽位；池满时返回 `-1`
     */
    fun spawn(
        p: CParticle,
        origin: Vec3,
        animationId: Int,
        blockLight: Int,
        skyLight: Int,
        epochTick: Int = 0,
        randomSeed: Int = p.randomSeed ?: CParticleGpuMath.nextAutomaticSeed(),
        colorMultiplier: Vector3f = Vector3f(1f),
        randomQuarterUv: Boolean = false,
        textureBindingKey: CParticleTextureBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
        textureGeneration: Int = 0,
        appearanceDescriptorId: Int = p.appearanceDescriptorId(),
    ): Int = spawnWithMask(
        p = p,
        origin = origin,
        animationId = animationId,
        blockLight = blockLight,
        skyLight = skyLight,
        epochTick = epochTick,
        randomSeed = randomSeed,
        colorMultiplier = colorMultiplier,
        randomQuarterUv = randomQuarterUv,
        textureBindingKey = textureBindingKey,
        textureGeneration = textureGeneration,
        appearanceDescriptorId = appearanceDescriptorId,
    )

    /**
     * 使用基础纹理和可选蒙版描述符生成一个粒子。
     *
     * Example: `spawn(particle, origin, animationId, 15, 15)` 占用一个空槽位。
     * Forbidden: 受全局限制的 store 达到共享上限后不能继续生成。
     *
     * @param p 粒子数据
     * @param origin 系统原点 (位置写入为原点相对 float)
     * @param animationId GPU 动画描述符
     * @param maskAnimationId 可选蒙版 GPU 动画描述符
     * @param blockLight 0..15 (light==-1 时由调用方先采样世界光照)
     * @param skyLight 0..15
     * @param epochTick 粒子生成时的 system tick
     * @param randomSeed GPU 随机种子
     * @param colorMultiplier 基础纹理颜色倍率
     * @param randomQuarterUv 是否随机裁剪到四分之一 UV
     * @param randomMaskQuarterUv 是否随机裁剪蒙版到四分之一 UV
     * @param textureBindingKey 粒子所属的基础纹理绑定
     * @param maskTextureBindingKey 粒子所属的可选蒙版纹理绑定
     * @param maskColorMultiplier 蒙版采样使用的独立 RGB 倍率
     * @param textureGeneration 纹理缓存代数
     * @param appearanceDescriptorId GPU 外观描述符
     * @return 分配的槽位；本地池或全局额度已满时返回 `-1`
     */
    internal fun spawnWithMask(
        p: CParticle,
        origin: Vec3,
        animationId: Int,
        blockLight: Int,
        skyLight: Int,
        epochTick: Int = 0,
        randomSeed: Int = p.randomSeed ?: CParticleGpuMath.nextAutomaticSeed(),
        colorMultiplier: Vector3f = Vector3f(1f),
        randomQuarterUv: Boolean = false,
        textureBindingKey: CParticleTextureBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
        textureGeneration: Int = 0,
        appearanceDescriptorId: Int = p.appearanceDescriptorId(),
        maskAnimationId: Int? = null,
        randomMaskQuarterUv: Boolean = false,
        maskTextureBindingKey: CParticleTextureBindingKey? = null,
        maskColorMultiplier: Vector3f? = null,
    ): Int {
        CParticleTextureDescriptors.requireValidDescriptorId(animationId)
        maskAnimationId?.let(CParticleTextureDescriptors::requireValidDescriptorId)
        CParticleAppearanceDescriptors.requireValidDescriptorId(appearanceDescriptorId)
        if (freeTop <= 0) return -1
        if (countsTowardGlobalLimit && !CParticleSystemManager.tryAcquireParticleSlot()) return -1
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
        val hasDirection = p.cameraOption == ParticleCameraOption.ROTATION && p.rotationDirection != null
        data[base + OFF_FLAGS] = packFlagsWithMask(
            true,
            p.cameraOption.ordinal,
            blockLight,
            skyLight,
            p.randomAgePreTick,
            hasDirection,
            randomQuarterUv,
            randomMaskQuarterUv,
            p.blockCollision,
        ).toFloat()
        if (p.blockCollision) blockCollisionCount++
        data[base + OFF_SIZE] = p.weightSize
        data[base + OFF_SIZE + 1] = p.heightSize
        data[base + OFF_SIZE + 2] = if (hasDirection) 0f else p.yaw
        data[base + OFF_SIZE + 3] = if (hasDirection) 0f else p.pitch
        val orientation = p.rotationDirection.takeIf { hasDirection }
        data[base + OFF_AXIS] = orientation?.x ?: p.axis.x.toFloat()
        data[base + OFF_AXIS + 1] = orientation?.y ?: p.axis.y.toFloat()
        data[base + OFF_AXIS + 2] = orientation?.z ?: p.axis.z.toFloat()
        data[base + OFF_ROLL] = p.roll
        data[base + OFF_ANIMATION] = animationId.toFloat()
        data[base + OFF_ANIMATION + 1] = p.age.toFloat()
        data[base + OFF_ANIMATION + 2] = CParticleGpuMath.seedLow(randomSeed).toFloat()
        data[base + OFF_ANIMATION + 3] = CParticleGpuMath.seedHigh(randomSeed).toFloat()
        data[base + OFF_COLOR] = p.color.x * colorMultiplier.x
        data[base + OFF_COLOR + 1] = p.color.y * colorMultiplier.y
        data[base + OFF_COLOR + 2] = p.color.z * colorMultiplier.z
        data[base + OFF_COLOR + 3] = p.alpha
        data[base + OFF_ANGULAR_VELOCITY] = p.angularVelocity.x
        data[base + OFF_ANGULAR_VELOCITY + 1] = p.angularVelocity.y
        data[base + OFF_ANGULAR_VELOCITY + 2] = p.angularVelocity.z
        data[base + OFF_EPOCH_TICK] = epochTick.toFloat()
        data[base + OFF_APPEARANCE] = appearanceDescriptorId.toFloat()
        data[base + OFF_SPEED_LIMIT] = p.speedLimit ?: SYSTEM_SPEED_LIMIT_SENTINEL
        data[base + OFF_MASK_ANIMATION] = (maskAnimationId ?: 0).toFloat()
        data[base + OFF_MASK_COLOR] = packRgb8(maskColorMultiplier).toFloat()

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
            snapshotDynamicSource(
                state,
                slot,
                p,
                colorMultiplier,
                randomQuarterUv,
                randomMaskQuarterUv,
                textureBindingKey,
                maskTextureBindingKey,
                textureGeneration,
            )
        }
        markDirty(slot)
        return slot
    }

    /** 兼容原有低层固定 UV 入口。 */
    fun spawn(
        p: CParticle,
        origin: Vec3,
        uv: CParticleSprites.UvRect,
        blockLight: Int,
        skyLight: Int,
    ): Int = spawn(p, origin, CParticleSprites.animationId(uv), blockLight, skyLight)

    /**
     * 释放槽位，清除 alive 位并让旧句柄失效。
     *
     * Example: `kill(slot)` 立即隐藏粒子并归还一份全局额度。
     * Forbidden: 重复释放同一槽位不能再次归还额度。
     *
     * @param slot 待释放的槽位
     * @param queueGpuFlag 是否排队补写 GPU alive 标记
     */
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
        if (countsTowardGlobalLimit) CParticleSystemManager.releaseParticleSlots(1)
        // 缓冲内清掉 alive 位, 让渲染端隐藏
        val base = slot * STRIDE
        val flags = data[base + OFF_FLAGS].toInt()
        if (flags and FLAG_BLOCK_COLLISION != 0) blockCollisionCount--
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

    fun setAge(slot: Int, age: Int, epochTick: Int = 0) {
        val safeAge = age.coerceIn(0, maxAges[slot])
        ages[slot] = safeAge
        data[slot * STRIDE + OFF_AGE] = safeAge.toFloat()
        rebaseEpoch(slot, epochTick)
        dynamicState?.sources?.get(slot)?.age = safeAge
        markDirty(slot)
    }

    /** 当前整数 tick 实际显示的欧拉角，分量为 x=pitch、y=yaw、z=roll。 */
    fun currentRotation(slot: Int, tick: Int): Vector3f {
        val base = slot * STRIDE
        val flags = data[base + OFF_FLAGS].toInt()
        val mode = (flags ushr CAMERA_SHIFT) and 3
        val elapsed = (tick - data[base + OFF_EPOCH_TICK].toInt()).coerceAtLeast(0).toFloat()
        val pitch = data[base + OFF_SIZE + 3]
        val yaw = data[base + OFF_SIZE + 2]
        val roll = data[base + OFF_ROLL]
        val rollVelocity = data[base + OFF_ANGULAR_VELOCITY + 2]
        if (mode != ParticleCameraOption.ROTATION.ordinal) {
            return Vector3f(pitch, yaw, roll + rollVelocity * elapsed)
        }
        val direction = if (flags and FLAG_ROTATION_DIRECTION != 0) {
            Vector3f(data[base + OFF_AXIS], data[base + OFF_AXIS + 1], data[base + OFF_AXIS + 2])
        } else {
            null
        }
        return CParticleGpuMath.accumulateAngles(
            Vector3f(pitch, yaw, roll),
            Vector3f(
                data[base + OFF_ANGULAR_VELOCITY],
                data[base + OFF_ANGULAR_VELOCITY + 1],
                rollVelocity,
            ),
            elapsed,
            direction,
        )
    }

    fun rotationDirection(slot: Int): Vector3f? {
        val base = slot * STRIDE
        val flags = data[base + OFF_FLAGS].toInt()
        if (flags and FLAG_ROTATION_DIRECTION == 0) return null
        return Vector3f(data[base + OFF_AXIS], data[base + OFF_AXIS + 1], data[base + OFF_AXIS + 2])
    }

    fun angularVelocity(slot: Int): Vector3f {
        val base = slot * STRIDE + OFF_ANGULAR_VELOCITY
        return Vector3f(data[base], data[base + 1], data[base + 2])
    }

    fun setBaseRotation(slot: Int, pitch: Float, yaw: Float, roll: Float, tick: Int) {
        val base = slot * STRIDE
        data[base + OFF_ANIMATION + 1] = ages[slot].toFloat()
        data[base + OFF_EPOCH_TICK] = tick.toFloat()
        data[base + OFF_SIZE + 2] = yaw
        data[base + OFF_SIZE + 3] = pitch
        data[base + OFF_ROLL] = roll
        val flags = data[base + OFF_FLAGS].toInt() and FLAG_ROTATION_DIRECTION.inv()
        data[base + OFF_FLAGS] = flags.toFloat()
        markDirty(slot)
    }

    fun setRotationDirection(slot: Int, direction: Vector3f?, tick: Int) {
        val base = slot * STRIDE
        val flags = data[base + OFF_FLAGS].toInt()
        val mode = (flags ushr CAMERA_SHIFT) and 3
        if (mode != ParticleCameraOption.ROTATION.ordinal) return
        val current = currentRotation(slot, tick)
        val elapsed = (tick - data[base + OFF_EPOCH_TICK].toInt()).coerceAtLeast(0).toFloat()
        val oldDirection = if (flags and FLAG_ROTATION_DIRECTION != 0) {
            Vector3f(data[base + OFF_AXIS], data[base + OFF_AXIS + 1], data[base + OFF_AXIS + 2])
        } else {
            null
        }
        val oldPointed = CParticleGpuMath.directionAngles(oldDirection)
        data[base + OFF_ANIMATION + 1] = ages[slot].toFloat()
        data[base + OFF_EPOCH_TICK] = tick.toFloat()
        data[base + OFF_ROLL] = current.z
        if (direction == null) {
            data[base + OFF_SIZE + 2] = current.y
            data[base + OFF_SIZE + 3] = current.x
            data[base + OFF_FLAGS] = (flags and FLAG_ROTATION_DIRECTION.inv()).toFloat()
        } else {
            data[base + OFF_SIZE + 2] = if (oldDirection != null) {
                current.y - oldPointed.y
            } else {
                data[base + OFF_ANGULAR_VELOCITY + 1] * elapsed
            }
            data[base + OFF_SIZE + 3] = if (oldDirection != null) {
                current.x - oldPointed.x
            } else {
                data[base + OFF_ANGULAR_VELOCITY] * elapsed
            }
            data[base + OFF_AXIS] = direction.x
            data[base + OFF_AXIS + 1] = direction.y
            data[base + OFF_AXIS + 2] = direction.z
            data[base + OFF_FLAGS] = (flags or FLAG_ROTATION_DIRECTION).toFloat()
        }
        markDirty(slot)
    }

    fun setAngularVelocity(slot: Int, velocity: Vector3f, tick: Int, markSlotDirty: Boolean = true) {
        rebaseEpoch(slot, tick)
        val base = slot * STRIDE + OFF_ANGULAR_VELOCITY
        data[base] = velocity.x
        data[base + 1] = velocity.y
        data[base + 2] = velocity.z
        if (markSlotDirty) markDirty(slot)
    }

    fun addRoll(slot: Int, radians: Float, tick: Int) {
        rebaseEpoch(slot, tick)
        data[slot * STRIDE + OFF_ROLL] += radians
        markDirty(slot)
    }

    private fun rebaseEpoch(slot: Int, tick: Int) {
        val base = slot * STRIDE
        val current = currentRotation(slot, tick)
        val flags = data[base + OFF_FLAGS].toInt()
        if (flags and FLAG_ROTATION_DIRECTION != 0) {
            val direction = Vector3f(data[base + OFF_AXIS], data[base + OFF_AXIS + 1], data[base + OFF_AXIS + 2])
            val pointed = CParticleGpuMath.directionAngles(direction)
            data[base + OFF_SIZE + 2] = current.y - pointed.y
            data[base + OFF_SIZE + 3] = current.x - pointed.x
        } else {
            data[base + OFF_SIZE + 2] = current.y
            data[base + OFF_SIZE + 3] = current.x
        }
        data[base + OFF_ROLL] = current.z
        data[base + OFF_ANIMATION + 1] = ages[slot].toFloat()
        data[base + OFF_EPOCH_TICK] = tick.toFloat()
    }

    /**
     * 清空全部粒子并一次性归还它们占用的全局额度。
     *
     * Example: system 换世界时调用 `store.clear()` 复位整个池。
     * Forbidden: 对空池重复调用不能减少其他 system 的全局计数。
     */
    fun clear() {
        val releasedCount = aliveCount
        java.util.Arrays.fill(aliveBits, 0L)
        java.util.Arrays.fill(agingBits, 0L)
        for (i in 0 until capacity) {
            generations[i]++
            freeStack[i] = capacity - 1 - i
            writeTicks[i] = Int.MIN_VALUE
        }
        freeTop = capacity
        aliveCount = 0
        blockCollisionCount = 0
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
        if (countsTowardGlobalLimit) CParticleSystemManager.releaseParticleSlots(releasedCount)
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
            state.sources[slot]?.publishAgeToDynamicData()
        }
    }

    /**
     * 把 DYNAMIC 源对象中确实发生变化的渲染字段写回 CPU 镜像。
     * 位置、速度和 age 不在这里同步，避免覆盖模拟器的状态。
     */
    internal fun prepareDynamicVisuals(
        tick: Int,
        resolveAnimation: (ResourceLocation?, ResourceLocation?) -> Int,
    ): Int = prepareDynamicVisuals(
        tick,
        textureGeneration = 0,
        expectedBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
        resolveTexture = { particle ->
            val descriptorId = resolveAnimation(
                particle.sprite,
                CParticleSprites.effectTypeId(particle.effect),
            )
            CParticleResolvedTexture(
                CParticleTextureBindingKey.PARTICLE_ATLAS,
                descriptorId,
                cn.coostack.cooparticlesapi.cparticle.CParticleUv.FULL,
                null,
                Vector3f(1f),
            )
        },
        onBindingMismatch = { _, _ -> },
    )

    /**
     * 把 DYNAMIC 来源中变化的视觉字段和纹理描述符写回 CPU 镜像。
     *
     * 纹理只在 revision 或资源代数变化时解析；跨 binding 的槽位会被移除。
     * Example: 同一方块图集内从石头切到泥土只更新 descriptor。
     * Forbidden: age 变化不能触发 [resolveTexture]。
     *
     * @param tick 当前系统 tick
     * @param textureGeneration 资源解析缓存代数
     * @param expectedBindingKey 当前系统固定绑定
     * @param resolveTexture 来源变化时调用的客户端解析函数
     * @param onBindingMismatch 跨 binding 被移除前的通知
     * @return 本帧需要补写 GPU 视觉区的槽位数量
     */
    internal fun prepareDynamicVisuals(
        tick: Int,
        textureGeneration: Int,
        expectedBindingKey: CParticleTextureBindingKey,
        resolveTexture: (CParticle) -> CParticleResolvedTexture,
        onBindingMismatch: (Int, CParticleTextureBindingKey) -> Unit,
    ): Int = prepareDynamicTextures(
        tick = tick,
        textureGeneration = textureGeneration,
        expectedBindingKey = expectedBindingKey,
        expectedMaskBindingKey = null,
        resolveTextures = { particle ->
            CParticleResolvedTextures(
                base = resolveTexture(particle),
                mask = null,
                randomBaseQuarterUv = false,
                randomMaskQuarterUv = false,
            )
        },
        onBindingMismatch = { slot, actual, _ -> onBindingMismatch(slot, actual) },
    )

    /**
     * 把 DYNAMIC 来源中的基础纹理和蒙版描述符写回 CPU 镜像。
     *
     * Example: 石头蒙版换成泥土蒙版时，两个来源都留在原 binding 才会更新槽位。
     * Forbidden: 任一来源跨 binding 时不能继续使用旧 system。
     *
     * @param tick 当前 system tick
     * @param textureGeneration 资源解析缓存代数
     * @param expectedBindingKey system 固定的基础纹理 binding
     * @param expectedMaskBindingKey system 固定的可选蒙版 binding
     * @param resolveTextures 来源变化时调用的双纹理解析函数
     * @param onBindingMismatch 跨 binding 被移除前的通知
     * @return 本帧需要补写 GPU 视觉区的槽位数量
     */
    internal fun prepareDynamicTextures(
        tick: Int,
        textureGeneration: Int,
        expectedBindingKey: CParticleTextureBindingKey,
        expectedMaskBindingKey: CParticleTextureBindingKey?,
        resolveTextures: (CParticle) -> CParticleResolvedTextures,
        onBindingMismatch: (Int, CParticleTextureBindingKey, CParticleTextureBindingKey?) -> Unit,
    ): Int {
        val state = dynamicState ?: return 0
        if (state.sourceCount == 0) return 0
        var dirtyCount = 0
        for (slot in 0 until highWater) {
            val source = state.sources[slot] ?: continue
            if (!isAlive(slot)) continue
            source.refreshDynamicDataSource()
            val base = slot * STRIDE
            val snapshot = slot * SNAPSHOT_STRIDE
            var changed = false

            changed = syncDynamicFloat(state, base + OFF_SIZE, snapshot + SNAP_SIZE_W, source.weightSize) || changed
            changed = syncDynamicFloat(state, base + OFF_SIZE + 1, snapshot + SNAP_SIZE_H, source.heightSize) || changed
            val colorBase = slot * 3
            changed = syncDynamicFloat(
                state,
                base + OFF_COLOR,
                snapshot + SNAP_COLOR_R,
                source.color.x,
                state.colorMultipliers[colorBase],
            ) || changed
            changed = syncDynamicFloat(
                state,
                base + OFF_COLOR + 1,
                snapshot + SNAP_COLOR_G,
                source.color.y,
                state.colorMultipliers[colorBase + 1],
            ) || changed
            changed = syncDynamicFloat(
                state,
                base + OFF_COLOR + 2,
                snapshot + SNAP_COLOR_B,
                source.color.z,
                state.colorMultipliers[colorBase + 2],
            ) || changed
            changed = syncDynamicFloat(state, base + OFF_COLOR + 3, snapshot + SNAP_ALPHA, source.alpha) || changed
            changed = syncDynamicFloat(
                state,
                base + OFF_SPEED_LIMIT,
                snapshot + SNAP_SPEED_LIMIT,
                source.speedLimit ?: SYSTEM_SPEED_LIMIT_SENTINEL,
            ) || changed

            val previousCameraMode = state.cameraModes[slot]
            val cameraMode = source.cameraOption.ordinal
            val light = source.light
            val cameraChanged = cameraMode != previousCameraMode

            val hasDirection = cameraMode == ParticleCameraOption.ROTATION.ordinal && source.rotationDirection != null
            val orientation = source.rotationDirection.takeIf { hasDirection }
            val orientationX = orientation?.x ?: source.axis.x.toFloat()
            val orientationY = orientation?.y ?: source.axis.y.toFloat()
            val orientationZ = orientation?.z ?: source.axis.z.toFloat()
            val yawChanged = source.yaw != state.snapshots[snapshot + SNAP_YAW]
            val pitchChanged = source.pitch != state.snapshots[snapshot + SNAP_PITCH]
            val rollChanged = source.roll != state.snapshots[snapshot + SNAP_ROLL]
            val orientationXChanged = orientationX != state.snapshots[snapshot + SNAP_AXIS_X]
            val orientationYChanged = orientationY != state.snapshots[snapshot + SNAP_AXIS_Y]
            val orientationZChanged = orientationZ != state.snapshots[snapshot + SNAP_AXIS_Z]
            val directionModeChanged = hasDirection != state.directionModes[slot]
            val angularChanged = source.angularVelocity.x != state.snapshots[snapshot + SNAP_ANGULAR_PITCH] ||
                    source.angularVelocity.y != state.snapshots[snapshot + SNAP_ANGULAR_YAW] ||
                    source.angularVelocity.z != state.snapshots[snapshot + SNAP_ANGULAR_ROLL]
            val rotationComponentChanged = yawChanged || pitchChanged || rollChanged ||
                    orientationXChanged || orientationYChanged || orientationZChanged
            if (cameraChanged || directionModeChanged || rotationComponentChanged || angularChanged) {
                writeDynamicRotation(state, slot, source, tick, previousCameraMode, cameraMode, hasDirection)
                snapshotRotation(state, slot, source, orientationX, orientationY, orientationZ, hasDirection)
                changed = true
            }
            if (cameraChanged) {
                var flags = data[base + OFF_FLAGS].toInt()
                flags = (flags and (3 shl CAMERA_SHIFT).inv()) or ((cameraMode and 3) shl CAMERA_SHIFT)
                data[base + OFF_FLAGS] = flags.toFloat()
                state.cameraModes[slot] = cameraMode
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

            if (source.textureRevision != state.textureRevisions[slot] ||
                textureGeneration != state.textureGenerations[slot]
            ) {
                val resolved = resolveTextures(source)
                if (!resolved.isValid ||
                    resolved.base.bindingKey != expectedBindingKey ||
                    resolved.mask?.bindingKey != expectedMaskBindingKey
                ) {
                    onBindingMismatch(slot, resolved.base.bindingKey, resolved.mask?.bindingKey)
                    kill(slot)
                    state.dirtySlots[dirtyCount++] = slot
                    continue
                }
                data[base + OFF_ANIMATION] =
                    (resolved.base.animationId ?: resolved.base.descriptorId).toFloat()
                data[base + OFF_MASK_ANIMATION] = resolved.mask
                    ?.let { it.animationId ?: it.descriptorId }
                    ?.toFloat()
                    ?: 0f
                state.textureRevisions[slot] = source.textureRevision
                state.textureGenerations[slot] = textureGeneration
                state.bindingKeys[slot] = resolved.base.bindingKey
                state.maskBindingKeys[slot] = resolved.mask?.bindingKey
                val colorMultiplier = resolved.base.colorMultiplier
                state.colorMultipliers[colorBase] = colorMultiplier.x
                state.colorMultipliers[colorBase + 1] = colorMultiplier.y
                state.colorMultipliers[colorBase + 2] = colorMultiplier.z
                data[base + OFF_COLOR] = source.color.x * colorMultiplier.x
                data[base + OFF_COLOR + 1] = source.color.y * colorMultiplier.y
                data[base + OFF_COLOR + 2] = source.color.z * colorMultiplier.z
                data[base + OFF_MASK_COLOR] = packRgb8(resolved.mask?.colorMultiplier).toFloat()
                var flags = data[base + OFF_FLAGS].toInt()
                flags = if (resolved.randomBaseQuarterUv) {
                    flags or FLAG_RANDOM_QUARTER_UV
                } else {
                    flags and FLAG_RANDOM_QUARTER_UV.inv()
                }
                flags = if (resolved.randomMaskQuarterUv) {
                    flags or FLAG_MASK_RANDOM_QUARTER_UV
                } else {
                    flags and FLAG_MASK_RANDOM_QUARTER_UV.inv()
                }
                data[base + OFF_FLAGS] = flags.toFloat()
                state.randomCropModes[slot] = resolved.randomBaseQuarterUv
                state.randomMaskCropModes[slot] = resolved.randomMaskQuarterUv
                changed = true
            }

            if (source.randomAgePreTick != state.randomModes[slot]) {
                var flags = data[base + OFF_FLAGS].toInt()
                flags = if (source.randomAgePreTick) flags or FLAG_RANDOM_AGE else flags and FLAG_RANDOM_AGE.inv()
                data[base + OFF_FLAGS] = flags.toFloat()
                state.randomModes[slot] = source.randomAgePreTick
                changed = true
            }
            val flagsBeforeCollision = data[base + OFF_FLAGS].toInt()
            val blockCollisionBefore = flagsBeforeCollision and FLAG_BLOCK_COLLISION != 0
            if (source.blockCollision != blockCollisionBefore) {
                data[base + OFF_FLAGS] = if (source.blockCollision) {
                    blockCollisionCount++
                    (flagsBeforeCollision or FLAG_BLOCK_COLLISION).toFloat()
                } else {
                    blockCollisionCount--
                    (flagsBeforeCollision and FLAG_BLOCK_COLLISION.inv()).toFloat()
                }
                changed = true
            }
            if (source.appearanceRevision != state.appearanceRevisions[slot]) {
                data[base + OFF_APPEARANCE] = source.appearanceDescriptorId().toFloat()
                state.appearanceRevisions[slot] = source.appearanceRevision
                changed = true
            }
            val explicitSeed = source.randomSeed
            val explicitSeedChanged = (explicitSeed != null) != state.explicitSeedModes[slot] ||
                    (explicitSeed != null && explicitSeed != state.explicitSeeds[slot])
            if (explicitSeedChanged) {
                val seed = explicitSeed ?: CParticleGpuMath.nextAutomaticSeed()
                data[base + OFF_ANIMATION + 2] = CParticleGpuMath.seedLow(seed).toFloat()
                data[base + OFF_ANIMATION + 3] = CParticleGpuMath.seedHigh(seed).toFloat()
                state.explicitSeedModes[slot] = explicitSeed != null
                state.explicitSeeds[slot] = explicitSeed ?: 0
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
        multiplier: Float = 1f,
    ): Boolean {
        if (value == state.snapshots[snapshotOffset]) return false
        data[dataOffset] = value * multiplier
        state.snapshots[snapshotOffset] = value
        return true
    }

    private fun snapshotDynamicSource(
        state: DynamicState,
        slot: Int,
        source: CParticle,
        colorMultiplier: Vector3f,
        randomQuarterUv: Boolean,
        randomMaskQuarterUv: Boolean,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        textureGeneration: Int,
    ) {
        val snapshot = slot * SNAPSHOT_STRIDE
        state.snapshots[snapshot + SNAP_SIZE_W] = source.weightSize
        state.snapshots[snapshot + SNAP_SIZE_H] = source.heightSize
        val hasDirection = source.cameraOption == ParticleCameraOption.ROTATION && source.rotationDirection != null
        val orientation = source.rotationDirection.takeIf { hasDirection }
        snapshotRotation(
            state,
            slot,
            source,
            orientation?.x ?: source.axis.x.toFloat(),
            orientation?.y ?: source.axis.y.toFloat(),
            orientation?.z ?: source.axis.z.toFloat(),
            hasDirection,
        )
        state.snapshots[snapshot + SNAP_COLOR_R] = source.color.x
        state.snapshots[snapshot + SNAP_COLOR_G] = source.color.y
        state.snapshots[snapshot + SNAP_COLOR_B] = source.color.z
        state.snapshots[snapshot + SNAP_ALPHA] = source.alpha
        state.snapshots[snapshot + SNAP_SPEED_LIMIT] = source.speedLimit ?: SYSTEM_SPEED_LIMIT_SENTINEL
        state.cameraModes[slot] = source.cameraOption.ordinal
        state.lights[slot] = source.light
        state.textureRevisions[slot] = source.textureRevision
        state.textureGenerations[slot] = textureGeneration
        state.bindingKeys[slot] = textureBindingKey
        state.maskBindingKeys[slot] = maskTextureBindingKey
        val colorBase = slot * 3
        state.colorMultipliers[colorBase] = colorMultiplier.x
        state.colorMultipliers[colorBase + 1] = colorMultiplier.y
        state.colorMultipliers[colorBase + 2] = colorMultiplier.z
        state.randomCropModes[slot] = randomQuarterUv
        state.randomMaskCropModes[slot] = randomMaskQuarterUv
        state.randomModes[slot] = source.randomAgePreTick
        state.appearanceRevisions[slot] = source.appearanceRevision
        state.explicitSeedModes[slot] = source.randomSeed != null
        state.explicitSeeds[slot] = source.randomSeed ?: 0
    }

    private fun snapshotRotation(
        state: DynamicState,
        slot: Int,
        source: CParticle,
        orientationX: Float,
        orientationY: Float,
        orientationZ: Float,
        hasDirection: Boolean,
    ) {
        val snapshot = slot * SNAPSHOT_STRIDE
        state.snapshots[snapshot + SNAP_YAW] = source.yaw
        state.snapshots[snapshot + SNAP_PITCH] = source.pitch
        state.snapshots[snapshot + SNAP_AXIS_X] = orientationX
        state.snapshots[snapshot + SNAP_AXIS_Y] = orientationY
        state.snapshots[snapshot + SNAP_AXIS_Z] = orientationZ
        state.snapshots[snapshot + SNAP_ROLL] = source.roll
        state.snapshots[snapshot + SNAP_ANGULAR_PITCH] = source.angularVelocity.x
        state.snapshots[snapshot + SNAP_ANGULAR_YAW] = source.angularVelocity.y
        state.snapshots[snapshot + SNAP_ANGULAR_ROLL] = source.angularVelocity.z
        state.directionModes[slot] = hasDirection
    }

    private fun writeDynamicRotation(
        state: DynamicState,
        slot: Int,
        source: CParticle,
        tick: Int,
        previousCameraMode: Int,
        cameraMode: Int,
        hasDirection: Boolean,
    ) {
        val base = slot * STRIDE
        val snapshot = slot * SNAPSHOT_STRIDE
        val current = currentRotation(slot, tick)
        val oldFlags = data[base + OFF_FLAGS].toInt()
        val oldHasDirection = previousCameraMode == ParticleCameraOption.ROTATION.ordinal &&
                oldFlags and FLAG_ROTATION_DIRECTION != 0
        val oldDirection = if (oldHasDirection) {
            Vector3f(data[base + OFF_AXIS], data[base + OFF_AXIS + 1], data[base + OFF_AXIS + 2])
        } else {
            null
        }
        val oldPointed = CParticleGpuMath.directionAngles(oldDirection)
        val definitionChanged = previousCameraMode != cameraMode || state.directionModes[slot] != hasDirection
        val yawChanged = source.yaw != state.snapshots[snapshot + SNAP_YAW]
        val pitchChanged = source.pitch != state.snapshots[snapshot + SNAP_PITCH]
        val orientation = source.rotationDirection.takeIf { hasDirection }
        val sourceOrientationX = orientation?.x ?: source.axis.x.toFloat()
        val sourceOrientationY = orientation?.y ?: source.axis.y.toFloat()
        val sourceOrientationZ = orientation?.z ?: source.axis.z.toFloat()
        val nextOrientationX = if (definitionChanged ||
            sourceOrientationX != state.snapshots[snapshot + SNAP_AXIS_X]
        ) sourceOrientationX else data[base + OFF_AXIS]
        val nextOrientationY = if (definitionChanged ||
            sourceOrientationY != state.snapshots[snapshot + SNAP_AXIS_Y]
        ) sourceOrientationY else data[base + OFF_AXIS + 1]
        val nextOrientationZ = if (definitionChanged ||
            sourceOrientationZ != state.snapshots[snapshot + SNAP_AXIS_Z]
        ) sourceOrientationZ else data[base + OFF_AXIS + 2]
        val preserveEulerPhase = previousCameraMode == ParticleCameraOption.ROTATION.ordinal
        val pitchPhase = if (preserveEulerPhase) {
            current.x - if (oldHasDirection) oldPointed.x else state.snapshots[snapshot + SNAP_PITCH]
        } else {
            0f
        }
        val yawPhase = if (preserveEulerPhase) {
            current.y - if (oldHasDirection) oldPointed.y else state.snapshots[snapshot + SNAP_YAW]
        } else {
            0f
        }
        val rollPhase = current.z - state.snapshots[snapshot + SNAP_ROLL]
        data[base + OFF_SIZE + 2] = when {
            cameraMode != ParticleCameraOption.ROTATION.ordinal ->
                if (definitionChanged || yawChanged) source.yaw else data[base + OFF_SIZE + 2]
            hasDirection -> yawPhase
            else -> source.yaw + yawPhase
        }
        data[base + OFF_SIZE + 3] = when {
            cameraMode != ParticleCameraOption.ROTATION.ordinal ->
                if (definitionChanged || pitchChanged) source.pitch else data[base + OFF_SIZE + 3]
            hasDirection -> pitchPhase
            else -> source.pitch + pitchPhase
        }
        data[base + OFF_AXIS] = nextOrientationX
        data[base + OFF_AXIS + 1] = nextOrientationY
        data[base + OFF_AXIS + 2] = nextOrientationZ
        data[base + OFF_ROLL] = source.roll + rollPhase
        data[base + OFF_ANGULAR_VELOCITY] = source.angularVelocity.x
        data[base + OFF_ANGULAR_VELOCITY + 1] = source.angularVelocity.y
        data[base + OFF_ANGULAR_VELOCITY + 2] = source.angularVelocity.z
        data[base + OFF_ANIMATION + 1] = ages[slot].toFloat()
        data[base + OFF_EPOCH_TICK] = tick.toFloat()
        var flags = data[base + OFF_FLAGS].toInt()
        flags = if (hasDirection) flags or FLAG_ROTATION_DIRECTION else flags and FLAG_ROTATION_DIRECTION.inv()
        data[base + OFF_FLAGS] = flags.toFloat()
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
        val directionModes = BooleanArray(capacity)
        val textureRevisions = IntArray(capacity)
        val textureGenerations = IntArray(capacity)
        val bindingKeys = arrayOfNulls<CParticleTextureBindingKey>(capacity)
        val maskBindingKeys = arrayOfNulls<CParticleTextureBindingKey>(capacity)
        val colorMultipliers = FloatArray(capacity * 3)
        val randomCropModes = BooleanArray(capacity)
        val randomMaskCropModes = BooleanArray(capacity)
        val randomModes = BooleanArray(capacity)
        val appearanceRevisions = IntArray(capacity)
        val explicitSeedModes = BooleanArray(capacity)
        val explicitSeeds = IntArray(capacity)
        val dirtySlots = IntArray(capacity)
        var sourceCount = 0
    }

    private class KilledState(capacity: Int) {
        val slots = IntArray(capacity)
        val queuedBits = LongArray((capacity + 63) ushr 6)
        var count = 0
    }

}
