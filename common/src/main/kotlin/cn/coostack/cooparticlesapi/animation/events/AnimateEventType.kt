package cn.coostack.cooparticlesapi.animation.events

enum class AnimateEventType {
    /**
     * 准备开始时执行
     */
    BEFORE,

    /**
     * 开始时执行
     */
    START,

    /**
     * 开始后,每tick执行一次
     */
    TICK,

    /**
     * 因其他原因导致此次动画取消执行
     */
    CANCEL,

    /**
     * 结束动画执行
     */
    FINISH

}