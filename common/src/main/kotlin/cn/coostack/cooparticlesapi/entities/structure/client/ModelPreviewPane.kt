package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import cn.coostack.cooparticlesapi.entities.collision.client.IrregularCollisionDebugRenderer
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.entities.structure.StructureModels
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import com.mojang.blaze3d.platform.Lighting
import net.minecraft.client.renderer.RenderType
import com.mojang.math.Axis
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.min
import kotlin.math.pow

/** 主编辑器内嵌的独立预览视口，空模型只绘制坐标网格，不加入世界或修改草稿。 */
internal class ModelPreviewPane {
    /** 服务端返回的展示实体，未选择或加载失败时为空。 */
    private var model: StructureModelEntity? = null
    /** 当前展示的结构名称。 */
    private var name = ""
    /** 首次放置需要以底部中心对齐。 */
    private var newPlacement = false
    /** 相机旋转、缩放与平移均独立于模型参数。 */
    private var yaw = 35F
    /** 相机俯仰角，限制在正负 89 度内。 */
    private var pitch = 20F
    /** 取景后的相对缩放倍数。 */
    private var zoom = 1.0
    /** 相机水平平移，单位为界面像素。 */
    private var panX = 0.0
    /** 相机竖直平移，单位为界面像素。 */
    private var panY = 0.0
    /** 相机中心和范围仅在载入模型或主动适应视图时更新。 */
    private var center = Vec3.ZERO
    /** 用于自适应缩放的外包框对角线长度，单位为格。 */
    private var diagonal = 8.0
    /** 鼠标捕获只允许从视口内部开始。 */
    private var cameraButton: Int? = null
    /** 绘制和命中测试共用视口边界，单位为界面像素。 */
    var left = 0
    /** 视口顶部，与左侧编辑面板上沿对齐。 */
    var top = 4
    /** 视口右边界，不包含外边距。 */
    var right = 0
    /** 视口底边界，不包含相机工具栏。 */
    var bottom = 0
    /** 模型局部中心轴及碰撞框由主页面开关控制。 */
    var axes = true
    /** 是否叠加模型碰撞线框。 */
    var collision = false

    fun clear() {
        model = null
        center = Vec3.ZERO
        diagonal = 8.0
        zoom = 1.0
        panX = 0.0
        panY = 0.0
    }

    /** 替换服务端快照后重新取景，实体只在客户端内存中存在。 */
    fun load(name: String, snapshot: StructureSnapshot, settings: StructureModelSettings, newPlacement: Boolean) {
        val world = Minecraft.getInstance().level ?: return
        this.name = name
        this.newPlacement = newPlacement
        model = StructureModelEntity(StructureModels.ENTITY, world).apply { configurePreview(name, snapshot, settings) }
        update(settings)
        fit()
    }

    /** 参数输入更新模型但不自动调整相机，保证偏移和缩放的变化可见。 */
    fun update(settings: StructureModelSettings) {
        val display = model ?: return
        val snapshot = display.snapshot ?: return
        if (!settings.valid()) return
        display.configurePreview(name, snapshot, settings)
        val origin = if (newPlacement) snapshot.placementOrigin(settings, Vec3.ZERO) else Vec3.ZERO
        display.setPos(origin.subtract(display.modelOriginOffset))
    }

    fun fit() {
        val bounds = model?.boundingBoxForCulling
        center = bounds?.center ?: Vec3.ZERO
        diagonal = bounds?.let { Vec3(it.xsize, it.ysize, it.zsize).length().coerceAtLeast(0.1) } ?: 8.0
        zoom = 1.0
        panX = 0.0
        panY = 0.0
    }

    /** 复用现有实体渲染器和原版线段层；空预览不使用占位方块或错误模型。 */
    fun render(context: GuiGraphics) {
        if (right <= left || bottom <= top) return
        context.fill(left, top, right, bottom, 0xFF16191D.toInt())
        context.flush()
        context.enableScissor(left, top, right, bottom)
        val matrices = context.pose()
        matrices.pushPose()
        try {
            val pixels = (min(right - left, bottom - top) * 0.8 / diagonal * zoom).toFloat()
            matrices.translate((left + right) / 2.0 + panX, (top + bottom) / 2.0 + panY, 200.0)
            matrices.scale(pixels, -pixels, pixels)
            matrices.mulPose(Axis.XP.rotationDegrees(pitch))
            matrices.mulPose(Axis.YP.rotationDegrees(yaw))
            matrices.translate(-center.x, -center.y, -center.z)
            Lighting.setupForEntityInInventory()
            val consumers = context.bufferSource()
            val lines = consumers.getBuffer(RenderType.lines())
            val extent = (diagonal / 2.0).coerceAtLeast(4.0)
            val spacing = extent / 8.0
            matrices.pushPose()
            val gridOrigin = model?.let { it.settings.previewGridOrigin(it.modelOrigin) } ?: Vec3.ZERO
            matrices.translate(gridOrigin.x, gridOrigin.y, gridOrigin.z)
            val entry = matrices.last()
            // 网格对齐未施加手动偏移的模型中心，偏移只移动模型，不移动网格。
            for (index in -8..8) {
                val position = (index * spacing).toFloat()
                val limit = extent.toFloat()
                val xColor = if (index == 0) 0xFFB75D5D.toInt() else 0xFF44494D.toInt()
                val zColor = if (index == 0) 0xFF557FC0.toInt() else 0xFF44494D.toInt()
                lines.addVertex(entry, -limit, 0F, position).setColor(xColor).setNormal(entry, 1F, 0F, 0F)
                lines.addVertex(entry, limit, 0F, position).setColor(xColor).setNormal(entry, 1F, 0F, 0F)
                lines.addVertex(entry, position, 0F, -limit).setColor(zColor).setNormal(entry, 0F, 0F, 1F)
                lines.addVertex(entry, position, 0F, limit).setColor(zColor).setNormal(entry, 0F, 0F, 1F)
            }
            matrices.popPose()
            model?.let { display ->
                matrices.translate(display.x, display.y, display.z)
                RenderSystem.runAsFancy {
                    Minecraft.getInstance().entityRenderDispatcher.getRenderer(display)
                        .render(display, 0F, 0F, matrices, consumers, 15728880)
                }
                if (collision) IrregularCollisionDebugRenderer.drawCollision(matrices,
                    consumers.getBuffer(RenderType.lines()), display, 1F, 1F, 1F)
                if (axes) {
                    IrregularCollisionDebugRenderer.drawAxes(matrices, consumers.getBuffer(RenderType.lines()), display)
                }
            }
            context.flush()
        } finally {
            matrices.popPose()
            Lighting.setupFor3DItems()
            context.disableScissor()
        }
    }

    fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom

    fun click(x: Double, y: Double, button: Int): Boolean {
        if (!contains(x, y) || button !in listOf(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT)) return false
        cameraButton = button
        return true
    }

    fun drag(button: Int, dx: Double, dy: Double): Boolean {
        if (cameraButton != button) return false
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            yaw = (yaw + dx.toFloat() * 0.5F) % 360F
            pitch = (pitch + dy.toFloat() * 0.5F).coerceIn(-89F, 89F)
        } else {
            panX += dx
            panY += dy
        }
        return true
    }

    fun release(button: Int): Boolean {
        if (cameraButton != button) return false
        cameraButton = null
        return true
    }

    fun scroll(x: Double, y: Double, amount: Double): Boolean {
        if (!contains(x, y)) return false
        zoom = (zoom * 1.1.pow(amount)).coerceIn(0.05, 20.0)
        return true
    }
}

/**
 * 求预览网格在展示空间中的原点，包含中心补偿但排除手动偏移。
 * 示例：`settings.previewGridOrigin(display.modelOrigin)`。
 * @receiver 已验证的模型设置
 * @param modelOrigin 预览实体的结构原点，与实体身体坐标不同
 * @return 不跟随 offset 移动的模型中心
 */
internal fun StructureModelSettings.previewGridOrigin(modelOrigin: Vec3): Vec3 =
    modelOrigin.add(pivot[0] + pivotCompensation[0], pivot[1] + pivotCompensation[1], pivot[2] + pivotCompensation[2])
