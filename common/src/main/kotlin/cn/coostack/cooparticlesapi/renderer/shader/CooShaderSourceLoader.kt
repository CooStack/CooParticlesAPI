package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceProvider

internal object CooShaderSourceLoader {
    fun load(resources: ResourceProvider, source: ResourceLocation): String {
        return preprocess(source) { location ->
            resources.getResource(location)
                .orElseThrow { IllegalArgumentException("Coo shader source does not exist: $location") }
                .open()
                .use { stream -> stream.readBytes().decodeToString() }
        }
    }

    internal fun preprocess(
        source: ResourceLocation,
        read: (ResourceLocation) -> String
    ): String {
        return preprocess(source, linkedSetOf(), read)
    }

    private fun preprocess(
        source: ResourceLocation,
        imports: MutableSet<ResourceLocation>,
        read: (ResourceLocation) -> String
    ): String {
        check(imports.add(source)) { "Coo shader import cycle: $source" }
        return try {
            read(source).lineSequence().joinToString("\n") { line ->
                importPath(line)?.let { path ->
                    preprocess(includeLocation(path), imports, read)
                } ?: line
            }
        } finally {
            imports.remove(source)
        }
    }

    private fun importPath(line: String): String? {
        val import = line.trim().removePrefix("#coo_import").trim().takeIf { line.trim().startsWith("#coo_import") }
            ?: return null
        require(import.startsWith('<') && import.endsWith('>')) {
            "Coo shader import must use <path>: $line"
        }
        return import.substring(1, import.lastIndex).trim().also { path ->
            require(path.isNotEmpty()) { "Coo shader import path must not be empty" }
        }
    }

    private fun includeLocation(path: String): ResourceLocation {
        if (':' in path) {
            return requireNotNull(ResourceLocation.tryParse(path)) {
                "Invalid Coo shader import: $path"
            }.let { include ->
                ResourceLocation.fromNamespaceAndPath(include.namespace, "shader/${include.path}")
            }
        }
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "shader/include/$path")
    }
}
