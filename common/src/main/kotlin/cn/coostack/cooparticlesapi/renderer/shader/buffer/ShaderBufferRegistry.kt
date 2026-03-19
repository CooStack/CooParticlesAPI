package cn.coostack.cooparticlesapi.renderer.shader.buffer

object ShaderBufferRegistry {
    private val layouts = LinkedHashMap<String, ShaderBufferLayout<*>>()
    private val nextBinding = mutableMapOf(
        ShaderBufferBinding.UNIFORM_BUFFER to 0,
        ShaderBufferBinding.SHADER_STORAGE_BUFFER to 0
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> register(layout: ShaderBufferLayout<T>): ShaderBufferLayout<T> {
        val existing = layouts[layout.name]
        if (existing != null) {
            return existing as ShaderBufferLayout<T>
        }
        val binding = nextBinding.getValue(layout.requestedBinding)
        nextBinding[layout.requestedBinding] = binding + 1
        val assigned = layout.assignBinding(binding)
        layouts[assigned.name] = assigned
        return assigned
    }

    fun get(name: String): ShaderBufferLayout<*>? = layouts[name]

    fun all(): Collection<ShaderBufferLayout<*>> = layouts.values

    fun bindingCount(binding: ShaderBufferBinding): Int = nextBinding.getValue(binding)

    fun clear() {
        layouts.clear()
        nextBinding[ShaderBufferBinding.UNIFORM_BUFFER] = 0
        nextBinding[ShaderBufferBinding.SHADER_STORAGE_BUFFER] = 0
    }
}
