package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.entities.structure.StructureModels
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Quaternionf
import org.joml.Vector3f
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

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

    /** 使用 GUI 画中画提交实体，并通过 GUI 图元绘制辅助线。 */
    fun render(context: GuiGraphicsExtractor, partialTick: Float) {
        if (right <= left || bottom <= top) return
        context.fill(left, top, right, bottom, 0xFF16191D.toInt())
        context.enableScissor(left, top, right, bottom)
        try {
            val extent = (diagonal / 2.0).coerceAtLeast(4.0)
            val spacing = extent / 8.0
            val gridOrigin = model?.let { it.settings.previewGridOrigin(it.modelOrigin) } ?: Vec3.ZERO
            for (index in -8..8) {
                val position = index * spacing
                val xColor = if (index == 0) 0xFFB75D5D.toInt() else 0xFF44494D.toInt()
                val zColor = if (index == 0) 0xFF557FC0.toInt() else 0xFF44494D.toInt()
                drawProjectedLine(context, -extent, 0.0, position, extent, 0.0, position, gridOrigin, xColor)
                drawProjectedLine(context, position, 0.0, -extent, position, 0.0, extent, gridOrigin, zColor)
            }
            model?.let { display ->
                val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(display) as? StructureModelRenderer
                if (renderer != null) {
                    val renderState = renderer.createRenderState()
                    renderer.extractRenderState(display, renderState, partialTick)
                    val viewportScale = min(right - left, bottom - top).toFloat() / (bottom - top)
                    val scale = viewportScale * 0.8F / diagonal.toFloat() * zoom.toFloat()
                    val panScale = (bottom - top) * scale
                    context.entity(
                        renderState,
                        scale,
                        Vector3f(
                            (-center.x + panX / panScale.toDouble()).toFloat(),
                            (-center.y - panY / panScale.toDouble()).toFloat(),
                            -center.z.toFloat(),
                        ),
                        Quaternionf().rotationX((pitch * PI / 180.0).toFloat())
                            .rotateY((yaw * PI / 180.0).toFloat()),
                        null,
                        left,
                        top,
                        right,
                        bottom,
                    )
                }
                if (collision) {
                    val bounds = display.boundingBoxForCulling
                    val entityOffset = Vec3(display.x, display.y, display.z)
                    for (corner in 0 until 8) {
                        val x = if ((corner and 1) == 0) bounds.minX else bounds.maxX
                        val y = if ((corner and 2) == 0) bounds.minY else bounds.maxY
                        val z = if ((corner and 4) == 0) bounds.minZ else bounds.maxZ
                        for (axis in intArrayOf(1, 2, 4)) {
                            if ((corner and axis) != 0) continue
                            val nextX = if (axis == 1) bounds.maxX else x
                            val nextY = if (axis == 2) bounds.maxY else y
                            val nextZ = if (axis == 4) bounds.maxZ else z
                            drawProjectedLine(
                                context,
                                x - display.x, y - display.y, z - display.z,
                                nextX - display.x, nextY - display.y, nextZ - display.z,
                                entityOffset,
                                0xFF65D6D0.toInt(),
                            )
                        }
                    }
                }
                if (axes) {
                    val axisLength = (diagonal / 4.0).coerceAtLeast(1.0)
                    val entityOffset = Vec3(display.x, display.y, display.z)
                    drawProjectedLine(context, 0.0, 0.0, 0.0, axisLength, 0.0, 0.0, entityOffset, 0xFFFF5555.toInt())
                    drawProjectedLine(context, 0.0, 0.0, 0.0, 0.0, axisLength, 0.0, entityOffset, 0xFF55FF55.toInt())
                    drawProjectedLine(context, 0.0, 0.0, 0.0, 0.0, 0.0, axisLength, entityOffset, 0xFF5599FF.toInt())
                }
            }
        } finally {
            context.disableScissor()
        }
    }

    private fun drawProjectedLine(
        context: GuiGraphicsExtractor,
        x0: Double,
        y0: Double,
        z0: Double,
        x1: Double,
        y1: Double,
        z1: Double,
        offset: Vec3,
        color: Int,
    ) {
        val yawRadians = yaw * PI / 180.0
        val pitchRadians = pitch * PI / 180.0
        val scale = min(right - left, bottom - top) * 0.8 / diagonal * zoom
        val centerX = (left + right) / 2.0 + panX
        val centerY = (top + bottom) / 2.0 + panY

        fun project(x: Double, y: Double, z: Double): Pair<Double, Double> {
            val localX = x + offset.x - center.x
            val localY = y + offset.y - center.y
            val localZ = z + offset.z - center.z
            val rotatedX = localX * cos(yawRadians) + localZ * sin(yawRadians)
            val rotatedZ = -localX * sin(yawRadians) + localZ * cos(yawRadians)
            val rotatedY = localY * cos(pitchRadians) - rotatedZ * sin(pitchRadians)
            return centerX + rotatedX * scale to centerY - rotatedY * scale
        }

        val start = project(x0, y0, z0)
        val end = project(x1, y1, z1)
        val deltaX = end.first - start.first
        val deltaY = end.second - start.second
        val length = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (length < 1.0) return
        context.pose().pushMatrix()
        try {
            context.pose().translate(start.first.toFloat(), start.second.toFloat())
            context.pose().rotate(atan2(deltaY, deltaX).toFloat())
            context.horizontalLine(0, 0, length.toInt(), color)
        } finally {
            context.pose().popMatrix()
        }
    }

    fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom

    fun click(x: Double, y: Double, button: Int): Boolean {
        if (!contains(x, y) || button !in listOf(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT)) return false
        cameraButton = button
        return true
    }

    fun drag(button: Int, dx: Double, dy: Double): Boolean {
        if (cameraButton != button) return false
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
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
