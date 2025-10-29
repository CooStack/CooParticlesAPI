package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
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
    /**
     * 是否始终转向玩家(默认实现)
     */
    val faceToCamera: Boolean = true
) : TextureSheetParticle(world, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z) {


    companion object {
        @JvmStatic
        val LINEAR_INTERPOLATOR: ParticleLerpInterpolator = ParticleLerpInterpolator { p1, p2, delta ->
            GraphMathHelper.lerp(delta, p1, p2)
        }
    }

    /**
     * 插值修改器
     */
    var interpolator: ParticleLerpInterpolator = LINEAR_INTERPOLATOR
        private set
    val controler: ParticleControler = ControlParticleManager.getControl(controlUUID)!!

    /**
     * 穿过液体的标记
     * 用于适配 ParticleOnLiquidEvent
     */
    internal var crossLiquid = false

    /**
     * 粒子亮度
     * 设置为-1则为环境亮度
     */
    var light = 15
        set(value) {
            if (value == -1) {
                field = value
                return
            }
            field = value.coerceIn(0, 15)
        }

    /**
     * 粒子渲染类型
     * 可以使用
     *
     */
    var textureSheet: ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_LIT

    /**
     * 是否调用 net.minecraft.client.particle.Particle中的tick方法
     */
    var minecraftTick: Boolean = false

    /**
     * @see world
     */
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

    /**
     * @see scale
     * 粒子尺寸
     */
    var size: Float
        get() = super.quadSize
        set(value) {
            super.quadSize = value
            // 对应scale方法
            this.setSize(0.2f * size, 0.2f * size)
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

    /**
     * @see boundingBox
     */
    var bounding: AABB
        get() = boundingBox
        set(value) {
            boundingBox = value
        }

    /**
     * @see onGround
     */
    var onTheGround: Boolean
        get() = onGround
        set(value) {
            onGround = value
        }

    /**
     * @see hasPhysics fabric ->collidesWithWorld
     */
    var collidesWithTheWorld: Boolean
        get() = hasPhysics
        set(value) {
            hasPhysics = value
        }

    /**
     * @see dead
     */
    var death: Boolean
        get() = removed
        set(value) = if (value) {
            remove()
        } else {
            removed = false
        }

    /**
     * byd neoforge什么傻逼mapping
     * @see bbWidth - > fabric spacingXZ
     * @see bbHeight - > Fabric spacingY
     */
    var spacing: Vector2f
        get() = Vector2f(bbWidth, bbHeight)
        set(value) {
            bbWidth = value.x
            bbHeight = value.y
        }

    /**
     * @see random
     */
    val rand: RandomSource
        get() = random

    /**
     * @see age
     */
    var currentAge: Int
        get() = age
        set(value) {
            age = value
        }


    /**
     * @see gravityStrength
     */
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

    var previewAngleX: Float = 0f
    var currentAngleX: Float = 0f

    var previewAngleY: Float = 0f
    var currentAngleY: Float = 0f

    /**
     * @see prevAngle
     */
    var previewAngleZ: Float
        get() = oRoll
        set(value) {
            oRoll = value
        }

    /**
     * @see angle
     */
    var currentAngleZ: Float
        get() = roll
        set(value) {
            super.roll = value
        }

    /**
     * @see velocityMultiplier
     */
    var velocityMulti: Float
        get() = friction
        set(value) {
            friction = value
        }


    /**
     * @see speedUpWhenYMotionIsBlocked -> fabric ascending
     * 让粒子乱飘的罪恶源头?
     */
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

    var lastRotate = Vector3f(previewAngleX, previewAngleY, previewAngleZ)
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

    /**
     * 防止频繁调用Math3DUtil (让键盘休息一会)
     * 也不用调用 color = Vector3f(xxx/255f,xxx/255f,xxx/255f)
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
     * 请使用作为tick方法
     * @see ParticleControler.addPreTickAction
     */
    final override fun tick() {
        if (age > lifetime) {
            age = lifetime
        }

        if (minecraftTick) {
            super.tick()
        }
        controler.doTick()
        xo = x
        yo = y
        zo = z
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
        previewAngleX = currentAngleX
        previewAngleY = currentAngleY
        previewAngleZ = currentAngleZ
        if (updateRotate) {
            currentAngleX = lastRotate.x
            currentAngleY = lastRotate.y
            currentAngleZ = lastRotate.z
            updateRotate = false
        }
    }

    fun setInterpolator(newInterpolator: ParticleLerpInterpolator): ControlableParticle {
        this.interpolator = newInterpolator
        return this
    }

    /**
     * @see ParticleControler.remove()
     */
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
        // 摄像空间（摄像头位置为原点）
        val lerpPos = (interpolator.consume(
            Vec3(xo, yo, zo), Vec3(x, y, z), tickDelta
        ) - cameraPos).toVector3f()
        if (faceToCamera) {
            this.facingCameraMode.setRotation(q, camera, tickDelta)
            if (this.roll != 0f) {
                q.rotateZ(Mth.lerp(tickDelta, this.oRoll, this.roll))
            }
        } else {
            q.rotateXYZ(
                Mth.lerp(tickDelta, this.previewAngleX, this.currentAngleX),
                Mth.lerp(tickDelta, this.previewAngleY, this.currentAngleY),
                Mth.lerp(tickDelta, this.previewAngleZ, this.currentAngleZ)
            )
        }
        // 构建顶点几何
        if (faceToCamera) {
            this.renderRotatedQuad(vertexConsumer, q, lerpPos.x, lerpPos.y, lerpPos.z, tickDelta);
            return
        }
        val light = this.getLightColor(tickDelta)
        setParticleTexture(vertexConsumer, q, lerpPos.x, lerpPos.y, lerpPos.z, tickDelta, light)
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
        val s = getQuadSize(tickDelta)
        u1

        addVertex(
            vertexConsumer, q, x, y, z, 1f, -1f, u1, v1, s, light
        )
        addVertex(
            vertexConsumer, q, x, y, z, 1f, 1f, u1, v0, s, light
        )
        addVertex(
            vertexConsumer, q, x, y, z, -1f, 1f, u0, v0, s, light
        )
        addVertex(
            vertexConsumer, q, x, y, z, -1f, -1f, u0, v1, s, light
        )

        // 背面
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = -1f, // 左下角 X
            vy = -1f,
            tu = u0, // UV 镜像
            tv = v1,
            size = s,
            light = light
        )
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = -1f, // 左上角 X
            vy = 1f,
            tu = u0,
            tv = v0,
            size = s,
            light = light
        )
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = 1f,  // 右上角 X
            vy = 1f,
            tu = u1, // UV 镜像
            tv = v0,
            size = s,
            light = light
        )
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = 1f,  // 右下角 X
            vy = -1f,
            tu = u1,
            tv = v1,
            size = s,
            light = light
        )
    }

    private fun addVertex(
        consumer: VertexConsumer,
        q: Quaternionf,
        dx: Float,
        dy: Float,
        dz: Float,
        vx: Float,
        vy: Float,
        tu: Float,
        tv: Float,
        size: Float,
        light: Int
    ) {
        val pos = Vector3f(vx, vy, 0f).rotate(q).mul(size).add(dx, dy, dz)
        consumer.addVertex(pos.x, pos.y, pos.z)
            .setUv(tu, tv)
            .setColor(rCol, gCol, bCol, alpha)
            .setLight(light)
    }


    override fun getRenderType(): ParticleRenderType {
        return textureSheet
    }

    /**
     * 在黑夜里粒子也会很亮
     */
    override fun getLightColor(partialTick: Float): Int {
        return if (light == -1) {
            LevelRenderer.getLightColor(level, BlockPos(loc.x.toInt(), loc.y.toInt(), loc.z.toInt()))
        } else {
            LightTexture.pack(light, light)
        }
    }

}
