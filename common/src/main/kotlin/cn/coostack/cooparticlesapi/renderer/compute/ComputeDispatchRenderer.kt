package cn.coostack.cooparticlesapi.renderer.compute

import cn.coostack.cooparticlesapi.renderer.effects.builtin.ComputeDispatchRenderRequest
import org.lwjgl.opengl.GL43.glMemoryBarrier

internal object ComputeDispatchRenderer {
    /**
     * 执行 `ComputeDispatchRenderer` 的 `renderRequests` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`renderRequests(requests = requests)`。
     *
     * @param requests 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     */
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
