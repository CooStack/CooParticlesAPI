package cn.coostack.cooparticlesapi.cparticle.storage

class CParticleCommandGlBuffer {
    val initialized: Boolean get() = false
    fun init() {}
    fun upload(data: FloatArray, count: Int) {}
    fun release() {}
    fun dispose() {}
    fun bindShaderStorage(binding: Int) {}
}
