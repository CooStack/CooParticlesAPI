package cn.coostack.cooparticlesapi.mixin.structure;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.TorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 只读取得火把配置的显示粒子，不调用方块逻辑或环境 tick。 */
@Mixin(TorchBlock.class)
public interface TorchParticleAccess {
    /** @return 火把原版配置的火焰粒子类型 */
    @Accessor("flameParticle")
    SimpleParticleType getTorchParticle();
}
