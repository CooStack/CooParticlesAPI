package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import cn.coostack.cooparticlesapi.display.DisplayEntityManager;
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager;
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 在客户端 F3 调试信息中显示 CooParticlesAPI 的运行时对象数量。
 *
 * <p>示例：打开 F3 后可直接查看当前客户端加载的发射器、组合、粒子和渲染实体数量。
 * 禁止在服务端代码中直接加载此类或使用这些数值判断服务端状态。
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    /**
     * 在原版左侧调试文本末尾追加客户端运行时计数。
     *
     * <p>示例：原版信息生成后会追加 {@code [CPLib] Emitters: 3}。
     * 禁止把注册类型数量或服务端容器数量混入这些客户端实例计数。
     *
     * @param info 原版调试文本返回值的回调信息
     */
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void cooParticlesAPI$appendRuntimeCounts(CallbackInfoReturnable<List<String>> info) {
        List<String> lines = new ArrayList<>(info.getReturnValue());
        lines.add("[CPLib] Emitters: " + ParticleEmittersManager.INSTANCE.getClientEmitters().size());
        lines.add("[CPLib] Compositions: " + ParticleCompositionManager.loadedClientCount());
        lines.add("[CPLib] CParticles: " + CParticleSystemManager.totalAlive());
        lines.add("[CPLib] DisplayEntities: " + DisplayEntityManager.INSTANCE.getClientView().size());
        lines.add("[CPLib] RenderEntities: " + ClientRenderEntityManager.loadedEntityCount());
        info.setReturnValue(lines);
    }
}
