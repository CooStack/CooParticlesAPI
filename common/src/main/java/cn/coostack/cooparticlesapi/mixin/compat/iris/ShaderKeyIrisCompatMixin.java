package cn.coostack.cooparticlesapi.mixin.compat.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderKey", remap = false)
public abstract class ShaderKeyIrisCompatMixin {
    @Unique
    private static final float cooparticlesapi$PARTICLE_ALPHA_THRESHOLD = 0.001F;
    @Unique
    private static volatile boolean cooparticlesapi$particleAlphaTestResolved;
    @Unique
    private static Object cooparticlesapi$particleAlphaTest;

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
            return constructor.newInstance(function, cooparticlesapi$PARTICLE_ALPHA_THRESHOLD);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
