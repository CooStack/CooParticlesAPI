package cn.coostack.cooparticlesapi.mixin.events;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.client.ClientInteractionInputEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 将两个加载器共用的原版客户端交互入口发布为可取消事件。 */
@Mixin(Minecraft.class)
public abstract class StructureClientInputMixin {
    /** 取消攻击时同时停止原版的方块或实体操作。 */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void attack(CallbackInfoReturnable<Boolean> callback) {
        if (CooEventBus.call(new ClientInteractionInputEvent(ClientInteractionInputEvent.Action.ATTACK)).isCancelled()) {
            callback.setReturnValue(false);
        }
    }

    /** 使用事件覆盖空气、实体和方块命中。 */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void use(CallbackInfo callback) {
        if (CooEventBus.call(new ClientInteractionInputEvent(ClientInteractionInputEvent.Action.USE)).isCancelled()) callback.cancel();
    }

    /** 长按挖掘独立于攻击边沿，避免工具开始破坏方块。 */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void continueAttack(boolean held, CallbackInfo callback) {
        if (CooEventBus.call(new ClientInteractionInputEvent(ClientInteractionInputEvent.Action.CONTINUE_ATTACK)).isCancelled()) callback.cancel();
    }
}
