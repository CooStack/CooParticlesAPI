package cn.coostack.cooparticlesapi.event.events.client

import cn.coostack.cooparticlesapi.event.api.CooEvent

/** 客户端关闭且图形上下文尚未销毁时发布，用于释放客户端资源。 */
class ClientStoppingEvent : CooEvent()
