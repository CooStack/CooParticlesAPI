package cn.coostack.cooparticlesapi.network.particle.emitters.command

enum class OrbitMode {
    /**
     * 物理轨道（默认）
     *
     * - 切向 + 径向纠正
     * - 手感最真实
     * - 需要限幅，否则启动阶段可能不稳定
     */
    PHYSICAL,

    /**
     * 弹簧轨道
     *
     * - 半径误差像弹簧一样回弹
     * - 非常稳定，不容易飞走
     * - 手感偏“设计感”
     */
    SPRING,

    /**
     * 吸附轨道
     *
     * - 每 tick 直接把粒子拉到目标半径附近
     * - 最稳定，最不物理
     * - 适合魔法阵 / UI / 装饰粒子
     */
    SNAP
}
