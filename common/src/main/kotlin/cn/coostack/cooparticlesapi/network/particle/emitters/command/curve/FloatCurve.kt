package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

/**
 * 生命周期曲线接口（标量）。
 *
 * 约定：
 * - 输入 `t` 是生命周期进度，通常期望在 `[0,1]` 范围内
 * - 实现类应尽量在内部做 `t` 的钳制，避免调用方传入异常值时不稳定
 *
 * 用途：
 * - 给粒子命令（如 Force/Velocity/Inherit）提供“随时间变化”的系数
 * - 统一不同曲线实现（常量、线性、关键帧）的采样入口
 */
fun interface FloatCurve {
    /**
     * 采样曲线值。
     *
     * @param t 生命周期进度（通常是 `currentAge / lifetime`）
     * @return 曲线在该进度下的值
     */
    fun sample(t: Double): Double
}
