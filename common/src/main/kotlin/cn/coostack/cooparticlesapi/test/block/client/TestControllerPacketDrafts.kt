package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S

/** 配置历史使用的快照类型别名。 */
internal typealias TestControllerConfigSnapshot = TestControllerPacketDrafts.TestControllerConfigSnapshot

/**
 * 在多个控制器界面之间复制配置草稿。
 *
 * 示例：拾取流程和曲线编辑器都通过 [reopenPacket] 返回控制器界面。
 * 禁止在这里读取服务端方块实体；该对象只处理客户端包快照。
 */
internal object TestControllerPacketDrafts {
    /** 从开屏包创建配置快照，供撤回和重做使用。 */
    fun snapshotFrom(source: PacketOpenTestControllerScreenS2C): TestControllerConfigSnapshot {
        return TestControllerConfigSnapshot(copyPacket(source))
    }

    /** 将当前界面草稿合并进开屏包后创建配置快照。 */
    fun snapshotFrom(
        source: PacketOpenTestControllerScreenS2C,
        update: PacketUpdateTestControllerC2S,
    ): TestControllerConfigSnapshot {
        val merged = reopenPacket(source, update)
        if (update.mode == "index") {
            merged.optionParamValues = ArrayList(source.optionParamValues).also { values ->
                while (values.size <= update.optionParamIndex) values.add("")
                values[update.optionParamIndex] = update.optionParamValues
            }
        } else {
            merged.optionParamValues = ArrayList(source.optionParamValues)
        }
        return TestControllerConfigSnapshot(merged)
    }

    fun copyPacket(source: PacketOpenTestControllerScreenS2C): PacketOpenTestControllerScreenS2C {
        return PacketOpenTestControllerScreenS2C().also {
            it.blockPos = source.blockPos
            it.boxDepth = source.boxDepth
            it.boxHeight = source.boxHeight
            it.boxWidth = source.boxWidth
            it.currentIndex = source.currentIndex
            it.dimension = source.dimension
            it.forwardDynamic = source.forwardDynamic
            it.forwardTrack = source.forwardTrack
            it.forwardX = source.forwardX
            it.forwardY = source.forwardY
            it.forwardZ = source.forwardZ
            it.groupId = source.groupId
            it.mode = source.mode
            it.offsetX = source.offsetX
            it.offsetY = source.offsetY
            it.offsetZ = source.offsetZ
            it.optionCount = source.optionCount
            it.optionIds = ArrayList(source.optionIds)
            it.optionParamSpecs = source.optionParamSpecs.map { bytes -> bytes.copyOf() }
            it.optionParamValues = ArrayList(source.optionParamValues)
            it.pendingReview = source.pendingReview
            it.positionDynamic = source.positionDynamic
            it.positionTrack = source.positionTrack
            it.registeredIds = ArrayList(source.registeredIds)
            it.registeredOptionIds = ArrayList(source.registeredOptionIds)
            it.registeredOptionParamSpecs = source.registeredOptionParamSpecs.map { specs -> specs.map { bytes -> bytes.copyOf() } }
            it.repeatDelayTicks = source.repeatDelayTicks
            it.repeatIndex = source.repeatIndex
            it.running = source.running
            it.selectedIndex = source.selectedIndex
            it.status = source.status
        }
    }

    /** 客户端配置快照的值语义；包对象本身仍保持可变以复用现有界面草稿流程。 */
    class TestControllerConfigSnapshot internal constructor(
        private val state: PacketOpenTestControllerScreenS2C,
    ) {
        fun toPacket(): PacketOpenTestControllerScreenS2C = copyPacket(state)

        private val key: String by lazy {
            buildString {
                append(state.blockPos).append('|').append(state.dimension).append('|')
                append(state.groupId).append('|').append(state.mode).append('|')
                append(state.selectedIndex).append('|').append(state.repeatIndex).append('|')
                append(state.repeatDelayTicks).append('|')
                append(state.offsetX).append(',').append(state.offsetY).append(',').append(state.offsetZ).append('|')
                append(state.forwardX).append(',').append(state.forwardY).append(',').append(state.forwardZ).append('|')
                append(state.forwardDynamic).append('|').append(state.forwardTrack).append('|')
                append(state.positionDynamic).append('|').append(state.positionTrack).append('|')
                append(state.boxWidth).append(',').append(state.boxHeight).append(',').append(state.boxDepth).append('|')
                append(state.optionParamValues.joinToString("\u001E"))
            }
        }

        override fun equals(other: Any?): Boolean {
            return other is TestControllerConfigSnapshot && key == other.key
        }

        override fun hashCode(): Int = key.hashCode()
    }

    /**
     * 从服务端开屏快照创建一个可提交的更新包。
     *
     * 示例：动态窗口打开时传入 `draftFrom(packet)`。
     * 禁止把返回包直接当作服务端已保存状态。
     *
     * @param source 开屏快照
     * @return 客户端配置草稿
     */
    fun draftFrom(source: PacketOpenTestControllerScreenS2C): PacketUpdateTestControllerC2S {
        return PacketUpdateTestControllerC2S().also {
            it.blockPos = source.blockPos
            it.boxDepth = source.boxDepth
            it.boxHeight = source.boxHeight
            it.boxWidth = source.boxWidth
            it.dimension = source.dimension
            it.forwardDynamic = source.forwardDynamic
            it.forwardTrack = source.forwardTrack
            it.forwardX = source.forwardX
            it.forwardY = source.forwardY
            it.forwardZ = source.forwardZ
            it.groupId = source.groupId
            it.mode = source.mode
            it.offsetX = source.offsetX
            it.offsetY = source.offsetY
            it.offsetZ = source.offsetZ
            it.optionParamIndex = source.selectedIndex.coerceAtLeast(0)
            it.optionParamValues = source.optionParamValues
                .getOrNull(it.optionParamIndex)
                .orEmpty()
            it.positionDynamic = source.positionDynamic
            it.positionTrack = source.positionTrack
            it.repeatDelayTicks = source.repeatDelayTicks
            it.repeatIndex = source.repeatIndex
            it.selectedIndex = source.selectedIndex
        }
    }

    /** 用恢复的快照覆盖现有草稿对象，保持曲线返回回调引用仍然有效。 */
    fun copyDraft(source: PacketUpdateTestControllerC2S, target: PacketUpdateTestControllerC2S) {
        target.blockPos = source.blockPos
        target.boxDepth = source.boxDepth
        target.boxHeight = source.boxHeight
        target.boxWidth = source.boxWidth
        target.dimension = source.dimension
        target.forwardDynamic = source.forwardDynamic
        target.forwardTrack = source.forwardTrack
        target.forwardX = source.forwardX
        target.forwardY = source.forwardY
        target.forwardZ = source.forwardZ
        target.groupId = source.groupId
        target.mode = source.mode
        target.offsetX = source.offsetX
        target.offsetY = source.offsetY
        target.offsetZ = source.offsetZ
        target.optionParamIndex = source.optionParamIndex
        target.optionParamValues = source.optionParamValues
        target.positionDynamic = source.positionDynamic
        target.positionTrack = source.positionTrack
        target.repeatDelayTicks = source.repeatDelayTicks
        target.repeatIndex = source.repeatIndex
        target.reopen = source.reopen
        target.selectedIndex = source.selectedIndex
    }

    /**
     * 用更新包的草稿值重新构造开屏快照，保留服务端提供的候选项和状态。
     *
     * 示例：曲线编辑返回主界面时调用该方法显示本地未提交修改。
     * 禁止把它当作服务端确认；点击保存或开始时仍需发送更新包。
     *
     * @param source 原始开屏快照
     * @param update 客户端草稿
     * @return 合并后的开屏快照
     */
    fun reopenPacket(
        source: PacketOpenTestControllerScreenS2C,
        update: PacketUpdateTestControllerC2S,
    ): PacketOpenTestControllerScreenS2C {
        return PacketOpenTestControllerScreenS2C().also {
            it.blockPos = source.blockPos
            it.boxDepth = update.boxDepth
            it.boxHeight = update.boxHeight
            it.boxWidth = update.boxWidth
            it.currentIndex = source.currentIndex
            it.dimension = source.dimension
            it.forwardDynamic = update.forwardDynamic
            it.forwardTrack = update.forwardTrack
            it.forwardX = update.forwardX
            it.forwardY = update.forwardY
            it.forwardZ = update.forwardZ
            it.groupId = update.groupId
            it.mode = update.mode
            it.offsetX = update.offsetX
            it.offsetY = update.offsetY
            it.offsetZ = update.offsetZ
            it.optionCount = source.optionCount
            it.optionIds = ArrayList(source.optionIds)
            it.optionParamSpecs = ArrayList(source.optionParamSpecs)
            it.optionParamValues = ArrayList(source.optionParamValues).also { values ->
                while (values.size <= update.optionParamIndex) {
                    values.add("")
                }
                values[update.optionParamIndex] = update.optionParamValues
            }
            it.pendingReview = source.pendingReview
            it.positionDynamic = update.positionDynamic
            it.positionTrack = update.positionTrack
            it.registeredIds = ArrayList(source.registeredIds)
            it.registeredOptionIds = ArrayList(source.registeredOptionIds)
            it.registeredOptionParamSpecs = ArrayList(source.registeredOptionParamSpecs)
            it.repeatDelayTicks = update.repeatDelayTicks
            it.repeatIndex = update.repeatIndex
            it.running = source.running
            it.selectedIndex = update.selectedIndex
            it.status = source.status
        }
    }
}
