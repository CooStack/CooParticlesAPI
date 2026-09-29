package cn.coostack.cooparticlesapi.mixin.entity;

import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionEntity;
import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionQueries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

/** 鼠标选取和投射物使用精确形状，其他实体的命中仍由原版计算。 */
@Mixin(ProjectileUtil.class)
public abstract class IrregularRaycastMixin {
    /** 排除自定义实体后重入原版方法，不会重复进入精确分支。 */
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void pickIrregular(Entity source, Vec3 start, Vec3 end, AABB query, Predicate<Entity> predicate,
                                      double distance, CallbackInfoReturnable<EntityHitResult> callback) {
        List<Entity> models = IrregularCollisionQueries.find(source.level(), source, query, predicate);
        if (models.isEmpty()) return;
        EntityHitResult vanilla = ProjectileUtil.getEntityHitResult(source, start, end, query,
                entity -> !(entity instanceof IrregularCollisionEntity) && predicate.test(entity), distance);
        // 原版鼠标选取不攻击同一根载具中的实体。
        models.removeIf(entity -> entity.getRootVehicle() == source.getRootVehicle());
        callback.setReturnValue(IrregularCollisionQueries.nearest(models, start, end, distance, vanilla));
    }

    /** 投射物膨胀仅保留给普通实体，孔洞形状按真实表面求交。 */
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void hitIrregular(Level level, Entity source, Vec3 start, Vec3 end, AABB query, Predicate<Entity> predicate,
                                     float margin, CallbackInfoReturnable<EntityHitResult> callback) {
        List<Entity> models = IrregularCollisionQueries.find(level, source, query, predicate);
        if (models.isEmpty()) return;
        EntityHitResult vanilla = ProjectileUtil.getEntityHitResult(level, source, start, end, query,
                entity -> !(entity instanceof IrregularCollisionEntity) && predicate.test(entity), margin);
        callback.setReturnValue(IrregularCollisionQueries.nearest(models, start, end, start.distanceToSqr(end), vanilla));
    }
}
