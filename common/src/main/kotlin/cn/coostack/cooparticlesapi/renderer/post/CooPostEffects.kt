package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectCollector
import cn.coostack.cooparticlesapi.network.packet.server.PacketRendererPostEffectS2C
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import java.util.concurrent.atomic.AtomicLong

object CooPostEffects {
    private val sequence = AtomicLong()

    val client: ClientRuntime = ClientRuntime
    val server: ServerRuntime = ServerRuntime
    val builtin: BuiltinPostEffects = BuiltinPostEffects

    fun nextInstanceId(): String = "post-${sequence.incrementAndGet()}"

    object ClientRuntime {
        private val instances = LinkedHashMap<String, PostEffectInstance>()

        fun add(instance: PostEffectInstance): PostEffectInstance {
            instances[instance.instanceId] = instance
            return instance
        }

        fun update(instance: PostEffectInstance): PostEffectInstance {
            instances[instance.instanceId] = instance
            return instance
        }

        fun remove(instanceId: String) {
            instances.remove(instanceId)
        }

        fun clear() {
            instances.clear()
        }

        fun activeInstances(): List<PostEffectInstance> = instances.values.toList()

        fun tick() {
            val iterator = instances.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val next = entry.value.tick()
                if (next.expired) {
                    iterator.remove()
                } else {
                    entry.setValue(next)
                }
            }
        }

        fun collectFramePost(context: RenderFrameContext, collector: RenderEffectCollector) {
            instances.values.forEach { instance ->
                if (!context.backend.capabilities.containsAll(instance.type.requiredCapabilities)) {
                    CooParticlesConstants.logger.debug(
                        "Skipping post effect type={} id={} because backend lacks required capabilities {}",
                        instance.type.id,
                        instance.instanceId,
                        instance.type.requiredCapabilities - context.backend.capabilities
                    )
                    return@forEach
                }
                collector.submit(instance.type.toDescriptor(instance))
            }
        }
    }

    object ServerRuntime {
        fun spawn(level: ServerLevel, instance: PostEffectInstance): PostEffectInstance {
            val chunk = SectionPos.blockToSectionCoord(resolveBlockX(instance.binding))
            val chunkZ = SectionPos.blockToSectionCoord(resolveBlockZ(instance.binding))
            CooParticlesServices.SERVER_NETWORK.sendToPlayersTrackingChunk(
                level,
                ChunkPos(chunk, chunkZ),
                PacketRendererPostEffectS2C.create(instance.toNetworkState())
            )
            return instance
        }

        fun send(player: ServerPlayer, instance: PostEffectInstance): PostEffectInstance {
            CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.create(instance.toNetworkState()), player)
            return instance
        }

        fun update(player: ServerPlayer, instance: PostEffectInstance): PostEffectInstance {
            CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.update(instance.toNetworkState()), player)
            return instance
        }

        fun remove(player: ServerPlayer, instanceId: String) {
            CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.remove(instanceId), player)
        }

        fun remove(level: ServerLevel, binding: PostEffectBinding, instanceId: String) {
            val chunk = SectionPos.blockToSectionCoord(resolveBlockX(binding))
            val chunkZ = SectionPos.blockToSectionCoord(resolveBlockZ(binding))
            CooParticlesServices.SERVER_NETWORK.sendToPlayersTrackingChunk(
                level,
                ChunkPos(chunk, chunkZ),
                PacketRendererPostEffectS2C.remove(instanceId)
            )
        }

        private fun resolveBlockX(binding: PostEffectBinding): Int {
            return when (binding) {
                is PostEffectBinding.Block -> binding.pos.x
                is PostEffectBinding.WorldPos -> binding.x.toInt()
                else -> 0
            }
        }

        private fun resolveBlockZ(binding: PostEffectBinding): Int {
            return when (binding) {
                is PostEffectBinding.Block -> binding.pos.z
                is PostEffectBinding.WorldPos -> binding.z.toInt()
                else -> 0
            }
        }
    }
}

object BuiltinPostEffects {
    fun grayscale(): PostEffectInstance = BuiltinPostEffectTypes.GRAYSCALE.create().bindScreen()

    fun screenDistortion(): PostEffectInstance = BuiltinPostEffectTypes.SCREEN_DISTORTION.create().bindScreen()

    fun shockwave(): PostEffectInstance = BuiltinPostEffectTypes.SHOCKWAVE.create().bindScreen()

    fun bloom(): PostEffectInstance = BuiltinPostEffectTypes.BLOOM.create().bindScreen()

    fun halo(): PostEffectInstance = BuiltinPostEffectTypes.HALO.create().bindScreen()

    fun maskDebug(): PostEffectInstance = BuiltinPostEffectTypes.MASK_DEBUG.create().bindScreen()
}
