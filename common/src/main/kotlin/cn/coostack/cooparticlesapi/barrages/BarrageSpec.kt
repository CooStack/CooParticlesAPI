package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Builder/DSL，用于一次性弹幕。无需继承 [AbstractBarrage]。
 *
 * ### 最小用法
 * ```kotlin
 * BarrageManager.spawn(world, loc) {
 *     direction = playerLook
 *     shooter = player
 *     hitBox { HitBox.of(2.0, 2.0, 2.0).setDirection(direction) }
 *     controler { BarrageItemDisplayEntity(loc, world).apply { item = sword } }
 *     options { enableSpeedWithOptions(0.5); maxLivingTick = 200 }
 *     onHit { result -> /* ... */ }
 * }
 * ```
 *
 * ### 复用
 * 如果要把同一类弹幕复用在多个调用点，把整段配置抽到一个 `fun BarrageSpec.swordShot()` 扩展即可，
 * 不需要再写一个 [AbstractBarrage] 子类。
 */
class BarrageSpec internal constructor(
    val world: ServerLevel,
    val loc: Vec3,
) {
    var direction: Vec3 = Vec3.ZERO
    var shooter: LivingEntity? = null

    val options: BarrageOption = BarrageOption()

    private var hitBoxFactory: () -> HitBox = { HitBox.of(0.5, 0.5, 0.5) }
    private var controlerFactory: (() -> ServerControler<*>)? = null
    private var entityFilter: (LivingEntity) -> Boolean = { true }
    private var barrageFilter: ((Barrage, AbstractBarrage) -> Boolean)? = null
    private var hitHandler: (BarrageHitResult) -> Unit = {}
    private var tickHook: (LambdaBarrage) -> Unit = {}
    private var spawnHook: (LambdaBarrage) -> Unit = {}

    /** 设置碰撞盒生成函数。每次重置 Memo 时被调用。 */
    fun hitBox(factory: () -> HitBox) = apply { hitBoxFactory = factory }

    /** 设置控制器生成函数 (必填，没有控制器无法 spawn)。 */
    fun controler(factory: () -> ServerControler<*>) = apply { controlerFactory = factory }

    /** 命中实体过滤。返回 true 保留。 */
    fun filterEntity(predicate: (LivingEntity) -> Boolean) = apply { entityFilter = predicate }

    /**
     * 命中其他弹幕的过滤逻辑。第一参数是被检测的弹幕，第二参数是当前弹幕。
     * 默认行为见 [AbstractBarrage.filterHitBarrage]。
     */
    fun filterBarrage(predicate: (Barrage, AbstractBarrage) -> Boolean) = apply { barrageFilter = predicate }

    /** 命中回调。 */
    fun onHit(block: (BarrageHitResult) -> Unit) = apply { hitHandler = block }

    /** 每 tick 末尾的钩子，super.tick() 已经执行过。 */
    fun onTick(block: (LambdaBarrage) -> Unit) = apply { tickHook = block }

    /** 第一次成功 tick 时回调一次，可用于初始化 controler 朝向等。 */
    fun onSpawn(block: (LambdaBarrage) -> Unit) = apply { spawnHook = block }

    /** 直接修改 [options]。 */
    fun options(block: BarrageOption.() -> Unit) = apply { options.block() }

    internal fun build(): LambdaBarrage {
        val factory = controlerFactory
            ?: error("BarrageSpec.controler { ... } is required before spawning")
        return LambdaBarrage(
            loc, world, options,
            hitBoxFactory, factory, entityFilter, barrageFilter,
            hitHandler, tickHook, spawnHook,
        ).also {
            it.shooter = shooter
            it.direction = direction
        }
    }
}

/** 通过 [BarrageSpec] 构建出来的通用弹幕实现。 */
class LambdaBarrage internal constructor(
    loc: Vec3,
    world: ServerLevel,
    options: BarrageOption,
    private val hitBoxFactory: () -> HitBox,
    private val controlerFactory: () -> ServerControler<*>,
    private val entityFilter: (LivingEntity) -> Boolean,
    private val barrageFilter: ((Barrage, AbstractBarrage) -> Boolean)?,
    private val hitHandler: (BarrageHitResult) -> Unit,
    private val tickHook: (LambdaBarrage) -> Unit,
    private val spawnHook: (LambdaBarrage) -> Unit,
) : AbstractBarrage(loc, world, options) {

    private var spawnHookFired = false

    override fun filterHitEntity(livingEntity: LivingEntity): Boolean = entityFilter(livingEntity)

    override fun createHitBox(): HitBox = hitBoxFactory()

    override fun createControler(): ServerControler<*> = controlerFactory()

    override fun filterHitBarrage(barrage: Barrage): Boolean {
        val custom = barrageFilter
        return if (custom != null) custom(barrage, this) else super.filterHitBarrage(barrage)
    }

    override fun onHit(result: BarrageHitResult) = hitHandler(result)

    override fun tick() {
        super.tick()
        if (!spawnHookFired) {
            spawnHookFired = true
            spawnHook(this)
        }
        if (valid) tickHook(this)
    }
}
