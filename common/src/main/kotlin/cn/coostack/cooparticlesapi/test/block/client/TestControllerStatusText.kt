package cn.coostack.cooparticlesapi.test.block.client

internal fun testControllerRunningStatus(
    currentIndex: Int,
    optionCount: Int,
    currentOptionId: String
): String {
    return testControllerStatusWithOptionId("运行中", currentIndex, optionCount, currentOptionId)
}

internal fun testControllerStatusWithOptionId(
    status: String,
    currentIndex: Int,
    optionCount: Int,
    currentOptionId: String
): String {
    if (currentIndex <= 0 || optionCount <= 0) {
        return status
    }
    return buildString {
        append(status).append(" 当前索引: ").append(currentIndex).append('/').append(optionCount)
        if (currentOptionId.isNotBlank()) {
            append(" ID: ").append(currentOptionId)
        }
    }
}
