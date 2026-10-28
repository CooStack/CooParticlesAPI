package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.platform.CooClientServices
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import kotlin.math.sqrt

/**
 * 结构实体的 26.3 提取/提交 renderer。
 *
 * 方块状态和姿势变换在提取阶段生成轻量状态；方块、流体与方块实体在提交阶段进入各自材质队列。
 * 旧 `VertexBuffer` 持久网格缓存不属于此 renderer；目标版本的提交队列拥有几何和排序生命周期。
 */
class StructureModelRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<StructureModelEntity, StructureModelRenderState>(context) {
    private val displays = WeakHashMap<StructureSnapshot, Map<BlockPos, BlockEntity>>()
    private val blockEntities = Minecraft.getInstance().blockEntityRenderDispatcher

    override fun createRenderState(): StructureModelRenderState = StructureModelRenderState()

    override fun shouldRender(
        entity: StructureModelEntity,
        frustum: Frustum,
        x: Double,
        y: Double,
        z: Double,
        partialTick: Float,
    ): Boolean {
        val bounds = entity.boundingBoxForCulling.inflate(0.5)
        val dx = x - x.coerceIn(bounds.minX, bounds.maxX)
        val dy = y - y.coerceIn(bounds.minY, bounds.maxY)
        val dz = z - z.coerceIn(bounds.minZ, bounds.maxZ)
        return entity.shouldRenderAtSqrDistance(dx * dx + dy * dy + dz * dz) && frustum.isVisible(bounds)
    }

    override fun extractRenderState(
        entity: StructureModelEntity,
        state: StructureModelRenderState,
        partialTick: Float,
    ) {
        super.extractRenderState(entity, state, partialTick)
        state.entity = entity
        state.partialTick = partialTick
        state.visible = entity.isAlive || entity.settings.deathAnimation
        state.origin = Vec3(
            entity.xo + (entity.x - entity.xo) * partialTick,
            entity.yo + (entity.y - entity.yo) * partialTick,
            entity.zo + (entity.z - entity.zo) * partialTick,
        ).add(entity.modelOriginOffset)
        state.deathDegrees = if (entity.deathTime > 0 && entity.settings.deathAnimation) {
            val progress = sqrt(((entity.deathTime + partialTick - 1F) / 20F * 1.6F).coerceAtLeast(0F))
                .coerceAtMost(1F)
            progress * 90F
        } else {
            0F
        }
        state.overlay = OverlayTexture.pack(
            0F,
            entity.hurtTime > 0 || (entity.deathTime > 0 && entity.settings.deathAnimation),
        )
        state.packedLight = getPackedLightCoords(entity, partialTick)
    }

    override fun submit(
        state: StructureModelRenderState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState,
    ) {
        val entity = state.entity ?: return
        val snapshot = entity.snapshot ?: return
        if (!state.visible) return

        val client = Minecraft.getInstance()
        val snapshotBlockEntities = displays.getOrPut(snapshot) {
            snapshot.blockData.mapNotNull { (pos, data) ->
                BlockEntity.loadStatic(pos, snapshot.getBlockState(pos), data, entity.registryAccess())?.let {
                    // 原版同时暴露受保护字段和非对称访问器，Kotlin 无法生成可写 level 属性。
                    it.setLevel(entity.level())
                    pos to it
                }
            }.toMap()
        }
        val localPose = PoseStack()
        entity.settings.applyModelTransform(localPose, state.deathDegrees)
        val tracked = entity.level().getEntity(entity.id) === entity
        val view = StructureRenderView(
            snapshot,
            entity.level(),
            state.origin,
            localPose.last().pose(),
            if (tracked) null else state.packedLight,
            snapshotBlockEntities,
        )
        val luminousView = StructureRenderView(
            snapshot,
            entity.level(),
            state.origin,
            localPose.last().pose(),
            LightCoordsUtil.FULL_BRIGHT,
            snapshotBlockEntities,
        )
        val clientPose = matrices
        clientPose.pushPose()
        try {
            val originOffset = entity.modelOriginOffset
            clientPose.translate(originOffset.x, originOffset.y, originOffset.z)
            entity.settings.applyModelTransform(clientPose, state.deathDegrees)
            val modelEntries = snapshot.states.entries.filter { it.value.renderShape == RenderShape.MODEL }
            for ((pos, blockState) in modelEntries) {
                val materialView = if (
                    blockState.lightEmission > 0 || blockState.emissiveRendering()
                ) {
                    luminousView
                } else {
                    view
                }
                clientPose.pushPose()
                try {
                    clientPose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    CooClientServices.STRUCTURE_BLOCK_RENDERER.submit(
                        materialView,
                        pos,
                        blockState,
                        clientPose,
                        collector,
                        entity.level().random,
                        state.overlay,
                    )
                } finally {
                    clientPose.popPose()
                }
            }
            for ((pos, blockState) in snapshot.states) {
                val fluidState = blockState.fluidState
                if (fluidState.isEmpty) continue
                val materialView = if (blockState.lightEmission > 0) luminousView else view
                clientPose.pushPose()
                try {
                    clientPose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    CooClientServices.STRUCTURE_BLOCK_RENDERER.submitFluid(
                        materialView,
                        pos,
                        blockState,
                        fluidState,
                        clientPose,
                        collector,
                        state.overlay,
                    )
                } finally {
                    clientPose.popPose()
                }
            }
            for ((pos, blockEntity) in snapshotBlockEntities) {
                val extracted = blockEntities.tryExtractRenderState(
                    blockEntity,
                    state.partialTick,
                    null as ModelFeatureRenderer.CrumblingOverlay?,
                    false,
                ) ?: continue
                clientPose.pushPose()
                try {
                    clientPose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    blockEntities.submit(extracted, clientPose, collector, cameraState)
                } finally {
                    clientPose.popPose()
                }
            }
        } finally {
            clientPose.popPose()
        }
    }
}

/**
 * 每帧由实体数据提取器填充的结构提交快照。
 *
 * @property entity 来源结构实体；仅在当前客户端帧的提取与提交阶段使用
 * @property partialTick 当前帧实体插值
 * @property visible 是否应绘制当前生命/死亡动画状态
 * @property origin 当前帧的结构世界锚点
 * @property deathDegrees 当前帧死亡侧翻角度
 * @property overlay 当前帧伤害覆盖纹理坐标
 * @property packedLight 原 renderer 计算的实体光照
 */
class StructureModelRenderState : EntityRenderState() {
    var entity: StructureModelEntity? = null
    var partialTick = 0F
    var visible = false
    var origin = Vec3.ZERO
    var deathDegrees = 0F
    var overlay = 0
    var packedLight = 0
}
