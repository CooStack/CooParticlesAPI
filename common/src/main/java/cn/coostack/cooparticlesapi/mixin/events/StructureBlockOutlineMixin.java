package cn.coostack.cooparticlesapi.mixin.events;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.client.ClientBlockOutlineEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 两个加载器共用的原版选取轮廓通知，不干预实体选取或其他模组独立绘制的轮廓。 */
@Mixin(LevelRenderer.class)
public abstract class StructureBlockOutlineMixin {
    /** 将是否隐藏原版轮廓交给 CooEvent 监听器。 */
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void outline(PoseStack matrices, VertexConsumer vertices, Entity entity,
                         double x, double y, double z, BlockPos pos, BlockState state, CallbackInfo callback) {
        if (CooEventBus.call(new ClientBlockOutlineEvent(entity, pos, state)).isCancelled()) callback.cancel();
    }
}
