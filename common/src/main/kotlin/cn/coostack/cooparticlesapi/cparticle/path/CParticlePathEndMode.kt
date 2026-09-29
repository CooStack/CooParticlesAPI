package cn.coostack.cooparticlesapi.cparticle.path

/**
 * # 路径到达终点的处理方式
 *
 * 只有 [CParticlePathPlayMode.ONCE] 会真正“到达终点”；循环与 PingPong 不会结束播放。
 * 本模式**不修改**粒子寿命：不启用 [DISAPPEAR] 时，路径播放结束只是停止继续约束位置，
 * 粒子的 `maxAge` 与年龄账本保持用户设置的原值。
 *
 * [wireValue] 属于 GPU/CPU 共用的 Command ABI，不能按枚举顺序重新编号。
 */
enum class CParticlePathEndMode(
    /** 写入 Command 参数的数值标识。 */
    val wireValue: Int,
) {
    /**
     * 保持约束在终点。
     *
     * 粒子继续按自己的寿命存活，位置被固定在路径终点（含环绕偏移与朝向），
     * 不会因为路径播放结束而被回收或复活。
     */
    HOLD(0),

    /**
     * 到达后消失：显式提前结束粒子。
     *
     * 粒子在到达终点的那个 tick 被标记结束，可以早于 `maxAge`。
     * GPU 路径通过实例 `flags` 的结束位与一个紧凑的结束槽位缓冲通知 CPU 账本，
     * 不做逐粒子状态回读；CPU 路径直接复用现有槽位回收入口。
     */
    DISAPPEAR(1),
}
