package cn.coostack.cooparticlesapi.network.particle

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.utils.MathDataUtil

/**
 * 由于服务端获取不到客户层的粒子总数
 * 所以这里不做粒子上限
 * 但是记录是用于 其他客户端同步
 *
 * 如果要设定自动同步
 * 在 otherPacketArgs 设置 "toggle" to ParticleControlerDataBuffers.int(serverSequencedParticleCount)
 * 或者 在这个方法中 return mapOf(toggleArgLeastIndex(),toggleArgStatus(),...其他自定义的数据)
 *
 * 你无需在 SequencedServerParticleGroup的Provider中 手动应用toggle输入的count
 * 服务器会自动处理此参数
 *
 * 数据包设定
 *  addCount args type int -> count
 *  removeCount args type int -> count
 *  toggle args type int-> count
 */
abstract class SequencedServerParticleGroup(visibleRange: Double = 32.0) : ServerParticleGroup(visibleRange) {
    /**
     * 记录了每一个粒子的显示状态 (只要maxCount确实等于loadParticleLocationsWithIndex返回的map的size)
     */
    private val clientIndexStatus = LongArray((maxCount() / 64) + 1)

    var serverSequencedParticleCount = 0

    /**
     * 切记一定要和 SequencedParticleGroup.loadParticleLocationsWithIndex().size 相同
     * 如果你的group的粒子数量是可变的(使用了flush方法刷新了粒子样式 其中长度发生变化)
     * 那么请在服务器层做好数据同步 ( size同步 )
     * 如果此处的 maxCount > SequencedParticleGroup.loadParticleLocationsWithIndex().size 则会导致数组越界异常
     * 如果此处的 maxCount < SequencedParticleGroup.loadParticleLocationsWithIndex().size 则会导致粒子控制不完全(部分粒子无法从服务器生成)
     * @return 应当返回 SequencedParticleGroup.loadParticleLocationsWithIndex().size
     */
    abstract fun maxCount(): Int

    fun isDisplayed(index: Int): Boolean {
        val storage = MathDataUtil.getStoragePageLong(index)
        if (storage >= clientIndexStatus.size) {
            return false
        }

        return MathDataUtil.getStorageWithBitLong(
            MathDataUtil.getStorageWithBitLong(index)
        ) == 1
    }

    fun setDisplayed(index: Int, status: Boolean) {
        val storage = MathDataUtil.getStorageWithBitLong(index)
        val page = MathDataUtil.getStoragePageLong(index)
        if (page >= clientIndexStatus.size) {
            return
        }
        clientIndexStatus[page] = MathDataUtil.setStatusLong(clientIndexStatus[page], storage, status)
    }

    fun toggleArgLeastIndex(): Pair<String, ParticleControlerDataBuffer<Int>> {
        return "toggle" to ParticleControlerDataBuffers.int(1)
    }

    fun toggleArgStatus(): Pair<String, ParticleControlerDataBuffer<LongArray>> {
        return "toggle_status" to ParticleControlerDataBuffers.longArray(clientIndexStatus)
    }

    fun addSingle() {
        if (serverSequencedParticleCount >= maxCount()) {
            return
        }
        change(
            {
                setDisplayed(serverSequencedParticleCount++, true)
            },
            mapOf(
                "addCount" to ParticleControlerDataBuffers.int(1)
            )
        )
    }

    fun addMultiple(count: Int) {
        change(
            {
                for (index in serverSequencedParticleCount until serverSequencedParticleCount + count) {
                    setDisplayed(index, true)
                }
                serverSequencedParticleCount += count
            },
            mapOf(
                "addCount" to ParticleControlerDataBuffers.int(count)
            )
        )
        if (serverSequencedParticleCount >= maxCount()) {
            serverSequencedParticleCount = maxCount()
        }
    }

    fun removeSingle(): Boolean {
        if (serverSequencedParticleCount <= 0) {
            return false
        }
        change(
            {
                setDisplayed(serverSequencedParticleCount--, false)
            },
            mapOf(
                "removeCount" to ParticleControlerDataBuffers.int(1)
            )
        )
        return true
    }

    fun removeMultiple(count: Int) {
        if (serverSequencedParticleCount <= 0) {
            return
        }
        change(
            {
                for (index in serverSequencedParticleCount - count + 1..serverSequencedParticleCount) {
                    setDisplayed(index, false)
                }
                serverSequencedParticleCount -= count
                if (serverSequencedParticleCount <= 0) {
                    serverSequencedParticleCount = 0
                }
            },
            mapOf(
                "removeCount" to ParticleControlerDataBuffers.int(count)
            )
        )
    }

    fun changeSingle(index: Int, status: Boolean) {
        if (index >= maxCount() || index < 0) {
            return
        }
        change(
            {
                setDisplayed(index, status)
            }, mapOf(
                "change_single" to ParticleControlerDataBuffers.int(index)
            )
        )
    }

    fun toggleCurrentCount() {
        change(
            {},
            mapOf(
                "toggle" to ParticleControlerDataBuffers.int(serverSequencedParticleCount),
                "toggle_status" to ParticleControlerDataBuffers.longArray(clientIndexStatus)
            )
        )
    }
}