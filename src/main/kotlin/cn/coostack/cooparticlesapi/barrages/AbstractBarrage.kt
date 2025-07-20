package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.network.particle.ServerControler
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import com.google.common.base.Predicate
import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.Chunk
import java.util.UUID
import kotlin.math.max

abstract class AbstractBarrage(
    override var loc: Vec3d,
    override val world: ServerWorld,
    override var hitBox: HitBox,
    override val bindControl: ServerControler<*>,
    override val options: BarrageOption,
) : Barrage {
    override var shooter: LivingEntity? = null
    override var direction: Vec3d = Vec3d.ZERO
    override var lunch: Boolean = false
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

    override fun tick() {
        if (!lunch || !valid) {
            return
        }
        // 区块不加载不执行tick
        if (!world.isChunkLoaded(loc.x.toInt() shr 4, loc.z.toInt() shr 4)) {
            return
        }

        // 判定速度
        if (options.enableSpeed) {
            loc = loc.add(direction.normalize().multiply(options.speed))
            options.speed += options.acceleration
            // 判定加速度最大值设定
            if (options.accelerationMaxSpeedEnabled) {
                options.speed = max(options.accelerationMaxSpeed, options.speed)
            }
        } else {
            loc = loc.add(direction)
        }

        bindControl.teleportTo(loc)
        // 判断击中
        if (options.maxLivingTick != -1) {
            if (currentTick++ > options.maxLivingTick) {
                hit(BarrageHitResult())
                return
            }
        }
        var hit = false
        val blockPos = BlockPos.ofFloored(loc)
        val result = BarrageHitResult()
        if (world.shouldTick(blockPos) && world.isPosLoaded(blockPos.x, blockPos.z)) {
            val block = world.getBlockState(blockPos)
            if (!block.isAir) {
                val shape = block.getCollisionShape(world, blockPos)
                if (block.isLiquid) {
                    if (!options.acrossLiquid) {
                        result.hitBlockState = block
                        hit = true
                    }
                } else if (!options.acrossBlock && (!shape.isEmpty || !options.acrossEmptyCollectionShape)) {
                    result.hitBlockState = block
                    hit = true
                }
            }
        }

        if (spawnTick < options.noneHitBoxTick) {
            spawnTick++
            return
        }
        val collection = hitBoxEntities().filter {
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
        val res = HashSet<LivingEntity>()
        res.addAll(world.getEntitiesByClass(LivingEntity::class.java, hitBox.ofBox(loc), { true }))
        return res
    }

    fun hitBoxEntities(filter: Predicate<LivingEntity>): Set<LivingEntity> {
        val res = HashSet<LivingEntity>()
        res.addAll(world.getEntitiesByClass(LivingEntity::class.java, hitBox.ofBox(loc), filter))
        return res
    }

}