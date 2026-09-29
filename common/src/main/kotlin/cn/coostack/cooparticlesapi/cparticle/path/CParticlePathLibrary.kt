package cn.coostack.cooparticlesapi.cparticle.path

import cn.coostack.cooparticlesapi.gpudata.CooGpuDataBindingPoints
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataGlBuffer
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataHandle
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataLayer
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataLayerView
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataRegistry
import org.lwjgl.opengl.GL43

/**
 * # 路径槽位
 *
 * 一个槽位代表一条路径在共享图层中的位置。它把三件事分开：
 * - [definition]：用户编辑的几何与控制柄。
 * - [layerHandle]：这条路径在图层的稳定引用；槽位回收后句柄失效，旧命令不会引用到新路径。
 * - [base]：当前在图层缓冲中的 float 基址，由图层映射表维护。
 *
 * 槽位由 [CParticlePathLibrary] 创建与回收，调用方不应直接构造。
 */
class CParticlePathSlot internal constructor(
    /** 图层槽位号；写入命令参数。 */
    val slot: Int,
    /** 路径几何资源。 */
    val definition: CParticlePathDefinition,
    /** 图层引用句柄。 */
    internal val layerHandle: CooGpuDataHandle,
) {
    /** 当前在图层缓冲中的 float 基址；未分配时为 [CooPathLayer.SLOT_FREE]。 */
    var base: Int = CooPathLayer.SLOT_FREE
        internal set

    /** 该槽位当前占用的 float 数（含负载表头）。 */
    var allocatedFloats: Int = 0
        internal set

    /** 已经写入图层的几何版本；与 [CParticlePathDefinition] 的修订号比较以决定是否重传。 */
    internal var writtenRevision: Long = Long.MIN_VALUE

    /** 槽位是否仍然有效。 */
    var released: Boolean = false
        internal set

    /**
     * 取本路径所在图层的只读读取视图。
     *
     * RenderEntity 的 shader 与 CParticle 的 compute 读取**同一个** GL 缓冲：shader 侧声明
     * `layout(std430, binding = binding) readonly buffer`（绑定值见 [CParticlePathLibrary.binding]），
     * CPU 侧用本视图换算下标做对照读取。
     *
     * 视图不增加引用计数；槽位被释放后 [CooGpuDataLayerView.isAlive] 会变为 `false`。
     *
     * @return 图层读取视图
     */
    fun layerView(): CooGpuDataLayerView =
        CooGpuDataLayerView.of(layerHandle, CParticlePathLibrary.binding, CooPathLayer.layout)

    /** 当前图层重建版本；与负载表头缓存的版本比较可判断基址是否仍然有效。 */
    val revision: Int
        get() = CParticlePathLibrary.layerRevision

    override fun toString(): String = "CParticlePathSlot#$slot(base=$base)"
}

/**
 * # 路径资源库
 *
 * 统一管理路径槽位的分配、几何变更同步、图层重建与回收。这里集中了三条关键不变量：
 *
 * 1. **一个绑定点承载全部路径**。OpenGL 4.3 只保证 8 个着色器存储绑定点，CParticle 的 compute
 *    已经占用 0..3，因此路径共用 [CooPathLayer.layout] 一张图层与一个绑定。
 * 2. **几何只在变化时重建**。静态路径首次写入后不再采样、不再上传。
 * 3. **释放与复用不会让旧命令读到新资源**。槽位回收会让图层引用计数归零、句柄世代递增；
 *    命令在运行时会校验图层重建版本，不匹配就整条命令跳过。
 *
 * ### 线程与生命周期
 * - 声明式入口（[create]、[release]、[revision]）可在服务端与客户端调用。
 * - [syncLayer] / [bind] 只在客户端渲染线程调用。
 * - [invalidateGlResources] 用于资源重载、换世界与断线，会保留仍被引用的路径定义。
 */
object CParticlePathLibrary {
    private val slots = arrayOfNulls<CParticlePathSlot>(CooPathLayer.SLOT_COUNT)
    private val allocatedSlots = ArrayList<Int>()

    private var layer: CooGpuDataLayer? = null
    private var glBuffer: CooGpuDataGlBuffer? = null

    /**
     * 路径图层的着色器存储绑定点。
     *
     * 固定为 [CooGpuDataBindingPoints.PATH_LAYER]：全部路径共用一张图层，
     * 因此不需要按路径数量动态申请绑定点。
     */
    val binding: Int
        get() = CooGpuDataBindingPoints.PATH_LAYER

    /** 图层重建版本；任何基址重新分配都会递增。 */
    var layerRevision: Int = 0
        private set

    /** 当前已分配的槽位数量。 */
    val allocatedCount: Int
        get() = allocatedSlots.size

    /** 当前图层占用的 float 数；尚未创建时为 0。 */
    val layerFloatCount: Int
        get() = layer?.floatCount ?: 0

    /**
     * 当前图层数据的只读快照引用，供 CPU 侧路径求值使用。
     *
     * CPU 从同版本图层求值，不从 GPU 回读路径纹理或粒子位置。图层尚未创建时返回 `null`，
     * 此时所有路径命令都会被跳过而不是读到无效数据。
     *
     * @return 图层数据数组；没有路径时为 `null`
     */
    fun currentLayerData(): FloatArray? = layer?.data

    /**
     * 用一份可序列化几何在本地建立路径。
     *
     * 这是「同步过来的几何数据 → 本地可用路径」的标准入口：持有者（发射器 / 实体）把
     * [CParticlePathGeometry] 作为自己的数据同步，每一侧在真正需要时调用本方法建立**自己的**槽位。
     * 因为是从同一份数据建立的，两侧形状一致；而几何是任意数据，不受预设种类限制。
     *
     * @param geometry 要建立的几何
     * @return 新建的路径槽位；调用方负责在用完后 [release]
     */
    @Synchronized
    fun create(geometry: CParticlePathGeometry): CParticlePathSlot =
        create(geometry.points, geometry.segmentType, geometry.closed)

    /**
     * 创建一条新路径并占用一个槽位。
     *
     * @param points 控制点序列，至少两个点
     * @param segmentType 段类型
     * @param closed 几何是否首尾相连
     * @return 新建的路径槽位
     * @throws IllegalStateException 槽位耗尽时抛出；调用方应复用或回收已有路径
     */
    @Synchronized
    fun create(
        points: List<CParticlePathPoint>,
        segmentType: CParticlePathSegmentType = CParticlePathSegmentType.LINEAR,
        closed: Boolean = false,
    ): CParticlePathSlot {
        val index = nextFreeSlot()
        val definition = CParticlePathDefinition(points, segmentType, closed)
        val handle = CooGpuDataRegistry.allocate(CooPathLayer.layout, CooPathLayer.PAYLOAD_BASE)
        val slot = CParticlePathSlot(index, definition, handle)
        slots[index] = slot
        allocatedSlots.add(index)
        layerRebuildRequired = true
        return slot
    }

    /**
     * 取一个仍然有效的槽位。
     *
     * @param index 槽位号
     * @return 有效槽位；已释放或越界时返回 `null`
     */
    fun slotAt(index: Int): CParticlePathSlot? {
        if (index !in 0 until CooPathLayer.SLOT_COUNT) return null
        return slots[index]?.takeIf { !it.released }
    }

    /**
     * 为一个新使用者增加该路径的图层引用。
     *
     * 示例：第二个对象或第二条命令要共享同一条路径时先调用它，之后各自在不再使用时 [release]。
     * 禁止：只加引用而不配对释放，图层会被永久占住。
     *
     * @param slot 要共享的路径槽位
     * @return 引用成功时返回 `true`；槽位已释放时返回 `false`
     */
    @Synchronized
    fun retain(slot: CParticlePathSlot): Boolean {
        if (slot.released) return false
        if (slots.getOrNull(slot.slot) !== slot) return false
        return CooGpuDataRegistry.acquire(slot.layerHandle)
    }

    /**
     * 释放一个使用者对槽位持有的引用。
     *
     * 引用计数归零时才清空图层数据并让槽位号可被 [create] 复用；仍有其他引用者时本调用只减少计数，
     * 槽位保持有效。新槽位总是取得新的图层世代，因此旧命令不会静默引用到新路径。
     *
     * @param slot 要释放的槽位引用
     * @return 本次释放让引用计数归零（即真正回收了槽位）时返回 `true`
     */
    @Synchronized
    fun release(slot: CParticlePathSlot): Boolean {
        val index = slot.slot
        if (slot.released) return false
        val becameUnreferenced = CooGpuDataRegistry.release(slot.layerHandle)
        if (!becameUnreferenced) return false
        if (slots.getOrNull(index) !== slot) return false
        slot.released = true
        slots[index] = null
        allocatedSlots.remove(index)
        layerRebuildRequired = true
        return true
    }

    /** 回收全部槽位；用于换世界与断线。 */
    @Synchronized
    fun releaseAll() {
        for (index in allocatedSlots.toList()) {
            slots[index]?.let { release(it) }
        }
        CooGpuDataRegistry.clear()
        releaseLayer()
    }

    /**
     * 让所有槽位在下一帧重新写入图层。
     *
     * 示例：资源重载或图层缓冲被重建后调用。
     * 禁止：不要在逐 tick 路径上调用，它会让所有静态路径重新上传一次。
     */
    @Synchronized
    fun invalidateGeometrySync() {
        for (index in allocatedSlots) {
            slots[index]?.writtenRevision = Long.MIN_VALUE
        }
        layerRevision++
    }

    /**
     * 把几何变化同步到图层，并在需要时重建缓冲布局与上传脏区间。
     *
     * 只有 [CParticlePathDefinition] 修订号变化的槽位才会重新采样并标记脏区间，
     * 因此静态路径在首次上传后不会再产生采样或 GL 调用。
     *
     * 本方法只在客户端渲染线程（持有 GL 上下文）调用。
     */
    fun syncLayer() {
        val target = ensureLayer() ?: return
        if (layerRebuildRequired) {
            rebuildLayer(target)
        }
        for (index in allocatedSlots) {
            val slot = slots[index] ?: continue
            val revision = slot.definition.revision
            if (slot.writtenRevision == revision && slot.base != CooPathLayer.SLOT_FREE) continue
            if (slot.base == CooPathLayer.SLOT_FREE) continue
            writeSlotGeometry(target, slot)
            slot.writtenRevision = revision
        }
        val buffer = glBuffer ?: return
        if (buffer.prepare(target) || target.needsUpload) {
            buffer.upload(target)
        }
        glResourcesActive = true
    }

    /**
     * 绑定图层缓冲到路径绑定点。
     *
     * 由 CParticle 的模拟刷新与粒子渲染阶段调用，使同一渲染帧内的 RenderEntity shader
     * 也能读到同一份图层。GL 资源尚未创建时不做任何 GL 调用，避免在无上下文环境报错。
     */
    fun bind() {
        if (!glResourcesActive) return
        val buffer = glBuffer ?: return
        if (!buffer.initialized) return
        buffer.bindShaderStorage(binding)
    }

    /**
     * 解除路径图层绑定。
     *
     * 示例：客户端断开连接、切换世界或完全释放 GPU 数据图层时调用。
     */
    fun unbind() {
        if (!glResourcesActive) return
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, 0)
    }

    /** 释放 GL 资源，保留 CPU 侧路径定义；下一次 [syncLayer] 会重建并整段重传。 */
    fun invalidateGlResources() {
        glBuffer?.release()
        glResourcesActive = false
        invalidateGeometrySync()
    }

    /** 彻底销毁：回收全部槽位与 GL 资源。 */
    fun clear() {
        releaseAll()
    }

    // ------------------------------------------------------------------ 内部

    private var layerRebuildRequired = true

    /** GL 缓冲是否已经创建；为 `false` 时 [bind] 不产生任何 GL 调用。 */
    private var glResourcesActive = false

    private fun nextFreeSlot(): Int {
        for (index in 0 until CooPathLayer.SLOT_COUNT) {
            if (slots[index] == null) return index
        }
        error(
            "Particle path slot table is full (${CooPathLayer.SLOT_COUNT} slots); " +
                "release unused paths instead of allocating more"
        )
    }

    private fun ensureLayer(): CooGpuDataLayer? {
        layer?.let { return it }
        if (allocatedSlots.isEmpty()) return null
        val created = CooGpuDataLayer(CooPathLayer.layout, CooPathLayer.PAYLOAD_BASE)
        val required = requiredLayerFloats()
        created.ensureCapacity(required)
        created.resize(required)
        val data = created.data
        data[CooPathLayer.LH_MAGIC] = CooPathLayer.MAGIC.toFloat()
        data[CooPathLayer.LH_ABI] = CooPathLayer.ABI_VERSION.toFloat()
        data[CooPathLayer.LH_SLOT_COUNT] = CooPathLayer.SLOT_COUNT.toFloat()
        layer = created
        glBuffer = CooGpuDataGlBuffer()
        layerRebuildRequired = true
        return created
    }

    private fun releaseLayer() {
        glBuffer?.dispose()
        glBuffer = null
        layer = null
        layerRebuildRequired = true
        glResourcesActive = false
        layerRevision++
    }

    /** 按当前存活槽位的负载规模计算图层需要的 float 数。 */
    private fun requiredLayerFloats(): Int {
        var total = CooPathLayer.PAYLOAD_BASE
        for (index in allocatedSlots) {
            val slot = slots[index] ?: continue
            total += slotFloats(slot)
        }
        return total
    }

    private fun slotFloats(slot: CParticlePathSlot): Int =
        CooPathLayer.CooPathPayload.floats(slot.definition.layerPointCount, slot.definition.sampleCount)

    /**
     * 重新分配全部槽位基址，并整段重写映射表与负载。
     *
     * 重建会把每条路径的几何重新写一遍，因此只在槽位增删或某条路径超出预算时发生；
     * 单条路径增点通常只会触发它自己的负载扩张，代价与路径数线性相关，与粒子数无关。
     */
    private fun rebuildLayer(target: CooGpuDataLayer) {
        val required = requiredLayerFloats()
        target.ensureCapacity(required)
        target.resize(required)
        val data = target.data
        data[CooPathLayer.LH_MAGIC] = CooPathLayer.MAGIC.toFloat()
        data[CooPathLayer.LH_ABI] = CooPathLayer.ABI_VERSION.toFloat()
        data[CooPathLayer.LH_SLOT_COUNT] = CooPathLayer.SLOT_COUNT.toFloat()
        data[CooPathLayer.LH_PATH_COUNT] = allocatedSlots.size.toFloat()
        data[CooPathLayer.LH_TOTAL_FLOATS] = required.toFloat()

        for (index in 0 until CooPathLayer.SLOT_COUNT) {
            data[CooPathLayer.SLOT_TABLE + index] = CooPathLayer.SLOT_FREE.toFloat()
        }

        var cursor = CooPathLayer.PAYLOAD_BASE
        for (index in allocatedSlots) {
            val slot = slots[index] ?: continue
            val floats = slotFloats(slot)
            check(floats <= CooPathLayer.MAX_FLOATS_PER_PATH) {
                "Particle path #$index needs $floats floats which exceeds " +
                    "${CooPathLayer.MAX_FLOATS_PER_PATH}"
            }
            slot.base = cursor
            slot.allocatedFloats = floats
            data[CooPathLayer.SLOT_TABLE + index] = cursor.toFloat()
            cursor += floats
        }
        layerRevision++
        data[CooPathLayer.LH_REBUILD_VERSION] = layerRevision.toFloat()
        // 映射表与表头在同一个写操作里更新，使用端不会读到新表头配旧表。
        target.markDirty(0, cursor - 1)
        layerRebuildRequired = false

        for (index in allocatedSlots) {
            val slot = slots[index] ?: continue
            writeSlotGeometry(target, slot)
            slot.writtenRevision = slot.definition.revision
        }
    }

    private fun writeSlotGeometry(target: CooGpuDataLayer, slot: CParticlePathSlot) {
        val base = slot.base
        if (base == CooPathLayer.SLOT_FREE) return
        slot.definition.writeToLayer(target, base, layerRevision)
        val writtenFloats = CooPathLayer.CooPathPayload.floats(
            slot.definition.layerPointCount,
            slot.definition.sampleCount,
        )
        target.markDirty(base, base + writtenFloats - 1)
    }
}
