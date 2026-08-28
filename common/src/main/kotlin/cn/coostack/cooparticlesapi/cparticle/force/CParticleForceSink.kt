package cn.coostack.cooparticlesapi.cparticle.force

/**
 * 流式收集 Force Command，避免 emitter 为每次提交创建临时 List。
 *
 * Sink 在 emitter 同步开始前由桥接层清空。容量耗尽后不会覆盖已经提交的 Command，后续
 * [submit] 返回 `false`，并把 [dropped] 加一，调用方可据此发现力被截断。
 *
 * @property capacity 本次同步最多保存的 Command 数量；必须大于或等于零
 */
class CParticleForceSink(
    val capacity: Int = 128,
) {
    init {
        require(capacity >= 0) { "capacity must be non-negative: $capacity" }
    }

    private val commands = ArrayList<ForceCommand>(capacity.coerceAtLeast(0))

    private var submissionDropped = 0
    private var externalOverflow = 0

    /** 因 Sink 容量或 system 合并上限而被丢弃的 Command 数量；[clear] 后归零。 */
    val dropped: Int get() = submissionDropped + externalOverflow

    /** 当前已经接受的 Command 数量。 */
    val size: Int get() = commands.size

    /** 当前还能接受的 Command 数量；Sink 已满时为零。 */
    val remainingCapacity: Int get() = (capacity - commands.size).coerceAtLeast(0)

    /** 当前是否已经达到 [capacity]。 */
    val isFull: Boolean get() = commands.size >= capacity

    /** 清除已提交的 Command，并重置 [dropped]。 */
    fun clear() {
        commands.clear()
        submissionDropped = 0
        externalOverflow = 0
    }

    /**
     * 提交一个力及其粒子选择器。
     *
     * [selector] 只决定哪些粒子执行该力，不改变力本身的参数或执行顺序。
     *
     * @param force 要加入本 tick 模拟的力
     * @param selector 要接收该力的粒子范围，默认选择全部粒子
     * @return 接受提交时为 `true`；容量耗尽时为 `false`
     */
    fun submit(
        force: CParticleForce,
        selector: CParticleSelector = CParticleSelector.All,
    ): Boolean {
        if (commands.size >= capacity) {
            submissionDropped++
            return false
        }
        commands.add(ForceCommand(force, selector))
        return true
    }

    /**
     * 提交已经组合好的 Command。
     *
     * @param command 包含力和选择器的 Command
     * @return 接受提交时为 `true`；容量耗尽时为 `false`
     */
    fun submit(command: ForceCommand): Boolean = submit(command.force, command.selector)

    /**
     * 按迭代顺序提交一组力，并让它们共用同一个选择器。
     *
     * 容量用尽后仍会继续统计剩余元素，每个未接受的力都会计入 [dropped]。
     *
     * @param forces 按执行顺序提供的力
     * @param selector 这组力共同使用的粒子选择器
     */
    fun submitAll(
        forces: Iterable<CParticleForce>,
        selector: CParticleSelector = CParticleSelector.All,
    ) {
        for (force in forces) submit(force, selector)
    }

    /**
     * 按迭代顺序提交一组完整 Command。
     *
     * @param commands 每项各自携带力和选择器的 Command
     */
    fun submitAll(commands: Iterable<ForceCommand>) {
        for (command in commands) submit(command)
    }

    internal fun drainTo(destination: MutableList<ForceCommand>) {
        destination.addAll(commands)
    }

    internal fun forEach(action: (ForceCommand) -> Unit) {
        commands.forEach(action)
    }

    internal fun commands(): List<ForceCommand> = commands

    /** 用另一份快照中的 Command 引用替换当前内容，不重新创建 Force 或 Command。 */
    internal fun replaceWith(source: CParticleForceSink) {
        commands.clear()
        val copiedCount = source.commands.size.coerceAtMost(capacity)
        commands.addAll(source.commands.subList(0, copiedCount))
        submissionDropped = source.submissionDropped + source.commands.size - copiedCount
        externalOverflow = source.externalOverflow
    }

    /** 记录 system 旧 `forces` 与 Sink Command 合并后超出 GPU 容量的数量。 */
    internal fun setExternalOverflow(count: Int) {
        externalOverflow = count.coerceAtLeast(0)
    }
}
