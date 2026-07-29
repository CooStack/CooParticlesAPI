package cn.coostack.cooparticlesapi.test.block

/**
 * 描述一对相邻关键帧之间的进度曲线。
 *
 * 示例：关键帧 A 的 [BEZIER] 会使用 A 的右手柄和 B 的左手柄。
 * 禁止把该值当作空间路径类型；空间位置仍由两端向量插值。
 *
 * @property id 存档使用的稳定标识
 * @property displayName 界面显示名称
 */
enum class BlockTestCurveType(val id: String, val displayName: String) {
    /** 使用等速进度。 */
    LINEAR("linear", "线性"),

    /** 使用两个控制手柄计算三次贝塞尔进度。 */
    BEZIER("bezier", "贝塞尔");

    /**
     * 保存曲线类型的解析入口。
     *
     * 示例：`BlockTestCurveType.fromId("bezier")` 返回 [BEZIER]。
     * 禁止在这里持有可变关键帧。
     */
    companion object {
        /**
         * 从稳定标识恢复曲线类型，未知值回退为 [LINEAR]。
         *
         * 示例：读取旧存档时传入空串会得到线性曲线。
         * 禁止用本地化显示名称作为参数。
         *
         * @param id 持久化标识
         * @return 对应的曲线类型
         */
        fun fromId(id: String): BlockTestCurveType {
            return entries.firstOrNull { it.id == id } ?: LINEAR
        }
    }
}
