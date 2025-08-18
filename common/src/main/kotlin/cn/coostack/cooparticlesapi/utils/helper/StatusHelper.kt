package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers

/**
 * 使用此类需要在style的read args中加入
 * helper.readFromServer(args)
 * 并且不能存在 display_status 和 display_time 作为key的参数
 */
abstract class StatusHelper : ParticleHelper {
    enum class Status(val id: Int) {
        ENABLE(1),
        DISABLE(2);

        companion object {
            fun fromID(id: Int): Status {
                return when (id) {
                    1 -> ENABLE
                    2 -> DISABLE
                    else -> ENABLE
                }
            }
        }
    }

    /**
     * 1 为开启
     * 2 为关闭
     */
    var displayStatus = 1
        private set

    /**
     * 当状态设置为2时 保留的时间
     * 为0 立刻删除
     */
    var closedInternal = 0

    var current = 0
        protected set

    fun setStatus(status: Int) {
        val enter = status.coerceIn(1, 2)
        this.displayStatus = enter

        changeStatus(status)
    }

    fun setStatus(status: Status) {
        this.displayStatus = status.id
        changeStatus(status.id)
    }

    fun toArgsPairs(): List<Pair<String, ParticleControlerDataBuffer<Int>>> {
        return listOf(
            "display_status" to ParticleControlerDataBuffers.int(displayStatus),
            "display_time" to ParticleControlerDataBuffers.int(current)
        )
    }

    fun readFromServer(args: Map<String, ParticleControlerDataBuffer<*>>) {
        args["display_status"]?.let {
            setStatus(it.loadedValue as Int)
        }
        args["display_time"]?.let {
            current = it.loadedValue as Int
        }
    }

    /**
     * 调用group/style 的change方法 同步给其他客户端
     */
    abstract fun changeStatus(status: Int)

    abstract fun initHelper()

}