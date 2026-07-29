package cn.coostack.cooparticlesapi.test.block

/**
 * 表示人工复核或自动执行后的测试项结果。
 *
 * 示例：控制器收到通过操作后传入 [PASSED]。
 * 禁止用该结果表示整个测试组的运行模式。
 *
 * @property displayName 状态行使用的中文名称
 */
enum class BlockTestOptionResult(val displayName: String) {
    /** 测试项通过。 */
    PASSED("通过"),

    /** 测试项失败。 */
    FAILED("失败"),

    /** 测试项未执行完成，由操作员跳过。 */
    SKIPPED("跳过")
}
