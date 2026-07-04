package cn.coostack.cooparticlesapi.test.block

enum class BlockTestMode(val id: String, val displayName: String) {
    SEQUENTIAL("sequential", "顺序模式"),
    INDEX("index", "索引模式"),
    LOOP("loop", "循环模式");

    companion object {
        fun fromId(id: String): BlockTestMode {
            return entries.firstOrNull { it.id == id } ?: SEQUENTIAL
        }
    }
}
