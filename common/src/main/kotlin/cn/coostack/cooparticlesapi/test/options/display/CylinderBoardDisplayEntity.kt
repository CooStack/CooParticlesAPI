package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.display.AutoDisplayEntity
import cn.coostack.cooparticlesapi.display.CooParticlesRenderTypes
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f

@CooAutoRegister
class CylinderBoardDisplayEntity() : AutoDisplayEntity(Vec3.ZERO, null) {
    constructor(pos: Vec3, world: Level?) : this() {
        this.pos = pos
        this.world = world
    }

    @CodecField
    var direction: Vec3 = Vec3(0.0, 1.0, 0.0)

    @CodecField
    var length: Float = 1.0f

    @CodecField
    var lineWidth: Float = 0.08f

    @CodecField
    var velocity: Vec3 = Vec3.ZERO

    @CodecField
    var maxAge: Int = 20

    @CodecField
    var fadeOut: Boolean = true

    @CodecField
    var color: Vector4f = Vector4f(0.25f, 0.85f, 1.0f, 0.72f)

    @Transient
    private var age = 0

    @Transient
    private var initialAlpha = color.w

    init {
        manageRotation = false
    }

    override fun tick() {
        super.tick()
        if (age == 0) {
            initialAlpha = color.w
        }
        if (velocity.lengthSqr() > 1.0E-6) {
            pos = pos.add(velocity)
        }
        if (maxAge > 0) {
            age++
            if (fadeOut) {
                val live = (1f - age.toFloat() / maxAge.toFloat()).coerceIn(0f, 1f)
                color.w = initialAlpha * live
            }
            if (age >= maxAge) {
                remove()
            }
        }
    }

    override fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: Float,
        camera: Camera
    ) {
        val basis = MinecraftRendererUtil.axialBillboardBasis(direction, camera, pos)
        val consumer = buffer.getBuffer(
            CooParticlesRenderTypes.entityCutoutEmissive(
                ResourceLocation.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID, "none"
                ), 5f
            )
        )
        val halfLength = length.coerceAtLeast(0.01f).toDouble() * 0.5
        val halfWidth = lineWidth.coerceAtLeast(0.01f).toDouble() * 0.5
        val top = basis.axis.scale(halfLength)
        val side = basis.right.scale(halfWidth)

        RenderSystem.disableCull()
        addBoard(
            consumer,
            modelMatrixStack,
            top.scale(-1.0).subtract(side),
            top.scale(-1.0).add(side),
            top.add(side),
            top.subtract(side),
            basis.face
        )
    }

    private fun addBoard(
        consumer: VertexConsumer,
        stack: PoseStack,
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        p4: Vec3,
        normal: Vec3
    ) {
        addVertex(consumer, stack, p1, normal, 0f, 1f)
        addVertex(consumer, stack, p2, normal, 1f, 1f)
        addVertex(consumer, stack, p3, normal, 1f, 0f)
        addVertex(consumer, stack, p4, normal, 0f, 0f)
    }

    private fun addVertex(
        consumer: VertexConsumer,
        stack: PoseStack,
        point: Vec3,
        normal: Vec3,
        u: Float,
        v: Float
    ) {
        consumer.addVertex(stack.last(), point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
            .setNormal(stack.last(), normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            .setColor(color.x, color.y, color.z, color.w)
            .setUv(u, v)
            .setUv1(1, 1)
            .setLight(LightTexture.FULL_BRIGHT)
            .setOverlay(OverlayTexture.NO_OVERLAY)
    }
}
