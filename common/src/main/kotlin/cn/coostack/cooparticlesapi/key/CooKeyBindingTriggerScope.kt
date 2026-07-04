package cn.coostack.cooparticlesapi.key

enum class CooKeyBindingTriggerScope {
    /**
     * GUI和非GUI都可触发
     */
    BOTH,

    /**
     * 只在GUI中触发
     */
    GUI,

    /**
     * 只在非GUI状态触发, 进入GUI后会停止
     */
    NON_GUI,

    /**
     * 只允许从非GUI状态开始触发, 触发后可以长按进入GUI
     */
    NON_GUI_START;

    fun canStart(isGuiOpen: Boolean): Boolean {
        return when (this) {
            BOTH -> true
            GUI -> isGuiOpen
            NON_GUI -> !isGuiOpen
            NON_GUI_START -> !isGuiOpen
        }
    }

    fun canContinue(isGuiOpen: Boolean): Boolean {
        return when (this) {
            BOTH -> true
            GUI -> isGuiOpen
            NON_GUI -> !isGuiOpen
            NON_GUI_START -> true
        }
    }
}
