package cn.coostack.cooparticlesapi.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 使用原版工厂创建可提交的粒子绘制类型，不绕过命令收集器。 */
@Mixin(RenderType.class)
public interface ParticleRenderTypeCreateInvoker {
    /**
     * @param name 用于诊断的图层名称
     * @param setup 图层的管线、图集与光照配置
     * @return 原版管理的绘制类型
     */
    @Invoker("create")
    static RenderType createParticleRenderType(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
