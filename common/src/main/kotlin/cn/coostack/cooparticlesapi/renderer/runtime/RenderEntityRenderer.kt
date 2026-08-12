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
     * 是否让可见世界几何进入当前 shader pack 的实体渲染阶段。
     *
     * `true` 会让可见几何进入 Iris，参与 shader pack 的曝光、雾和材质处理。
     * `false` 会在 shader pack final pass 后绘制，适合需要保持自身颜色和后处理结果的特效。
     * 未启用 shader pack 时，该设置不改变绘制结果。
     */
    val shaderPackHandled: Boolean get() = false

    /**
     * 该实体类型共享的不可变渲染 pipeline。
     *
     * Runtime 会缓存它的拓扑编译结果，因此实现不能在实体绘制期间替换或修改 pipeline 图。
     * 同一帧中，相同 pipeline id 和 fullscreen 参数的实体会共用附件，并且只执行一次 fullscreen 链。
     * world 节点参数按实体绘制求值，不会拆分后处理批次；fullscreen 参数值不同时会分别执行。
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
