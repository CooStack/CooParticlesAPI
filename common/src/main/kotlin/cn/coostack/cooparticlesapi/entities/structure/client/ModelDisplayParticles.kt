package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.entities.structure.ModelTorchEmission
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.client.ClientPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldChangeEvent
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.particles.ParticleTypes
import org.joml.Vector3f
import java.util.WeakHashMap
import kotlin.random.Random

/** 按客户端 tick 为世界中活着的结构补充显示粒子；不修改世界、不运行方块 tick、不向服务器发送粒子。 */
@EventListener(dist = DistType.CLIENT)
internal object ModelDisplayParticles {
    /** 快照对应的局部发射点，不引用世界或快照本身，弱键在模型卸载后自动释放。 */
    private val emissions = WeakHashMap<StructureSnapshot, List<ModelTorchEmission>>()

    /** 由客户端初始化调用一次，预览和手持展示实体不在世界实体列表内，因此不会泄露粒子。 */
    @EventHandler
    fun change(event: ClientWorldChangeEvent) { emissions.clear() }

    /** 两个平台共用同一个客户端 tick 入口。 */
    @EventHandler
    fun onTick(event: ClientPostTickEvent) { tick(Minecraft.getInstance()) }

    /** 限制可见距离和每 tick 的总发射点数，避免大量火把让粒子成本随模型大小无限增长。 */
    private fun tick(client: Minecraft) {
        val world = client.level ?: return
        if (client.isPaused) return
        val camera = client.gameRenderer.mainCamera.position
        val maxDistanceSquared = 32.0 * 32.0
        var budget = 128
        val models = world.entitiesForRendering().filterIsInstance<StructureModelEntity>().filter {
            it.isAlive && !it.isRemoved && it.boundingBoxForCulling.distanceToSqr(camera) <= maxDistanceSquared
        }
        if (models.isEmpty()) return
        val firstModel = Random.nextInt(models.size)
        for (index in models.indices) {
            val model = models[(index + firstModel) % models.size]
            val snapshot = model.snapshot ?: continue
            val sources = emissions.getOrPut(snapshot) {
                snapshot.states.mapNotNull { (pos, state) -> ModelTorchEmission.fromState(state, pos) }
            }
            if (sources.isEmpty()) continue
            val matrices = PoseStack()
            model.settings.applyModelTransform(matrices)
            val transform = matrices.last().pose()
            val origin = model.modelOrigin
            val firstSource = Random.nextInt(sources.size)
            for (sourceIndex in sources.indices) {
                val source = sources[(sourceIndex + firstSource) % sources.size]
                // 显示频率基于 tick 而非渲染帧率，较低频率保持火焰连续又避免烟雾过密。
                if (Random.nextInt(4) != 0) continue
                val local = if (source.jitter) source.position.add(
                    Random.nextDouble(-0.1, 0.1), Random.nextDouble(-0.1, 0.1), Random.nextDouble(-0.1, 0.1)
                ) else source.position
                val point = transform.transformPosition(Vector3f(local.x.toFloat(), local.y.toFloat(), local.z.toFloat()))
                val at = origin.add(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
                if (at.distanceToSqr(camera) > maxDistanceSquared) continue
                world.addParticle(source.particle, at.x, at.y, at.z, 0.0, 0.0, 0.0)
                if (source.smoke) world.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.0, 0.0)
                if (--budget == 0) return
            }
        }
    }
}
