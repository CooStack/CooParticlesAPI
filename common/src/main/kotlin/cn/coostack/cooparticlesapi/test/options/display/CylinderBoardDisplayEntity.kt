package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.display.AutoDisplayEntity
import cn.coostack.cooparticlesapi.display.CooParticlesRenderTypes
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
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
    var length: Float = 1.0F

    @CodecField
    var lineWidth: Float = 0.08F

    @CodecField
    var velocity: Vec3 = Vec3.ZERO

    @CodecField
    var maxAge: Int = 20

    @CodecField
    var fadeOut: Boolean = true

    @CodecField
    var color: Vector4f = Vector4f(0.25F, 0.85F, 1.0F, 0.72F)

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
                val live = (1F - age.toFloat() / maxAge.toFloat()).coerceIn(0F, 1F)
                color.w = initialAlpha * live
            }
            if (age >= maxAge) {
                remove()
            }
        }
    }

    override fun submit(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        collector: SubmitNodeCollector,
        delta: Float,
        camera: CameraRenderState
    ) {
        val basis = MinecraftRendererUtil.axialBillboardBasis(direction, camera.pos, pos)
        val renderType = CooParticlesRenderTypes.entityCutoutEmissive(
            Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "none"), 5F
        )
        val halfLength = length.coerceAtLeast(0.01F).toDouble() * 0.5
        val halfWidth = lineWidth.coerceAtLeast(0.01F).toDouble() * 0.5
        val top = basis.axis.scale(halfLength)
        val side = basis.right.scale(halfWidth)
        collector.submitCustomGeometry(modelMatrixStack, renderType) { pose, consumer ->
            addBoard(
                consumer,
                pose,
                top.scale(-1.0).subtract(side),
                top.scale(-1.0).add(side),
                top.add(side),
                top.subtract(side),
                basis.face,
            )
        }
    }

    private fun addBoard(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        p4: Vec3,
        normal: Vec3
    ) {
        addVertex(consumer, pose, p1, normal, 0F, 1F)
        addVertex(consumer, pose, p2, normal, 1F, 1F)
        addVertex(consumer, pose, p3, normal, 1F, 0F)
        addVertex(consumer, pose, p4, normal, 0F, 0F)
    }

    private fun addVertex(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        point: Vec3,
        normal: Vec3,
        u: Float,
        v: Float
    ) {
        consumer.addVertex(pose, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            .setColor(color.x, color.y, color.z, 0.1F)
            .setUv(u, v)
            .setUv1(1, 1)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setOverlay(OverlayTexture.NO_OVERLAY)
    }
}
