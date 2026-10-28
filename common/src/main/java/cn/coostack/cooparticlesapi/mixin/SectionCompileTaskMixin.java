package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 仅覆盖几何的区段不能被发布为全局 EMPTY 单例，否则会丢失独立覆盖资源身份。 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
public abstract class SectionCompileTaskMixin {
    /** 只替换原版空几何分支的 EMPTY 选择，不触碰取消、上传回调和网格交换顺序。 */
    @ModifyArg(method = "doTask", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;setSectionMesh(" +
                    "Lnet/minecraft/client/renderer/chunk/SectionMesh;)Lnet/minecraft/client/renderer/chunk/SectionMesh;"), index = 0)
    private SectionMesh preserveOverlayOnlySection(SectionMesh selected, @Local CompiledSectionMesh compiled) {
        return selected == CompiledSectionMesh.EMPTY && VanillaTerrainOverlay.hasGeometry(compiled) ? compiled : selected;
    }
}
