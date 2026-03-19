package cn.coostack.cooparticlesapi.renderer.compute

import cn.coostack.cooparticlesapi.renderer.effects.builtin.ComputeDispatchRenderRequest
import org.lwjgl.opengl.GL43.glMemoryBarrier

object ComputeDispatchRenderer {
    fun renderRequests(requests: List<ComputeDispatchRenderRequest>) {
        requests.forEach { request ->
            val program = request.program
            if (program.program == 0) {
                program.init()
            }
            program.useOnContext {
                request.prepare(this)
                dispatch(request.groupX, request.groupY, request.groupZ)
            }
            request.memoryBarrierMask?.let(::glMemoryBarrier)
        }
    }
}
