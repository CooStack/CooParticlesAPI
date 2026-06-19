package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

import java.util.HashSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * TODO 重构Style使得让自动更新默认打开(如果更新时发现不存在那就重新生成)
 *
 * @constructor Create empty Particle style manager
 */
@Deprecated("使用ParticleComposition")
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
    @JvmStatic
    fun register(
        type: Class<out ParticleGroupStyle>,
        provider: ParticleStyleProvider
    ) {
        registerBuilders[type] = provider
    }

    fun getBuilder(type: Class<out ParticleGroupStyle>): ParticleStyleProvider? {
        return registerBuilders[type]
    }

    @JvmStatic
    fun spawnStyle(world: Level, pos: Vec3, style: ParticleGroupStyle) {
        if (world.isClientSide) {
            // 生成粒子
            style.display(pos, world)
            clientViewStyles[style.uuid] = style
            return
        }
        style.display(pos, world)
        serverViewStyles[style.uuid] = style
        // 发送数据包 -> 包括type
        world.players().filter { style.pos.distanceTo(it.position()) <= style.visibleRange }.forEach {
            addStylePlayerView(it as ServerPlayer, style)
        }
    }


    fun doTickClient() {
        val player = Minecraft.getInstance().player ?: return
        if (player.isDeadOrDying) {
            clientViewStyles.clear()
            return
        }
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
                style.world!!.players().forEach {
                    val visibleSet = visible.getOrPut(it.uuid) { HashSet() }
                    // 不可见的粒子没有必要同步
                    if (!visibleSet.contains(style)) {
                        return@forEach
                    }
                    CooParticlesServices.SERVER_NETWORK.send(
                        buildAutoTogglePacket(style),
                        it as ServerPlayer,
                    )
                }
            }
            if (!style.valid) {
                filterVisiblePlayer(style).forEach {
                    val player = style.world!!.getPlayerByUUID(it) ?: return@forEach
                    removeGroupPlayerView(player as ServerPlayer, style)
                    visible[it]?.remove(style)
                }
                iterator.remove()
            }
        }
        val playerVisibleIterator = visible.iterator()
        while (playerVisibleIterator.hasNext()) {
            val playerUUID = playerVisibleIterator.next().key
            // 判断玩家是否在线
            val server = CooParticlesAPI.serverOrNull ?: return
            val player = server.playerList.getPlayer(playerUUID)
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
        val server = CooParticlesAPI.serverOrNull ?: return
        server.playerList.players.forEach { p ->
            val visibleSet = visible.getOrPut(p.uuid) { HashSet() }
            if (p.level().dimension() != style.world?.dimension()) {
                // 世界转换
                if (style in visibleSet) {
                    removeGroupPlayerView(p, style)
                    visibleSet!!.remove(style)
                }
                return@forEach
            }
            if (p.isDeadOrDying) {
                if (style in visibleSet) {
                    removeGroupPlayerView(p, style)
                    visibleSet!!.remove(style)
                }
                return@forEach
            }
            if (style.pos.distanceTo(p.position()) <= style.visibleRange) {
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
    private fun removeGroupPlayerView(target: ServerPlayer, targetGroup: ParticleGroupStyle) {
        // 发包给玩家
        val packet = PacketParticleStyleS2C(
            targetGroup.uuid,
            ControlType.REMOVE, mapOf()
        )
        CooParticlesServices.SERVER_NETWORK.send(packet, target)
    }

    /*
    * @param target
    * @param targetStyle
    */
    private fun addStylePlayerView(
        target: ServerPlayer,
        targetStyle: ParticleGroupStyle,
    ) {
        // 修复一个包发送给一个玩家两次
        val visibleSet = visible.getOrPut(target.uuid) { HashSet() }
        visibleSet.add(targetStyle)
        // 发包给玩家
        val packet = buildCreatePacket(targetStyle, targetStyle.pos)
        CooParticlesServices.SERVER_NETWORK.send(packet, target)
    }

    private fun buildAutoTogglePacket(
        style: ParticleGroupStyle,
    ): PacketParticleStyleS2C {

        /**
         * SequencedParticleStyle
         * 你赢了
         * addMultiple的index 能从 7 自动同步成 0
         *
         * 一看 server side 诶 index没有问题
         * 一看args 诶 index没有问题
         * 但是呢
         * 传入客户端之后啊
         * 7 变成 0 了捏
         * 不知道的以为是Packet的 CODEC被你妈吃数据包了
         */

        return PacketParticleStyleS2C(
            style.uuid,
            ControlType.CHANGE,
            mapOf(
                *style.writePacketArgs().map { entry -> entry.key to entry.value }.toTypedArray()
            )
        )
    }

    private fun buildCreatePacket(style: ParticleGroupStyle, pos: Vec3): PacketParticleStyleS2C =
        PacketParticleStyleS2C(
            style.uuid,
            ControlType.CREATE,
            mapOf(
                "style_type" to ParticleControlerDataBuffers.string(style::class.java.name),
                "pos" to ParticleControlerDataBuffers.vec3d(pos),
                "rotate" to ParticleControlerDataBuffers.double(style.rotate),
                "axis" to ParticleControlerDataBuffers.vec3d(style.axis.toVector()),
                "scale" to ParticleControlerDataBuffers.double(style.scale),
                "lastUpdatedGameTime" to ParticleControlerDataBuffers.long(style.lastUpdatedGameTime),
                "displayedTime" to ParticleControlerDataBuffers.long(style.displayedTime),
                *style.writePacketArgs().map { entry -> entry.key to entry.value }.toTypedArray()
            )
        )

    fun clearAllVisible() {
        clientViewStyles.onEach {
            it.value.remove()
        }.clear()
    }

    fun clearServer() {
        serverViewStyles.clear()
        visible.clear()
    }
}
