package cn.coostack.cooparticlesapi.coofx.adapter.renderentity

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxPlayRequest

/**
 * 从少量 RenderEntity 或其他同步主体中提取 CooFX 播放请求的纯适配边界。
 *
 * 实现只读取实体状态并创建不可变请求，不保存当前实体的可变渲染状态，也不承诺跨实体合批。
 * 大量共享网格实例必须交给 mesh particle runtime，而不是通过该接口逐实体提交。
 */
fun interface CooFxRenderEntityRequestAdapter<T : Any> {
    fun createRequest(entity: T): CooFxPlayRequest?
}
