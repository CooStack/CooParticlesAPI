package cn.coostack.cooparticlesapi.mixin.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** 只在客户端存在 Sodium 时提供专用 Mixin，避免解析不存在的可选目标。 */
public class CooSodiumMixinPlugin implements IMixinConfigPlugin {
    /** 探测类资源而不初始化 Sodium 或 Minecraft。 */
    private static boolean sodiumAvailable() {
        return CooSodiumMixinPlugin.class.getClassLoader().getResource(
                "net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.class"
        ) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sodiumAvailable();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    /**
     * 在配置准备阶段按存在性和逻辑端提供目标，缺少 Sodium 时不触发 Mixin 环境初始化。
     *
     * @return 当前可应用的客户端 Mixin 相对类名；服务端或未安装 Sodium 时为空
     */
    @Override
    public List<String> getMixins() {
        if (!sodiumAvailable()) {
            return List.of();
        }
        if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) {
            return List.of();
        }
        return List.of(
                "BlockRendererMixin",
                "ChunkBuilderMeshingTaskMixin",
                "ChunkBuildOutputMixin",
                "GameRendererMixin",
                "RenderRegionManagerMixin",
                "RenderSectionMixin",
                "RenderSectionManagerMixin",
                "SodiumWorldRendererMixin"
        );
    }

    @Override
    public void preApply(String targetClassName,
                         ClassNode targetClass,
                         String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName,
                          ClassNode targetClass,
                          String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
