package cn.coostack.cooparticlesapi.renderer.shader.buffer

object ShaderBufferCache {
    private val buffers = LinkedHashMap<String, ShaderBufferObject<*>>()
    var shaderStorageSupported: Boolean = true

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(layout: ShaderBufferLayout<T>): ShaderBufferObject<T> {
        val registered = ShaderBufferRegistry.register(layout)
        return buffers.getOrPut(registered.name) {
            ShaderBufferObject(registered, shaderStorageSupported)
        } as ShaderBufferObject<T>
    }

    fun bind(layout: ShaderBufferLayout<*>) {
        getOrCreate(layout).bindBase()
    }

    fun bindAll(layouts: Collection<ShaderBufferLayout<*>>) {
        layouts.forEach(::bind)
    }

    fun unbind(layout: ShaderBufferLayout<*>) {
        getOrCreate(layout).unbind()
    }

    fun release(name: String): Boolean {
        val buffer = buffers.remove(name) ?: return false
        buffer.release()
        return true
    }

    fun release(layout: ShaderBufferLayout<*>): Boolean {
        return release(layout.name)
    }

    fun releaseLayouts(layouts: Collection<ShaderBufferLayout<*>>): Int {
        return layouts
            .asSequence()
            .map { it.name }
            .distinct()
            .count { release(it) }
    }

    fun releaseAll() {
        buffers.values.forEach { it.release() }
        buffers.clear()
    }
}
