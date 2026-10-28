package cn.coostack.cooparticlesapi.mixin.compat.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 保持 Coo 低透明度粒子在 Iris 粒子通道中的裁剪阈值。
 *
 * <p>反射只在可选 Iris Mixin 中发生，缺少对应公开构造器时保留 Iris 原始配置。
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderKey", remap = false)
public abstract class ShaderKeyIrisCompatMixin {
    /** 公开透明度配置的反射解析是否已经完成；失败也只尝试一次。 */
    @Unique
    private static volatile boolean cooparticlesapi$particleAlphaTestResolved;
    /** 与 Iris 粒子通道共享的不可变透明度配置；接口不匹配时为空。 */
    @Unique
    private static Object cooparticlesapi$particleAlphaTest;

    /** 只调整两种粒子通道，实体和地形沿用各自的 Iris 裁剪配置。 */
    @Inject(method = "getAlphaTest", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void cooparticlesapi$lowerParticleAlphaThreshold(CallbackInfoReturnable<Object> cir) {
        String shaderKey = ((Enum<?>) (Object) this).name();
        if (!"PARTICLES".equals(shaderKey) && !"PARTICLES_TRANS".equals(shaderKey)) {
            return;
        }
        Object alphaTest = cooparticlesapi$getParticleAlphaTest(cir.getReturnValue());
        if (alphaTest != null) {
            cir.setReturnValue(alphaTest);
        }
    }

    /** 以同步惰性初始化方式复用解析结果，避免每次 ShaderKey 查询都进行反射。 */
    @Unique
    private static Object cooparticlesapi$getParticleAlphaTest(Object original) {
        if (cooparticlesapi$particleAlphaTestResolved) {
            return cooparticlesapi$particleAlphaTest;
        }
        synchronized (ShaderKeyIrisCompatMixin.class) {
            if (cooparticlesapi$particleAlphaTestResolved) {
                return cooparticlesapi$particleAlphaTest;
            }
            cooparticlesapi$particleAlphaTest = cooparticlesapi$createParticleAlphaTest(original);
            cooparticlesapi$particleAlphaTestResolved = true;
            return cooparticlesapi$particleAlphaTest;
        }
    }

    /**
     * 根据原配置的比较函数构造低透明度阈值，不加载公共路径中的可选 Iris 类型。
     *
     * @param original Iris 返回的原始配置
     * @return 同一函数的新配置；缺失或不兼容时为空
     */
    @Unique
    private static Object cooparticlesapi$createParticleAlphaTest(Object original) {
        if (original == null) {
            return null;
        }
        try {
            Class<?> alphaTestClass = original.getClass();
            Method functionAccessor = alphaTestClass.getMethod("function");
            Object function = functionAccessor.invoke(original);
            Constructor<?> constructor = alphaTestClass.getConstructor(function.getClass(), float.class);
            // GREATER 比较使用前一个浮点数，保留恰好等于阈值的片元。
            return constructor.newInstance(function, Math.nextDown(0.001F));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
