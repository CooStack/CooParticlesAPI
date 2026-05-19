package cn.coostack.cooparticlesapi.mixin.events.entity;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadType;
import cn.coostack.cooparticlesapi.event.events.entity.client.ClientEntityLoadEvent;
import cn.coostack.cooparticlesapi.event.events.entity.client.ClientEntityUnloadEvent;
import cn.coostack.cooparticlesapi.event.events.entity.server.ServerEntityLoadEvent;
import cn.coostack.cooparticlesapi.event.events.entity.server.ServerEntityUnloadEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixinNeoForge {
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void onSetLevel(Level level, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            CooEventBus.call(new ClientEntityLoadEvent(entity));
        } else {
            CooEventBus.call(new ServerEntityLoadEvent(entity));
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() == null) {
            return;
        }
        EntityUnloadType unloadType = switch (reason) {
            case KILLED -> EntityUnloadType.ENTITY_DEATH;
            case DISCARDED -> EntityUnloadType.ENTITY_DISCARD;
            case UNLOADED_TO_CHUNK -> EntityUnloadType.CHUNK_UNLOAD;
            case UNLOADED_WITH_PLAYER -> EntityUnloadType.QUIT;
            case CHANGED_DIMENSION -> EntityUnloadType.WORLD_CHANGE;
        };
        if (entity.level().isClientSide) {
            CooEventBus.call(new ClientEntityUnloadEvent(entity, unloadType));
        } else {
            CooEventBus.call(new ServerEntityUnloadEvent(entity, unloadType));
        }
    }
}
