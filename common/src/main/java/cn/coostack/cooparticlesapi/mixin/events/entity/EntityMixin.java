package cn.coostack.cooparticlesapi.mixin.events.entity;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.EntityMoveEvent;
import cn.coostack.cooparticlesapi.event.events.entity.EntityPreMoveEvent;
import cn.coostack.cooparticlesapi.event.events.entity.EntityPostTickEvent;
import cn.coostack.cooparticlesapi.event.events.entity.EntityPreTickEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Entity.class)
public class EntityMixin {

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 modifyMovePos(Vec3 original) {
        Entity entity = (Entity) (Object) this;

        var event = new EntityPreMoveEvent(entity, original);
        CooEventBus.call(event);
        if (event.isCancelled()) {
            return Vec3.ZERO;
        }
        return event.getMovement();
    }

    @ModifyArgs(method = "setPosRaw", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"))
    private void onEntityMove(Args args) {
        Entity entity = (Entity) (Object) this;
        Vec3 moveTo = new Vec3(args.get(0), args.get(1), args.get(2));
        Vec3 movement = moveTo.subtract(entity.position());
        var event = new EntityMoveEvent(entity, movement, moveTo);
        CooEventBus.call(event);
        var pos = event.isCancelled() ? entity.position() : event.getMoveTo();
        args.set(0, pos.x);
        args.set(1, pos.y);
        args.set(2, pos.z);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickStart(CallbackInfo ci) {
        // 如果 cancel 则这个实体的tick方法就不继续执行
        Entity entity = (Entity) (Object) this;
        var event = new EntityPreTickEvent(entity);
        CooEventBus.call(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickEnd(CallbackInfo ci) {
        // 如果 cancel 则这个实体的tick方法就不继续执行
        Entity entity = (Entity) (Object) this;
        var event = new EntityPostTickEvent(entity);
        CooEventBus.call(event);
    }
}
