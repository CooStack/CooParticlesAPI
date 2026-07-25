package cn.coostack.cooparticlesapi.test.block

/**
 * 方块测试组遇到 `MANUAL_VISUAL` Option 自然结束时采用的处理策略。
 *
 * [MANUAL_VISUAL] 保留待复核状态，必须由控制器执行通过、失败或跳过；[AUTO] 直接按通过处理。
 * Option 自身是否需要视觉复核仍由 `TestReviewMode` 声明，本枚举只决定方块测试组如何处理该声明。
 */
enum class BlockTestReviewMode {
    AUTO,
    MANUAL_VISUAL
}
