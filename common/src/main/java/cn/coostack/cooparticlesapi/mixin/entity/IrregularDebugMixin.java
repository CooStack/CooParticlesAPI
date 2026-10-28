package cn.coostack.cooparticlesapi.mixin.entity;

import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionEntity;
import cn.coostack.cooparticlesapi.entities.collision.client.IrregularCollisionDebugRenderer;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将不规则实体 F3+B 调试框提交到 26.3 原生 Gizmos，普通实体保持原版显示。 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class IrregularDebugMixin {
    /** 复用原版可见性与 F3+B 开关，几何使用实体真实有向框并保留插值偏移。 */
    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void renderIrregularHitbox(Entity entity, float partialTick, boolean serverEntity, CallbackInfo callback) {
        if (!(entity instanceof IrregularCollisionEntity irregular)) return;
        if (entity.isAlive()) {
            Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
            int color = serverEntity ? 0xFF00FF00 : 0xFFFFFFFF;
            IrregularCollisionDebugRenderer.emitGizmos(irregular, offset, color);
        }
        callback.cancel();
    }
}
