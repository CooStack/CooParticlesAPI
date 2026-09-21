package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.post.PostEffectBinding
import cn.coostack.cooparticlesapi.renderer.post.PostEffectLifecycle
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParams
import cn.coostack.cooparticlesapi.renderer.post.SyncedPostEffectState
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

class PacketRendererPostEffectS2C private constructor(
    internal val operation: Operation,
    internal val state: SyncedPostEffectState?,
    internal val instanceId: String
) {
    internal enum class Operation(val id: Int) {
        CREATE(0),
        UPDATE(1),
        REMOVE(2);

        companion object {
            fun idOf(id: Int): Operation {
                return entries.firstOrNull { it.id == id } ?: CREATE
            }
        }
    }

    companion object {
        private val identifierID =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "renderer_post_effect_packet")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "renderer_post_effect_packet")

        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeInt(packet.operation.id)
            buf.writeUtf(packet.instanceId)
            buf.writeBoolean(packet.state != null)
            packet.state?.write(buf)
        }, { buf ->
            val operation = Operation.idOf(buf.readInt())
            val instanceId = buf.readUtf()
            val state = if (buf.readBoolean()) readState(buf) else null
            PacketRendererPostEffectS2C(operation, state, instanceId)
        })

        internal fun create(state: SyncedPostEffectState): PacketRendererPostEffectS2C {
            return PacketRendererPostEffectS2C(Operation.CREATE, state, state.instanceId)
        }

        internal fun update(state: SyncedPostEffectState): PacketRendererPostEffectS2C {
            return PacketRendererPostEffectS2C(Operation.UPDATE, state, state.instanceId)
        }

        internal fun remove(instanceId: String): PacketRendererPostEffectS2C {
            return PacketRendererPostEffectS2C(Operation.REMOVE, null, instanceId)
        }

        private fun SyncedPostEffectState.write(buf: PacketByteBuf) {
            buf.writeResourceLocation(effectType)
            buf.writeUtf(instanceId)
            PostEffectBinding.writeTyped(buf, binding)
            lifecycle.write(buf)
            params.write(buf)
            buf.writeInt(uniformNames.size)
            uniformNames.sorted().forEach(buf::writeUtf)
            buf.writeUtf(sourceId)
            buf.writeInt(priority)
        }

        private fun readState(buf: PacketByteBuf): SyncedPostEffectState {
            return SyncedPostEffectState(
                effectType = buf.readResourceLocation(),
                instanceId = buf.readUtf(),
                binding = PostEffectBinding.readTyped(buf),
                lifecycle = PostEffectLifecycle.read(buf),
                params = PostEffectParams.read(buf),
                uniformNames = buildSet {
                    repeat(buf.readInt()) { add(buf.readUtf()) }
                },
                sourceId = buf.readUtf(),
                priority = buf.readInt()
            )
        }
    }
}
