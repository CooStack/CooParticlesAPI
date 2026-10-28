package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import cn.coostack.cooparticlesapi.utils.PhysicsUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.core.BlockPos
import net.minecraft.data.AtlasIds
import net.minecraft.util.ARGB
import net.minecraft.util.LightCoordsUtil
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


/**
 * 由控制器驱动的客户端普通粒子，负责插值、碰撞与几何快照提取。
 *
 * 实例由粒子提供器创建，并要求对应 UUID 的控制器已先进入管理器。
 * 渲染提交不得改变生命周期；所有顶点在客户端提取阶段复制。
 *
 * @property controlUUID 管理器中已注册控制器的身份
 * @property cameraOption 几何朝向与插值模式
 */
abstract class ControlableParticle(
    world: ClientLevel,
    pos: Vec3,
    velocity: Vec3,
    val controlUUID: UUID,
    /** 粒子的相机朝向模式 */
    var cameraOption: ParticleCameraOption = ParticleCameraOption.BILLBOARD
) : SingleQuadParticle(
    world,
    pos.x,
    pos.y,
    pos.z,
    velocity.x,
    velocity.y,
    velocity.z,
    Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.PARTICLES).missingSprite(),
) {
    constructor(
        world: ClientLevel,
        pos: Vec3,
        velocity: Vec3,
        controlUUID: UUID,
        faceToCamera: Boolean
    ) : this(world, pos, velocity, controlUUID, ParticleCameraOption.fromFaceToCamera(faceToCamera))

    companion object {
        /** 线性插值的兼容入口，通过无状态工厂返回函数，不保留独立缓存字段。 */
        @JvmStatic
        val LINEAR_INTERPOLATOR: ParticleLerpInterpolator
            get() = ParticleLerpInterpolator { p1, p2, delta -> GraphMathHelper.lerp(delta, p1, p2) }
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
    var textureSheet: ParticleRenderType = CooParticleTextureSheet.PARTICLE_SHEET_LIT

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
    private var currentDepthSize = 0F
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
        get() = (currentWeightSize + currentHeightSize) / 2F
        set(value) {
            currentWeightSize = value
            currentHeightSize = value
            updateRenderSizeBounds()
        }

    private fun updateRenderSizeBounds() {
        val boundSize = maxOf(currentWeightSize, currentHeightSize, currentDepthSize)
        // 对应scale方法
        this.setSize(0.2F * boundSize, 0.2F * boundSize)
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
            alpha = value.coerceIn(0F, 1F)
        }

    var previewPitch: Float = 0F
    var currentPitch: Float = 0F

    var previewYaw: Float = 0F
    var currentYaw: Float = 0F

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

    /**
     * 本 tick 是否需要在写入新位置后把渲染插值的历史位置一并对齐。
     *
     * 由 [teleportToResettingInterpolation] 置位、[tick] 消费，用于路径约束的循环跳转。
     */
    private var resetInterpolation = false


    fun teleportTo(pos: Vec3) {
        lastPreview = pos
        update = true
    }

    fun teleportTo(x: Double, y: Double, z: Double) {
        lastPreview = Vec3(x, y, z)
        update = true
    }

    /**
     * 传送到目标位置，并同时重置渲染插值的历史位置。
     *
     * 示例：路径位置约束的循环跳转会让粒子从终点瞬间回到起点；此时若保留上一 tick 的历史位置，
     * 插值会画出一条穿过整条路径的虚假轨迹。本方法把 `previous` 一起对齐到目标位置。
     * 禁止：常规的逐帧位移不要使用本方法，否则会丢失正常的插值平滑。
     *
     * 历史位置的对齐必须延迟到本 tick 真正写入 [loc] 之后：本方法由 pre-tick action 调用，
     * 而 [tick] 会在那之后无条件执行 `xo = x`，当场写入的 `xo` 会被旧坐标覆盖，
     * 重置因此失效。这里只登记意图，由 [tick] 在应用位移后统一对齐。
     *
     * @param target 目标世界坐标
     */
    fun teleportToResettingInterpolation(target: Vec3) {
        resetInterpolation = true
        teleportTo(target)
    }

    init {
        controler.loadParticle(this)
        controler.particleInit()
    }

    var lastRotate = Vector3f(previewPitch, previewYaw, previewRoll)
    var updateRotate = false
    /** 前一 tick 是否使用路径姿态，决定下一次请求能否连续插值。 */
    private var interpolatePathRotation = false
    /** 本次路径请求是否要求首帧或回绕时直接对齐姿态。 */
    private var resetPathRotation = false
    /** 当前 tick 是否收到了新的路径姿态请求。 */
    private var pathRotationQueued = false

    /**
     * 排队应用路径姿态，tick 结束时先保留旧姿态，再写入目标姿态。
     *
     * 示例：`particle.setPathRotation(rotation, request.pathWrapped())`。
     * 在粒子 tick action 中调用，tick 尾部应用；渲染阶段只读取前后姿态。
     * @param rotation 目标俯仰、偏航和滚转，单位为弧度
     * @param reset 回绕时直接对齐前后姿态，避免跨路径端点插值
     */
    fun setPathRotation(rotation: Vector3f, reset: Boolean) {
        resetPathRotation = reset || !interpolatePathRotation
        interpolatePathRotation = true
        pathRotationQueued = true
        lastRotate.set(rotation)
        updateRotate = true
    }
    fun rotateParticleTo(target: RelativeLocation) {
        rotateParticleTo(Vector3f(target.x.toFloat(), target.y.toFloat(), target.z.toFloat()))
    }

    fun rotateParticleTo(target: Vec3) {
        rotateParticleTo(target.toVector3f())
    }

    fun rotateParticleTo(target: Vector3f) {
        interpolatePathRotation = false
        pathRotationQueued = false
        resetPathRotation = false
        val (x, y, z) = Math3DUtil.calculateEulerAnglesToPoint(target)
        updateRotate = true
        lastRotate = Vector3f(x, y, z)
    }

    fun setAxisLocation(axis: RelativeLocation) {
        this.axis = Vec3(axis.x, axis.y, axis.z)
    }

    /**
     * 防止频繁调用Math3DUtil (让键盘休息一会) 也不用调用 color =
     * Vector3f(xxx/255F,xxx/255F,xxx/255F)
     */
    fun colorOfRGB(r: Int, g: Int, b: Int) {
        color = Math3DUtil.colorOf(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun colorOfRGBA(rgba: Int) {
        val a = (rgba shr 24) and 0xFF
        val r = (rgba shr 16) and 0xFF
        val g = (rgba shr 8) and 0xFF
        val b = rgba and 0xFF
        colorOfRGBA(r, g, b, a / 255F)
    }

    fun colorOfRGBA(r: Int, g: Int, b: Int, alpha: Float) {
        color = Math3DUtil.colorOf(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        this.alpha = alpha.coerceIn(0F, 1F)
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
            if (resetInterpolation) {
                // 路径循环跳转：此时 x/y/z 已是跳转后的位置，把历史位置一并对齐，
                // 否则渲染插值会从前一位置画出一条穿越路径的虚假轨迹。
                xo = x
                yo = y
                zo = z
                resetInterpolation = false
            }
        }
        previewPitch = currentPitch
        previewYaw = currentYaw
        previewRoll = currentRoll
        if (updateRotate) {
            currentPitch = lastRotate.x
            currentYaw = lastRotate.y
            currentRoll = lastRotate.z
            if (resetPathRotation) {
                previewPitch = currentPitch
                previewYaw = currentYaw
                previewRoll = currentRoll
                resetPathRotation = false
            }
            updateRotate = false
        }
        interpolatePathRotation = pathRotationQueued
        pathRotationQueued = false
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
        // 外部直接移除粒子时也要清除控制器；队列驱逐另走 QUEUE 原因，避免触发死亡重生。
        ControlParticleManager.removeControl(controlUUID)
    }

    /**
     * 在提取阶段复制顶点，延迟提交时不再读取粒子的可变状态。
     *
     * @return 摄像机相对坐标中的顶点；禁用绘制时返回 null
     */
    internal fun captureGeometry(camera: Camera, tickDelta: Float): List<ParticleVertex>? {
        if (textureSheet == CooParticleTextureSheet.NO_RENDER) return null
        val vertices = ArrayList<ParticleVertex>()
        val color = ARGB.colorFromFloat(alpha, rCol, gCol, bCol)
        val light = getLightCoords(tickDelta)
        val cameraPos = camera.position()
        val q = Quaternionf()
        val worldPos = interpolator.consume(
            Vec3(xo, yo, zo), Vec3(x, y, z), tickDelta
        )
        // 摄像空间（摄像头位置为原点）
        val lerpPos = (worldPos - cameraPos).toVector3f()
        when (cameraOption) {
            ParticleCameraOption.BILLBOARD -> {
                this.getFacingCameraMode().setRotation(q, camera, tickDelta)
                if (this.roll != 0F) {
                    q.rotateZ(Mth.lerp(tickDelta, this.oRoll, this.roll))
                }
            }

            ParticleCameraOption.AXIS_BILLBOARD -> {
                renderAxisBillboardQuad(vertices, cameraPos, worldPos, lerpPos, tickDelta, light, color)
                return vertices
            }

            ParticleCameraOption.ROTATION -> {
                if (interpolatePathRotation) {
                    q.rotationXYZ(previewPitch, previewYaw, previewRoll).slerp(
                        Quaternionf().rotationXYZ(currentPitch, currentYaw, currentRoll),
                        tickDelta.coerceIn(0F, 1F),
                    )
                } else {
                    q.rotateXYZ(
                        Mth.lerp(tickDelta, this.previewPitch, this.currentPitch),
                        Mth.lerp(tickDelta, this.previewYaw, this.currentYaw),
                        Mth.lerp(tickDelta, this.previewRoll, this.currentRoll)
                    )
                }
            }
        }
        if (cameraOption == ParticleCameraOption.BILLBOARD) {
            setParticleTexture(vertices, q, lerpPos.x, lerpPos.y, lerpPos.z, tickDelta, light, color)
            return vertices
        }
        setParticleTexture(
            vertices,
            q,
            lerpPos.x,
            lerpPos.y,
            lerpPos.z,
            tickDelta,
            light,
            color,
        )
        return vertices
    }

    private fun renderAxisBillboardQuad(
        vertices: MutableList<ParticleVertex>,
        cameraPos: Vec3,
        worldPos: Vec3,
        lerpPos: Vector3f,
        tickDelta: Float,
        light: Int,
        color: Int,
    ) {
        val axis = GraphMathHelper.lerp(tickDelta, previewAxis, currentAxis)
        val basis = MinecraftRendererUtil.axialBillboardBasis(axis, cameraPos, worldPos)
        var right = basis.right
        val roll = Mth.lerp(tickDelta, this.previewRoll, this.currentRoll)
        if (roll != 0F) {
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
            vertices,
            lerpPos,
            right,
            basis.axis,
            basis.face,
            getWeightSize(tickDelta),
            getHeightSize(tickDelta),
            getDepthSize(tickDelta),
            light,
            color,
        )
    }

    private fun setParticleTexture(
        vertices: MutableList<ParticleVertex>,
        q: Quaternionf,
        x: Float,
        y: Float,
        z: Float,
        tickDelta: Float,
        light: Int,
        color: Int,
    ) {
        val right = Vector3f(1F, 0F, 0F).rotate(q)
        val up = Vector3f(0F, 1F, 0F).rotate(q)
        val forward = Vector3f(0F, 0F, 1F).rotate(q)
        renderSizedQuad(
            vertices,
            Vector3f(x, y, z),
            Vec3(right.x.toDouble(), right.y.toDouble(), right.z.toDouble()),
            Vec3(up.x.toDouble(), up.y.toDouble(), up.z.toDouble()),
            Vec3(forward.x.toDouble(), forward.y.toDouble(), forward.z.toDouble()),
            getWeightSize(tickDelta),
            getHeightSize(tickDelta),
            getDepthSize(tickDelta),
            light,
            color,
        )
    }

    private fun renderSizedQuad(
        consumer: MutableList<ParticleVertex>,
        center: Vector3f,
        right: Vec3,
        up: Vec3,
        forward: Vec3,
        weightSize: Float,
        heightSize: Float,
        depthSize: Float,
        light: Int,
        color: Int,
    ) {
        if (depthSize == 0F) {
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
                1F,
                -1F,
                0F,
                1F,
                1F,
                0F,
                -1F,
                1F,
                0F,
                -1F,
                -1F,
                0F,
                color,
            )
            return
        }

        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1F, -1F, 1F, 1F, 1F, 1F, -1F, 1F, 1F, -1F, -1F, 1F, color
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            -1F, -1F, -1F, -1F, 1F, -1F, 1F, 1F, -1F, 1F, -1F, -1F, color
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1F, -1F, -1F, 1F, 1F, -1F, 1F, 1F, 1F, 1F, -1F, 1F, color
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            -1F, -1F, 1F, -1F, 1F, 1F, -1F, 1F, -1F, -1F, -1F, -1F, color
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1F, 1F, 1F, 1F, 1F, -1F, -1F, 1F, -1F, -1F, 1F, 1F, color
        )
        addBoxFace(
            consumer, center, right, up, forward, weightSize, heightSize, depthSize, light,
            1F, -1F, -1F, 1F, -1F, 1F, -1F, -1F, 1F, -1F, -1F, -1F, color
        )
    }

    private fun addBoxFace(
        consumer: MutableList<ParticleVertex>,
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
        z3: Float,
        color: Int,
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
            z3,
            color,
        )
    }

    private fun addDoubleSidedQuad(
        consumer: MutableList<ParticleVertex>,
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
        z3: Float,
        color: Int,
    ) {
        addVertex(consumer, center, right, up, forward, x0, y0, z0, u1, v1, weightSize, heightSize, depthSize, light, color)
        addVertex(consumer, center, right, up, forward, x1, y1, z1, u1, v0, weightSize, heightSize, depthSize, light, color)
        addVertex(consumer, center, right, up, forward, x2, y2, z2, u0, v0, weightSize, heightSize, depthSize, light, color)
        addVertex(consumer, center, right, up, forward, x3, y3, z3, u0, v1, weightSize, heightSize, depthSize, light, color)

        addVertex(consumer, center, right, up, forward, x3, y3, z3, u0, v1, weightSize, heightSize, depthSize, light, color)
        addVertex(consumer, center, right, up, forward, x2, y2, z2, u0, v0, weightSize, heightSize, depthSize, light, color)
        addVertex(consumer, center, right, up, forward, x1, y1, z1, u1, v0, weightSize, heightSize, depthSize, light, color)
        addVertex(consumer, center, right, up, forward, x0, y0, z0, u1, v1, weightSize, heightSize, depthSize, light, color)
    }

    private fun addVertex(
        consumer: MutableList<ParticleVertex>,
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
        light: Int,
        color: Int,
    ) {
        val pos = Vector3f(
            center.x + (right.x * vx * weightSize + up.x * vy * heightSize + forward.x * vz * depthSize).toFloat(),
            center.y + (right.y * vx * weightSize + up.y * vy * heightSize + forward.y * vz * depthSize).toFloat(),
            center.z + (right.z * vx * weightSize + up.z * vy * heightSize + forward.z * vz * depthSize).toFloat()
        )
        consumer.add(ParticleVertex(pos.x, pos.y, pos.z, tu, tv, color, light))
    }

    override fun getGroup(): ParticleRenderType = textureSheet

    override fun getLayer(): SingleQuadParticle.Layer = SingleQuadParticle.Layer.TRANSLUCENT

    /**
     * 从渲染队列驱逐时释放控制器，不执行寿命结束后的重生行为。
     *
     * 此入口供粒子引擎 Mixin 和队列清理使用；重复清理已死亡粒子不会再次通知控制器。
     * 示例：`particle.removeFromQueue()`。
     */
    fun removeFromQueue() {
        if (isAlive) {
            controler.remove(RemoveReason.QUEUE)
        }
    }

    /** 在黑夜里粒子也会很亮 */
    override fun getLightCoords(partialTick: Float): Int {
        return if (light == -1) {
            LightCoordsUtil.getLightCoords(level, BlockPos.containing(loc.x, loc.y, loc.z))
        } else {
            LightCoordsUtil.pack(light, light)
        }
    }

}

/**
 * 提取后的单个粒子顶点，颜色为 ARGB，光照为原版打包值。
 *
 * @property x 摄像机相对横坐标
 * @property y 摄像机相对纵坐标
 * @property z 摄像机相对深度
 * @property u 图集横向纹理坐标
 * @property v 图集纵向纹理坐标
 * @property color 打包后的顶点颜色
 * @property light 打包后的天空与方块光照
 */
internal data class ParticleVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    val u: Float,
    val v: Float,
    val color: Int,
    val light: Int,
)
