package cn.coostack.cooparticlesapi.mixin.plugin;

import cn.coostack.cooparticlesapi.platform.CooParticlesServices;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * @author CooStack
 */
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
        if ("cn.coostack.cooparticlesapi.mixin.ParticleEngineMixin".equals(mixinClassName)) {
            return CooParticlesServices.API_CONFIG_MANAGER.getConfig().getEnabledParticleCountInject();
        }
        if ("cn.coostack.cooparticlesapi.mixin.ParticleManagerRenderMixin".equals(mixinClassName)) {
            return CooParticlesServices.API_CONFIG_MANAGER.getConfig().getEnabledParticleAsync();
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
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }


}
