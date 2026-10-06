package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import net.minecraft.world.phys.Vec3

/**
 * 在根粒子出生时展开有限的 GPU 后继树，CPU 子粒子是展开边界。
 * 列表元素表示同一次死亡产生的兄弟粒子，parent 为 -1 表示根粒子。
 * 广度优先展开避免用户设置较大代数时耗尽调用栈；超过预算直接拒绝整条链。
 */
internal class ParticlePreparedRespawns(
    command: ParticleDeathCommand,
    data: ControlableCParticleData,
    position: Vec3,
    emitterPosition: Vec3,
) {
    class Node(val parent: Int, val request: ParticleRespawnRequest, val referencePosition: Vec3)

    val nodes: List<Node>
    private var preparedNodeCount = 0
    /** 预留容量时直接使用，避免每个根粒子再次遍历 nodes 做类型统计。 */
    var gpuNodeCount: Int = 0
        private set

    init {
        fun append(parent: Int, source: ControlableParticleData, reference: Vec3): List<Node> {
            if (!command.acceptsGeneration(source.respawnCount)) return emptyList()
            val contextData = if (command.gpuContextDataReadOnly) {
                source
            } else {
                source.clone().apply { respawnCount = source.respawnCount }
            }
            val requests = command.createParticles(ParticleDeathContext(
                contextData, reference, source.velocity,
                source.age, RemoveReason.LIFECYCLE, emitterPosition, source.respawnCount, preparingGpu = true,
            ))
            require(requests.size <= command.maxPreparedParticles - preparedNodeCount) {
                "GPU respawn tree exceeds maxPreparedParticles=${command.maxPreparedParticles}"
            }
            if (command.maxRespawns == 1 && parent < 0 && requests.size == 1) {
                val request = requests[0]
                val birthReference = if (request.relativeToDeath) reference + request.position else request.position
                if (request.data is ControlableCParticleData) gpuNodeCount++
                preparedNodeCount++
                return listOf(Node(parent, request, birthReference))
            }
            val added = ArrayList<Node>(requests.size)
            for (request in requests) {
                val birthReference = if (request.relativeToDeath) reference + request.position else request.position
                added.add(Node(parent, request, birthReference))
                preparedNodeCount++
                if (request.data is ControlableCParticleData) gpuNodeCount++
            }
            return added
        }
        val first = append(-1, data, position)
        if (command.maxRespawns == 1 && first.size == 1) {
            nodes = first
        } else {
            val expanded = ArrayList<Node>(first.size)
            expanded.addAll(first)
            var index = 0
            while (index < expanded.size) {
                val node = expanded[index]
                if (node.request.data is ControlableCParticleData) {
                    expanded.addAll(append(index, node.request.data, node.referencePosition))
                }
                index++
            }
            nodes = expanded
        }
    }
}
