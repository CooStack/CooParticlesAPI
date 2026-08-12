package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.network.packet.server.PacketDisplayEntityS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.PoseStack
import io.netty.buffer.Unpooled
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DisplayEntityManager {
    val clientView = ConcurrentHashMap<UUID, DisplayEntity>()

    val serverView = ConcurrentHashMap<UUID, DisplayEntity>()

    val registeredTypes = ConcurrentHashMap<String, StreamCodec<in RegistryFriendlyByteBuf, DisplayEntity>>()

    fun addClient(entity: DisplayEntity) {
        entity.prevPos = entity.pos
        entity.prevYaw = entity.yaw
        entity.prevPitch = entity.pitch
        entity.prevRoll = entity.roll
        entity.prevScale = entity.scale
        clientView[entity.controlUUID] = entity
    }

    fun spawn(entity: DisplayEntity) {
        serverView[entity.controlUUID] = entity
        sendCreateOrUpdate(entity)
    }


    fun register(randomInstance: DisplayEntity) {
        val id = randomInstance::class.java.name
        val codec = randomInstance.getCodec()
        registeredTypes[id] = codec
    }

    fun registerScanner() {
        CooParticlesConstants.logger.info("正在自动注册 DisplayEntity")
        CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
            .iterator()
            .forEach {
                val clazz = it.toClass()
                if (!DisplayEntity::class.java.isAssignableFrom(clazz)) {
                    return@forEach
                }
                // 获取instance
                val instance =
                    clazz.declaredConstructors.find {
                        it.parameterCount == 0
                    }?.newInstance() ?: clazz.getDeclaredConstructor(
                        Vec3::class.java,
                        Level::class.java
                    )
                        .newInstance(Vec3.ZERO, null)
                register(instance as DisplayEntity)
            }
    }


    /**
     * 疑似渲染顺序有点问题
     *
     * 右乘 T(-off) * R * T(off) * T(up [自定义的一些偏移 在render方法里面会调用]) * T(-off) * T(w) * v
     *
     *
     * @param view
     * @param proj
     * @param modelMatrixStack
     * @param buffer
     * @param delta
     * @param camera
     */
    fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: DeltaTracker,
        camera: Camera
    ) {
        val lerp = delta.getGameTimeDeltaPartialTick(true)
        val iterator = clientView.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entity = entry.value
            if (!entity.isValid()) {
                iterator.remove()
                continue
            }
            modelMatrixStack.pushPose()
            MinecraftRendererUtil.transformTo(
                camera,
                entity.position(lerp) + entity.transformOffset(),
                modelMatrixStack
            ) {
                val offset = entity.renderCenterOffset()
                if (entity.manageRotation) {
                    MinecraftRendererUtil.applyAtPoint(
                        offset, this
                    ) {
                        MinecraftRendererUtil.applyRotation(
                            this, entity.yaw(lerp), entity.pitch(lerp), entity.roll(lerp)
                        )
                    }
                }
                if (entity.canRender(view, proj, modelMatrixStack, lerp, camera)) {
                    runCatching {
                        entity.render(view, proj, modelMatrixStack, buffer, lerp, camera)
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
            }
            modelMatrixStack.popPose()
        }
    }

    fun tickClient() {
        val iterator = clientView.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.tick()
            if (!entry.value.isValid()) {
                iterator.remove()
            }
        }
    }

    fun tickServer() {
        val iterator = serverView.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.tick()
            if (!entry.value.isValid()) {
                sendRemove(entry.value)
                iterator.remove()
                continue
            }
            sendCreateOrUpdate(entry.value)
        }
    }


    fun sendCreateOrUpdate(entity: DisplayEntity) {
        val server = CooParticlesAPI.serverOrNull ?: return
        val registryAccess = CooParticlesAPI.registryAccessOrNull ?: return
        val uuid = entity.controlUUID
        val type = entity::class.java.name
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess)
        entity.getCodec().encode(buf, entity)
        val data = ByteArray(buf.readableBytes()).apply {
            buf.readBytes(this)
        }
        val packet = PacketDisplayEntityS2C(uuid, type, data)
        server.playerList.players.forEach {
            if (it.level().dimension() != entity.world?.dimension()) {
                return@forEach
            }
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun sendRemove(entity: DisplayEntity) {
        val server = CooParticlesAPI.serverOrNull ?: return
        val packet = PacketDisplayEntityS2C(entity.controlUUID, entity::class.java.name, ByteArray(0), true)
        server.playerList.players.forEach {
            if (it.level().dimension() != entity.world?.dimension()) {
                return@forEach
            }
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun clearClient() {
        clientView.clear()
    }

    fun clearServer() {
        serverView.clear()
    }

}
