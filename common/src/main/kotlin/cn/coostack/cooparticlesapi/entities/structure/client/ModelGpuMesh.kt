package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.BufferBuilder
import net.minecraft.client.renderer.RenderType
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.SectionPos
import kotlin.math.abs
import net.minecraft.world.phys.Vec3

/**
 * 一份结构姿态下的非透明网格，独占 GPU 缓冲，仅在渲染线程创建、绘制和关闭。
 * 顶点烘焙为相对结构原点的姿态坐标，视角变化只更新相机偏移，不重建模型。
 * @property snapshot 构建时的只读快照身份
 * @property settings 构建时的只读设置身份，变化后须重新采样世界光照
 * @property origin 构建时的结构世界原点，保留双精度
 */
internal class ModelGpuMesh(val snapshot: StructureSnapshot, val settings: StructureModelSettings, val origin: Vec3) : AutoCloseable {
    /** 仅拥有固体及镂空层的缓冲，透明层不在此缓存。 */
    private val buffers = linkedMapOf<RenderType, VertexBuffer>()
    /** 构建时实际采样过的世界区段，光照更新只使相关网格失效。 */
    val sections = LongOpenHashSet()
    /** 实际提交过的纹理，缓存命中时仍需通知 Sodium 保持其动画活跃。 */
    val sprites = linkedSetOf<TextureAtlasSprite>()
    /** 字节预算按实际上传顶点大小计量，共享原版顺序索引不重复计算。 */
    var bytes = 0L
        private set
    /** 相关光照、区块或染色发生变化后，下一次绘制前重建。 */
    var dirty = false
    /** 最近使用的客户端 tick，用于清理长期不可见的网格。 */
    var lastUsed = 0L

    /**
     * 构建并上传一个原版层；回调只写入局部顶点，返回后缓冲由本对象独占。
     * @param layer 原版非透明层
     * @param emit 接收顶点写入器与独立局部矩阵，不得保存二者引用
     */
    fun buildLayer(layer: RenderType, emit: (VertexConsumer, PoseStack) -> Unit) {
        check(!buffers.containsKey(layer))
        ByteBufferBuilder(65536).use { allocator ->
            val builder = BufferBuilder(allocator, layer.mode(), layer.format())
            val pose = PoseStack()
            settings.applyModelTransform(pose)
            emit(ModelMeshVertices(builder, sprites), pose)
            val built = builder.build() ?: return
            val size = built.vertexBuffer().remaining().toLong()
            val buffer = try {
                VertexBuffer(VertexBuffer.Usage.STATIC)
            } catch (failure: Throwable) {
                built.close()
                throw failure
            }
            try {
                buffer.bind()
                // 原版 upload 接管并关闭 BuiltBuffer，包括上传异常的路径。
                buffer.upload(built)
                buffers[layer] = buffer
                bytes += size
            } catch (failure: Throwable) {
                buffer.close()
                throw failure
            } finally {
                VertexBuffer.unbind()
            }
        }
    }

    /** 使用原版区块偏移定位，位置和雾距离同时保持相机相对坐标；返回时恢复偏移与材质状态。 */
    fun draw(camera: Vec3) {
        val offset = cameraOffset(camera)
        val modelView = RenderSystem.getModelViewMatrix()
        val projection = RenderSystem.getProjectionMatrix()
        for ((layer, buffer) in buffers) {
            layer.setupRenderState()
            try {
                buffer.bind()
                val shader = checkNotNull(RenderSystem.getShader())
                // 原版地形着色器的雾距离在 ModelViewMat 之前计算，不能把平移只塞进矩阵。
                val chunkOffset = checkNotNull(shader.CHUNK_OFFSET)
                chunkOffset.set(offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat())
                try {
                    buffer.drawWithShader(modelView, projection, shader)
                } finally {
                    chunkOffset.set(0F, 0F, 0F)
                }
            } finally {
                VertexBuffer.unbind()
                layer.clearRenderState()
            }
        }
    }

    /** 先用双精度相减再上传为浮点数，避免大世界坐标让模型抖动。 */
    internal fun cameraOffset(camera: Vec3): Vec3 = origin.subtract(camera)

    /** 快照或姿态按身份变更，位置按双精度值变更；任一变化均重新构建，不能复用旧光照。 */
    fun matches(snapshot: StructureSnapshot, settings: StructureModelSettings, origin: Vec3): Boolean =
        !dirty && this.snapshot === snapshot && this.settings === settings && this.origin == origin

    /** 生物群系混色可读取相邻区块，区块内容更新时保守扩展一列进行失效判断。 */
    fun dependsOnChunk(x: Int, z: Int): Boolean = sections.any {
        abs(SectionPos.x(it) - x) <= 1 && abs(SectionPos.z(it) - z) <= 1
    }

    override fun close() {
        buffers.values.forEach { it.close() }
        buffers.clear()
        sections.clear()
        sprites.clear()
        bytes = 0
    }
}
