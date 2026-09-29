package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.TextureAtlas
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.entity.LivingEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.util.RandomSource
import cn.coostack.cooparticlesapi.platform.CooClientServices
import java.util.WeakHashMap
import kotlin.math.sqrt

/** 复用原版方块和方块实体渲染管线，所有顶点接受同一模型变换及死亡覆盖。 */
class StructureModelRenderer(context: EntityRendererProvider.Context) : EntityRenderer<StructureModelEntity>(context) {
    /** 原版方块批次入口，无自定义着色器或额外渲染阶段。 */
    private val blocks = context.blockRenderDispatcher
    /** 展示用方块实体缓存，不运行 tick；快照替换或世界退出后可释放。 */
    private val displays = WeakHashMap<StructureSnapshot, Map<BlockPos, BlockEntity>>()
    /** 快照内同层模型连续提交，避免交替切换固体、镂空层而逐块刷新缓冲；不持有快照本身。 */
    private val batches = WeakHashMap<StructureSnapshot, Map<RenderType, List<Pair<BlockPos, BlockState>>>>()
    /** 流体单独提交，避免打断普通方块批次；快照替换后旧条目可释放。 */
    private val fluids = WeakHashMap<StructureSnapshot, List<Pair<BlockPos, BlockState>>>()
    /** 自定义 FRAPI 模型可能动态生成顶点，未声明静态语义时整份结构走即时路径。 */
    private val cacheable = WeakHashMap<StructureSnapshot, Boolean>()
    /** 分组对应的材质资源和画质代次。 */
    private var meshGeneration = -1L

    /** 按可见范围的最近点检查距离，避免远离实体身体的倒地模型被提前裁掉。 */
    override fun shouldRender(entity: StructureModelEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        val bounds = entity.boundingBoxForCulling.inflate(0.5)
        val dx = x - x.coerceIn(bounds.minX, bounds.maxX)
        val dy = y - y.coerceIn(bounds.minY, bounds.maxY)
        val dz = z - z.coerceIn(bounds.minZ, bounds.maxZ)
        return entity.shouldRenderAtSqrDistance(dx * dx + dy * dy + dz * dz) && frustum.isVisible(bounds)
    }

    /** 绘制静态结构快照，动画只改变显示矩阵，死亡后的物理碰撞已由实体停用。 */
    override fun render(entity: StructureModelEntity, yaw: Float, tickDelta: Float, matrices: PoseStack,
                        vertexConsumers: MultiBufferSource, light: Int) {
        val snapshot = entity.snapshot ?: return
        val settings = entity.settings
        if (!entity.isAlive && !settings.deathAnimation) return
        val client = Minecraft.getInstance()
        if (meshGeneration != StructureModelMeshes.generation) {
            batches.clear()
            cacheable.clear()
            meshGeneration = StructureModelMeshes.generation
        }
        val overlay = OverlayTexture.pack(0F, entity.hurtTime > 0 || (entity.deathTime > 0 && settings.deathAnimation))
        matrices.pushPose()
        val originOffset = entity.modelOriginOffset
        matrices.translate(originOffset.x, originOffset.y, originOffset.z)
        val deathDegrees = if (entity.deathTime > 0 && settings.deathAnimation) {
            val progress = sqrt(((entity.deathTime + tickDelta - 1F) / 20 * 1.6F).coerceAtLeast(0F))
                .coerceAtMost(1F)
            progress * 90F
        } else 0F
        settings.applyModelTransform(matrices, deathDegrees)
        val blockEntities = displays.getOrPut(snapshot) {
            snapshot.blockData.mapNotNull { (pos, data) ->
                BlockEntity.loadStatic(pos, snapshot.getBlockState(pos), data, entity.registryAccess())?.let {
                    it.level = entity.level()
                    pos to it
                }
            }.toMap()
        }
        val localMatrices = PoseStack()
        settings.applyModelTransform(localMatrices, deathDegrees)
        val origin = Vec3(
            entity.xo + (entity.x - entity.xo) * tickDelta,
            entity.yo + (entity.y - entity.yo) * tickDelta,
            entity.zo + (entity.z - entity.zo) * tickDelta
        ).add(entity.modelOriginOffset)
        // 未加入世界的物品和 GUI 模型必须保留调用方的展示光照，不能采样其占位坐标零点。
        val tracked = entity.level().getEntity(entity.id) === entity
        val view = StructureRenderView(snapshot, entity.level(), origin, localMatrices.last().pose(),
            if (tracked) null else light, blockEntities)
        val luminousView = StructureRenderView(snapshot, entity.level(), origin, localMatrices.last().pose(),
            LightTexture.FULL_BRIGHT, blockEntities)
        val modelBatches = batches.getOrPut(snapshot) {
            snapshot.states.entries.asSequence().filter { it.value.renderShape == RenderShape.MODEL }
                .map { it.key to it.value }.groupBy { ItemBlockRenderTypes.getMovingBlockRenderType(it.second) }
        }
        val cached = tracked && overlay == OverlayTexture.NO_OVERLAY && entity.deathTime == 0 &&
            entity.xo == entity.x && entity.yo == entity.y && entity.zo == entity.z &&
            entity.xOld == entity.x && entity.yOld == entity.y && entity.zOld == entity.z &&
            modelBatches.keys.any { StructureModelMeshes.supportsLayer(it) } &&
            cacheable.getOrPut(snapshot) {
                modelBatches.values.all { entries ->
                    entries.all { (_, state) ->
                        CooClientServices.STRUCTURE_BLOCK_RENDERER.canCache(blocks.getBlockModel(state))
                    }
                }
            } &&
            StructureModelMeshes.draw(entity, origin, vertexConsumers) { mesh ->
                for ((layer, entries) in modelBatches) {
                    if (!StructureModelMeshes.supportsLayer(layer)) continue
                    mesh.buildLayer(layer) { vertices, local ->
                        renderBatch(entries, view, luminousView, local, vertices, entity.level().random)
                    }
                }
                mesh.sections.addAll(view.sampledSections)
                mesh.sections.addAll(luminousView.sampledSections)
            }
        for ((layer, entries) in modelBatches) {
            if (cached && StructureModelMeshes.supportsLayer(layer)) continue
            for ((pos, state) in entries) {
                val luminous = state.lightEmission > 0 || state.emissiveRendering(view, pos)
                matrices.pushPose()
                try {
                    matrices.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    CooClientServices.STRUCTURE_BLOCK_RENDERER.render(
                        if (luminous) luminousView else view, pos, state, blockEntities[pos],
                        matrices, vertexConsumers, entity.level().random, overlay)
                } finally {
                    matrices.popPose()
                }
            }
        }
        val fluidEntries = fluids.getOrPut(snapshot) {
            snapshot.states.entries.filter { !it.value.fluidState.isEmpty }.map { it.key to it.value }
        }
        for ((pos, state) in fluidEntries) {
            val blockLight = LevelRenderer.getLightColor(view, state, pos)
            matrices.pushPose()
            matrices.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            blocks.renderLiquid(pos, view, StructureFluidVertices(
                vertexConsumers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)),
                matrices.last(), pos, overlay, blockLight
            ), state, state.fluidState)
            matrices.popPose()
        }
        for ((pos, display) in blockEntities) {
            matrices.pushPose()
            matrices.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            val blockLight = LevelRenderer.getLightColor(view, snapshot.getBlockState(pos), pos)
            client.blockEntityRenderDispatcher.renderItem(display, matrices, vertexConsumers, blockLight, overlay)
            matrices.popPose()
        }
        matrices.popPose()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    /**
     * 即时路径与网格构建共用同一方块渲染算法，保留植物染色、AO、内部面剔除和发光材质。
     * @param entries 每项为结构局部格坐标及其只读状态
     * @param view 普通材质的实时世界采样
     * @param luminousView 发光材质的满亮采样
     * @param matrices 调用方的局部或最终矩阵，返回时恢复
     * @param vertices 当前原版层的顶点接收器
     * @param random 原版模型随机源，每块使用固定渲染种子
     */
    private fun renderBatch(entries: List<Pair<BlockPos, BlockState>>, view: StructureRenderView,
                            luminousView: StructureRenderView, matrices: PoseStack,
                            vertices: VertexConsumer, random: RandomSource) {
        for ((pos, state) in entries) {
            val materialView = if (state.lightEmission > 0 || state.emissiveRendering(view, pos)) luminousView else view
            matrices.pushPose()
            try {
                matrices.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                blocks.modelRenderer.tesselateBlock(
                    materialView, blocks.getBlockModel(state), state, pos, matrices, vertices,
                    true, random, state.getSeed(pos), OverlayTexture.NO_OVERLAY
                )
            } finally {
                matrices.popPose()
            }
        }
    }

    override fun getTextureLocation(entity: StructureModelEntity): ResourceLocation = TextureAtlas.LOCATION_BLOCKS
}
