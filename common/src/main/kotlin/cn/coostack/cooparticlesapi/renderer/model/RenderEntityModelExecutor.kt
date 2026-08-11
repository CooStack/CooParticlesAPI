package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput

fun interface RenderEntityModelExecutor {
    /**
     * 执行 `RenderEntityModelExecutor` 的 `draw` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`draw(model = model, input = input)`。
     *
     * @param model 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param input 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun draw(model: RenderEntityModel, input: RenderInput<*>)
}
