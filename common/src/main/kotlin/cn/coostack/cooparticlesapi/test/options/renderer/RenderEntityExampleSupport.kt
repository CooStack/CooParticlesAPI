package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

/**
 * RenderEntity 示例共用工具。
 *
 * 这份支持类只做两件事：
 * 1. 统一复用测试顶点缓冲，避免每个案例都重复创建球体、屏幕四边形和 billboard。
 * 2. 把“屏幕投影”“scene color/depth 绑定”“glow 上下文构造”这些样板代码集中起来，
 *    让案例本身只专注讲 API 用法和效果实现。
 */
object RenderEntityExampleSupport {
    data class ProjectedSphereInfo(
        val centerUv: Vector2f = Vector2f(),
        val radiusPx: Float = 0.0f,
        val depth01: Float = 1.0f,
        val visible: Boolean = false
    )

    private val unitSphereBuffer = SimpleVertexBuffer().apply {
        setVertexes(
            ShaderUtil.genBall(1.0f, 72, 108),
            CooVertexFormat.POINT_FORMAT
        )
    }
    private val billboardBuffer = SimpleVertexBuffer().apply {
        setVertexes(
            ShaderUtil.genSquareUV(
                Vector3f(-0.5f, -0.5f, 0.0f),
                Vector3f(0.5f, -0.5f, 0.0f),
                Vector3f(0.5f, 0.5f, 0.0f),
                Vector3f(-0.5f, 0.5f, 0.0f)
            ),
            CooVertexFormat.POINT_TEXTURE_UV_FORMAT
        )
    }
    private val screenBuffer = SimpleVertexBuffer().apply {
        setVertexes(
            ShaderUtil.genSquareUVScreen(
                Vector3f(-1.0f, 1.0f, 0.0f),
                Vector3f(1.0f, 1.0f, 0.0f),
                Vector3f(1.0f, -1.0f, 0.0f),
                Vector3f(-1.0f, -1.0f, 0.0f)
            ),
            CooVertexFormat.POINT_TEXTURE_UV_FORMAT
        )
    }

    private var sphereReady = false
    private var billboardReady = false
    private var screenReady = false

    fun sphereBuffer(): SimpleVertexBuffer {
        if (!sphereReady) {
            sphereReady = true
            unitSphereBuffer.init()
        }
        return unitSphereBuffer
    }

    fun billboardBuffer(): SimpleVertexBuffer {
        if (!billboardReady) {
            billboardReady = true
            billboardBuffer.init()
        }
        return billboardBuffer
    }

    fun screenBuffer(): SimpleVertexBuffer {
        if (!screenReady) {
            screenReady = true
            screenBuffer.init()
        }
        return screenBuffer
    }

    fun buildModelMatrix(entity: RenderEntity, tickDelta: Float): Matrix4fStack {
        val stack = Matrix4fStack(16)
        RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
        return stack
    }

    fun buildGlowContext(input: LocalRenderInput<*>): ScreenGlowRenderContext {
        return buildGlowContext(input.tickDelta, input.viewMatrix, input.projMatrix)
    }

    fun buildGlowContext(frameContext: RenderFrameContext): ScreenGlowRenderContext {
        return buildGlowContext(frameContext.tickDelta, frameContext.viewMatrix, frameContext.projMatrix)
    }

    private fun buildGlowContext(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ): ScreenGlowRenderContext {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera.position
        return ScreenGlowRenderContext(
            tickDelta = tickDelta,
            cameraWorldPos = Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat()),
            viewMatrix = Matrix4f(viewMatrix),
            viewRotationMatrix = Matrix3f(viewMatrix),
            inverseViewRotationMatrix = Matrix3f(viewMatrix).invert(),
            projMatrix = Matrix4f(projMatrix),
            screenSize = mainRenderSize()
        )
    }

    fun mainRenderSize(): Vector2f {
        val target = Minecraft.getInstance().mainRenderTarget
        return Vector2f(target.width.toFloat(), target.height.toFloat())
    }

    /**
     * 把一个世界空间球体粗略投影到当前屏幕。
     *
     * 这不是引擎内部的精确包围盒测试，而是给案例 shader 提供一个足够稳定的
     * “屏幕圆心 + 屏幕半径 + 深度”参数，适合做水球折射、镜子圆盘和冲击波这类
     * 屏幕空间合成示例。
     */
    fun projectSphere(
        worldPos: Vec3,
        radius: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        screenSize: Vector2f = mainRenderSize()
    ): ProjectedSphereInfo {
        val viewPos = Matrix4f(viewMatrix).transform(
            Vector4f(worldPos.x.toFloat(), worldPos.y.toFloat(), worldPos.z.toFloat(), 1.0f)
        )
        if (viewPos.z >= -0.01f) {
            return ProjectedSphereInfo()
        }

        val clip = Matrix4f(projMatrix).transform(Vector4f(viewPos))
        if (clip.w <= 1.0e-5f) {
            return ProjectedSphereInfo()
        }

        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w
        val ndcZ = clip.z / clip.w
        val centerUv = Vector2f(
            ndcX * 0.5f + 0.5f,
            ndcY * 0.5f + 0.5f
        )
        val radiusPx = abs(projMatrix.m11() * radius / -viewPos.z) * screenSize.y * 0.5f
        val visible = radiusPx > 1.0f &&
            centerUv.x > -0.35f && centerUv.x < 1.35f &&
            centerUv.y > -0.35f && centerUv.y < 1.35f

        return ProjectedSphereInfo(
            centerUv = centerUv,
            radiusPx = radiusPx,
            depth01 = ndcZ * 0.5f + 0.5f,
            visible = visible
        )
    }

    /**
     * 绑定当前主场景颜色和深度纹理给屏幕空间 shader。
     *
     * 这里专门做成帮助方法，是为了让案例本身把注意力放在“怎么声明效果”和
     * “uniform 如何对应视觉逻辑”上，而不是把 GL 状态保存/恢复代码写满每个文件。
     */
    fun withSceneTextures(
        colorSlot: Int = 5,
        depthSlot: Int = 6,
        block: (colorSlot: Int, depthSlot: Int) -> Unit
    ) {
        val target = Minecraft.getInstance().mainRenderTarget
        if (target.colorTextureId <= 0 || target.depthTextureId <= 0) {
            return
        }

        RenderSystem.setShaderTexture(colorSlot, target.colorTextureId)
        RenderSystem.setShaderTexture(depthSlot, target.depthTextureId)

        try {
            block(colorSlot, depthSlot)
        } finally {
            RenderSystem.setShaderTexture(colorSlot, 0)
            RenderSystem.setShaderTexture(depthSlot, 0)
        }
    }
}
