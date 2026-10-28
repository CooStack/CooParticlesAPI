package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import cn.coostack.cooparticlesapi.display.DisplayEntityManager;
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager;
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
     * @param overlay 原版 F3 覆盖层
     * @param graphics 当前 GUI 提取器
     * @param originalLines 原版当前列的调试文本
     * @param left 是否为左侧调试列
     * @param width 当前可用 GUI 宽度
     * @param original 原版文本提取操作
     */
    @WrapOperation(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V")
    )
    private void cooParticlesAPI$appendRuntimeCounts(DebugScreenOverlay overlay, GuiGraphicsExtractor graphics,
                                                    List<String> originalLines, boolean left, int width,
                                                    Operation<Void> original) {
        if (!left) {
            original.call(overlay, graphics, originalLines, false, width);
            return;
        }
        List<String> lines = new ArrayList<>(originalLines);
        lines.add("[CPLib] Emitters: " + ParticleEmittersManager.INSTANCE.getClientEmitters().size());
        lines.add("[CPLib] Compositions: " + ParticleCompositionManager.loadedClientCount());
        lines.add("[CPLib] CParticles: " + CParticleSystemManager.totalAlive());
        lines.add("[CPLib] DisplayEntities: " + DisplayEntityManager.INSTANCE.getClientView().size());
        lines.add("[CPLib] RenderEntities: " + ClientRenderEntityManager.loadedEntityCount());
        original.call(overlay, graphics, lines, true, width);
    }
}
