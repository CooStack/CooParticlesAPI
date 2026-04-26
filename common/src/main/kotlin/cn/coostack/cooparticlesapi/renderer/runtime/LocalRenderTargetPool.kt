package cn.coostack.cooparticlesapi.renderer.runtime

import java.util.concurrent.atomic.AtomicInteger

data class LocalRenderTargetHandle(
    val id: String,
    val label: String,
    val poolId: Int,
    val ordinal: Int
) {
    var width: Int? = null
        private set
    var height: Int? = null
        private set
    var generation: Int = 0
        private set
    var released: Boolean = false
        private set

    internal fun resize(width: Int, height: Int, generation: Int) {
        this.width = width
        this.height = height
        this.generation = generation
    }

    internal fun release() {
        released = true
    }
}

class LocalRenderTargetPool {
    private val poolId = POOL_COUNTER.incrementAndGet()
    private var nextHandleId = 0
    private var generation = 0
    private val activeHandles = LinkedHashMap<String, LocalRenderTargetHandle>()

    fun allocate(label: String = "target"): LocalRenderTargetHandle {
        val handleId = nextHandleId++
        val handle = LocalRenderTargetHandle(
            id = "local-$poolId-$label-$handleId",
            label = label,
            poolId = poolId,
            ordinal = handleId
        )
        activeHandles[handle.id] = handle
        return handle
    }

    fun resize(width: Int, height: Int) {
        generation++
        activeHandles.values.forEach { handle ->
            handle.resize(width, height, generation)
        }
    }

    fun release(handle: LocalRenderTargetHandle) {
        activeHandles.remove(handle.id)?.release()
    }

    fun releaseAll() {
        activeHandles.values.forEach(LocalRenderTargetHandle::release)
        activeHandles.clear()
    }

    fun activeHandles(): List<LocalRenderTargetHandle> {
        return activeHandles.values.toList()
    }

    companion object {
        private val POOL_COUNTER = AtomicInteger()
    }
}
