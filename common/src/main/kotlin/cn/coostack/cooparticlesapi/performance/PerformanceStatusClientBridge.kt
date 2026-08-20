package cn.coostack.cooparticlesapi.performance

/** 客户端 Status 会话支持的远程控制动作。 */
enum class PerformanceStatusControlAction {
    START,
    STOP,
    GUI;

    /** 把协议文本解析为控制动作，未知值返回 null。 */
    companion object {
        /** 按枚举名称解析网络协议字段。 */
        fun fromNetworkName(name: String): PerformanceStatusControlAction? {
            return entries.firstOrNull { action -> action.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * 隔离纯 common 控制包与客户端 Status 实现。
 *
 * dedicated server 可以安全加载本对象；实际 handler 只由客户端入口安装。
 */
object PerformanceStatusClientBridge {
    /** 当前客户端控制动作接收器；服务端环境保持 null。 */
    private var handler: ((PerformanceStatusControlAction) -> Unit)? = null

    /** 由客户端初始化流程安装实际控制器。 */
    fun install(handler: (PerformanceStatusControlAction) -> Unit) {
        this.handler = handler
    }

    /** 把控制包动作转交给已安装的客户端控制器。 */
    fun handle(action: PerformanceStatusControlAction) {
        handler?.invoke(action)
    }
}
