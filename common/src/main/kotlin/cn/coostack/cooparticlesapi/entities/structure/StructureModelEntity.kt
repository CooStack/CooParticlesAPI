package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.entities.collision.CollisionGeometry
import cn.coostack.cooparticlesapi.entities.collision.EntityTransform
import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionEntity
import cn.coostack.cooparticlesapi.entities.collision.OrientedBox
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import kotlin.math.abs
import kotlin.math.max
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.core.BlockPos

/**
 * 静态结构是 [IrregularCollisionEntity] 的内置实现，支持生命、掉落、变换与原版风格死亡动画。
 * 从 [StructureModels.spawn] 在服务端创建，也支持模型方块、编辑器与物品转移工作流。
 * 实体身体选在真实形状内，放置锚点单独保留，修改支点不会意外移动模型。
 */
class StructureModelEntity(type: EntityType<out StructureModelEntity>, level: Level) :
    Mob(type, level), IrregularCollisionEntity {
    companion object {
        /** 快照与设置在同一条有界实体数据中同步，只在配置变化时更新。 */
        private val MODEL = SynchedEntityData.defineId(StructureModelEntity::class.java, EntityDataSerializers.COMPOUND_TAG)

        /**
         * 提供两个加载器注册所需的静态生物属性。
         * @return 无自主移动且抗击退的属性构建器
         */
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
    }

    /** 尚未收到有效数据时为空的只读快照。 */
    var snapshot: StructureSnapshot? = null
        private set
    /** 当前已验证的配置。 */
    var settings = StructureModelSettings()
        private set
    /** 原模板 ID，仅作追溯信息，加载存档不依赖模板继续存在。 */
    var structureName = ""
        private set
    /** 服务端真实掉落库存，由调用方填入物品，死亡结算后清空。 */
    val drops = object : SimpleContainer(27) {
        override fun stillValid(player: Player): Boolean = canEdit(player)
    }
    /** 无数据或死亡时共用的本实体空几何。 */
    private val emptyGeometry = CollisionGeometry(emptyList())
    /** 缓存的当前世界碰撞。 */
    private var physicalGeometry = emptyGeometry
    /** 缓存的当前世界选取形状。 */
    private var pickGeometry = emptyGeometry
    /** 几何缓存对应的实体位置。 */
    private var geometryOrigin: Vec3? = null
    /** 世界索引包围盒，不用于精确阻挡。 */
    private var geometryBounds: AABB? = null
    /** 完整结构的视觉范围，包含无碰撞部分。 */
    private var visualBounds: AABB? = null
    /** 选中身体形状相对于放置锚点的范围。 */
    private var bodyBounds: AABB? = null
    /** 实体身体位置相对于原始放置锚点的偏移。 */
    private var bodyOffset = Vec3.ZERO
    /** 渲染矩阵从实体坐标移动到模型放置锚点所需的位移。 */
    val modelOriginOffset: Vec3 get() = bodyOffset.reverse()
    /** 世界空间放置锚点；传送随实体移动，重新配置保持此点不变。 */
    val modelOrigin: Vec3 get() = position().add(modelOriginOffset)

    override val collisionOrigin: Vec3 get() = position()
    override val collisionPivot: Vec3 get() = modelOrigin.add(settings.transform.pivotPosition)
    override val collisionRotation: Quaterniond get() = settings.transform.quaternion()
    override val collisionGeometry: CollisionGeometry
        get() { ensureGeometry(); return if (isAlive) physicalGeometry else emptyGeometry }
    override val selectionGeometry: CollisionGeometry
        get() { ensureGeometry(); return if (isAlive) pickGeometry else emptyGeometry }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(MODEL, CompoundTag())
    }

    /**
     * 在服务端游戏线程原子替换快照和设置，保持放置锚点及掉落库存，恢复生命到配置上限。
     * 默认支点为模板底部水平中心；显式设置不会被覆盖。
     * 示例：`model.configure(id.toString(), snapshot, options)`。
     * @param name 原模板的追溯标识，最多 256 字符
     * @param data 已校验且独占数据的快照
     * @param options 显式配置，省略时使用默认底部中心
     * @throws IllegalArgumentException 变换后几何非有限或数据超出预算
     * @throws IllegalStateException 从客户端调用
     */
    fun configure(name: String, data: StructureSnapshot, options: StructureModelSettings =
        StructureModelSettings(EntityTransform(pivot = data.defaultPivot))) {
        check(!level().isClientSide) { "结构配置只能在服务端执行" }
        applyConfiguration(name, data, options)
    }

    /** 客户端仅为未加入世界的 GUI 和物品展示实例设置数据，不能修改已追踪实体。 */
    internal fun configurePreview(name: String, data: StructureSnapshot, options: StructureModelSettings) {
        check(level().isClientSide && level().getEntity(id) !== this)
        applyConfiguration(name, data, options)
    }

    /** 共用数据校验和身体定位，调用入口负责逻辑侧与实体所有权。 */
    private fun applyConfiguration(name: String, data: StructureSnapshot, options: StructureModelSettings) {
        require(options.valid()) { "结构设置无效" }
        require(name.length <= 256)
        validateBounds(data, options)
        val origin = modelOrigin
        val tag = CompoundTag().apply {
            putString("Name", name)
            put("Structure", data.toNbt())
            put("Settings", options.toNbt())
        }
        require(tag.sizeInBytes() <= 1048576L) { "结构与设置的 NBT 估算内存超过 1 MiB" }
        entityData.set(MODEL, tag)
        setPos(origin.add(bodyOffset))
        reapplyPosition()
        rebuildGeometry()
        getAttribute(Attributes.MAX_HEALTH)?.baseValue = options.health.toDouble()
        health = options.health
        setPersistenceRequired()
    }

    /** 两端先完整解析并校验，再发布状态，避免混用新快照和旧变换。 */
    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        if (key != MODEL) return
        val tag = entityData.get(MODEL)
        if (tag.isEmpty) return
        require(tag.sizeInBytes() <= 1048576L)
        val data = StructureSnapshot(registryAccess(), tag.getCompound("Structure"))
        val options = StructureModelSettings.fromNbt(tag.getCompound("Settings"), data.defaultPivot)
        require(options.valid()) { "结构设置无效" }
        validateBounds(data, options)
        val candidates = data.collision.ifEmpty { data.outline }.ifEmpty { listOf(localVisualBox(data)) }
            .map { OrientedBox(it, options.transform, Vec3.ZERO).bounds }
        val center = candidates.reduce(AABB::minmax).center
        val body = candidates.minBy { it.center.distanceToSqr(center) }
        snapshot = data
        settings = options
        structureName = tag.getString("Name")
        bodyBounds = body
        bodyOffset = Vec3(body.center.x, body.minY, body.center.z)
        rebuildGeometry()
    }

    /** 验证实际变换结果有限；不再对模型距放置锚点的距离设硬限制。 */
    private fun validateBounds(data: StructureSnapshot, options: StructureModelSettings) {
        val boxes = data.collision + data.outline + localVisualBox(data)
        require(boxes.all {
            val bounds = OrientedBox(it, options.transform, Vec3.ZERO).bounds
            listOf(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)
                .all { coordinate -> coordinate.isFinite() }
        }) { "变换后的模型坐标必须有限" }
    }

    /** 模板包含空气边界的完整局部视觉框。 */
    private fun localVisualBox(data: StructureSnapshot): AABB =
        AABB(0.0, 0.0, 0.0, data.size.x.toDouble(), data.size.y.toDouble(), data.size.z.toDouble())

    /** 传送或同步位置变化后的惰性缓存刷新，供查询和渲染入口使用。 */
    private fun ensureGeometry() {
        if (geometryOrigin != position() && snapshot != null) rebuildGeometry()
    }

    /** 一次性重建碰撞树、射线树和索引范围；只在位置或配置变化时执行。 */
    private fun rebuildGeometry() {
        val data = snapshot ?: return
        physicalGeometry = CollisionGeometry(data.collision.map { OrientedBox(it, settings.transform, modelOrigin) })
        pickGeometry = CollisionGeometry(data.outline.map { OrientedBox(it, settings.transform, modelOrigin) })
        visualBounds = OrientedBox(localVisualBox(data), settings.transform, modelOrigin).bounds
        geometryBounds = listOfNotNull(physicalGeometry.bounds, pickGeometry.bounds, visualBounds).reduce(AABB::minmax)
        geometryOrigin = position()
        boundingBox = geometryBounds!!
    }

    override fun makeBoundingBox(): AABB =
        geometryBounds?.move(position().subtract(geometryOrigin ?: position())) ?: super.makeBoundingBox()

    override fun getX(scale: Double): Double = bodyBounds?.let { x + it.xsize * scale } ?: super.getX(scale)
    override fun getY(scale: Double): Double = bodyBounds?.let { y + it.ysize * scale } ?: super.getY(scale)
    override fun getZ(scale: Double): Double = bodyBounds?.let { z + it.zsize * scale } ?: super.getZ(scale)

    /** 静态模型直接接受位置更新，编辑中心不触发旧身体位置到新位置的滑动。 */
    override fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int) {
        moveTo(x, y, z, yRot, xRot)
        rebuildGeometry()
    }

    /** 死亡侧翻围绕显式支点，包围范围覆盖外置支点造成的整段旋转。 */
    override fun getBoundingBoxForCulling(): AABB {
        ensureGeometry()
        val bounds = visualBounds ?: boundingBox
        if (deathTime == 0) return bounds
        val pivot = collisionPivot
        val radius = Vec3(max(abs(bounds.minX - pivot.x), abs(bounds.maxX - pivot.x)),
            max(abs(bounds.minY - pivot.y), abs(bounds.maxY - pivot.y)),
            max(abs(bounds.minZ - pivot.z), abs(bounds.maxZ - pivot.z))).length()
        return AABB(pivot, pivot).inflate(radius)
    }

    override fun registerGoals() {}

    /** 拾取要求启用开关并通过世界、距离与修改权限检查。 */
    fun canPickUp(player: Player): Boolean = settings.pickable && canAccess(player)

    /** 服务端每次提交和容器操作均重新检查工具与距离。 */
    fun canEdit(player: Player): Boolean = canAccess(player) &&
        (player.mainHandItem.`is`(StructureModels.EDITOR) || player.offhandItem.`is`(StructureModels.EDITOR))

    /** 世界权限和距离统一适用于编辑与收起。 */
    private fun canAccess(player: Player): Boolean = isAlive && player.isAlive && !player.isSpectator &&
        player.abilities.mayBuild && player.level() === level() &&
        level().mayInteract(player, BlockPos.containing(modelOrigin)) && player.canInteractWithEntity(boundingBox, 1.0)

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (player.isShiftKeyDown && settings.pickable) return StructureModels.HELD_MODEL.capture(this, player, hand)
        if (!player.getItemInHand(hand).`is`(StructureModels.EDITOR) || !canEdit(player)) return InteractionResult.PASS
        if (player is ServerPlayer) StructureModels.openEditor(player, blockPosition(), this)
        return InteractionResult.sidedSuccess(level().isClientSide)
    }

    override fun interactAt(player: Player, hit: Vec3, hand: InteractionHand): InteractionResult = mobInteract(player, hand)
    override fun isNoAi(): Boolean = true
    override fun removeWhenFarAway(distance: Double): Boolean = false
    override fun isNoGravity(): Boolean = true
    override fun isPushable(): Boolean = false
    override fun isPushedByFluid(): Boolean = false
    override fun canBeCollidedWith(): Boolean = false
    override fun isPickable(): Boolean = isAlive
    override fun pushEntities() {}
    override fun doPush(entity: Entity) {}
    override fun push(entity: Entity) {}
    override fun knockback(strength: Double, x: Double, z: Double) {}
    override fun move(type: MoverType, movement: Vec3) {}

    /** 保留生命和死亡计时，但不允许重力、流体或外部速度移动静态模型。 */
    override fun tick() {
        deltaMovement = Vec3.ZERO
        super.tick()
        deltaMovement = Vec3.ZERO
        ensureGeometry()
    }

    /** 实际掉落结算后立即清空，保持存档和重复回调不会复制库存。 */
    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {
        Containers.dropContents(level, this, drops)
        drops.clearContent()
    }

    override fun die(source: DamageSource) {
        super.die(source)
        if (!settings.deathAnimation && !level().isClientSide) discard()
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.put("StructureModel", entityData.get(MODEL).copy())
        tag.put("ModelDrops", drops.createTag(registryAccess()))
    }

    /** 存档位置已经是身体位置，不再次叠加放置锚点偏移，也不重置剩余生命。 */
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        entityData.set(MODEL, tag.getCompound("StructureModel"))
        reapplyPosition()
        rebuildGeometry()
        drops.fromTag(tag.getList("ModelDrops", 10), registryAccess())
    }
}
