package cn.coostack.cooparticlesapi.event.events.server

import cn.coostack.cooparticlesapi.event.api.CooEvent

/** 服务器停止、清空 API 服务端引用前发布，监听器只释放会话，不修改世界。 */
class ServerStoppedEvent : CooEvent()
