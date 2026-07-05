package cn.coostack.cooparticlesapi.test.api

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.impl.ControlableFallingDustEffect
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import java.util.Locale
import java.util.UUID

object ControlableParticleEffectRegistry {
    private const val EFFECT_PACKAGE = "cn.coostack.cooparticlesapi.particles.impl"
    private const val DEFAULT_ID = "cooparticlesapi:controlable_end_rod"

    private val specialClassNames = mapOf(
        "cooparticlesapi:controlable_effect" to "$EFFECT_PACKAGE.ControlableEffectParticleEffect",
        "cooparticlesapi:controlable_instant_effect" to "$EFFECT_PACKAGE.ControlableInstantEffectParticleEffect"
    )

    private val fallingDustBuilder = ControlableParticleEffectBuilder(
        "cooparticlesapi:controlable_falling_dust"
    ) { uuid, faceToPlayer ->
        ControlableFallingDustEffect(uuid, Blocks.STONE.defaultBlockState(), faceToPlayer)
    }

    private val fixedFactories = mapOf(
        "cooparticlesapi:controlable_falling_dust" to fallingDustBuilder,
        "controlable_falling_dust" to fallingDustBuilder,
        "$EFFECT_PACKAGE.ControlableFallingDustEffect" to fallingDustBuilder,
        "ControlableFallingDustEffect" to fallingDustBuilder
    )

    fun defaultBuilder(): ControlableParticleEffectBuilder {
        return resolve(DEFAULT_ID) ?: error("Default ControlableParticleEffect is not registered: $DEFAULT_ID")
    }

    fun resolve(raw: String): ControlableParticleEffectBuilder? {
        val text = raw.trim()
        if (text.isBlank()) return null

        fixedFactories[text]?.let { return it }
        fixedFactories["$EFFECT_PACKAGE.$text"]?.let { return it }

        parseResourceLocation(text)?.let { location ->
            val id = location.toString()
            fixedFactories[id]?.let { return it }
            val className = specialClassNames[id] ?: resourceIdToClassName(location)
            reflectiveBuilder(className, id)?.let { return it }
        }

        if (text.contains('.')) {
            reflectiveBuilder(text, text)?.let { return it }
        }

        return reflectiveBuilder("$EFFECT_PACKAGE.$text", text)
    }

    fun suggestions(): List<String> {
        return CooModParticles.particleTypes
            .flatMap { registry ->
                val id = registry.id.toString()
                val path = registry.id.path
                val className = specialClassNames[id] ?: resourceIdToClassName(registry.id)
                listOf(id, path, className, className.substringAfterLast('.'))
            }
            .distinct()
            .sorted()
    }

    private fun parseResourceLocation(text: String): ResourceLocation? {
        return runCatching {
            if (text.contains(':')) {
                ResourceLocation.parse(text)
            } else {
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, text)
            }
        }.getOrNull()
    }

    private fun resourceIdToClassName(id: ResourceLocation): String {
        val classBody = id.path
            .removePrefix("controlable_")
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString("") { it.toPascalCase() }
        return "$EFFECT_PACKAGE.Controlable${classBody}Effect"
    }

    private fun String.toPascalCase(): String {
        return substring(0, 1).uppercase(Locale.ROOT) + substring(1)
    }

    private fun reflectiveBuilder(className: String, id: String): ControlableParticleEffectBuilder? {
        fixedFactories[className]?.let { return it }
        val type = runCatching {
            Class.forName(className).asSubclass(ControlableParticleEffect::class.java)
        }.getOrNull() ?: return null

        val uuidBooleanConstructor = runCatching {
            type.getConstructor(UUID::class.java, Boolean::class.javaPrimitiveType!!)
        }.getOrNull()
        if (uuidBooleanConstructor != null) {
            return ControlableParticleEffectBuilder(id) { uuid, faceToPlayer ->
                uuidBooleanConstructor.newInstance(uuid, faceToPlayer)
            }
        }

        val uuidConstructor = runCatching {
            type.getConstructor(UUID::class.java)
        }.getOrNull() ?: return null
        return ControlableParticleEffectBuilder(id) { uuid, _ ->
            uuidConstructor.newInstance(uuid)
        }
    }
}
