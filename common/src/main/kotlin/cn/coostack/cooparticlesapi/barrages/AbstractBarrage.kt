package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import com.google.common.base.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

abstract class AbstractBarrage(
    override var loc: Vec3,
    override val world: ServerLevel,
    override var hitBox: HitBox,
    override val bindControl: ServerControler<*>,
    override val options: BarrageOption,
) : Barrage {
    override var shooter: LivingEntity? = null
    override var direction: Vec3 = Vec3.ZERO
    override var lunch: Boolean = false

    /**
     * 不公开的原因是他有上限
     */
    private var currentTick = 0
    private var spawnTick = 0
    internal var isValid = true
    override val valid: Boolean
        get() = isValid
    private var currentAcrossCount = 0
    override val uuid: UUID = UUID.randomUUID()

    /**
     * 当获取到hitBox有实体时，可以对实体进行过滤
     */
    abstract fun filterHitEntity(livingEntity: LivingEntity): Boolean

    /**
     * 重写此方法用于自定义弹幕击中判定
     * 如果重写
     * 必须加上 barrage != this 否则会出现自己击中自己的bug
     */
    open fun filterHitBarrage(barrage: Barrage): Boolean {
        return barrage.shooter != shooter && barrage != this
    }


    open fun getControlerLocation(): Vec3 {
        return loc
    }

    override fun tick() {
        if (!lunch || !valid) {
            return
        }
        // 区块不加载不执行tick
        if (!world.hasChunk(loc.x.toInt() shr 4, loc.z.toInt() shr 4)) {
            return
        }

        // 判定速度
        val previousLoc = loc
        if (options.enableSpeed) {
            loc = loc.add(direction.normalize().scale(options.speed))
            options.speed += options.acceleration
            // 判定加速度最大值设定
            if (options.accelerationMaxSpeedEnabled) {
                options.speed = max(options.accelerationMaxSpeed, options.speed)
            }
        } else {
            loc = loc.add(direction)
        }

        bindControl.teleportTo(getControlerLocation())
        // 判断击中
        if (options.maxLivingTick != -1) {
            if (currentTick++ > options.maxLivingTick) {
                hit(BarrageHitResult())
                return
            }
        }
        var hit = false

        val result = BarrageHitResult()
        BlockPos.betweenClosedStream(
            hitBox.ofBox(loc)
        ).forEach {
            if (world.shouldTickBlocksAt(it) && world.isPositionEntityTicking(it)) {
                val block = world.getBlockState(it)
                if (!block.isAir) {
                    val shape = block.getCollisionShape(world, it)
                    if (!block.isSolid) {
                        if (!options.acrossLiquid) {
                            result.hitBlockState = block
                            result.hitBlocks.add(it)
                            hit = true
                        }
                    } else if (!options.acrossBlock && (!shape.isEmpty || !options.acrossEmptyCollectionShape)) {
                        result.hitBlockState = block
                        result.hitBlocks.add(it)
                        hit = true
                    }
                }
            }
        }

        if (spawnTick < options.noneHitBoxTick) {
            spawnTick++
            return
        }
        val collection = hitBoxEntities(previousLoc, loc).filter {
            return@filter filterHitEntity(it)
        }
        if (collection.isNotEmpty()) {
            result.entities.addAll(collection)
            hit = true
        }
        if (!options.barrageIgnored) {
            val otherBarrages = BarrageManager.collectClipBarrages(world, hitBox.ofBox(loc))
                .filter(::filterHitBarrage)
            result.barrages.addAll(otherBarrages)
            hit = true
        }

        if (hit) {
            hit(result)
        }
    }

    /**
     * 判定barrage已经攻击到实体或者触发方块/液体时执行
     */
    override fun hit(result: BarrageHitResult) {
        onHit(result)
        val timeoutHit = options.maxLivingTick <= currentTick && options.maxLivingTick != -1
        if (options.acrossable && !timeoutHit) {
            if (options.maxAcrossCount == -1) return
            if (currentAcrossCount++ < options.maxAcrossCount) return
        }
        if (result.barrages.isNotEmpty()) {
            result.barrages.forEach { barrage ->
                // 防止出现自己调用自己
                if (!barrage.options.barrageIgnored && barrage is AbstractBarrage && barrage != this) {
                    barrage.hit(BarrageHitResult().also { it.barrages.add(this) })
                }
            }
        }
        remove()
    }

    fun remove() {
        bindControl.remove()
        isValid = false
    }

    /**
     * 没有碰撞体积
     */
    override fun noclip(): Boolean = spawnTick < options.noneHitBoxTick

    fun hitBoxEntities(): Set<LivingEntity> {
        return hitBoxEntities(loc, loc)
    }

    fun hitBoxEntities(filter: Predicate<LivingEntity>): Set<LivingEntity> {
        return hitBoxEntities(loc, loc, filter)
    }

    fun hitBoxEntities(from: Vec3, to: Vec3): Set<LivingEntity> {
        return hitBoxEntities(from, to, Predicate { true })
    }

    fun hitBoxEntities(from: Vec3, to: Vec3, filter: Predicate<LivingEntity>): Set<LivingEntity> {
        val res = HashSet<LivingEntity>()
        val fromBox = hitBox.ofBox(from)
        val toBox = hitBox.ofBox(to)
        val sweepBox = AABB(
            min(fromBox.minX, toBox.minX),
            min(fromBox.minY, toBox.minY),
            min(fromBox.minZ, toBox.minZ),
            max(fromBox.maxX, toBox.maxX),
            max(fromBox.maxY, toBox.maxY),
            max(fromBox.maxZ, toBox.maxZ),
        )
        world.getEntitiesOfClass(LivingEntity::class.java, sweepBox, filter).forEach { entity ->
            val expandedEntityBox = AABB(
                entity.boundingBox.minX - hitBox.x2,
                entity.boundingBox.minY - hitBox.y2,
                entity.boundingBox.minZ - hitBox.z2,
                entity.boundingBox.maxX - hitBox.x1,
                entity.boundingBox.maxY - hitBox.y1,
                entity.boundingBox.maxZ - hitBox.z1,
            )
            if (expandedEntityBox.contains(from) || expandedEntityBox.clip(from, to).isPresent) {
                res.add(entity)
            }
        }
        return res
    }

}
