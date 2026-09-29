package cn.coostack.cooparticlesapi.mixin.entity;

import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionQueries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 在双方实体移动查询末尾追加不规则实体的精确形状，普通实体结果保持原样。 */
@Mixin(EntityGetter.class)
public interface IrregularCollisionMixin {
    /** 只在查询命中自定义形状时复制列表，保留原版其他实体的阻挡。 */
    @Inject(method = "getEntityCollisions", at = @At("RETURN"), cancellable = true)
    default void appendIrregularCollisions(Entity source, AABB query, CallbackInfoReturnable<List<VoxelShape>> callback) {
        List<VoxelShape> extra = IrregularCollisionQueries.append((EntityGetter) this, source, query);
        if (extra.isEmpty()) return;
        List<VoxelShape> result = new ArrayList<>(callback.getReturnValue());
        result.addAll(extra);
        callback.setReturnValue(result);
    }
}
