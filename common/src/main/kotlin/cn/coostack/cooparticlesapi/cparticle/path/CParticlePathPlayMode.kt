package cn.coostack.cooparticlesapi.cparticle.path

/**
 * # 路径播放模式
 *
 * 播放模式描述沿路径参数推进的方式，与**几何闭合**是两个互不相关的属性：
 * 闭合决定曲线的首尾是否相连，播放模式决定参数如何回绕。
 *
 * 所有模式都受粒子自身寿命约束：播放周期不会修改、延长或重置 `maxAge`。
 *
 * [wireValue] 属于 GPU/CPU 共用的 Command ABI，不能按枚举顺序重新编号。
 */
enum class CParticlePathPlayMode(
    /** 写入 Command 参数的数值标识。 */
    val wireValue: Int,
) {
    /**
     * 单程：参数从 0 推进到 1 后停在终点。
     *
     * 此时终点行为取决于 [CParticlePathEndMode]。
     */
    ONCE(0),

    /**
     * 循环：参数到达 1 后跳回 0 继续推进。
     *
     * 开放路径会跳回起点；闭合路径因为首尾几何相连，跳回处的位置本身是连续的。
     * 跳回发生在一次模拟内时，`previous` 位置会被重置为跳转后的当前位置，
     * 避免渲染插值画出从终点穿越回起点的错误轨迹。
     */
    LOOP(1),

    /**
     * PingPong：参数在 0 与 1 之间往返。
     *
     * 周期约定为**一个往返**，即 `周期 = 2 × playPeriodTicks`；到达端点时立即反向，
     * 不做平滑转向或减速。
     */
    PING_PONG(2),
}
