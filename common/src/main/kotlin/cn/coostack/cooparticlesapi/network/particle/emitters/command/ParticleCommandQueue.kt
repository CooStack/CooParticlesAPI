package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import java.util.function.Predicate

/**
 * `ParticleCommand` 的顺序队列：用于把多个“粒子模块/预设”组合成一个可复用的执行链。
 *
 * ## 设计目的
 * 让你用非常接近 Unity 粒子系统 “模块叠加”的方式组织效果：
 * - 每个 module 都是一个 `ParticleCommand`
 * - 通过队列把多个 module 组装起来
 * - 每 tick 调一次 `applyVelocity(...)`，队列会按顺序把所有 module 应用到同一个粒子上
 *
 * ## 典型使用方式
 * 在 `Emitter.singleParticleAction(...)` 里把队列挂到粒子 controller 的 preTick：
 * ```kotlin
 * val queue = ParticleCommandQueue()
 *     .add(AirDragCommand(0.15))
 *     .add(AttractionCommand(target = center, strength = 0.8))
 *
 * override fun singleParticleAction(
 *     controler: ParticleControler,
 *     data: ControlableParticleData,
 *     spawnPos: RelativeLocation,
 *     spawnWorld: Level,
 *     particleLerpProgress: Float,
 *     posLerpProgress: Float
 * ) {
 *     controler.addPreTickAction {
 *         queue.applyVelocity(data, this)
 *     }
 * }
 * ```
 */
class ParticleCommandQueue {
    val commands =
        ArrayDeque<Pair<ParticleCommand, ParticleCommand.(ControlableParticleData, ControlableParticle) -> Boolean>>()

    /**
     * 对当前粒子执行队列中的所有命令。
     *
     * @param data 当前粒子的可变控制数据（被命令叠加修改）
     * @param particle 当前 tick 正在更新的粒子实例（用于读取实时状态）
     */
    fun applyVelocity(data: ControlableParticleData, particle: ControlableParticle) {
        commands.forEach {
            if (it.second(it.first, data, particle)) {
                it.first.execute(data, particle)
            }
        }
    }

    /**
     * 更新队列中指定索引位置的 Command 数据。
     *
     * @param T Command 的具体类型
     * @param applier 对该 Command 执行的更新逻辑（直接修改其内部参数）
     * @param index Command 在队列中的索引位置
     */
    fun <T : ParticleCommand> updateWith(index: Int, applier: T.() -> Unit): ParticleCommandQueue =
        apply {
            if (index !in commands.indices) {
                return@apply
            }
            val command = commands[index].first
            runCatching { applier(command as T) }
        }

    /**
     * 更新队列中所有符合指定类型的 Command。
     *
     * @param T 需要被更新的 Command 类型
     * @param applier 对所有匹配 Command 执行的更新逻辑
     */
    inline fun <reified T : ParticleCommand> updateWithTypes(applier: T.() -> Unit): ParticleCommandQueue = apply {
        commands.map { it.first }.filterIsInstance<T>()
            .forEach {
                applier(it)
            }
    }

    /**
     * 向队列末尾追加一个命令，并返回自身以支持链式调用。
     *
     * @param command 要追加的粒子命令
     * @return this（便于 `.add(...).add(...)` 链式写法）
     */
    fun add(command: ParticleCommand): ParticleCommandQueue {
        commands.add(command to { data, particle -> true })
        return this
    }

    /**
     * 向队列末尾追加一个命令，并返回自身以支持链式调用。
     *
     * @param command 要追加的粒子命令
     * @param predicate 执行这个命令的条件
     * @return this（便于 `.add(...).add(...)` 链式写法）
     */
    fun add(
        command: ParticleCommand,
        predicate: ParticleCommand.(ControlableParticleData, ControlableParticle) -> Boolean
    ): ParticleCommandQueue {
        commands.add(command to predicate)
        return this
    }
}