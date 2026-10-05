package cn.coostack.cooparticlesapi.event.events.client

import cn.coostack.cooparticlesapi.event.api.CooEvent

/** 客户端资源应用时在渲染线程发布，释放依赖旧贴图或模型的缓存。 */
class ClientResourceReloadEvent : CooEvent()
