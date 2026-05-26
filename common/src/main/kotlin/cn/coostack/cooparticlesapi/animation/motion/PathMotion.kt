package cn.coostack.cooparticlesapi.animation.motion

import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import net.minecraft.world.phys.Vec3

/**
 * # 运动控制器
 * ## 作用:
 *  - 把常见路径运动封装成可复用对象
 *  - 通过tick直接控制[ServerControler]的位置
 *  - 支持函数式路径、节点路线、周期路径等不同实现
 * ## 解决的问题
 *  - 避免在每个控制器里反复写相同的运动逻辑
 *  - 调用方只需要构建path， 用apply调整参数， 然后在tick中调用[path.tick]
 *
 * ## example
 * ```kotlin
 * @CooAutoRegister
 * class XXXEmitter(pos:Vec3,world: Level): AutoParticleEmitters(pos,world){
 *     val path = RoutePathMotion<ParticleEmitters>().apply {
 *          bind(this@XXXEmitter)
 *          speed = 0.35
 *          addPoint(pos)
 *          addPoint(pos.add(0.0, 3.0, 0.0))
 *     }
 *
 *     override fun tick(){
 *          path.tick()
 *     }
 * }
 * ```
 */
interface PathMotion<T : ServerControler<*>> {
    /**
     * 被此path控制的目标
     *
     * 一般通过[bind]设置， 保持空构造更干净。
     */
    var controler: T?

    /**
     * 内部管理， 外部一般不用调整
     *
     * 当前path已经执行过的tick数
     */
    var tickCount: Int

    /**
     * path是否已经执行结束
     */
    var finished: Boolean

    /**
     * 绑定被控制对象
     */
    fun bind(controler: T): PathMotion<T>

    /**
     * 调用方需要在他的调用的tick方法处执行该方法
     */
    fun tick()

    /**
     * 重置path状态
     */
    fun reset()
}

/**
 * [PathMotion]的通用骨架。
 *
 * 子类只需要实现[nextPosition]， 返回下一tick目标位置。
 */
abstract class AbstractPathMotion<T : ServerControler<*>> : PathMotion<T> {
    override var controler: T? = null
    override var tickCount: Int = 0
    override var finished: Boolean = false

    override fun bind(controler: T): AbstractPathMotion<T> = apply {
        this.controler = controler
    }

    override fun tick() {
        if (finished) {
            return
        }

        val target = controler ?: return
        if (!target.isValid()) {
            finished = true
            return
        }

        beforeTick(target)
        val next = nextPosition(target)
        if (next == null) {
            finished = true
            onFinish(target)
            return
        }

        target.teleportTo(next)
        tickCount++
        afterTick(target, next)
    }

    override fun reset() {
        tickCount = 0
        finished = false
        onReset()
    }

    /**
     * 计算下一tick的位置
     *
     * 返回null时， 当前path会被标记为完成。
     */
    protected abstract fun nextPosition(controler: T): Vec3?

    protected open fun beforeTick(controler: T) {}

    protected open fun afterTick(controler: T, pos: Vec3) {}

    protected open fun onFinish(controler: T) {}

    protected open fun onReset() {}
}
