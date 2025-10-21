package cn.coostack.cooparticlesapi.apt.enums

/**
 * 事件执行优先级
 * DEFAULT 及以上会优先于fabric的trigger事件处理
 * 以下会在trigger之后处理
 */
enum class EventPriority(val priority: Int) {
    LOWEST(0),
    LOW(1),
    DEFAULT(2),
    HIGH(3),
    HIGHEST(4)
}