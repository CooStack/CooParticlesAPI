package cn.coostack.cooparticlesapi.event.events.client

import cn.coostack.cooparticlesapi.event.api.CooEvent

/** 世界主渲染入口发布一次，用于重置每帧预算，不在实体重放时发布。 */
class ClientFrameStartEvent : CooEvent()
