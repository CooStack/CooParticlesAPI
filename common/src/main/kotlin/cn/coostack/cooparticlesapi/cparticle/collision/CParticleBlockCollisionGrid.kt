package cn.coostack.cooparticlesapi.cparticle.collision

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL43
import org.lwjgl.system.MemoryUtil
import java.nio.IntBuffer
import java.util.Arrays

/**
 * 保存一块供 CParticle 模拟器共享的方块占用位图。
 *
 * 任意非空碰撞形状都按完整方块处理，因此栅栏、台阶等复杂形状是保守近似。
 * Example: 同一 emitter 的不同纹理 system 可复用同一个网格。
 * Forbidden: 不要把本对象用于精确实体或 `VoxelShape` 碰撞。
 *
 * @property minX 网格最小世界 X
 * @property minY 网格最小世界 Y
 * @property minZ 网格最小世界 Z
 * @property size 每个轴的方块数
 */
internal class CParticleBlockCollisionGrid(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val size: Int = SIZE,
) {
    init {
        require(size > 0) { "Collision grid size must be positive: $size" }
        require(size.toLong() * size <= Int.MAX_VALUE.toLong() / size) {
            "Collision grid is too large: $size^3"
        }
    }

    /** 当前实例的网格单元总数。 */
    private val cellCount = size * size * size

    /** 当前实例的 32-bit 占用字数量。 */
    private val wordCount = ((cellCount.toLong() + 31) / 32).toInt()

    /** CPU 与 GPU 共用的 32-bit 占用位图。 */
    private val words = IntArray(wordCount)

    /** 上传位图时按需创建的直接缓冲；网格淘汰时显式释放。 */
    private var uploadBuffer: IntBuffer? = null

    /** 当前网格的 SSBO；仅在 GPU 模拟实际使用时创建。 */
    private var glBuffer = 0

    /** CPU 位图是否需要重新上传。 */
    private var gpuDirty = true

    /** 上次从客户端世界重建网格时的世界 tick。 */
    private var lastRefreshTick = Long.MIN_VALUE

    /** 最近一次被 system 使用的 manager tick，用于淘汰闲置网格。 */
    var lastUsedTick = Int.MIN_VALUE

    /**
     * 按固定间隔从已加载区块刷新占用位图。
     *
     * Example: 多个 system 在同一 tick 调用时只会重建一次。
     * Forbidden: 不会强制加载网格覆盖范围内的区块。
     *
     * @param level 当前客户端世界
     */
    fun refreshIfNeeded(level: ClientLevel) {
        val tick = level.gameTime
        if (lastRefreshTick != Long.MIN_VALUE &&
            tick >= lastRefreshTick &&
            tick - lastRefreshTick < REFRESH_INTERVAL_TICKS
        ) {
            return
        }
        rebuild(level)
        lastRefreshTick = tick
    }

    /**
     * 查询网格局部坐标是否被碰撞方块占用。
     *
     * Example: `isOccupied(1, 2, 3)` 查询 `[min + (1,2,3)]`。
     * Forbidden: 网格外坐标不会被当成墙体。
     *
     * @param x 局部 X
     * @param y 局部 Y
     * @param z 局部 Z
     * @return 对应单元在网格内且被占用时为 `true`
     */
    fun isOccupied(x: Int, y: Int, z: Int): Boolean {
        if (x !in 0 until size || y !in 0 until size || z !in 0 until size) return false
        val index = indexOf(x, y, z)
        return words[index ushr 5] and (1 shl (index and 31)) != 0
    }

    /**
     * 把当前位图绑定到 compute shader 的指定 SSBO binding。
     *
     * Example: GPU 模拟器在 dispatch 前绑定到 binding `1`。
     * Forbidden: 只能在持有 OpenGL 上下文的渲染线程调用。
     *
     * @param binding shader storage binding 点
     */
    fun bindShaderStorage(binding: Int) {
        if (glBuffer == 0) glBuffer = GL15.glGenBuffers()
        val previous = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, glBuffer)
        if (gpuDirty) {
            val buffer = uploadBuffer ?: MemoryUtil.memAllocInt(wordCount).also { uploadBuffer = it }
            buffer.clear()
            buffer.put(words)
            buffer.flip()
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW)
            gpuDirty = false
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, glBuffer)
    }

    /**
     * 释放网格的 GPU 缓冲和上传缓冲。
     *
     * Example: 世界切换或网格闲置后由 manager 调用。
     * Forbidden: 只能在渲染线程销毁 OpenGL 资源。
     */
    fun release() {
        if (glBuffer != 0) {
            GL15.glDeleteBuffers(glBuffer)
            glBuffer = 0
        }
        uploadBuffer?.let { MemoryUtil.memFree(it) }
        uploadBuffer = null
        gpuDirty = true
    }

    /**
     * 测试入口：直接修改一个局部占用位。
     *
     * Example: 单元测试可放置一个完整方块验证命中法线。
     * Forbidden: 生产路径应通过 [refreshIfNeeded] 从世界生成位图。
     *
     * @param x 局部 X
     * @param y 局部 Y
     * @param z 局部 Z
     * @param occupied 是否占用
     */
    internal fun setOccupiedForTest(x: Int, y: Int, z: Int, occupied: Boolean) {
        require(x in 0 until size && y in 0 until size && z in 0 until size)
        val index = indexOf(x, y, z)
        val word = index ushr 5
        val mask = 1 shl (index and 31)
        words[word] = if (occupied) words[word] or mask else words[word] and mask.inv()
        gpuDirty = true
    }

    /** 从已加载区块重建位图，不保留任何 BlockState 或 VoxelShape。 */
    private fun rebuild(level: ClientLevel) {
        Arrays.fill(words, 0)
        val mutablePos = BlockPos.MutableBlockPos()
        val maxX = minX + size
        val maxY = minY + size
        val maxZ = minZ + size
        val firstChunkX = minX shr 4
        val lastChunkX = (maxX - 1) shr 4
        val firstChunkZ = minZ shr 4
        val lastChunkZ = (maxZ - 1) shr 4
        for (chunkX in firstChunkX..lastChunkX) {
            for (chunkZ in firstChunkZ..lastChunkZ) {
                if (!level.hasChunk(chunkX, chunkZ)) continue
                val chunk = level.getChunk(chunkX, chunkZ)
                val fromX = maxOf(minX, chunkX shl 4)
                val toX = minOf(maxX, (chunkX + 1) shl 4)
                val fromZ = maxOf(minZ, chunkZ shl 4)
                val toZ = minOf(maxZ, (chunkZ + 1) shl 4)
                for (worldX in fromX until toX) {
                    for (worldZ in fromZ until toZ) {
                        for (worldY in maxOf(minY, level.minBuildHeight) until minOf(maxY, level.maxBuildHeight)) {
                            mutablePos.set(worldX, worldY, worldZ)
                            val state = chunk.getBlockState(mutablePos)
                            if (!state.isAir && !state.getCollisionShape(level, mutablePos).isEmpty) {
                                setOccupied(
                                    worldX - minX,
                                    worldY - minY,
                                    worldZ - minZ,
                                )
                            }
                        }
                    }
                }
            }
        }
        gpuDirty = true
    }

    /** 把一个已验证范围内的局部单元设为占用。 */
    private fun setOccupied(x: Int, y: Int, z: Int) {
        val index = indexOf(x, y, z)
        words[index ushr 5] = words[index ushr 5] or (1 shl (index and 31))
    }

    /** 返回 X 最快、随后 Z、最后 Y 的线性单元索引。 */
    private fun indexOf(x: Int, y: Int, z: Int): Int = (y * size + z) * size + x

    /** 保存 CPU/GPU 必须一致的默认网格参数。 */
    companion object {
        /** 相邻共享网格中心的间隔。Example: 同一 16³ 原点分区共享网格。 */
        const val ORIGIN_BUCKET_SIZE = 16

        /** 未声明范围时，原点分区到网格边缘的最小方块余量。 */
        const val DEFAULT_RANGE = 24

        /** 默认每个轴的方块数。Example: `64` 表示 64³ 单元。 */
        const val SIZE = ORIGIN_BUCKET_SIZE + DEFAULT_RANGE * 2

        /** 旧名称保留为默认范围别名。 */
        const val EDGE_MARGIN = DEFAULT_RANGE

        /** 方块状态变化反映到 GPU 网格的最大常规延迟，单位 tick。 */
        const val REFRESH_INTERVAL_TICKS = 5L

        /** 网格单元总数。 */
        const val CELL_COUNT = SIZE * SIZE * SIZE

        /** SSBO 中 32-bit 占用字的数量。 */
        const val WORD_COUNT = (CELL_COUNT + 31) / 32
    }
}
