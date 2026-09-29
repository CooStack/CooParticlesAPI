package cn.coostack.cooparticlesapi.mixin.entity;

import cn.coostack.cooparticlesapi.entities.collision.IrregularCollisionEntity;
import cn.coostack.cooparticlesapi.entities.collision.client.IrregularCollisionDebugRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只替换实现不规则碰撞能力的实体调试图形，普通实体保持原版显示。 */
@Mixin(EntityRenderDispatcher.class)
public abstract class IrregularDebugMixin {
    /** 复用原版 F3+B 开关和线段批次，中心坐标轴由能力实现提供。 */
    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void renderIrregularHitbox(PoseStack matrices, VertexConsumer vertices, Entity entity,
                                              float partialTick, float red, float green, float blue, CallbackInfo callback) {
        if (!(entity instanceof IrregularCollisionEntity irregular)) return;
        if (entity.isAlive()) IrregularCollisionDebugRenderer.draw(matrices, vertices, irregular, red, green, blue);
        callback.cancel();
    }
}
