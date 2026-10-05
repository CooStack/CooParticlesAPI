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

    val nodes = ArrayList<Node>()

    init {
        fun append(parent: Int, source: ControlableParticleData, reference: Vec3) {
            if (!command.acceptsGeneration(source.respawnCount)) return
            val requests = command.createParticles(ParticleDeathContext(
                source.clone().apply { respawnCount = source.respawnCount }, reference, source.velocity,
                source.age, RemoveReason.LIFECYCLE, emitterPosition, source.respawnCount, preparingGpu = true,
            ))
            require(requests.size <= command.maxPreparedParticles - nodes.size) {
                "GPU respawn tree exceeds maxPreparedParticles=${command.maxPreparedParticles}"
            }
            for (request in requests) {
                val birthReference = if (request.relativeToDeath) reference + request.position else request.position
                nodes.add(Node(parent, request, birthReference))
            }
        }
        append(-1, data, position)
        var index = 0
        while (index < nodes.size) {
            val node = nodes[index]
            if (node.request.data is ControlableCParticleData) append(index, node.request.data, node.referencePosition)
            index++
        }
    }
}
