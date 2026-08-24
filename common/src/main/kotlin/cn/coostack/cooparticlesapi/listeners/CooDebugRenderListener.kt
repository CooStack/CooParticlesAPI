package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundManager
import cn.coostack.cooparticlesapi.supports.sound.ManagedSoundInstance
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

@EventListener(CooParticlesConstants.MOD_ID)
object CooDebugRenderListener {
    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) return
        if (!Minecraft.getInstance().entityRenderDispatcher.shouldRenderHitBoxes()) return

        val camera = event.camera.position
        val partialTick = event.delta.getGameTimeDeltaPartialTick(true)
        val lines = event.buffer.getBuffer(RenderType.lines())
        ClientRenderEntityManager.debugEntities().forEach { entity ->
            renderEntity(event.poseStack, event.buffer, lines, camera, partialTick, entity)
        }
        ParticleEmittersManager.clientEmitters.values.forEach { emitter ->
            renderEmitter(event.poseStack, event.buffer, lines, camera, emitter)
        }
        ParticleCompositionManager.debugCompositions().forEach { composition ->
            renderComposition(event.poseStack, event.buffer, lines, camera, composition)
        }
        DisplayEntityManager.clientView.values.forEach { entity ->
            renderDisplayEntity(event.poseStack, event.buffer, lines, camera, partialTick, entity)
        }
        ClientSoundManager.activeSounds().forEach { sound ->
            renderSound(event.poseStack, event.buffer, lines, camera, sound)
        }
    }

    private fun renderEntity(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        lines: VertexConsumer,
        camera: Vec3,
        partialTick: Float,
        entity: RenderEntity,
    ) {
        val id = entity.getRenderID()
        val label = "${id.namespace}:${entity.javaClass.simpleName}"
        val transform = readTransform(entity)
        val details = listOf("age=${entity.age}") + transformDetails(transform, entity)
        val position = entity.lastRenderPos.lerp(entity.pos, partialTick.toDouble())
        renderObject(
            poseStack, buffer, lines, camera, position, label, details,
            0.95F, 0.2F, 0.2F, transform?.vector, readVector(entity, "axis")
        )
    }

    private fun renderEmitter(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        lines: VertexConsumer,
        camera: Vec3,
        emitter: ParticleEmitters,
    ) {
        val label = "${CooParticlesConstants.MOD_ID}:${emitter.javaClass.simpleName}"
        val transform = readTransform(emitter)
        val details = listOf("tick=${emitter.tick}/${emitter.maxTick}", "playing=${emitter.playing}") +
                transformDetails(transform, emitter)
        renderObject(
            poseStack, buffer, lines, camera, emitter.pos, label, details,
            0.95F, 0.85F, 0.15F, transform?.vector, readVector(emitter, "axis")
        )
    }

    private fun renderComposition(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        lines: VertexConsumer,
        camera: Vec3,
        composition: ParticleComposition,
    ) {
        val label = "${CooParticlesConstants.MOD_ID}:${composition.javaClass.simpleName}"
        val transform = readTransform(composition)
        val details = listOf(
            "scale=${format(composition.scale)}",
            "axis=${format(composition.axis.toVector())}",
        )
        renderObject(
            poseStack,
            buffer,
            lines,
            camera,
            composition.position,
            label,
            details,
            0.2F,
            0.9F,
            1.0F,
            transform?.vector,
            composition.axis.toVector()
        )
    }

    private fun renderDisplayEntity(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        lines: VertexConsumer,
        camera: Vec3,
        partialTick: Float,
        entity: DisplayEntity,
    ) {
        val position = entity.position(partialTick)
        val rotation = rotationVector(entity.yaw(partialTick), entity.pitch(partialTick), entity.roll(partialTick))
        val details = listOf(
            "rot=${format(entity.yaw(partialTick))},${format(entity.pitch(partialTick))},${format(entity.roll(partialTick))}",
            "rotVec=${format(rotation)}",
            "scale=${format(entity.scale(partialTick))}",
        ) + readVector(entity, "axis")?.let { listOf("axis=${format(it)}") }.orEmpty()
        renderObject(
            poseStack,
            buffer,
            lines,
            camera,
            position,
            "${CooParticlesConstants.MOD_ID}:${entity.javaClass.simpleName}",
            details,
            0.2F,
            1.0F,
            0.35F,
            rotation,
            readVector(entity, "axis")
        )
    }

    private fun renderSound(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        lines: VertexConsumer,
        camera: Vec3,
        sound: ManagedSoundInstance,
    ) {
        val position = sound.position
        val details = listOf(
            "key=${sound.key}",
            "sound=${sound.location}",
            "vol=${format(sound.volumeMultiplier)} pitch=${format(sound.pitchMultiplier)}",
        )
        renderObject(
            poseStack,
            buffer,
            lines,
            camera,
            position,
            "${sound.location.namespace}:${sound.javaClass.simpleName}",
            details,
            0.95F,
            0.3F,
            0.95F,
            null
        )
    }

    private fun renderObject(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        lines: VertexConsumer,
        camera: Vec3,
        position: Vec3,
        label: String,
        details: List<String>,
        red: Float,
        green: Float,
        blue: Float,
        rotation: Vec3?,
        axis: Vec3? = null,
    ) {
        val relative = position.subtract(camera)
        val box = AABB(
            relative.x - 0.05,
            relative.y - 0.05,
            relative.z - 0.05,
            relative.x + 0.05,
            relative.y + 0.05,
            relative.z + 0.05,
        )
        DebugRenderer.renderFilledBox(poseStack, buffer, box, red, green, blue, 0.75F)
        DebugRenderer.renderFloatingText(
            poseStack, buffer, label, position.x, position.y + 0.14, position.z, 0xFFFFFFFF.toInt()
        )
        details.forEachIndexed { index, detail ->
            DebugRenderer.renderFloatingText(
                poseStack,
                buffer,
                detail,
                position.x,
                position.y + 0.02 - index * 0.1,
                position.z,
                0xFFB8B8B8.toInt(),
            )
        }
        rotation?.let { drawVector(poseStack, lines, position, it, camera, 0.75F, 0.2F, 0.2F) }
        axis?.let { drawVector(poseStack, lines, position, it, camera, 0.2F, 0.9F, 1.0F) }
    }

    private fun drawVector(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        origin: Vec3,
        direction: Vec3,
        camera: Vec3,
        red: Float,
        green: Float,
        blue: Float,
    ) {
        val normalized = if (direction.lengthSqr() <= 1.0E-8) return else direction.normalize()
        val start = origin.subtract(camera)
        val end = origin.add(normalized.scale(0.75)).subtract(camera)
        val delta = end.subtract(start)
        val normal = delta.normalize()
        val pose = poseStack.last().pose()
        consumer.addVertex(pose, start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
            .setColor(red, green, blue, 1.0F)
            .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
        consumer.addVertex(pose, end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
            .setColor(red, green, blue, 1.0F)
            .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    private fun transformDetails(transform: TransformInfo?, target: Any): List<String> {
        val rotationDetails = if (transform != null) {
            val vector = transform.vector
            listOf(
                "rot=${format(transform.yaw)},${format(transform.pitch)},${format(transform.roll)}",
                "rotVec=${format(vector)}",
            )
        } else emptyList()
        val axis = readVector(target, "axis")
        return if (axis == null) rotationDetails else rotationDetails + "axis=${format(axis)}"
    }

    private fun readTransform(target: Any): TransformInfo? {
        val yaw = readNumber(target, "yaw")
        val pitch = readNumber(target, "pitch")
        val roll = readNumber(target, "roll")
        if (yaw == null && pitch == null && roll == null) return null
        val resolvedYaw = yaw ?: 0.0
        val resolvedPitch = pitch ?: 0.0
        val resolvedRoll = roll ?: 0.0
        return TransformInfo(
            resolvedYaw,
            resolvedPitch,
            resolvedRoll,
            rotationVector(resolvedYaw.toFloat(), resolvedPitch.toFloat(), resolvedRoll.toFloat()),
        )
    }

    private fun readNumber(target: Any, property: String): Double? {
        val methodName = "get${property.replaceFirstChar { it.uppercase() }}"
        val getter = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
        if (getter != null) {
            return (runCatching { getter.invoke(target) }.getOrNull() as? Number)?.toDouble()
        }
        return (readField(target, property) as? Number)?.toDouble()
    }

    private fun rotationVector(yaw: Float, pitch: Float, roll: Float): Vec3 {
        val rotation = Quaternionf()
            .rotateY(-yaw * PI.toFloat() / 180.0F)
            .rotateX(-pitch * PI.toFloat() / 180.0F)
            .rotateZ(roll * PI.toFloat() / 180.0F)
        val direction = Vector3f(0.0F, 0.0F, 1.0F).rotate(rotation)
        return Vec3(direction.x.toDouble(), direction.y.toDouble(), direction.z.toDouble())
    }

    private fun readVector(target: Any, property: String): Vec3? {
        val methodName = "get${property.replaceFirstChar { it.uppercase() }}"
        val getter = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
        val value = if (getter != null) {
            runCatching { getter.invoke(target) }.getOrNull()
        } else {
            readField(target, property)
        } ?: return null
        if (value is Vec3) return value
        return runCatching {
            val x = value.javaClass.methods.first { it.name == "getX" && it.parameterCount == 0 }
                .invoke(value) as Number
            val y = value.javaClass.methods.first { it.name == "getY" && it.parameterCount == 0 }
                .invoke(value) as Number
            val z = value.javaClass.methods.first { it.name == "getZ" && it.parameterCount == 0 }
                .invoke(value) as Number
            Vec3(x.toDouble(), y.toDouble(), z.toDouble())
        }.getOrNull()
    }

    private fun readField(target: Any, property: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == property }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun format(value: Number): String = "%.2f".format(Locale.ROOT, value.toDouble())

    private fun format(value: Vec3): String {
        return "(${format(value.x)},${format(value.y)},${format(value.z)})"
    }

    private data class TransformInfo(
        val yaw: Double,
        val pitch: Double,
        val roll: Double,
        val vector: Vec3,
    )
}
