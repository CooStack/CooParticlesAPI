package cn.coostack.cooparticlesapi.event.api

enum class EventPriority(val priority: Int) {
    HIGHEST(4),
    HIGH(3),
    NORMAL(2),
    LOW(1),
    LOWEST(0)
}