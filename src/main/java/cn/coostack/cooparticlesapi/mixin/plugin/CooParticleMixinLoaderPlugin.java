package cn.coostack.cooparticlesapi.mixin.plugin;

import cn.coostack.cooparticlesapi.CooParticleAPI;
import cn.coostack.cooparticlesapi.config.APIConfigManager;
import cn.coostack.cooparticlesapi.mixin.ParticleManagerMixin;
import net.minecraft.client.MinecraftClient;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CooParticleMixinLoaderPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return "CooParticleMixinLoaderPlugin";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if ("cn.coostack.cooparticlesapi.mixin.ParticleManagerMixin".equals(mixinClassName)) {
            return APIConfigManager.getConfig().getEnabledParticleCountInject();
        }
        if ("cn.coostack.cooparticlesapi.mixin.ParticleManagerRenderMixin".equals(mixinClassName)) {
            return APIConfigManager.getConfig().getEnabledParticleAsync();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
