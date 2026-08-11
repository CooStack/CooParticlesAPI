package cn.coostack.cooparticlesapi.renderer.server

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object ServerRenderEntityManager {
    val entities = HashMap<UUID, RenderEntity>()

    val playerViewable = HashMap<UUID, HashSet<RenderEntity>>()


    /**
     * 执行 `ServerRenderEntityManager` 定义的 `spawn` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`spawn(entity = entity)`。
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun spawn(entity: RenderEntity) {
        entities[entity.uuid] = entity
    }

    /**
     * 从 `ServerRenderEntityManager` 当前维护的状态中读取 `getPlayerViewable` 结果，不创建新的渲染资源。
     *
     * 示例：`getPlayerViewable(player = player)`。
     *
     * @param player 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun getPlayerViewable(player: UUID): HashSet<RenderEntity> {
        return playerViewable[player] ?: HashSet()
    }

    /**
     * 初始化或准备 `ServerRenderEntityManager` 的 `initPlayer` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`initPlayer(player = player)`。
     *
     * @param player 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun initPlayer(player: UUID) {
        if (playerViewable.containsKey(player)) {
            return
        }
        playerViewable[player] = HashSet()
    }

    /**
     * 清理 `ServerRenderEntityManager` 的 `clearEmptyData` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clearEmptyData()`。
     */
    fun clearEmptyData() {
        val server = CooParticlesAPI.serverOrNull ?: return
        val iterator = playerViewable.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            val player = server.playerList.getPlayer(entity.key)
            if (entity.value.isEmpty() || player == null) iterator.remove()
        }
    }


    /**
     * 更新 `ServerRenderEntityManager` 的 `tick` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`tick()`。
     */
    fun tick() {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next().value
            updateVisible(entity)
//            toggle(entity)
            entity.tick()
            if (entity.canceled) {
                removeAllView(entity)
                iterator.remove()
                continue
            }
            if (entity.shouldSync()) {
                toggle(entity)
            }
        }
        clearEmptyData()
    }


    /**
     * 更新 `ServerRenderEntityManager` 的 `updateVisible` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`updateVisible(entity = entity)`。
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun updateVisible(entity: RenderEntity) {
        val server = CooParticlesAPI.serverOrNull ?: return
        server.playerList.players.forEach {
            // 世界转换
            val actualCanView = playerCanView(it.uuid, entity)
            if (it.level().dimension() != entity.world?.dimension()) {
                if (actualCanView) {
                    removeVisible(it, entity)
                }
                return@forEach
            }
            if (it.isDeadOrDying && actualCanView) {
                removeVisible(it, entity)
                return@forEach
            }
            val pos = it.position()
            val entityPos = entity.pos
            val dis = pos.distanceTo(entityPos)
            val checkCurrentCanView = dis <= entity.renderRange
            if (!checkCurrentCanView && actualCanView) {
                removeVisible(it, entity)
                return@forEach
            }
            if (!actualCanView && checkCurrentCanView) {
                addVisible(it, entity)
            }
        }
    }


    /**
     * 执行 `ServerRenderEntityManager` 定义的 `playerCanView` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`playerCanView(player = player, entity = entity)`。
     *
     * @param player 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun playerCanView(player: UUID, entity: RenderEntity): Boolean {
        initPlayer(player)
        val views = getPlayerViewable(player)
        return views.contains(entity)
    }

    /**
     * 执行 `ServerRenderEntityManager` 定义的 `toggle` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`toggle(entity = entity)`。
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun toggle(entity: RenderEntity) {
        val packet = entity.getTogglePacket(entity.alwaysToggle) ?: return
        val server = CooParticlesAPI.serverOrNull ?: return
        val targets = server.playerList.players.filter { playerCanView(it.uuid, entity) }
        if (targets.isEmpty()) {
            entity.onSynced()
            return
        }
        targets.forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
        entity.onSynced()
    }

    /**
     * 把输入对象加入 `ServerRenderEntityManager` 的 `addVisible` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`addVisible(who = who, entity = entity)`。
     *
     * @param who 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun addVisible(who: ServerPlayer, entity: RenderEntity) {
        initPlayer(who.uuid)
        getPlayerViewable(who.uuid).add(entity)
        // 发包让玩家可见
        val packet = entity.getPacket(PacketRenderEntityS2C.Method.CREATE) ?: return
        CooParticlesServices.SERVER_NETWORK.send(packet, who)
    }

    /**
     * 从 `ServerRenderEntityManager` 的 `removeVisible` 管理范围移除目标，后续调用不再使用对应绑定或资源。
     *
     * 示例：`removeVisible(who = who, entity = entity)`。
     *
     * @param who 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun removeVisible(who: ServerPlayer, entity: RenderEntity) {
        initPlayer(who.uuid)
        getPlayerViewable(who.uuid).remove(entity)
        val packet = entity.getPacket(PacketRenderEntityS2C.Method.REMOVE) ?: return
        CooParticlesServices.SERVER_NETWORK.send(packet, who)
    }

    /**
     * 把输入对象加入 `ServerRenderEntityManager` 的 `addViewIfVisible` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`addViewIfVisible(entity = entity)`。
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun addViewIfVisible(entity: RenderEntity) {
        val world = entity.world ?: return
        world.players().forEach {
            addVisible(it as ServerPlayer, entity)
        }
    }

    /**
     * 从 `ServerRenderEntityManager` 的 `removeAllView` 管理范围移除目标，后续调用不再使用对应绑定或资源。
     *
     * 示例：`removeAllView(entity = entity)`。
     *
     * @param entity 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun removeAllView(entity: RenderEntity) {
        val world = entity.world ?: return
        playerViewable.entries.forEach {
            val player = world.getPlayerByUUID(it.key) as? ServerPlayer ?: return@forEach
            if (it.value.contains(entity)) {
                removeVisible(player, entity)
            }
        }
    }

    /**
     * 清理 `ServerRenderEntityManager` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear()`。
     */
    fun clear() {
        entities.clear()
        playerViewable.clear()
    }

}
