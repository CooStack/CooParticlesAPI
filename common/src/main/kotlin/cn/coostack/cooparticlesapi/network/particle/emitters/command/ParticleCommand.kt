package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle


fun interface ParticleCommand {
    /**
     * 粒子“命令/模块”接口：用于在粒子每个 tick 更新前，对粒子数据进行一次可组合的逻辑处理。
     *
     * ## 设计目的
     * 你可以把它理解成 **Unity 粒子系统的一个 Module**（例如：旋转力、阻尼、吸引力、漩涡、流场等）：
     * - 每个 module 都实现一个 `execute(...)`
     * - 在粒子 tick 之前按顺序调用这些 module
     * - module 之间通过“修改同一个 data”来叠加效果（通常是 `data.velocity`、`data.yaw/pitch/roll` 等）
     *
     * ## 调用时机（强烈建议）
     * 推荐在 `Emitter.singleParticleAction(...)` 中，将命令挂载到 `ParticleControler.addPreTickAction { ... }`：
     * - 这样命令会在 **每个 tick** 都执行一次（而不是只在生成时执行一次）
     * - 且执行时你能拿到 “实时的粒子实例状态”（`particle.loc`, `particle.onTheGround`, `particle.currentAge` 等）
     *
     * ## 参数说明
     * @param data
     * 可变的粒子控制数据（你自己的控制层数据）。
     * - **常见修改点**：`data.velocity`（加速度/阻尼/流场）、`data.faceToCamera`、`data.yaw/pitch/roll` 等
     * - 注意：`ControlableParticleData` 中有一部分字段在粒子生成后只生效一次（例如颜色/纹理等），
     *   因此命令更适合修改“每 tick 生效”的字段（通常是 velocity 与旋转等）。
     *
     * @param particle
     * 当前 tick 正在更新的粒子实例（客户端真实粒子对象）。
     * - **常用读取**：`particle.loc`（位置）、`particle.onTheGround`（是否落地）、`particle.currentAge`（年龄）等
     * - **注意**：一般不建议在命令里直接 teleport 粒子位置；更推荐通过 `data.velocity` 影响运动，
     *   由发射器的 physics/move 逻辑统一处理。
     *
     * ## 组合与顺序
     * 多个命令按添加顺序执行：
     * - 前面的命令可能会影响后面的命令输入（例如先吸引再阻尼 vs 先阻尼再吸引）
     * - 如果你在命令里做了 clamp/limit（例如限速），通常建议放到最后一个命令执行
     *
     * ## 编写建议（实践约定）
     * - **保持轻量**：命令每 tick 对每个粒子都会执行，避免昂贵计算/分配对象（尤其是大量粒子场景）
     * - **不要产生 NaN**：对距离接近 0 的情况要做钳制（例如 minDistance）
     * - **可复用**：命令实例最好可以被多个粒子共享（内部不要缓存“单粒子状态”）
     */
    fun execute(data: ControlableParticleData, particle: ControlableParticle)
}