package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline

/**
 * RenderEntity 的统一客户端渲染入口。
 *
 * 客户端注册表为每个 RenderEntity 类型惰性创建一个 renderer，并让该类型的全部实体共享它。
 * 实现可以在 renderer 中保存 shader、vertex buffer 等共享资源。单个实体的可变状态应放在实体中，
 * 或由 renderer 使用实体作为键隔离，不能用一个普通字段保存当前实体状态。
 * 旧式 RenderEntity 自身实现本接口时仍按实体保留独立 renderer，不进入共享缓存。
 *
 * 实现通常只需声明不可变 pipeline，并从 [RenderInput.entity] 读取本次绘制状态：
 * ```kotlin
 * override fun render(input: RenderInput<MyEntity>) {
 *     drawModel(input.entity)
 * }
 * ```
 *
 * @param T renderer 支持的 RenderEntity 类型
 */
interface RenderEntityRenderer<T : RenderEntity> {
    /**
     * 该实体类型共享的不可变渲染 pipeline。
     *
     * Runtime 会缓存它的拓扑编译结果，因此实现不能在实体绘制期间替换或修改 pipeline 图。
     * 同一帧中，相同 pipeline id 的实体会共用附件，并且只执行一次 fullscreen 链。
     * 同一 id 应使用相同的 fullscreen uniform 配置；实体之间不同的参数应放在 world 节点，或改用不同 id。
     */
    val pipeline: CooRenderPipeline<T>

    /**
     * 绘制当前输入中的实体。
     *
     * Runtime 会按 pipeline 节点和渲染阶段调用该方法；实现从 [RenderInput] 读取当前实体、矩阵和阶段。
     *
     * @param input 当前实体及本次渲染所需的上下文
     */
    fun render(input: RenderInput<T>)
}
