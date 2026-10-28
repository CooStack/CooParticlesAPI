package cn.coostack.cooparticlesapi.entities

import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

class TestRenderEntity(entityType: EntityType<*>, level: Level) : Entity(entityType, level) {

    constructor(level: Level) : this(CooModEntityTypes.TEST_RENDER.get(), level)

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
    }

    override fun readAdditionalSaveData(input: ValueInput) {
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
    }

    /** 此实体仅用于显示测试，不承受服务端伤害。 */
    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean = false

    override fun tick() {
        // 直接漂浮

    }
}
