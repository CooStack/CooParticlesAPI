package cn.coostack.cooparticlesapi.test.block

/**
 * 决定动态轨道到达末尾后的时间映射方式。
 *
 * 示例：位置轨道使用 [PINGPONG] 后会在起点和终点之间往返。
 * 禁止用该枚举替代 [BlockTestMode]；后者控制的是测试项执行顺序。
 *
 * @property id 持久化和网络传输使用的稳定标识
 * @property displayName 界面显示名称
 */
enum class BlockTestPlaybackMode(val id: String, val displayName: String) {
    /** 播放一次并停在最后一个关键帧。 */
    ONCE("once", "到达后保持"),

    /** 到达终点后的下一 tick 回到起点。 */
    LOOP("loop", "循环"),

    /** 到达终点后反向播放，再从起点转为正向。 */
    PINGPONG("pingpong", "往返");

    /**
     * 保存播放模式的解析入口。
     *
     * 示例：`BlockTestPlaybackMode.fromId("loop")` 返回 [LOOP]。
     * 禁止在这里保存控制器实例状态。
     */
    companion object {
        /**
         * 从稳定标识恢复播放模式，未知值回退为 [ONCE]。
         *
         * 示例：旧存档没有该字段时传入空串即可得到 [ONCE]。
         * 禁止依赖显示名称进行解析，显示文字可以调整。
         *
         * @param id 持久化标识
         * @return 对应的播放模式
         */
        fun fromId(id: String): BlockTestPlaybackMode {
            return entries.firstOrNull { it.id == id } ?: ONCE
        }
    }
}
