package cn.coostack.cooparticlesapi.compat.iris;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderTypeIrisSupposerRegistry {
    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static final Set<String> RENDER_TYPE_NAMES = ConcurrentHashMap.newKeySet();
    private static final Set<String> SHADER_NAMES = ConcurrentHashMap.newKeySet();

    private RenderTypeIrisSupposerRegistry() {
    }

    public static void register(String renderTypeName, String shaderName) {
        if (renderTypeName != null && !renderTypeName.isBlank()) {
            RENDER_TYPE_NAMES.add(renderTypeName);
        }
        registerShader(shaderName);
    }

    public static void registerShader(String shaderName) {
        if (shaderName == null || shaderName.isBlank()) {
            return;
        }
        SHADER_NAMES.add(shaderName);
        if (shaderName.startsWith(MINECRAFT_NAMESPACE)) {
            SHADER_NAMES.add(shaderName.substring(MINECRAFT_NAMESPACE.length()));
        } else if (!shaderName.contains(":")) {
            SHADER_NAMES.add(MINECRAFT_NAMESPACE + shaderName);
        }
    }

    public static boolean shouldAllowShader(String shaderName) {
        return shaderName != null && SHADER_NAMES.contains(shaderName);
    }

    public static boolean isRegisteredRenderType(String renderTypeName) {
        return renderTypeName != null && RENDER_TYPE_NAMES.contains(renderTypeName);
    }

    public static Set<String> registeredRenderTypes() {
        return Collections.unmodifiableSet(RENDER_TYPE_NAMES);
    }

    public static Set<String> registeredShaders() {
        return Collections.unmodifiableSet(SHADER_NAMES);
    }
}
