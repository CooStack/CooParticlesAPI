package cn.coostack.cooparticlesapi.coofx.asset

import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceProvider

fun interface CooFxResourceProvider {
    fun read(resource: Identifier): ByteArray
}

class MinecraftCooFxResourceProvider(
    private val resources: ResourceProvider,
) : CooFxResourceProvider {
    override fun read(resource: Identifier): ByteArray {
        val entry = resources.getResource(resource).orElseThrow {
            IllegalArgumentException("找不到资源：$resource")
        }
        return entry.open().use { it.readBytes() }
    }
}

internal fun resolveCooFxResource(base: Identifier, reference: String): Identifier? {
    if (reference.isBlank() || reference.contains('\\') || reference.startsWith('/')) {
        return null
    }
    val schemeSeparator = reference.indexOf(':')
    if (schemeSeparator >= 0) {
        val parsed = Identifier.tryParse(reference) ?: return null
        return if (hasSafePath(parsed.path)) parsed else null
    }
    if (!hasSafePath(reference)) {
        return null
    }
    val directory = base.path.substringBeforeLast('/', "")
    val path = if (directory.isEmpty()) reference else "$directory/$reference"
    return Identifier.tryParse("${base.namespace}:$path")
}

private fun hasSafePath(path: String): Boolean {
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
