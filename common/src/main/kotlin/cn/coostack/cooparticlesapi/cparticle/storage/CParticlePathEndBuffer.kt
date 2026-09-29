package cn.coostack.cooparticlesapi.cparticle.storage

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL43
import java.util.Arrays

/**
 * # GPU 提前结束槽位缓冲
 *
 * 路径约束的到达消失模式可以在 `maxAge` 之前结束粒子。结束**必须**与现有 CPU 槽位账本一致：
 * 不能只隐藏图像而长期占住槽位，也不能为了回收而同步读回百万粒子状态。
 *
 * 本缓冲是两者之间的紧凑通道：
 *
 * ```
 * [0]              计数器：GPU 本次 dispatch 追加的结束槽位数量
 * [1 .. capacity]  结束槽位号列表
 * ```
 *
 * GPU 侧用一次 `atomicAdd` 取得写入下标，因此追加是并发的、无锁的；CPU 侧平时只读回
 * **一个 4 字节计数器**，只有计数器大于零时才读取列表。列表容量有上限，超过容量时
 * GPU 停止追加但计数器继续增长，CPU 会因此发现漏收并做一次兜底扫描。
 *
 * ## 线程约定
 * 所有方法都只在客户端渲染线程调用。
 */
class CParticlePathEndBuffer(
    private val capacity: Int,
) {
    init {
        require(capacity > 0) { "Path end buffer capacity must be positive: $capacity" }
    }

    /** GL buffer 名字；0 表示尚未创建。 */
    var buffer: Int = 0
        private set

    val initialized: Boolean
        get() = buffer != 0

    /** 计数器读取数组：容量固定为 1 个 int。 */
    private val counterReadBack = IntArray(1)

    /** 计数器写入数组：容量固定为 1 个 int。 */
    private val counterWriteBack = IntArray(1)

    /** 列表读取数组：容量固定为列表容量，与粒子数无关。 */
    private val listReadBack = IntArray(capacity)

    /** 上一次读回的结束事件数量；供性能统计使用。 */
    var lastEndedCount: Int = 0
        private set

    /** 本缓冲是否发生过计数器溢出（结束数量超过列表容量）。 */
    var overflowed: Boolean = false
        private set

    fun init() {
        if (initialized) return
        buffer = GL15.glGenBuffers()
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            GL15.glBufferData(
                GL43.GL_SHADER_STORAGE_BUFFER,
                (1L + capacity) * Int.SIZE_BYTES,
                GL15.GL_DYNAMIC_DRAW,
            )
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
        resetCounter()
    }

    /**
     * 把计数器清零，准备本次 dispatch 的追加。
     *
     * 必须在 dispatch **之前**调用，否则 CPU 会读到上一次已经回收过的槽位。
     */
    fun resetCounter() {
        if (!initialized) return
        counterWriteBack[0] = 0
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, counterWriteBack)
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
        overflowed = false
    }

    fun bindShaderStorage(binding: Int) {
        if (initialized) GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, buffer)
    }

    /**
     * 读回本次 dispatch 的结束槽位。
     *
     * 先读取 4 字节计数器，计数大于零时再读取列表。两次读取使用各自独立的缓冲：
     * `glGetBufferSubData` 只填满缓冲容量，复用同一个缓冲会让 `limit` 与实际写入长度不一致。
     * 读取量固定为 `1 + capacity` 个 int，由构造参数决定，与粒子数无关。
     * 计数为零时不读取列表、不做回收。
     *
     * @param sink 接收结束槽位号的回调
     * @return 本次回收的槽位数量
     */
    fun drain(sink: (Int) -> Unit): Int {
        if (!initialized) return 0
        lastEndedCount = 0
        overflowed = false
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            // 使用数组重载读取：`glGetBufferSubData` 的 DirectBuffer 重载依赖 buffer 的 position，
            // 在某些驱动/绑定版本组合下不会推进 position，导致后续读取越界。
            // 数组重载由 GL 直接填充，不涉及 buffer 游标。
            counterReadBack[0] = 0
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, counterReadBack)
            val reported = counterReadBack[0]
            if (reported <= 0) return 0
            val recorded = reported.coerceAtMost(capacity)
            overflowed = reported > capacity
            Arrays.fill(listReadBack, 0, recorded, 0)
            GL15.glGetBufferSubData(
                GL43.GL_SHADER_STORAGE_BUFFER,
                Int.SIZE_BYTES.toLong(),
                listReadBack,
            )
            for (index in 0 until recorded) {
                sink(listReadBack[index])
            }
            lastEndedCount = recorded
            return recorded
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
    }

    fun release() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer)
            buffer = 0
        }
        lastEndedCount = 0
        overflowed = false
    }

    fun dispose() {
        release()
    }

    /** 供测试与诊断使用的列表容量。 */
    val listedCapacity: Int
        get() = capacity
}
