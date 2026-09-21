package cn.coostack.cooparticlesapi.mixin.compat.iris;

public interface FinalPassRendererAccessor {
    void coParticlesAPI$setMainTarget(net.minecraft.client.renderer.MultiBufferSource.BufferSource target);
    net.minecraft.client.renderer.MultiBufferSource.BufferSource coParticlesAPI$getMainTarget();
}
