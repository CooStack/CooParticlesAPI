package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.barrages.Barrage
import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.coofx.client.CooFXClient
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneRenderEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.emitters.simple.BoxPresetEmitter
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectRegistry
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegion
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegistry
import cn.coostack.cooparticlesapi.supports.sound.ClientSoundManager
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 原版 F3+B 打开判定箱显示时，为 CooParticlesAPI 的客户端资产补齐调试线框。
 *
 * 覆盖 RenderEntity(含 CooFX scene)、Emitters、Composition、DisplayEntity、托管音频、
 * CooFX 模型实例、Terrain 效果组与 Terrain mapping，以及单机存档服务端侧的弹幕碰撞盒。
 * 单个 CParticle/ControlableParticle 数量级太大，不在这里绘制。
 *
 * 绘制顺序不能随意调整：1.21.1 的 `MultiBufferSource.BufferSource` 对使用共享缓冲区的 RenderType
 * 只保留一个 builder，请求另一个共享 RenderType 会立刻结算上一批并让旧的 [VertexConsumer] 失效
 * (继续写入会抛 `Not building!`)。因此这里先收集条目，再按 填充盒 -> 文本 -> 线框 的顺序分批提交，
 * [RenderType.lines] 最后取用且一次性写完，同时保持与原版一致的 lines 批次结算时机。
 *
 * 必须声明 [DistType.CLIENT]：本监听器的方法签名直接引用 `PoseStack`、`MultiBufferSource` 等
 * 客户端类，若在专用服务端被登记，`CooEventBus.initListeners` 反射读取方法表时会抛
 * `NoClassDefFoundError`，且该处没有异常兜底。
 */
@EventListener(CooParticlesConstants.MOD_ID, dist = DistType.CLIENT)
object CooDebugRenderListener {
    /** 超出该距离的资产完全不绘制。 */
    private const val MAX_RENDER_DISTANCE = 96.0

    /** 超出该距离只保留标签，不绘制明细文本和实心标记。 */
    private const val MAX_DETAIL_DISTANCE = 32.0

    /** 每个分类每帧最多绘制的条目数，避免资产密集时文本压垮渲染线程。 */
    private const val MAX_ENTRIES_PER_CATEGORY = 32

    /** 每个 Terrain 效果组每帧最多绘制的方块线框数。 */
    private const val MAX_TERRAIN_BLOCKS = 256

    /** 点状资产的标记半径，以及标记开始随距离放大的参考距离。 */
    private const val MARKER_HALF_SIZE = 0.06
    private const val MARKER_REFERENCE_DISTANCE = 16.0

    /** 朝向指示线长度。 */
    private const val VECTOR_LENGTH = 0.75

    /** 球形/圆柱区域线框的分段数。 */
    private const val CIRCLE_SEGMENTS = 32

    private const val LABEL_OFFSET = 0.14
    private const val DETAIL_OFFSET = 0.02
    private const val DETAIL_LINE_HEIGHT = 0.1
    private const val LABEL_COLOR = 0xFFFFFFFF.toInt()
    private const val DETAIL_COLOR = 0xFFB8B8B8.toInt()

    private val COLOR_RENDER_ENTITY = DebugColor(0.95F, 0.2F, 0.2F)
    private val COLOR_COOFX_SCENE = DebugColor(1.0F, 0.55F, 0.1F)
    private val COLOR_COOFX_MODEL = DebugColor(1.0F, 0.85F, 0.45F)
    private val COLOR_EMITTER = DebugColor(0.95F, 0.85F, 0.15F)
    private val COLOR_COMPOSITION = DebugColor(0.2F, 0.9F, 1.0F)
    private val COLOR_DISPLAY_ENTITY = DebugColor(0.2F, 1.0F, 0.35F)
    private val COLOR_SOUND = DebugColor(0.95F, 0.3F, 0.95F)
    private val COLOR_TERRAIN_GROUP = DebugColor(0.45F, 0.8F, 0.35F)
    private val COLOR_TERRAIN_UNRESOLVED = DebugColor(0.6F, 0.6F, 0.6F)
    private val COLOR_TERRAIN_MAPPING = DebugColor(0.35F, 0.6F, 1.0F)
    private val COLOR_BARRAGE = DebugColor(1.0F, 0.35F, 0.35F)
    private val COLOR_BARRAGE_NOCLIP = DebugColor(0.7F, 0.65F, 0.35F)
    private val COLOR_ROTATION = DebugColor(0.75F, 0.2F, 0.2F)
    private val COLOR_AXIS = DebugColor(0.2F, 0.9F, 1.0F)

    /** 反射读取器缓存，避免每帧重复扫描方法表和字段表。 */
    private val propertyReaders = ConcurrentHashMap<PropertyKey, (Any) -> Any?>()

    private val missingReader: (Any) -> Any? = { null }

    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) return
        val client = Minecraft.getInstance()
        if (!client.entityRenderDispatcher.shouldRenderHitBoxes()) return
        val level = client.level ?: return

        val camera = event.camera.position
        val partialTick = event.delta.getGameTimeDeltaPartialTick(true)
        val entries = ArrayList<DebugEntry>()
        collectRenderEntities(entries, level, camera, partialTick)
        collectEmitters(entries, level, camera)
        collectCompositions(entries, level, camera)
        collectDisplayEntities(entries, level, camera, partialTick)
        collectSounds(entries, camera)
        collectCooFxModels(entries, camera)
        collectTerrainGroups(entries, level, camera)
        collectTerrainMappings(entries, level, camera)
        collectBarrages(entries, client, level, camera)
        if (entries.isEmpty()) return

        drawMarkers(event.poseStack, event.buffer, camera, entries)
        drawTexts(event.poseStack, event.buffer, entries)
        drawShapes(event.poseStack, event.buffer, camera, entries)
    }

    private fun collectRenderEntities(
        out: MutableList<DebugEntry>,
        level: ClientLevel,
        camera: Vec3,
        partialTick: Float,
    ) {
        ClientRenderEntityManager.debugEntities().asSequence()
            .filter { entity -> entity.world == null || entity.world === level }
            .map { entity -> entity to entity.lastRenderPos.lerp(entity.pos, partialTick.toDouble()) }
            .filter { (_, position) -> inRange(position, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { (entity, position) ->
                val transform = readTransform(entity)
                val scene = entity as? CooFxSceneRenderEntity
                val details = ArrayList<String>()
                details += "age=${entity.age}"
                details += "range=${format(entity.renderRange)} canceled=${entity.canceled}"
                details += transformDetails(transform, entity)
                if (scene != null) {
                    details += "resource=${scene.resourceIdText.ifBlank { "-" }}"
                    details += "mode=${scene.mode()} clip=${scene.clipIdText.ifBlank { "-" }}"
                    details += "emitter=${scene.emitterId() ?: "-"} speed=${format(scene.playbackSpeed)}"
                    details += "scale=${format(scene.scaleX)},${format(scene.scaleY)},${format(scene.scaleZ)}"
                }
                out += DebugEntry(
                    position = position,
                    label = "${entity.getRenderID().namespace}:${entity.javaClass.simpleName}",
                    details = details,
                    color = if (scene != null) COLOR_COOFX_SCENE else COLOR_RENDER_ENTITY,
                    detailed = detailed(position, camera),
                    segments = orientationSegments(position, transform?.vector, readVector(entity, "axis")),
                )
            }
    }

    private fun collectEmitters(out: MutableList<DebugEntry>, level: ClientLevel, camera: Vec3) {
        ParticleEmittersManager.clientEmitters.values.asSequence()
            .filter { emitter -> emitter.world == null || emitter.world === level }
            .filter { emitter -> inRange(emitter.pos, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { emitter ->
                val transform = readTransform(emitter)
                val details = ArrayList<String>()
                details += "tick=${emitter.tick}/${emitter.maxTick} delay=${emitter.delay}"
                details += "playing=${emitter.playing} canceled=${emitter.canceled}"
                details += transformDetails(transform, emitter)
                val boxes = if (emitter is BoxPresetEmitter) {
                    details += "boxSize=${format(emitter.boxSize)}"
                    listOf(volumeBox(emitter.pos, emitter.boxSize))
                } else {
                    emptyList()
                }
                out += DebugEntry(
                    position = emitter.pos,
                    label = "${CooParticlesConstants.MOD_ID}:${emitter.javaClass.simpleName}",
                    details = details,
                    color = COLOR_EMITTER,
                    detailed = detailed(emitter.pos, camera),
                    boxes = boxes,
                    segments = orientationSegments(emitter.pos, transform?.vector, readVector(emitter, "axis")),
                )
            }
    }

    private fun collectCompositions(out: MutableList<DebugEntry>, level: ClientLevel, camera: Vec3) {
        ParticleCompositionManager.debugCompositions().asSequence()
            .filter { composition -> composition.world == null || composition.world === level }
            .filter { composition -> inRange(composition.position, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { composition ->
                val axis = composition.axis.toVector()
                val details = listOf(
                    "scale=${format(composition.scale)} roll=${format(composition.roll)}",
                    "axis=${format(axis)}",
                    "particles=${composition.particles.size}",
                    "displayed=${composition.displayed} canceled=${composition.canceled}",
                )
                out += DebugEntry(
                    position = composition.position,
                    label = "${CooParticlesConstants.MOD_ID}:${composition.javaClass.simpleName}",
                    details = details,
                    color = COLOR_COMPOSITION,
                    detailed = detailed(composition.position, camera),
                    segments = orientationSegments(composition.position, null, axis),
                )
            }
    }

    private fun collectDisplayEntities(
        out: MutableList<DebugEntry>,
        level: ClientLevel,
        camera: Vec3,
        partialTick: Float,
    ) {
        DisplayEntityManager.clientView.values.asSequence()
            .filter { entity -> entity.world == null || entity.world === level }
            .map { entity -> entity to entity.position(partialTick) }
            .filter { (_, position) -> inRange(position, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { (entity, position) ->
                val yaw = entity.yaw(partialTick)
                val pitch = entity.pitch(partialTick)
                val roll = entity.roll(partialTick)
                val rotation = rotationVector(yaw, pitch, roll)
                val details = listOf(
                    "rot=${format(yaw)},${format(pitch)},${format(roll)}",
                    "rotVec=${format(rotation)}",
                    "scale=${format(entity.scale(partialTick))}",
                )
                out += DebugEntry(
                    position = position,
                    label = "${CooParticlesConstants.MOD_ID}:${entity.javaClass.simpleName}",
                    details = details,
                    color = COLOR_DISPLAY_ENTITY,
                    detailed = detailed(position, camera),
                    segments = orientationSegments(position, rotation, readVector(entity, "axis")),
                )
            }
    }

    private fun collectSounds(out: MutableList<DebugEntry>, camera: Vec3) {
        ClientSoundManager.activeSounds().asSequence()
            .filter { sound -> inRange(sound.position, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { sound ->
                val details = listOf(
                    "key=${sound.key}",
                    "sound=${sound.location}",
                    "vol=${format(sound.volumeMultiplier)} pitch=${format(sound.pitchMultiplier)}",
                    "looping=${sound.loopingSound} relative=${sound.relativeSound} stopped=${sound.isStopped}",
                    "entityId=${sound.entityId}",
                )
                out += DebugEntry(
                    position = sound.position,
                    label = "${sound.location.namespace}:${sound.javaClass.simpleName}",
                    details = details,
                    color = COLOR_SOUND,
                    detailed = detailed(sound.position, camera),
                )
            }
    }

    private fun collectCooFxModels(out: MutableList<DebugEntry>, camera: Vec3) {
        CooFXClient.debugModelInstances().asSequence()
            .map { instance -> instance to Vec3(instance.transform.x, instance.transform.y, instance.transform.z) }
            .filter { (_, position) -> inRange(position, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { (instance, position) ->
                val transform = instance.transform
                val forward = Vector3f(0.0F, 0.0F, 1.0F)
                    .rotate(
                        Quaternionf(
                            transform.rotationX,
                            transform.rotationY,
                            transform.rotationZ,
                            transform.rotationW,
                        )
                    )
                val details = listOf(
                    "model=${instance.resourceId}",
                    "instance=${instance.instanceId} draws=${instance.drawCount}",
                    "clip=${instance.clipIndex} age=${instance.ageTicks} speed=${format(instance.playbackSpeed)}",
                    "scale=${format(transform.scaleX)},${format(transform.scaleY)},${format(transform.scaleZ)}",
                )
                out += DebugEntry(
                    position = position,
                    label = "${CooParticlesConstants.MOD_ID}:CooFxModelInstance",
                    details = details,
                    color = COLOR_COOFX_MODEL,
                    detailed = detailed(position, camera),
                    segments = orientationSegments(
                        position,
                        Vec3(forward.x.toDouble(), forward.y.toDouble(), forward.z.toDouble()),
                        null,
                    ),
                )
            }
    }

    private fun collectTerrainGroups(out: MutableList<DebugEntry>, level: ClientLevel, camera: Vec3) {
        val dimension = level.dimension().location()
        val gameTime = level.gameTime
        CooTerrainEffectRegistry.debugGroups(dimension).asSequence()
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { group ->
                val snapshot = group.snapshot
                val active = ArrayList<AABB>()
                val waiting = ArrayList<AABB>()
                var anchor: Vec3? = null
                var anchorDistance = Double.MAX_VALUE
                snapshot.activations.forEach { (position, activationTick) ->
                    val center = Vec3(position.x + 0.5, position.y + 0.5, position.z + 0.5)
                    val distance = center.distanceToSqr(camera)
                    if (distance > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) return@forEach
                    if (distance < anchorDistance) {
                        anchorDistance = distance
                        anchor = center
                    }
                    val box = AABB(position).deflate(0.01)
                    if (group.resolved && gameTime >= activationTick) {
                        if (active.size < MAX_TERRAIN_BLOCKS) active += box
                    } else if (waiting.size < MAX_TERRAIN_BLOCKS) {
                        waiting += box
                    }
                }
                val labelPosition = anchor?.add(0.0, 0.6, 0.0) ?: return@forEach
                val details = listOf(
                    "group=${snapshot.id}",
                    "pipeline=${snapshot.pipelineId}",
                    "blocks=${snapshot.activations.size} active=${active.size} waiting=${waiting.size}",
                    "resolved=${group.resolved} prio=${snapshot.priority} comp=${snapshot.composition}",
                    "started=${snapshot.startedAt} expires=${snapshot.expiresAt ?: "-"} now=$gameTime",
                )
                out += DebugEntry(
                    position = labelPosition,
                    label = "${CooParticlesConstants.MOD_ID}:CooTerrainEffectGroup",
                    details = details,
                    color = if (group.resolved) COLOR_TERRAIN_GROUP else COLOR_TERRAIN_UNRESOLVED,
                    detailed = detailed(labelPosition, camera),
                    boxes = active,
                )
                if (waiting.isNotEmpty()) {
                    out += DebugEntry(
                        position = labelPosition,
                        label = "",
                        details = emptyList(),
                        color = COLOR_TERRAIN_UNRESOLVED,
                        detailed = false,
                        marker = false,
                        boxes = waiting,
                    )
                }
            }
    }

    private fun collectTerrainMappings(out: MutableList<DebugEntry>, level: ClientLevel, camera: Vec3) {
        val dimension = level.dimension().location()
        val gameTime = level.gameTime
        CooTerrainMappingRegistry.debugMappings(dimension).asSequence()
            .map { mapping -> mapping to regionCenter(mapping.region) }
            .filter { (_, center) -> inRange(center, camera) }
            .take(MAX_ENTRIES_PER_CATEGORY)
            .forEach { (mapping, center) ->
                val expired = mapping.expiresAt?.let { gameTime >= it } == true
                val details = listOf(
                    "instance=${mapping.instanceId}",
                    "mapping=${mapping.mappingId}",
                    "region=${regionDescription(mapping.region)}",
                    "prio=${mapping.priority} comp=${mapping.composition}",
                    "paused=${mapping.isPaused()} expired=$expired expires=${mapping.expiresAt ?: "-"} now=$gameTime",
                )
                val color = if (mapping.isPaused() || expired) COLOR_TERRAIN_UNRESOLVED else COLOR_TERRAIN_MAPPING
                out += DebugEntry(
                    position = center,
                    label = "${CooParticlesConstants.MOD_ID}:CooTerrainMapping",
                    details = details,
                    color = color,
                    detailed = detailed(center, camera),
                    boxes = regionBoxes(mapping.region),
                    segments = regionSegments(mapping.region, color),
                )
            }
    }

    /**
     * 收集单机存档服务端侧的弹幕。
     *
     * 弹幕只存在于逻辑服务端，多人客户端没有镜像数据；这里读取集成服务端的实时状态，
     * 因此只读取逻辑线程已经算好的字段，用 [cn.coostack.cooparticlesapi.utils.storage.Memo.peek]
     * 避免在渲染线程触发碰撞盒计算。
     */
    private fun collectBarrages(
        out: MutableList<DebugEntry>,
        client: Minecraft,
        level: ClientLevel,
        camera: Vec3,
    ) {
        val server = client.singleplayerServer ?: return
        val serverLevel = server.getLevel(level.dimension()) ?: return
        val collected = ArrayList<Barrage>()
        runCatching {
            BarrageManager.forEach(serverLevel) { barrage ->
                if (collected.size < MAX_ENTRIES_PER_CATEGORY && barrage.valid && inRange(barrage.loc, camera)) {
                    collected += barrage
                }
            }
        }
        collected.forEach { barrage ->
            val hitBox = barrage.hitBox.peek()
            val noclip = barrage.noclip()
            val details = listOf(
                "uuid=${barrage.uuid.toString().take(8)}",
                "dir=${format(barrage.direction)}",
                "noclip=$noclip lunch=${barrage.lunch}",
                "shooter=${barrage.shooter?.name?.string ?: "-"}",
                "hitBox=" + (hitBox?.let { box ->
                    "${format(box.x2 - box.x1)}x${format(box.y2 - box.y1)}x${format(box.z2 - box.z1)}"
                } ?: "pending"),
            )
            out += DebugEntry(
                position = barrage.loc,
                label = "${CooParticlesConstants.MOD_ID}:${barrage.javaClass.simpleName}",
                details = details,
                color = if (noclip) COLOR_BARRAGE_NOCLIP else COLOR_BARRAGE,
                detailed = detailed(barrage.loc, camera),
                boxes = hitBox?.let { listOf(it.ofBox(barrage.loc)) }.orEmpty(),
                segments = orientationSegments(barrage.loc, barrage.direction, null),
            )
        }
    }

    /** 绘制点状资产的实心标记，只在近距离绘制以控制共享缓冲区的批次数量。 */
    private fun drawMarkers(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        camera: Vec3,
        entries: List<DebugEntry>,
    ) {
        entries.forEach { entry ->
            if (!entry.marker || !entry.detailed) return@forEach
            val box = markerBox(entry.position, camera).move(-camera.x, -camera.y, -camera.z)
            DebugRenderer.renderFilledBox(
                poseStack, buffer, box, entry.color.red, entry.color.green, entry.color.blue, 0.65F
            )
        }
    }

    private fun drawTexts(poseStack: PoseStack, buffer: MultiBufferSource, entries: List<DebugEntry>) {
        entries.forEach { entry ->
            val position = entry.position
            if (entry.label.isNotEmpty()) {
                DebugRenderer.renderFloatingText(
                    poseStack, buffer, entry.label,
                    position.x, position.y + LABEL_OFFSET, position.z, LABEL_COLOR,
                )
            }
            if (!entry.detailed) return@forEach
            entry.details.forEachIndexed { index, detail ->
                DebugRenderer.renderFloatingText(
                    poseStack,
                    buffer,
                    detail,
                    position.x,
                    position.y + DETAIL_OFFSET - index * DETAIL_LINE_HEIGHT,
                    position.z,
                    DETAIL_COLOR,
                )
            }
        }
    }

    /**
     * 一次性写完全部线框。
     *
     * [RenderType.lines] 使用共享缓冲区，必须在取得 consumer 之后不再请求其他 RenderType。
     */
    private fun drawShapes(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        camera: Vec3,
        entries: List<DebugEntry>,
    ) {
        val consumer = buffer.getBuffer(RenderType.lines())
        entries.forEach { entry ->
            if (entry.marker) {
                drawBox(poseStack, consumer, markerBox(entry.position, camera), camera, entry.color)
            }
            entry.boxes.forEach { box -> drawBox(poseStack, consumer, box, camera, entry.color) }
            entry.segments.forEach { segment -> drawSegment(poseStack, consumer, segment, camera) }
        }
    }

    private fun drawBox(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        box: AABB,
        camera: Vec3,
        color: DebugColor,
    ) {
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            box.move(-camera.x, -camera.y, -camera.z),
            color.red,
            color.green,
            color.blue,
            1.0F,
        )
    }

    private fun drawSegment(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        segment: DebugSegment,
        camera: Vec3,
    ) {
        val start = segment.from.subtract(camera)
        val end = segment.to.subtract(camera)
        val delta = end.subtract(start)
        if (delta.lengthSqr() <= 1.0E-8) return
        val normal = delta.normalize()
        val pose = poseStack.last().pose()
        consumer.addVertex(pose, start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
            .setColor(segment.color.red, segment.color.green, segment.color.blue, 1.0F)
            .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
        consumer.addVertex(pose, end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
            .setColor(segment.color.red, segment.color.green, segment.color.blue, 1.0F)
            .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    /** 朝向线：旋转向量为红色，自转轴为青色。 */
    private fun orientationSegments(origin: Vec3, rotation: Vec3?, axis: Vec3?): List<DebugSegment> {
        val segments = ArrayList<DebugSegment>(2)
        rotation?.let { vector -> vectorSegment(origin, vector, COLOR_ROTATION)?.let { segments += it } }
        axis?.let { vector -> vectorSegment(origin, vector, COLOR_AXIS)?.let { segments += it } }
        return segments
    }

    private fun vectorSegment(origin: Vec3, direction: Vec3, color: DebugColor): DebugSegment? {
        if (direction.lengthSqr() <= 1.0E-8) return null
        return DebugSegment(origin, origin.add(direction.normalize().scale(VECTOR_LENGTH)), color)
    }

    private fun markerBox(position: Vec3, camera: Vec3): AABB {
        val scale = (position.distanceTo(camera) / MARKER_REFERENCE_DISTANCE).coerceAtLeast(1.0)
        val half = MARKER_HALF_SIZE * scale
        return AABB(
            position.x - half,
            position.y - half,
            position.z - half,
            position.x + half,
            position.y + half,
            position.z + half,
        )
    }

    /** [size] 是完整边长，与 `PointsBuilder.addCubeSolid` 的取值一致。 */
    private fun volumeBox(center: Vec3, size: Vec3): AABB = AABB(
        center.x - size.x / 2.0,
        center.y - size.y / 2.0,
        center.z - size.z / 2.0,
        center.x + size.x / 2.0,
        center.y + size.y / 2.0,
        center.z + size.z / 2.0,
    )

    private fun regionCenter(region: CooTerrainMappingRegion): Vec3 = when (region) {
        is CooTerrainMappingRegion.Sphere -> region.center
        is CooTerrainMappingRegion.Box -> region.center
        is CooTerrainMappingRegion.Cylinder -> region.center
    }

    private fun regionDescription(region: CooTerrainMappingRegion): String = when (region) {
        is CooTerrainMappingRegion.Sphere -> "sphere r=${format(region.radius)}"
        is CooTerrainMappingRegion.Box -> "box half=${format(region.halfExtents)}"
        is CooTerrainMappingRegion.Cylinder -> "cylinder r=${format(region.radius)} h=${format(region.height)}"
    }

    /** 长方体区域直接用线框盒表示，其余形状交给 [regionSegments]。 */
    private fun regionBoxes(region: CooTerrainMappingRegion): List<AABB> = when (region) {
        is CooTerrainMappingRegion.Box -> listOf(
            AABB(
                region.center.x - region.halfExtents.x,
                region.center.y - region.halfExtents.y,
                region.center.z - region.halfExtents.z,
                region.center.x + region.halfExtents.x,
                region.center.y + region.halfExtents.y,
                region.center.z + region.halfExtents.z,
            )
        )

        else -> emptyList()
    }

    /** 球体画三个大圆，圆柱画上下两个圆加四条母线。 */
    private fun regionSegments(region: CooTerrainMappingRegion, color: DebugColor): List<DebugSegment> {
        val segments = ArrayList<DebugSegment>()
        when (region) {
            is CooTerrainMappingRegion.Sphere -> {
                appendCircle(segments, region.center, region.radius, Plane.XZ, color)
                appendCircle(segments, region.center, region.radius, Plane.XY, color)
                appendCircle(segments, region.center, region.radius, Plane.YZ, color)
            }

            is CooTerrainMappingRegion.Cylinder -> {
                val halfHeight = region.height / 2.0
                val bottom = region.center.add(0.0, -halfHeight, 0.0)
                val top = region.center.add(0.0, halfHeight, 0.0)
                appendCircle(segments, bottom, region.radius, Plane.XZ, color)
                appendCircle(segments, top, region.radius, Plane.XZ, color)
                for (index in 0 until 4) {
                    val angle = index * PI / 2.0
                    val offsetX = cos(angle) * region.radius
                    val offsetZ = sin(angle) * region.radius
                    segments += DebugSegment(
                        bottom.add(offsetX, 0.0, offsetZ),
                        top.add(offsetX, 0.0, offsetZ),
                        color,
                    )
                }
            }

            is CooTerrainMappingRegion.Box -> Unit
        }
        return segments
    }

    private fun appendCircle(
        out: MutableList<DebugSegment>,
        center: Vec3,
        radius: Double,
        plane: Plane,
        color: DebugColor,
    ) {
        var previous = circlePoint(center, radius, plane, 0.0)
        for (index in 1..CIRCLE_SEGMENTS) {
            val angle = index.toDouble() / CIRCLE_SEGMENTS * 2.0 * PI
            val current = circlePoint(center, radius, plane, angle)
            out += DebugSegment(previous, current, color)
            previous = current
        }
    }

    private fun circlePoint(center: Vec3, radius: Double, plane: Plane, angle: Double): Vec3 {
        val first = cos(angle) * radius
        val second = sin(angle) * radius
        return when (plane) {
            Plane.XZ -> center.add(first, 0.0, second)
            Plane.XY -> center.add(first, second, 0.0)
            Plane.YZ -> center.add(0.0, first, second)
        }
    }

    private fun inRange(position: Vec3, camera: Vec3): Boolean =
        position.distanceToSqr(camera) <= MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE

    private fun detailed(position: Vec3, camera: Vec3): Boolean =
        position.distanceToSqr(camera) <= MAX_DETAIL_DISTANCE * MAX_DETAIL_DISTANCE

    private fun transformDetails(transform: TransformInfo?, target: Any): List<String> {
        val rotationDetails = if (transform != null) {
            listOf(
                "rot=${format(transform.yaw)},${format(transform.pitch)},${format(transform.roll)}",
                "rotVec=${format(transform.vector)}",
            )
        } else {
            emptyList()
        }
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

    private fun rotationVector(yaw: Float, pitch: Float, roll: Float): Vec3 {
        val rotation = Quaternionf()
            .rotateY(-yaw * PI.toFloat() / 180.0F)
            .rotateX(-pitch * PI.toFloat() / 180.0F)
            .rotateZ(roll * PI.toFloat() / 180.0F)
        val direction = Vector3f(0.0F, 0.0F, 1.0F).rotate(rotation)
        return Vec3(direction.x.toDouble(), direction.y.toDouble(), direction.z.toDouble())
    }

    private fun readNumber(target: Any, property: String): Double? =
        (readProperty(target, property) as? Number)?.toDouble()

    private fun readVector(target: Any, property: String): Vec3? {
        val value = readProperty(target, property) ?: return null
        if (value is Vec3) return value
        val x = readNumber(value, "x") ?: return null
        val y = readNumber(value, "y") ?: return null
        val z = readNumber(value, "z") ?: return null
        return Vec3(x, y, z)
    }

    /** 按 getter 优先、字段兜底的顺序读取可选属性，读取器按类型缓存。 */
    private fun readProperty(target: Any, property: String): Any? {
        val reader = propertyReaders.getOrPut(PropertyKey(target.javaClass, property)) {
            resolveReader(target.javaClass, property)
        }
        return runCatching { reader(target) }.getOrNull()
    }

    private fun resolveReader(type: Class<*>, property: String): (Any) -> Any? {
        val getterName = "get${property.replaceFirstChar { it.uppercase() }}"
        type.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }?.let { method ->
            runCatching { method.isAccessible = true }
            return { target -> method.invoke(target) }
        }
        var current: Class<*>? = type
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == property }?.let { field ->
                runCatching { field.isAccessible = true }
                return { target -> field.get(target) }
            }
            current = current.superclass
        }
        return missingReader
    }

    private fun format(value: Number): String = "%.2f".format(Locale.ROOT, value.toDouble())

    private fun format(value: Vec3): String = "(${format(value.x)},${format(value.y)},${format(value.z)})"

    /** 单帧收集的一个调试对象。世界坐标保持绝对值，减去相机位置只发生在提交顶点时。 */
    private class DebugEntry(
        val position: Vec3,
        val label: String,
        val details: List<String>,
        val color: DebugColor,
        val detailed: Boolean,
        val marker: Boolean = true,
        val boxes: List<AABB> = emptyList(),
        val segments: List<DebugSegment> = emptyList(),
    )

    private class DebugSegment(val from: Vec3, val to: Vec3, val color: DebugColor)

    private class DebugColor(val red: Float, val green: Float, val blue: Float)

    private enum class Plane { XZ, XY, YZ }

    private data class PropertyKey(val type: Class<*>, val property: String)

    private data class TransformInfo(
        val yaw: Double,
        val pitch: Double,
        val roll: Double,
        val vector: Vec3,
    )
}
