package cn.coostack.cooparticlesapi.particles.control

enum class ControlType(val id: Int) {
    /**
     * 发包创建粒子
     */
    CREATE(0),
    CHANGE(1),
    REMOVE(2);

    companion object {
        @JvmStatic
        fun getTypeById(id: Int): ControlType {
            return when (id) {
                0 -> CREATE
                1 -> CHANGE
                2 -> REMOVE
                else -> CHANGE
            }
        }
    }
}