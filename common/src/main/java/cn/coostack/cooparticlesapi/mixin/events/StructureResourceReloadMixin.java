package cn.coostack.cooparticlesapi.mixin.events;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.client.ClientResourceReloadEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 使用实体渲染器的资源应用阶段发布重载通知，两个加载器均在渲染线程调用。 */
@Mixin(EntityRenderDispatcher.class)
public abstract class StructureResourceReloadMixin {
    /** 旧渲染器和贴图引用失效时同步释放结构网格。 */
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void reload(CallbackInfo callback) {
        CooEventBus.call(new ClientResourceReloadEvent());
    }
}
