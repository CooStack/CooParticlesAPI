package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times

/**
 * 配置普通粒子与 GPU 粒子共用的死亡重生行为，默认只生成一代后继粒子。
 *
 * 在 emitter 初始化时设置 `deathCommand = ParticleDeathCommand { listOf(respawn(template)) }`。
 * 普通粒子在死亡时执行 action，可按真实状态判断并执行普通指令。
 * GPU 粒子在根粒子出生时展开有限后继树，每个可能的父粒子配置只求值一次；死亡时不再运行 Kotlin。
 * GPU 可按出生配置 sign 和 respawnCount 选模板，真实死亡位置与速度请使用 respawnAtDeath。
 * 返回列表表示同一次死亡产生的兄弟粒子，不是按下标推进的阶段表。
 * 指令在客户端执行，服务端配置应通过 emitter 的 CodecField 参数同步，再由客户端构造行为。
 * 不参与逐 tick 的 ParticleCommandQueue；不会伪造 GPU 粒子的控制器。
 * 普通和 GPU 父粒子均可生成任一种子粒子，子粒子的 data 类型决定执行路径。
 *
 * @property maxRespawns 每条血缘链最多重生的次数，默认 1；0 表示禁用，必须非负
 * @property includeManualRemoval 是否响应 CALL，默认响应；QUEUE 始终不响应
 * @property maxPreparedParticles 单个 GPU 根粒子允许预计算的后继总数，超过即拒绝整条链
 * @param action CPU 死亡时或 GPU 出生预计算时返回子粒子配置；检查 context.preparingGpu 可区分
 */
class ParticleDeathCommand(
    val maxRespawns: Int = 1,
    val includeManualRemoval: Boolean = true,
    /** 单个 GPU 根粒子允许预计算的后继总数；超出即拒绝，避免分支指数展开。 */
    val maxPreparedParticles: Int = 4096,
    private val action: ParticleDeathContext.() -> List<ParticleRespawnRequest>,
) {
    /**
     * GPU 预计算时是否保证只读取 `context.data`，不修改父粒子配置。
     *
     * 默认关闭以保留旧行为：预计算会给 action 一个独立父数据副本。只读的 GPU
     * death command 可以在构造后将其设为 `true`，避免每个根粒子为父数据分配 clone；
     * CPU 死亡路径始终继续使用独立快照。
     */
    var gpuContextDataReadOnly: Boolean = false

    init {
        require(maxRespawns >= 0) { "maxRespawns must be non-negative" }
        require(maxPreparedParticles > 0) { "maxPreparedParticles must be positive" }
    }

    /** 当前代数是否仍有后继；终代粒子的配置无需继续求值。 */
    internal fun acceptsGeneration(respawnCount: Int): Boolean = respawnCount in 0 until maxRespawns

    /**
     * 根据死亡快照生成独立请求，并统一推进代数。
     *
     * 示例：`command.createParticles(context)`；通常由 emitter 调用，无需手动执行。
     * 清理或超出代数上限时不会执行用户回调，返回的模板不会被本方法修改。
     *
     * @param context 父粒子的客户端死亡快照
     * @return 配置和 UUID 彼此独立的新粒子请求
     */
    fun createParticles(context: ParticleDeathContext): List<ParticleRespawnRequest> {
        if (!acceptsGeneration(context.respawnCount) || context.reason == RemoveReason.QUEUE ||
            context.reason == RemoveReason.CALL && !includeManualRemoval
        ) return emptyList()
        val requests = action(context)
        if (context.preparingGpu) require(requests.size <= maxPreparedParticles) {
            "GPU respawn requests exceed maxPreparedParticles=$maxPreparedParticles"
        }
        // respawn() 已经为每个请求创建了独立 child。GPU 预计算不需要再为这些
        // owned request 构造 request.copy；直接复用 action 返回的列表可避免每粒子
        // 后继链产生一组短命 Pair/Request 对象。手工构造的 request 仍走旧 clone 保护。
        if (context.preparingGpu && requests.all(ParticleRespawnRequest::ownsData)) {
            requests.forEach { request ->
                request.data.respawnCount = context.respawnCount + 1
            }
            return requests
        }
        return requests.map { request ->
            // respawn() 已经返回独立 clone；GPU 出生预计算直接复用它，避免每颗根粒子
            // 在同一条后继链中重复 clone 一次。直接构造 ParticleRespawnRequest 仍复制，
            // 保留旧 API 对共享模板的保护。
            val child = if (context.preparingGpu && request.ownsData) {
                request.data
            } else {
                request.data.clone()
            }.apply {
                respawnCount = context.respawnCount + 1
                if (!context.preparingGpu && request.inheritVelocity != 0.0) {
                    velocity = velocity + context.velocity * request.inheritVelocity
                }
            }
            if (context.preparingGpu) request.copy(data = child) else request.copy(
                data = child,
                position = if (request.relativeToDeath) context.position + request.position else request.position,
                relativeToDeath = false,
                inheritVelocity = 0.0,
            )
        }
    }
}
