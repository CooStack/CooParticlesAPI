package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.particles.control.ControlType
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ParticleStyleManager {

    /**
     * 服务端拥有的 -> (Server View)客户层是empty
     */
    val serverViewStyles = ConcurrentHashMap<UUID, ParticleGroupStyle>()

    /**
     * 玩家可以看见的 style
     */
    internal val visible = ConcurrentHashMap<UUID, MutableSet<ParticleGroupStyle>>()

    /**
     * 当前客户端可见的 (Client View)服务层是empty
     */
    val clientViewStyles = ConcurrentHashMap<UUID, ParticleGroupStyle>()

    private val registerBuilders =
        HashMap<Class<out ParticleGroupStyle>, ParticleStyleProvider>()

    /**
     * 在ClientModInitializer 注册ParticleStyle 用于服务器同步
     */
    @Environment(EnvType.CLIENT)
    fun register(
        type: Class<out ParticleGroupStyle>,
        provider: ParticleStyleProvider
    ) {
        registerBuilders[type] = provider
    }

    fun getBuilder(type: Class<out ParticleGroupStyle>): ParticleStyleProvider? {
        return registerBuilders[type]
    }

    fun spawnStyle(world: World, pos: Vec3d, style: ParticleGroupStyle) {
        if (world.isClient) {
            // 生成粒子
            style.display(pos, world)
            clientViewStyles[style.uuid] = style
            return
        }
        style.display(pos, world)
        serverViewStyles[style.uuid] = style
        // 发送数据包 -> 包括type
        world.players.filter { style.pos.distanceTo(it.pos) <= style.visibleRange }.forEach {
            addStylePlayerView(it as ServerPlayerEntity, style)
        }
    }


    fun doTickClient() {
        val iterator = clientViewStyles.iterator()
        while (iterator.hasNext()) {
            val style = iterator.next().value
            style.tick()
            if (!style.valid) {
                iterator.remove()
            }
        }
    }

    fun doTickServer() {
        val iterator = serverViewStyles.iterator()
        while (iterator.hasNext()) {
            val style = iterator.next().value
            // 更新可见性
            upgradeVisible(style)
            style.tick()
            if (style.autoToggle) {
                style.world!!.players.forEach {
                    val visibleSet = visible.getOrPut(it.uuid) { HashSet() }
                    // 不可见的粒子没有必要同步
                    if (!visibleSet.contains(style)) {
                        return@forEach
                    }
                    ServerPlayNetworking.send(
                        it as ServerPlayerEntity,
                        buildAutoTogglePacket(style)
                    )
                }
            }
            if (!style.valid) {
                filterVisiblePlayer(style).forEach {
                    val player = style.world!!.getPlayerByUuid(it) ?: return@forEach
                    removeGroupPlayerView(player as ServerPlayerEntity, style)
                    visible[it]?.remove(style)
                }
                iterator.remove()
            }
        }
        val playerVisibleIterator = visible.iterator()
        while (playerVisibleIterator.hasNext()) {
            val playerUUID = playerVisibleIterator.next().key
            // 判断玩家是否在线
            val player = CooParticleAPI.server.playerManager.getPlayer(playerUUID)
            if (player == null) {
                playerVisibleIterator.remove()
            }
        }
    }

    fun filterVisiblePlayer(group: ParticleGroupStyle): Set<UUID> {
        val set = HashSet<UUID>()
        visible.forEach {
            if (group in it.value) {
                set.add(it.key)
            }
        }
        return set
    }

    private fun upgradeVisible(style: ParticleGroupStyle) {
        CooParticleAPI.server.playerManager.playerList.forEach { p ->
            val visibleSet = visible.getOrPut(p.uuid) { HashSet() }
            if (p.world != style.world) {
                // 世界转换
                if (style in visibleSet) {
                    removeGroupPlayerView(p, style)
                    visibleSet!!.remove(style)
                }
                return@forEach
            }
            if (style.pos.distanceTo(p.pos) <= style.visibleRange) {
                // 防止重复添加(发包)
                if (style in visibleSet) {
                    return@forEach
                }
                addStylePlayerView(p, style)
            } else {
                if (style in visibleSet) {
                    removeGroupPlayerView(p, style)
                }
                // 超过范围
                visibleSet.remove(style)
            }
        }
    }

    /**
     * 在该玩家的视角中移除这些粒子
     * @param target
     * @param targetGroup
     */
    private fun removeGroupPlayerView(target: ServerPlayerEntity, targetGroup: ParticleGroupStyle) {
        // 发包给玩家
        val packet = PacketParticleStyleS2C(
            targetGroup.uuid,
            ControlType.REMOVE, mapOf()
        )
        ServerPlayNetworking.send(target, packet)
    }

    /*
    * @param target
    * @param targetStyle
    */
    private fun addStylePlayerView(
        target: ServerPlayerEntity,
        targetStyle: ParticleGroupStyle,
    ) {
        // 修复一个包发送给一个玩家两次
        val visibleSet = visible.getOrPut(target.uuid) { HashSet() }
        visibleSet.add(targetStyle)
        // 发包给玩家
        val packet = buildCreatePacket(targetStyle, targetStyle.pos)
        ServerPlayNetworking.send(target, packet)
    }

    private fun buildAutoTogglePacket(
        style: ParticleGroupStyle,
    ): PacketParticleStyleS2C {
        return PacketParticleStyleS2C(
            style.uuid,
            ControlType.CHANGE,
            mapOf(
                *style.writePacketArgs().map { entry -> entry.key to entry.value }.toTypedArray()
            )
        )
    }

    private fun buildCreatePacket(style: ParticleGroupStyle, pos: Vec3d): PacketParticleStyleS2C =
        PacketParticleStyleS2C(
            style.uuid,
            ControlType.CREATE,
            mapOf(
                "style_type" to ParticleControlerDataBuffers.string(style::class.java.name),
                "pos" to ParticleControlerDataBuffers.vec3d(pos),
                "rotate" to ParticleControlerDataBuffers.double(style.rotate),
                "axis" to ParticleControlerDataBuffers.vec3d(style.axis.toVector()),
                "scale" to ParticleControlerDataBuffers.double(style.scale),
                *style.writePacketArgs().map { entry -> entry.key to entry.value }.toTypedArray()
            )
        )

    @Environment(EnvType.CLIENT)
    fun clearAllVisible() {
        clientViewStyles.onEach {
            it.value.remove()
        }.clear()
    }
}
