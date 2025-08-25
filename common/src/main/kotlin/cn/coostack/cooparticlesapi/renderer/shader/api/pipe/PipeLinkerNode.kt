package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

class PipeLinkerNode(val pipe: ShaderPipe, val channel: Int) {
    override fun equals(other: Any?): Boolean {
        if (other !is PipeLinkerNode) return false
        return pipe == other.pipe && channel == other.channel
    }

    override fun hashCode(): Int {
        var result = channel
        result = 31 * result + pipe.hashCode()
        return result
    }
}