package cn.coostack.cooparticlesapi.utils

/**
 * 服务于
 * @see cn.coostack.cooparticlesapi.particles.control.group.SequencedParticleGroup
 *
 * 为了在SequenceServerParticleGroup中能够自由的修改 SequencedParticleGroup 某个粒子是否显示
 * 同时优化内存防止创建拥有超高容量的数组
 * 使用IntArray 或者 LongArray将 要存储的Boolean数组平均分割 达到节省内存的目的
 */
object MathDataUtil {
    fun setStatusInt(container: Int, bit: Int, status: Boolean): Int {
        if (bit > 32 || bit <= 0) {
            return container
        }
        val move = bit - 1
        return if (status) {
            container or (1 shl move)
        } else {
            (container xor (1 shl move)) and container // 防止把原先就是0的值变成1
        }
    }

    /**
     * @return -1 bit超出int上限
     */
    fun getStatusInt(container: Int, bit: Int): Int {
        if (bit > 32 || bit <= 0) {
            return -1
        }
        val move = bit - 1
        return container and (1 shl move) ushr move
    }

    /**
     * @return 设置状态之后的container
     */
    fun setStatusLong(container: Long, bit: Int, status: Boolean): Long {
        if (bit > 64 || bit <= 0) {
            return container
        }
        val move = bit - 1
        return if (status) {
            container or (1L shl move)
        } else {
            (container xor (1L shl move)) and container
        }
    }

    /**
     * @return -1 bit超出long上限
     */
    fun getStatusLong(container: Long, bit: Int): Int {
        if (bit > 64 || bit <= 0) {
            return -1
        }
        val move = bit - 1
        return (container and (1L shl move) ushr move).toInt()
    }

    /**
     * 用int array 来存储多个索引的状态
     * @param index 索引的实际编号
     * @return 存储于 int array的编号
     */
    fun getStoragePageInt(index: Int): Int {
        return index / 32
    }

    /**
     * 用int array 来存储多个索引的状态
     * @param index 索引的实际编号
     * @return 找到int array所属的数字后 返回这个数字所在的二进制位数
     */
    fun getStorageWithBitInt(index: Int): Int {
        return index - getStoragePageInt(index) * 32
    }

    /**
     * 用int array 来存储多个索引的状态
     * @param index 索引的实际编号
     * @return 存储于 long array的编号
     */
    fun getStoragePageLong(index: Int): Int {
        return index / 64
    }

    /**
     * 用long array 来存储多个索引的状态
     * @param index 索引的实际编号
     * @return 找到long array所属的数字后 返回这个数字所在的二进制位数
     */
    fun getStorageWithBitLong(index: Int): Int {
        return index - getStoragePageLong(index) * 64
    }
}