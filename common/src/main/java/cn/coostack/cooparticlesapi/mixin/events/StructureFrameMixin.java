package cn.coostack.cooparticlesapi.mixin.events;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.client.ClientFrameStartEvent;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在世界主帧开始发布事件，不在 Iris 实体或阴影重放入口累计预算。 */
@Mixin(GameRenderer.class)
public abstract class StructureFrameMixin {
    /** 每帧事件不修改原版相机、矩阵或渲染状态。 */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void beginFrame(CallbackInfo callback) {
        CooEventBus.call(new ClientFrameStartEvent());
    }
}
