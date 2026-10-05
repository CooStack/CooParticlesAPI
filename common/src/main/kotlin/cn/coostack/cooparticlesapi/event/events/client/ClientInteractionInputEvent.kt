package cn.coostack.cooparticlesapi.event.events.client

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventCancelable

/**
 * 客户端原版交互入口同步发布，取消后不再发出原版交互包。
 * @property action 输入入口，不表示服务端已接受操作
 */
class ClientInteractionInputEvent(val action: Action) : CooEvent(), EventCancelable {
    /** 是否由监听器接管输入。 */
    override var isCancelled = false

    /**
     * 原版客户端交互入口分类，仅影响当前客户端。
     * ATTACK 为一次左键攻击，USE 为右键使用，CONTINUE_ATTACK 为长按挖掘；
     * 持续挖掘取消不代表一次新的选点，不应重复发送操作。
     */
    enum class Action { ATTACK, USE, CONTINUE_ATTACK }
}
