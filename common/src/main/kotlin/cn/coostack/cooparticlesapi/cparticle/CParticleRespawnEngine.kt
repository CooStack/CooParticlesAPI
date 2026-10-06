package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.storage.CParticleRespawnBuffer
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleRespawnRequest
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramRegistry
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL43
import org.lwjgl.system.MemoryUtil
import java.util.BitSet
import java.nio.FloatBuffer

/**
 * 管理出生时预提交的 GPU 生命及后继关系，仅在客户端渲染线程使用。
 * Manager 先模拟全部 system，再捕获死亡、激活后继；不依赖 system 或工作组的执行顺序。
 * 纯 GPU 重生只异步回读回收位图，不读取运动数据，也不在死亡时执行配置闭包。
 */
internal object CParticleRespawnEngine {
    /** 按 GPU slot 直接寻址，避免百万粒子使用 HashMap<Int, Entry> 的 boxing/node 开销。 */
    private class SlotEntries(initialCapacity: Int) {
        private companion object {
            const val PAGE_SHIFT = 8
            const val PAGE_SIZE = 1 shl PAGE_SHIFT
            const val PAGE_MASK = PAGE_SIZE - 1
        }

        // 外层只保存页指针；页本身按实际出现的 slot 懒分配，避免大 system 创建百万引用数组。
        private var pages = arrayOfNulls<Array<Entry?>>(pageCount(initialCapacity))
        var size: Int = 0
            private set

        operator fun get(slot: Int): Entry? {
            if (slot < 0) return null
            val page = pages.getOrNull(slot ushr PAGE_SHIFT) ?: return null
            return page[slot and PAGE_MASK]
        }

        operator fun contains(slot: Int): Boolean = get(slot) != null

        fun getValue(slot: Int): Entry = get(slot)
            ?: throw NoSuchElementException("No prepared lifetime for slot $slot")

        operator fun set(slot: Int, entry: Entry) {
            require(slot >= 0) { "slot must be non-negative" }
            val pageIndex = slot ushr PAGE_SHIFT
            if (pageIndex >= pages.size) {
                var capacity = pages.size.coerceAtLeast(1)
                while (capacity <= pageIndex) capacity = (capacity * 2).coerceAtLeast(pageIndex + 1)
                pages = pages.copyOf(capacity)
            }
            val page = pages[pageIndex] ?: arrayOfNulls<Entry>(PAGE_SIZE).also { pages[pageIndex] = it }
            if (page[slot and PAGE_MASK] == null) size++
            page[slot and PAGE_MASK] = entry
        }

        fun remove(slot: Int): Entry? {
            val page = pages.getOrNull(slot ushr PAGE_SHIFT) ?: return null
            val offset = slot and PAGE_MASK
            val entry = page[offset] ?: return null
            page[offset] = null
            size--
            return entry
        }

        fun forEach(action: (Entry) -> Unit) {
            pages.forEach { page -> page?.forEach { it?.let(action) } }
        }

        fun isEmpty(): Boolean = size == 0

        fun isNotEmpty(): Boolean = size != 0

        private fun pageCount(capacity: Int): Int =
            ((capacity.coerceAtLeast(1) + PAGE_SIZE - 1) ushr PAGE_SHIFT).coerceAtLeast(1)
    }

    private class Entry(val generation: Int, val includeManual: Boolean) {
        var cpuAction: ((CParticleDeathState) -> Unit)? = null

        // 大多数 GPU 后继树节点只有一个 outgoing/incoming link。把这个常见形态
        // 内联到 Entry，避免每个粒子无条件分配两个 ArrayList 和 Pair；分支树才
        // 懒创建 overflow 列表。
        private var linkBatch: Batch? = null
        private var linkIndex = -1
        private var linkOverflow: ArrayList<LinkRef>? = null
        private var incomingBatch: Batch? = null
        private var incomingIndex = -1
        private var incomingOverflow: ArrayList<LinkRef>? = null

        private data class LinkRef(val batch: Batch, val index: Int)

        fun addLink(batch: Batch, index: Int, incoming: Boolean) {
            if (incoming) {
                incomingOverflow?.let {
                    it.add(LinkRef(batch, index))
                    return
                }
                val current = incomingBatch
                if (current == null) {
                    incomingBatch = batch
                    incomingIndex = index
                } else {
                    val overflow = incomingOverflow ?: ArrayList<LinkRef>(2).also {
                        it.add(LinkRef(current, incomingIndex))
                        incomingBatch = null
                        incomingIndex = -1
                        incomingOverflow = it
                    }
                    overflow.add(LinkRef(batch, index))
                }
            } else {
                linkOverflow?.let {
                    it.add(LinkRef(batch, index))
                    return
                }
                val current = linkBatch
                if (current == null) {
                    linkBatch = batch
                    linkIndex = index
                } else {
                    val overflow = linkOverflow ?: ArrayList<LinkRef>(2).also {
                        it.add(LinkRef(current, linkIndex))
                        linkBatch = null
                        linkIndex = -1
                        linkOverflow = it
                    }
                    overflow.add(LinkRef(batch, index))
                }
            }
        }

        fun forEachLink(incoming: Boolean, action: (Batch, Int) -> Unit) {
            val batch = if (incoming) incomingBatch else linkBatch
            if (batch != null) action(batch, if (incoming) incomingIndex else linkIndex)
            val overflow = if (incoming) incomingOverflow else linkOverflow
            // unlink() mutates the relation, so only the uncommon overflow case needs a snapshot.
            overflow?.toList()?.forEach { action(it.batch, it.index) }
        }

        fun removeLink(batch: Batch, index: Int, incoming: Boolean) {
            val singleBatch = if (incoming) incomingBatch else linkBatch
            val singleIndex = if (incoming) incomingIndex else linkIndex
            if (singleBatch === batch && singleIndex == index) {
                if (incoming) {
                    incomingBatch = null
                    incomingIndex = -1
                } else {
                    linkBatch = null
                    linkIndex = -1
                }
                return
            }
            val overflow = if (incoming) incomingOverflow else linkOverflow
            overflow?.removeIf { it.batch === batch && it.index == index }
        }

        fun removeBatch(batch: Batch, incoming: Boolean) {
            val singleBatch = if (incoming) incomingBatch else linkBatch
            if (singleBatch === batch) {
                if (incoming) {
                    incomingBatch = null
                    incomingIndex = -1
                } else {
                    linkBatch = null
                    linkIndex = -1
                }
            }
            val overflow = if (incoming) incomingOverflow else linkOverflow
            overflow?.removeIf { it.batch === batch }
        }
    }

    private class Readback {
        val buffer = CParticleRespawnBuffer()
        val motion = CParticleRespawnBuffer()
        var fence = 0L
        var words = IntArray(0)
        var capturesMotion = false
        var origin = Vec3.ZERO
        val transform = Matrix4f()
    }

    private class Batch(val source: CParticleSystem, val target: CParticleSystem) {
        val buffer = CParticleRespawnBuffer()
        var records = FloatArray(0)
        val free = ArrayDeque<Int>()
        val dirty = BitSet()
        var count = 0
        var live = 0
        private var uploadScratch: FloatBuffer? = null

        fun release() {
            uploadScratch?.let(MemoryUtil::memFree)
            uploadScratch = null
            buffer.release()
        }

        fun add(parent: Int, child: Int, request: ParticleRespawnRequest): Int {
            val offset = if (request.relativeToDeath) request.position else request.position - target.origin
            val offsetX = offset.x.toFloat()
            val offsetY = offset.y.toFloat()
            val offsetZ = offset.z.toFloat()
            val inheritVelocity = request.inheritVelocity.toFloat()
            require(offsetX.isFinite() && offsetY.isFinite() && offsetZ.isFinite() && inheritVelocity.isFinite()) {
                "Respawn parameters exceed GPU float range"
            }
            val index = free.removeLastOrNull() ?: count++
            if (records.size < count * 8) records = records.copyOf(maxOf(count * 8, records.size * 2, 128))
            val base = index * 8
            records[base] = Float.fromBits(parent)
            records[base + 1] = Float.fromBits(child)
            records[base + 2] = 0F
            records[base + 3] = Float.fromBits(if (request.relativeToDeath) 1 else 0)
            records[base + 4] = offsetX
            records[base + 5] = offsetY
            records[base + 6] = offsetZ
            records[base + 7] = inheritVelocity
            dirty.set(index)
            live++
            return index
        }

        fun remove(index: Int) {
            if (records[index * 8].toRawBits() < 0) return
            records[index * 8] = Float.fromBits(-1)
            free.addLast(index)
            dirty.set(index)
            live--
        }

        fun upload() {
            buffer.ensure(records.size.toLong() * Float.SIZE_BYTES)
            // 仅上传变更区间；不能把 CPU 的旧消费标记覆盖 GPU 已消费的记录。
            var first = dirty.nextSetBit(0)
            while (first >= 0) {
                val end = dirty.nextClearBit(first)
                val floatCount = (end - first) * 8
                val scratch = uploadScratch?.takeIf { it.capacity() >= floatCount } ?: run {
                    uploadScratch?.let(MemoryUtil::memFree)
                    MemoryUtil.memAllocFloat(floatCount.coerceAtLeast(128)).also { uploadScratch = it }
                }
                scratch.clear()
                scratch.put(records, first * 8, floatCount)
                scratch.flip()
                buffer.upload(first * 32L, scratch)
                first = dirty.nextSetBit(end)
            }
            dirty.clear()
        }
    }

    private class Channel(val system: CParticleSystem) {
        val entries = SlotEntries(system.capacity)
        val snapshots = CParticleRespawnBuffer()
        val ended = CParticleRespawnBuffer()
        val readbacks = Array(3) { Readback() }
        val pending = ArrayDeque<Readback>()
        val clearSlots = BitSet()
        val batches = LinkedHashMap<CParticleSystem, Batch>()
        var reclaiming = false
        var cpuEntries = 0

        fun release() {
            system.store.preparedDeath = null
            system.store.preparedClear = null
            snapshots.release()
            ended.release()
            batches.values.forEach(Batch::release)
            readbacks.forEach {
                if (it.fence != 0L) GL32.glDeleteSync(it.fence)
                it.buffer.release()
                it.motion.release()
            }
        }
    }

    private val channels = LinkedHashMap<CParticleSystem, Channel>()
    private var captureProgram: CooComputeShaderProgram? = null
    private var activateProgram: CooComputeShaderProgram? = null

    fun owns(system: CParticleSystem): Boolean = channels[system]?.entries?.isNotEmpty() == true

    /** 全部槽位均归预提交通道管理时，不再同步读取旧路径结束通道。 */
    fun requiresPathReadback(system: CParticleSystem): Boolean =
        (channels[system]?.entries?.size ?: 0) < system.store.aliveCount

    /** 为已分配槽位接管生命周期；waiting 为 true 时只占容量，不显示、不模拟。 */
    fun track(system: CParticleSystem, slot: Int, waiting: Boolean, includeManual: Boolean) {
        check(!CParticleCapabilities.forceCpuSimulation) { "Prepared respawn requires GPU simulation" }
        val channel = channels.getOrPut(system) {
            Channel(system).also { current ->
                system.store.preparedClear = { clearSystem(system) }
                system.store.preparedDeath = { target, reason ->
                    val entry = current.entries[target]
                    if (current.reclaiming || entry == null) false else {
                        system.store.requestPreparedDeath(target, reason,
                            reason == RemoveReason.QUEUE || reason == RemoveReason.CALL && !entry.includeManual)
                        true
                    }
                }
            }
        }
        check(slot !in channel.entries) { "Particle already has a prepared lifetime" }
        channel.entries[slot] = Entry(system.store.generations[slot], includeManual)
        channel.clearSlots.set(slot)
        system.store.prepareGpuRespawn(slot, waiting)
    }

    /** 子槽位可使用不同的基础纹理、蒙版、渲染层和 system 原点。 */
    fun link(source: CParticleSystem, parent: Int, target: CParticleSystem, child: Int, request: ParticleRespawnRequest) {
        val channel = channels.getValue(source)
        val batch = channel.batches.getOrPut(target) { Batch(source, target) }
        val index = batch.add(parent, child, request)
        channel.entries.getValue(parent).addLink(batch, index, incoming = false)
        channels.getValue(target).entries.getValue(child).addLink(batch, index, incoming = true)
    }

    /** 双向解除连接，避免提前取消的子槽位复用后被旧父粒子重新激活。 */
    private fun unlink(batch: Batch, index: Int) {
        val parent = batch.records[index * 8].toRawBits()
        if (parent < 0) return
        val child = batch.records[index * 8 + 1].toRawBits()
        channels[batch.source]?.entries?.get(parent)?.removeLink(batch, index, incoming = false)
        channels[batch.target]?.entries?.get(child)?.removeLink(batch, index, incoming = true)
        batch.remove(index)
    }

    /** GPU 到 CPU 明确走异步读回；回调只实例化出生时已经固定的模板。 */
    fun onCpuChildren(system: CParticleSystem, slot: Int, action: (CParticleDeathState) -> Unit) {
        val channel = channels.getValue(system)
        val entry = channel.entries.getValue(slot)
        if (entry.cpuAction == null) channel.cpuEntries++
        entry.cpuAction = action
    }

    /** 出生事务失败时撤销尚未上传的生命，不触发死亡行为。 */
    fun rollback(system: CParticleSystem, slot: Int) {
        val channel = channels[system]
        channel?.entries?.remove(slot)?.let { entry ->
            entry.forEachLink(incoming = false) { batch, index -> unlink(batch, index) }
            entry.forEachLink(incoming = true) { batch, index -> unlink(batch, index) }
            if (entry.cpuAction != null) channel.cpuEntries--
        }
        channel?.clearSlots?.clear(slot)
        system.store.kill(slot, reason = RemoveReason.QUEUE)
        if (channel?.entries?.isEmpty() == true) clearSystem(system)
    }

    /** 在下一次模拟前顺序消费已完成 fence；未就绪时不等待 GPU。 */
    fun poll() {
        for (channel in channels.values) {
            while (channel.pending.isNotEmpty()) {
                val readback = channel.pending.first()
                val status = GL32.glClientWaitSync(readback.fence, 0, 0L)
                check(status != GL32.GL_WAIT_FAILED) { "GPU respawn fence failed" }
                if (status == GL32.GL_TIMEOUT_EXPIRED) break
                channel.pending.removeFirst()
                GL32.glDeleteSync(readback.fence)
                readback.fence = 0L
                readback.buffer.read(readback.words)
                readback.words.forEachIndexed { word, value ->
                    var bits = value
                    while (bits != 0) {
                        val bit = bits.countTrailingZeroBits()
                        bits = bits and (bits - 1)
                        val slot = word * 32 + bit
                        val entry = channel.entries.remove(slot) ?: continue
                        entry.forEachLink(incoming = false) { batch, index -> unlink(batch, index) }
                        entry.forEachLink(incoming = true) { batch, index -> unlink(batch, index) }
                        if (entry.cpuAction != null) channel.cpuEntries--
                        if (!channel.system.checkHandle(slot, entry.generation)) continue
                        try {
                            entry.cpuAction?.let { action ->
                                check(readback.capturesMotion)
                                val motion = readback.motion.readMotion(slot)
                                val flags = motion[7].toInt()
                                if (flags and CParticleInstanceFlags.RESPAWN_CANCELED == 0) {
                                    val position = readback.transform.transformPosition(Vector3f(motion[0], motion[1], motion[2]))
                                    val velocity = readback.transform.transformDirection(Vector3f(motion[4], motion[5], motion[6]))
                                    action(CParticleDeathState(
                                        readback.origin.add(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()),
                                        Vec3(velocity.x.toDouble(), velocity.y.toDouble(), velocity.z.toDouble()),
                                        motion[3].toInt(),
                                        if (flags and CParticleInstanceFlags.RESPAWN_MANUAL != 0) RemoveReason.CALL else RemoveReason.LIFECYCLE,
                                    ))
                                }
                            }
                        } finally {
                            channel.reclaiming = true
                            try {
                                channel.system.store.kill(slot, queueGpuFlag = false, reason = RemoveReason.QUEUE)
                            } finally {
                                channel.reclaiming = false
                            }
                        }
                    }
                }
            }
        }
        // 已回收的通道不再持有缓冲和后端约束，后续出生按新的配置重新接管。
        channels.values.filter { it.entries.isEmpty() }.map { it.system }.forEach(::clearSystem)
    }

    /** 全部 system 模拟后捕获死亡并激活后继，后继从下一 tick 开始模拟。 */
    fun finishTick() {
        if (channels.isEmpty()) return
        if (channels.values.none { it.entries.isNotEmpty() }) return
        check(!CParticleCapabilities.forceCpuSimulation) { "Clear prepared lifetimes before switching to CPU simulation" }
        for (channel in channels.values) {
            if (channel.entries.isEmpty()) continue
            val system = channel.system
            system.ensureRespawnMetadata()
            channel.snapshots.ensure(system.capacity * 32L)
            channel.ended.ensure(((system.capacity + 31L) / 32L) * Int.SIZE_BYTES)
            var first = channel.clearSlots.nextSetBit(0)
            while (first >= 0) {
                val end = channel.clearSlots.nextClearBit(first)
                channel.snapshots.upload(first * 32L, FloatArray((end - first) * 8))
                first = channel.clearSlots.nextSetBit(end)
            }
            channel.clearSlots.clear()
            dispatch(program(true), system.store.activeSlotCount, intArrayOf(system.glBuffer.vbo, channel.snapshots.id, channel.ended.id)) {
                setInt("uFirst", system.store.firstAliveSlot)
                setInt("uCount", system.store.activeSlotCount)
            }
        }
        for (channel in channels.values) {
            for (batch in channel.batches.values) {
                if (batch.live == 0 || batch.target.released) continue
                batch.upload()
                val target = batch.target
                check(target.groupTransform.determinant3x3().let { it.isFinite() && it != 0F }) { "Respawn target transform must be invertible" }
                dispatch(program(false), batch.count, intArrayOf(channel.snapshots.id, target.glBuffer.vbo, target.metadataGlBuffer.buffer, batch.buffer.id)) {
                    setInt("uCount", batch.count)
                    setFloat("uEpoch", target.tickCount.toFloat())
                    setFloat3("uOriginDelta", Vector3f(
                        (batch.source.origin.x - target.origin.x).toFloat(),
                        (batch.source.origin.y - target.origin.y).toFloat(),
                        (batch.source.origin.z - target.origin.z).toFloat(),
                    ))
                    setMatrix4("uSourceTransform", batch.source.groupTransform)
                    setMatrix4("uTargetInverse", Matrix4f(target.groupTransform).invertAffine())
                }
            }
        }
        for (channel in channels.values) {
            if (channel.entries.isEmpty()) continue
            val available = channel.readbacks.firstOrNull { it.fence == 0L } ?: continue
            val words = ((channel.system.capacity.toLong() + 31L) / 32L).toInt()
            if (available.words.size != words) available.words = IntArray(words)
            channel.ended.copyTo(available.buffer, words.toLong() * Int.SIZE_BYTES)
            available.capturesMotion = channel.cpuEntries > 0
            if (available.capturesMotion) {
                channel.snapshots.copyTo(available.motion, channel.system.capacity * 32L)
                available.origin = channel.system.origin
                available.transform.set(channel.system.groupTransform)
            }
            available.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
            channel.pending.addLast(available)
            channel.ended.clear()
        }
        GL11.glFlush()
    }

    private fun program(capture: Boolean): CooComputeShaderProgram {
        val existing = if (capture) captureProgram else activateProgram
        val candidate = existing ?: AdvancedShaderProgramBuilder()
            .compute(if (capture) "core/compute/cparticle_death_capture.comp" else "core/compute/cparticle_respawn.comp")
            .managedId(if (capture) "cparticle/death_capture" else "cparticle/respawn")
            .buildCompute().also { if (capture) captureProgram = it else activateProgram = it }
        ShaderProgramRegistry.register(candidate)
        if (candidate.program == 0) candidate.init()
        return candidate
    }

    private fun dispatch(compute: CooComputeShaderProgram, count: Int, bindings: IntArray, uniforms: CooComputeShaderProgram.() -> Unit) {
        if (count <= 0) return
        val previous = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        val indexed = IntArray(bindings.size) { GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, it) }
        try {
            bindings.forEachIndexed { index, buffer -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, buffer) }
            compute.useOnContext {
                uniforms()
                GL43.glDispatchCompute((count + 255) / 256, 1, 1)
            }
        } finally {
            GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT or GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT or GL43.GL_BUFFER_UPDATE_BARRIER_BIT)
            indexed.forEachIndexed { index, buffer -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, buffer) }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
    }

    /** 清理源 system 时取消其尚未确认的后继，避免永久占用预留槽位。 */
    fun clearSystem(system: CParticleSystem) {
        val channel = channels.remove(system) ?: return
        channel.entries.forEach { entry ->
            entry.forEachLink(incoming = false) { batch, index ->
                val targetSlot = batch.records[index * 8 + 1].toRawBits()
                if (batch.target !== system && !batch.target.released) batch.target.store.kill(targetSlot, reason = RemoveReason.QUEUE)
                unlink(batch, index)
            }
            entry.forEachLink(incoming = true) { batch, index -> unlink(batch, index) }
        }
        for (other in channels.values) {
            other.batches.remove(system)?.let { batch ->
                other.entries.forEach { it.removeBatch(batch, incoming = false) }
                batch.release()
            }
        }
        channel.release()
    }

    fun clear() {
        channels.values.forEach { it.release() }
        channels.clear()
    }

    fun releasePrograms() {
        captureProgram?.let(ShaderProgramRegistry::unregister)
        activateProgram?.let(ShaderProgramRegistry::unregister)
        captureProgram = null
        activateProgram = null
    }
}
