package cn.coostack.cooparticlesapi.cparticle.force

/**
 * 粒子力命令的选择器模式。
 *
 * 选择器只读取粒子 metadata，不改变旧 Force 的 16-float ABI。`sourceId` 是 system 或 emitter
 * 创建时分配的运行时来源标识；`sign` 是调用方写入的逻辑标签；`commandMask` 是粒子
 * 允许参与哪些 Command 类别的位掩码。三者用途不同，不能用 `sign` 代替 emitter 身份。
 */
sealed class CParticleSelector {
    /** 写入 Command header 的选择器类型编号，CPU 与 GPU 使用同一组编号。 */
    abstract val mode: Int

    /** 完整比较或掩码比较使用的目标值。 */
    abstract val value: Int

    /** 掩码比较使用的有效位；完整比较模式使用全位掩码。 */
    abstract val mask: Int

    /** 选择当前 system 中的全部粒子。 */
    data object All : CParticleSelector() {
        override val mode: Int = 0
        override val value: Int = 0
        override val mask: Int = 0
    }

    /**
     * 选择 `sourceId` 与 [sourceId] 完全相等的粒子。
     *
     * 适合只影响某个 emitter 或 system 生成的粒子。`sourceId` 是运行时值，不应持久化
     * 或作为跨网络稳定 ID。
     *
     * @property sourceId 目标运行时来源 ID
     */
    data class SourceEquals(val sourceId: Int) : CParticleSelector() {
        override val mode: Int = 1
        override val value: Int get() = sourceId
        override val mask: Int = -1
    }

    /**
     * 选择 `particle.sourceId & sourceMask == sourceId & sourceMask` 的粒子。
     *
     * @property sourceId 掩码比较的目标来源值
     * @property sourceMask 参与比较的来源位
     */
    data class SourceMask(val sourceId: Int, val sourceMask: Int) : CParticleSelector() {
        override val mode: Int = 2
        override val value: Int get() = sourceId
        override val mask: Int get() = sourceMask
    }

    /**
     * 选择 `sign` 与 [sign] 完全相等的粒子。
     *
     * `sign` 由业务代码定义，例如区分火焰、烟雾或碎片，不表示粒子来自哪个 emitter。
     *
     * @property sign 目标逻辑标签
     */
    data class SignEquals(val sign: Int) : CParticleSelector() {
        override val mode: Int = 3
        override val value: Int get() = sign
        override val mask: Int = -1
    }

    /**
     * 选择 `particle.sign & signMask == sign & signMask` 的粒子。
     *
     * @property sign 掩码比较的目标逻辑标签
     * @property signMask 参与比较的标签位
     */
    data class SignMask(val sign: Int, val signMask: Int) : CParticleSelector() {
        override val mode: Int = 4
        override val value: Int get() = sign
        override val mask: Int get() = signMask
    }

    /**
     * 选择 `particle.commandMask & commandMask != 0` 的粒子。
     *
     * 一个粒子只要开启了 [commandMask] 中任意一位就会匹配，适合用位标志声明它允许参与的
     * 力类别。
     *
     * @property commandMask 要检查的 Command 类别位
     */
    data class CommandMask(val commandMask: Int) : CParticleSelector() {
        override val mode: Int = 5
        override val value: Int = 0
        override val mask: Int get() = commandMask
    }

    internal fun matches(sourceId: Int, sign: Int, commandMask: Int): Boolean = when (this) {
        All -> true
        is SourceEquals -> sourceId == value
        is SourceMask -> sourceId and mask == value and mask
        is SignEquals -> sign == value
        is SignMask -> sign and mask == value and mask
        is CommandMask -> commandMask and mask != 0
    }
}

typealias ParticleSelector = CParticleSelector
