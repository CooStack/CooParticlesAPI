package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import cn.coostack.cooparticlesapi.utils.PhysicsUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.*


abstract class ControlableParticle(
    world: ClientLevel,
    pos: Vec3,
    velocity: Vec3,
    val controlUUID: UUID,
    /** 粒子的相机朝向模式 */
    var cameraOption: ParticleCameraOption = ParticleCameraOption.BILLBOARD
) : TextureSheetParticle(world, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z) {
    constructor(
        world: ClientLevel,
        pos: Vec3,
        velocity: Vec3,
        controlUUID: UUID,
        faceToCamera: Boolean
    ) : this(world, pos, velocity, controlUUID, ParticleCameraOption.fromFaceToCamera(faceToCamera))

    companion object {
        @JvmStatic
        val LINEAR_INTERPOLATOR: ParticleLerpInterpolator = ParticleLerpInterpolator { p1, p2, delta ->
            GraphMathHelper.lerp(delta, p1, p2)
        }
    }

    /** 旧布尔 API 的兼容桥接：true = BILLBOARD，false = ROTATION。 */
    var faceToCamera: Boolean
        get() = cameraOption == ParticleCameraOption.BILLBOARD
        set(value) {
            cameraOption = ParticleCameraOption.fromFaceToCamera(value)
        }

    /** 插值修改器 */
    var interpolator: ParticleLerpInterpolator = LINEAR_INTERPOLATOR
        private set
    val controler: ParticleControler = ControlParticleManager.getControl(controlUUID)!!

    /** 穿过液体的标记 用于适配 ParticleOnLiquidEvent */
    internal var crossLiquid = false

    /** 粒子亮度 设置为-1则为环境亮度 */
    var light = 15
        set(value) {
            if (value == -1) {
                field = value
                return
            }
            field = value.coerceIn(0, 15)
        }

    /** 粒子渲染类型 可以使用 */
    var textureSheet: ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_LIT

    private var currentAxis: Vec3 = Vec3(0.0, 1.0, 0.0)
    var previewAxis: Vec3 = currentAxis

    /** AXIS_BILLBOARD 使用的固定轴方向 */
    var axis: Vec3
        get() = currentAxis
        set(value) {
            currentAxis = value
        }

    /** 是否调用 net.minecraft.client.particle.Particle中的tick方法 */
    var minecraftTick: Boolean = false

    /** @see world */
    val clientWorld: ClientLevel
        get() = level

    /**
     * @see x
     * @see y
     * @see z
     */
    var loc: Vec3
        get() = Vec3(x, y, z)
        set(value) {
            this.x = value.x
            this.y = value.y
            this.z = value.z
        }

    /** @see scale 粒子尺寸 */
    private var currentWeightSize = super.quadSize
    private var currentHeightSize = super.quadSize
    private var currentDepthSize = 0f
    var previewWeightSize = currentWeightSize
    var previewHeightSize = currentHeightSize
    var previewDepthSize = currentDepthSize

    /** 是否保持宽高等比。开启后单独设置宽或高会同步另一边。 */
    var uniformSize: Boolean = true

    var weightSize: Float
        get() = currentWeightSize
        set(value) {
            currentWeightSize = value
            if (uniformSize) {
                currentHeightSize = value
            }
            updateRenderSizeBounds()
        }

    var heightSize: Float
        get() = currentHeightSize
        set(value) {
            currentHeightSize = value
            if (uniformSize) {
                currentWeightSize = value
            }
            updateRenderSizeBounds()
        }

    var depthSize: Float
        get() = currentDepthSize
        set(value) {
            currentDepthSize = value
            updateRenderSizeBounds()
        }

    var size: Float
        get() = (currentWeightSize + currentHeightSize) / 2f
        set(value) {
            currentWeightSize = value
            currentHeightSize = value
            updateRenderSizeBounds()
    }

    private fun updateRenderSizeBounds() {
        val boundSize = maxOf(currentWeightSize, currentHeightSize, currentDepthSize)
        // 对应scale方法
        this.setSize(0.2f * boundSize, 0.2f * boundSize)
    }

    /**
     * @see prevPosX
     * @see prevPosY
     * @see prevPosZ
     */
    var prevPos: Vec3
        get() = Vec3(xo, yo, zo)
        set(value) {
            this.xo = value.x
            this.yo = value.y
            this.zo = value.z
        }

    /**
     * @see velocityX
     * @see velocityY
     * @see velocityZ
     */
    var velocity: Vec3
        get() = Vec3(xd, yd, zd)
        set(value) {
            this.xd = value.x
            this.yd = value.y
            this.zd = value.z
        }

    /** @see boundingBox */
    var bounding: AABB
        get() = boundingBox
        set(value) {
            boundingBox = value
        }

    /** @see onGround */
    var onTheGround: Boolean
        get() = onGround
        set(value) {
            onGround = value
        }

    /** @see hasPhysics fabric ->collidesWithWorld */
    var collidesWithTheWorld: Boolean
        get() = hasPhysics
        set(value) {
            hasPhysics = value
        }

    /** @see dead */
    var death: Boolean
        get() = removed
        set(value) = if (value) {
            remove()
        } else {
            removed = false
        }

    /**
     * byd neoforge什么傻逼mapping
     *
     * @see bbWidth - > fabric spacingXZ
     * @see bbHeight - > Fabric spacingY
     */
    var spacing: Vector2f
        get() = Vector2f(bbWidth, bbHeight)
        set(value) {
            bbWidth = value.x
            bbHeight = value.y
        }

    /** @see random */
    val rand: RandomSource
        get() = random

    /** @see age */
    var currentAge: Int
        get() = age
        set(value) {
            age = value
        }


    /** @see gravityStrength */
    var gravityStrength: Float
        get() = super.gravity
        set(value) {
            super.gravity = value
        }

    var color: Vector3f
        get() = Vector3f(rCol, gCol, bCol)
        set(value) {
            rCol = value.x
            gCol = value.y
            bCol = value.z
        }

    var particleAlpha: Float
        get() = alpha
        set(value) {
            alpha = value.coerceIn(0f, 1f)
        }

    var previewPitch: Float = 0f
    var currentPitch: Float = 0f

    var previewYaw: Float = 0f
    var currentYaw: Float = 0f

    /** @see prevAngle */
    var previewRoll: Float
        get() = oRoll
        set(value) {
            oRoll = value
        }

    /** @see angle */
    var currentRoll: Float
        get() = roll
        set(value) {
            super.roll = value
        }

    /** @see velocityMultiplier */
    var velocityMulti: Float
        get() = friction
        set(value) {
            friction = value
        }


    /** @see speedUpWhenYMotionIsBlocked -> fabric ascending 让粒子乱飘的罪恶源头? */
    var canAscending: Boolean
        get() = speedUpWhenYMotionIsBlocked
        set(value) {
            speedUpWhenYMotionIsBlocked = value
        }


    private var lastPreview = pos
    private var update = false


    fun teleportTo(pos: Vec3) {
        lastPreview = pos
        update = true
    }

    fun teleportTo(x: Double, y: Double, z: Double) {
        lastPreview = Vec3(x, y, z)
        update = true
    }

    init {
        controler.loadParticle(this)
        controler.particleInit()
    }

    var lastRotate = Vector3f(previewPitch, previewYaw, previewRoll)
    var updateRotate = false
    fun rotateParticleTo(target: RelativeLocation) {
        rotateParticleTo(Vector3f(target.x.toFloat(), target.y.toFloat(), target.z.toFloat()))
    }

    fun rotateParticleTo(target: Vec3) {
        rotateParticleTo(target.toVector3f())
    }

    fun rotateParticleTo(target: Vector3f) {
        val (x, y, z) = Math3DUtil.calculateEulerAnglesToPoint(target)
        updateRotate = true
        lastRotate = Vector3f(x, y, z)
    }

    fun setAxisLocation(axis: RelativeLocation) {
        this.axis = Vec3(axis.x, axis.y, axis.z)
    }

    /**
     * 防止频繁调用Math3DUtil (让键盘休息一会) 也不用调用 color =
     * Vector3f(xxx/255f,xxx/255f,xxx/255f)
     */
    fun colorOfRGB(r: Int, g: Int, b: Int) {
        color = Math3DUtil.colorOf(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun colorOfRGBA(rgba: Int) {
        val a = (rgba shr 24) and 0xFF
        val r = (rgba shr 16) and 0xFF
        val g = (rgba shr 8) and 0xFF
        val b = rgba and 0xFF
        colorOfRGBA(r, g, b, a / 255f)
    }

    fun colorOfRGBA(r: Int, g: Int, b: Int, alpha: Float) {
        color = Math3DUtil.colorOf(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        this.alpha = alpha.coerceIn(0f, 1f)
    }

    /**
     * 将粒子从 loc 移动到 pos 在下一个tick生效
     *
     * @see teleportTo
     */
    fun moveToWithPhysics(pos: Vec3) {
        val rel = pos - loc
        val res = PhysicsUtil.collide(loc, rel, clientWorld)
        val actualPos = if (res.type != HitResult.Type.MISS) {
            PhysicsUtil.fixBeforeCollidePosition(res)
        } else {
            pos
        }
        teleportTo(actualPos)
    }

    /**
     * 将粒子从 loc 移动到 pos 在下一个tick生效
     *
     * @see teleportTo
     */
    fun moveToWithPhysics(pos: Vec3, collideResult: BlockHitResult) {
        val actualPos = if (collideResult.type != HitResult.Type.MISS) {
            PhysicsUtil.fixBeforeCollidePosition(collideResult)
        } else {
            pos
        }
        teleportTo(actualPos)
    }

    /** 将粒子从 loc 移动到 pos 同teleportTo 在下一个tick生效 */
    fun moveToWithPhysics(x: Double, y: Double, z: Double, collideResult: BlockHitResult) {
        moveToWithPhysics(Vec3(x, y, z), collideResult)
    }

    /** 将粒子从 loc 移动到 pos 同teleportTo 在下一个tick生效 */
    fun moveToWithPhysics(x: Double, y: Double, z: Double) {
        moveToWithPhysics(Vec3(x, y, z))
    }

    /**
     * 请使用作为tick方法
     *
     * @see ParticleControler.addPreTickAction
     */
    final override fun tick() {
        if (age > lifetime) {
            age = lifetime
        }

        if (minecraftTick) {
            super.tick()
        }
        previewWeightSize = currentWeightSize
        previewHeightSize = currentHeightSize
        previewDepthSize = currentDepthSize
        previewAxis = currentAxis
        controler.tick()
        xo = x
        yo = y
        zo = z
        controler.tickPostActions()
        if (update) {
            if (!minecraftTick) {
                this.boundingBox = AABB.ofSize(
                    this.loc,
                    this.boundingBox.maxX - this.boundingBox.minX,
                    this.boundingBox.maxY - this.boundingBox.minY,
                    this.boundingBox.maxZ - this.boundingBox.minZ,
                )
            }
            this.loc = lastPreview
            update = false
        }
        previewPitch = currentPitch
        previewYaw = currentYaw
        previewRoll = currentRoll
        if (updateRotate) {
            currentPitch = lastRotate.x
            currentYaw = lastRotate.y
            currentRoll = lastRotate.z
            updateRotate = false
        }
    }

    fun setInterpolator(newInterpolator: ParticleLerpInterpolator): ControlableParticle {
        this.interpolator = newInterpolator
        return this
    }

    override fun getQuadSize(tickDelta: Float): Float {
        return maxOf(getWeightSize(tickDelta), getHeightSize(tickDelta), getDepthSize(tickDelta))
    }

    private fun getWeightSize(tickDelta: Float): Float {
        return Mth.lerp(tickDelta, previewWeightSize, currentWeightSize)
    }

    private fun getHeightSize(tickDelta: Float): Float {
        return Mth.lerp(tickDelta, previewHeightSize, currentHeightSize)
    }

    private fun getDepthSize(tickDelta: Float): Float {
        return Mth.lerp(tickDelta, previewDepthSize, currentDepthSize)
    }

    /** @see ParticleControler.remove() */
    override fun remove() {
        super.remove()
        // FIXME 原版的驱逐队列满后不会调用 markDead，百分百泄漏，
        //  我们的 ParticleManagerMixin 可以确保没问题，
        //  但是一旦关闭 ParticleManagerMixin 注入，就绝对有问题
        // 粒子的移除方法被原版调用时也要移除 controller 否则会内存泄漏
        // 不能放在 controller.remove() 里，因为 markDead 可能在模组外部调用
        ControlParticleManager.removeControl(controlUUID)
    }

    override fun render(vertexConsumer: VertexConsumer, camera: Camera, tickDelta: Float) {
        val q = Quaternionf()
        // 获取摄像机位置
        val cameraPos = camera.position
        val worldPos = interpolator.consume(
            Vec3(xo, yo, zo), Vec3(x, y, z), tickDelta
        )
        // 摄像空间（摄像头位置为原点）
        val lerpPos = (worldPos - cameraPos).toVector3f()
        when (cameraOption) {
            ParticleCameraOption.BILLBOARD -> {
                this.facingCameraMode.setRotation(q, camera, tickDelta)
                if (this.roll != 0f) {
                    q.rotateZ(Mth.lerp(tickDelta, this.oRoll, this.roll))
                }
            }

            ParticleCameraOption.AXIS_BILLBOARD -> {
                val light = this.getLightColor(tickDelta)
                renderAxisBillboardQuad(vertexConsumer, camera, worldPos, lerpPos, tickDelta, light)
                return
            }

            ParticleCameraOption.ROTATION -> {
                q.rotateXYZ(
                    Mth.lerp(tickDelta, this.previewPitch, this.currentPitch),
                    Mth.lerp(tickDelta, this.previewYaw, this.currentYaw),
                    Mth.lerp(tickDelta, this.previewRoll, this.currentRoll)
                )
            }
        }
        // 构建顶点几何
        if (cameraOption == ParticleCameraOption.BILLBOARD) {
            val light = this.getLightColor(tickDelta)
            setParticleTexture(vertexConsumer, q, lerpPos.x, lerpPos.y, lerpPos.z, tickDelta, light)
            return
        }
        val light = this.getLightColor(tickDelta)
        setParticleTexture(
            vertexConsumer,
            q,
            lerpPos.x,
            lerpPos.y,
            lerpPos.z,
            tickDelta,
            light
        )
    }

    private fun renderAxisBillboardQuad(
        vertexConsumer: VertexConsumer,
        camera: Camera,
        worldPos: Vec3,
        lerpPos: Vector3f,
        tickDelta: Float,
        light: Int
    ) {
        val axis = GraphMathHelper.lerp(tickDelta, previewAxis, currentAxis)
        val basis = MinecraftRendererUtil.axialBillboardBasis(axis, camera, worldPos)
        var right = basis.right
        val roll = Mth.lerp(tickDelta, this.previewRoll, this.currentRoll)
        if (roll != 0f) {
            val rollQ = Quaternionf().rotateAxis(
                roll,
                basis.axis.x.toFloat(),
                basis.axis.y.toFloat(),
                basis.axis.z.toFloat()
            )
            val rolled = Vector3f(right.x.toFloat(), right.y.toFloat(), right.z.toFloat()).rotate(rollQ)
            right = Vec3(rolled.x.toDouble(), rolled.y.toDouble(), rolled.z.toDouble())
        }

        renderSizedQuad(
            vertexConsumer,
            lerpPos,
            right,
            basis.axis,
            basis.face,
            getWeightSize(tickDelta),
            getHeightSize(tickDelta),
            getDepthSize(tickDelta),
            light
        )
    }

    private fun setParticleTexture(
        vertexConsumer: VertexConsumer,
        q: Quaternionf,
        x: Float,
        y: Float,
        z: Float,
        tickDelta: Float,
        light: Int
    ) {
        val right = Vector3f(1f, 0f, 0f).rotate(q)
        val up = Vector3f(0f, 1f, 0f).rotate(q)
        val forward = Vector3f(0f, 0f, 1f).rotate(q)
        renderSizedQuad(
            vertexConsumer,
            Vector3f(x, y, z),
            Vec3(right.x.toDouble(), right.y.toDouble(), right.z.toDouble()),
            Vec3(up.x.toDouble(), up.y.toDouble(), up.z.toDouble()),
            Vec3(forward.x.toDouble(), forward.y.toDouble(), forward.z.toDouble()),
            getWeightSize(tickDelta),
            getHeightSize(tickDelta),
            getDepthSize(tickDelta),
            light
        )
    }

    private fun renderSizedQuad(
        consumer: VertexConsumer,
        center: Vector3f,
        right: Vec3,
        up: Vec3,
        forward: Vec3,
        weightSize: Float,
        heightSize: Float,
        depthSize: Float,
        light: Int
    ) {
        if (depthSize == 0f) {
            addDoubleSidedQuad(
                consumer,
                center,
                right,
                up,
                forward,
                weightSize,
                heightSize,
                depthSize,
                light,
                1f,
                -1f,
                0f,
                1f,
                1f,
                0f,
                -1f,
                1f,
                0f,
                -1f,
                -1f,
                0f
            )
            return
        }

        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f, -1f
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f, -1f, -1f, -1f
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1f, 1f, 1f, 1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1f, -1f, -1f, 1f, -1f, 1f, -1f, -1f, 1f, -1f, -1f, -1f
        )
    }

    private fun addBoxFace(
        consumer: VertexConsumer,
        center: Vector3f,
        right: Vec3,
        up: Vec3,
        forward: Vec3,
        weightSize: Float,
        heightSize: Float,
        depthSize: Float,
        light: Int,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float
    ) {
        addDoubleSidedQuad(
            consumer,
            center,
            right,
            up,
            forward,
            weightSize,
            heightSize,
            depthSize,
            light,
            x0,
            y0,
            z0,
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            x3,
            y3,
            z3
        )
    }

    private fun addDoubleSidedQuad(
        consumer: VertexConsumer,
        center: Vector3f,
        right: Vec3,
        up: Vec3,
        forward: Vec3,
        weightSize: Float,
        heightSize: Float,
        depthSize: Float,
        light: Int,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float
    ) {
        addVertex(consumer, center, right, up, forward, x0, y0, z0, u1, v1, weightSize, heightSize, depthSize, light)
        addVertex(consumer, center, right, up, forward, x1, y1, z1, u1, v0, weightSize, heightSize, depthSize, light)
        addVertex(consumer, center, right, up, forward, x2, y2, z2, u0, v0, weightSize, heightSize, depthSize, light)
        addVertex(consumer, center, right, up, forward, x3, y3, z3, u0, v1, weightSize, heightSize, depthSize, light)

        addVertex(consumer, center, right, up, forward, x3, y3, z3, u0, v1, weightSize, heightSize, depthSize, light)
        addVertex(consumer, center, right, up, forward, x2, y2, z2, u0, v0, weightSize, heightSize, depthSize, light)
        addVertex(consumer, center, right, up, forward, x1, y1, z1, u1, v0, weightSize, heightSize, depthSize, light)
        addVertex(consumer, center, right, up, forward, x0, y0, z0, u1, v1, weightSize, heightSize, depthSize, light)
    }

    private fun addVertex(
        consumer: VertexConsumer,
        center: Vector3f,
        right: Vec3,
        up: Vec3,
        forward: Vec3,
        vx: Float,
        vy: Float,
        vz: Float,
        tu: Float,
        tv: Float,
        weightSize: Float,
        heightSize: Float,
        depthSize: Float,
        light: Int
    ) {
        val pos = Vector3f(
            center.x + (right.x * vx * weightSize + up.x * vy * heightSize + forward.x * vz * depthSize).toFloat(),
            center.y + (right.y * vx * weightSize + up.y * vy * heightSize + forward.y * vz * depthSize).toFloat(),
            center.z + (right.z * vx * weightSize + up.z * vy * heightSize + forward.z * vz * depthSize).toFloat()
        )
        consumer
            .addVertex(pos.x, pos.y, pos.z)
            .setUv(tu, tv)
            .setColor(rCol, gCol, bCol, alpha)
            .setLight(light)
    }


    override fun getRenderType(): ParticleRenderType {
        return textureSheet
    }

    /** 在黑夜里粒子也会很亮 */
    override fun getLightColor(partialTick: Float): Int {
        return if (light == -1) {
            LevelRenderer.getLightColor(level, BlockPos(loc.x.toInt(), loc.y.toInt(), loc.z.toInt()))
        } else {
            LightTexture.pack(light, light)
        }
    }

}
