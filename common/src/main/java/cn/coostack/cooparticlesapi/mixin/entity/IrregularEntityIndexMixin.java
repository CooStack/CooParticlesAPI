package cn.coostack.cooparticlesapi.mixin.entity;

import cn.coostack.cooparticlesapi.entities.collision.IrregularEntityIndex;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在两端实体真正加入和离开原版索引时同步不规则实体集合。 */
@Mixin(EntityLookup.class)
public abstract class IrregularEntityIndexMixin {
    /** 检查实际登记结果，不把重复 UUID 或生成失败实体作为障碍物。 */
    @Inject(method = "add", at = @At("RETURN"))
    private void addIrregular(EntityAccess value, CallbackInfo callback) {
        EntityLookup<?> lookup = (EntityLookup<?>) (Object) this;
        if (value instanceof Entity entity && lookup.getEntity(value.getUUID()) == value) IrregularEntityIndex.add(entity);
    }

    /** 卸载区块、移除实体和切换维度时立即注销。 */
    @Inject(method = "remove", at = @At("HEAD"))
    private void removeIrregular(EntityAccess value, CallbackInfo callback) {
        EntityLookup<?> lookup = (EntityLookup<?>) (Object) this;
        EntityAccess tracked = lookup.getEntity(value.getUUID());
        if (tracked instanceof Entity entity) IrregularEntityIndex.remove(entity);
    }
}
