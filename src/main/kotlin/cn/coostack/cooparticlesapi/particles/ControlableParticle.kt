package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.client.particle.SpriteBillboardParticle
import net.minecraft.client.render.Camera
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.*


@Environment(EnvType.CLIENT)
abstract class ControlableParticle(
    world: ClientWorld,
    pos: Vec3d,
    velocity: Vec3d,
    val controlUUID: UUID,
    /**
     * 是否始终转向玩家(默认实现)
     */
    val faceToCamera: Boolean = true
) : SpriteBillboardParticle(world, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z) {
    val controler: ParticleControler = ControlParticleManager.getControl(controlUUID)!!

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
    var textureSheet: ParticleTextureSheet = ParticleTextureSheet.PARTICLE_SHEET_LIT

    /**
     * 是否调用 net.minecraft.client.particle.Particle中的tick方法
     */
    var minecraftTick: Boolean = false

    /**
     * @see x
     * @see y
     * @see z
     */
    var pos: Vec3d
        get() = Vec3d(x, y, z)
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
        get() = super.scale
        set(value) {
            super.scale = value
            // 对应scale方法
            this.setBoundingBoxSpacing(0.2f * scale, 0.2f * scale)
        }

    /**
     * @see prevPosX
     * @see prevPosY
     * @see prevPosZ
     */
    var prevPos: Vec3d
        get() = Vec3d(prevPosX, prevPosY, prevPosZ)
        set(value) {
            this.prevPosX = value.x
            this.prevPosY = value.y
            this.prevPosZ = value.z
        }

    /**
     * @see velocityX
     * @see velocityY
     * @see velocityZ
     */
    var velocity: Vec3d
        get() = Vec3d(velocityX, velocityY, velocityZ)
        set(value) {
            this.velocityX = value.x
            this.velocityY = value.y
            this.velocityZ = value.z
        }

    /**
     * @see boundingBox
     */
    var bounding: Box
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
     * @see collidesWithWorld
     */
    var collidesWithTheWorld: Boolean
        get() = collidesWithWorld
        set(value) {
            collidesWithWorld = value
        }

    /**
     * @see dead
     */
    var death: Boolean
        get() = dead
        set(value) = if (value) {
            markDead()
        } else {
            dead = false
        }

    /**
     * @see spacingXZ
     * @see spacingY
     */
    var spacing: Vector2f
        get() = Vector2f(spacingXZ, spacingY)
        set(value) {
            spacingXZ = value.x
            spacingY = value.y
        }

    /**
     * @see random
     */
    val rand: Random
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
    var gravity: Float
        get() = gravityStrength
        set(value) {
            gravityStrength = value
        }

    var color: Vector3f
        get() = Vector3f(red, green, blue)
        set(value) {
            red = value.x
            green = value.y
            blue = value.z
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
        get() = prevAngle
        set(value) {
            prevAngle = value
        }

    /**
     * @see angle
     */
    var currentAngleZ: Float
        get() = angle
        set(value) {
            angle = value
        }

    /**
     * @see velocityMultiplier
     */
    var velocityMulti: Float
        get() = velocityMultiplier
        set(value) {
            velocityMultiplier = value
        }


    /**
     * @see ascending
     * 让粒子乱飘的罪恶源头?
     */
    var canAscending: Boolean
        get() = ascending
        set(value) {
            ascending = value
        }


    private var lastPreview = cloneVec(pos)
    private var update = false


    fun teleportTo(pos: Vec3d) {
        lastPreview = cloneVec(pos)
        update = true
    }

    fun teleportTo(x: Double, y: Double, z: Double) {
        lastPreview = Vec3d(x, y, z)
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

    fun rotateParticleTo(target: Vec3d) {
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

    fun colorOfRGBA(r: Int, g: Int, b: Int, alpha: Float) {
        color = Math3DUtil.colorOf(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        this.alpha = alpha.coerceIn(0f, 1f)
    }

    /**
     * 请使用作为tick方法
     * @see ParticleControler.addPreTickAction
     */
    final override fun tick() {
        if (age > maxAge) {
            age = maxAge
        }

        if (minecraftTick) {
            super.tick()
        }
        controler.doTick()
        prevPos = this.pos
        if (update) {
            if (!minecraftTick) {
                this.boundingBox = Box.of(
                    this.pos,
                    this.boundingBox.maxX - this.boundingBox.minX,
                    this.boundingBox.maxY - this.boundingBox.minY,
                    this.boundingBox.maxZ - this.boundingBox.minZ,
                )
            }
            prevPos = this.pos
            this.pos = lastPreview
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

    /**
     * @see ParticleControler.remove()
     */
    override fun markDead() {
        super.markDead()
        // FIXME 原版的驱逐队列满后不会调用 markDead，百分百泄漏，
        //  我们的 ParticleManagerMixin 可以确保没问题，
        //  但是一旦关闭 ParticleManagerMixin 注入，就绝对有问题
        // 粒子的移除方法被原版调用时也要移除 controller 否则会内存泄漏
        // 不能放在 controller.remove() 里，因为 markDead 可能在模组外部调用
        ControlParticleManager.removeControl(controlUUID)
    }

    override fun buildGeometry(vertexConsumer: VertexConsumer, camera: Camera, tickDelta: Float) {
        if (faceToCamera) {
            super.buildGeometry(vertexConsumer, camera, tickDelta)
            return
        }
        // 获取摄像机位置
        val cameraPos = camera.pos
        val x = (MathHelper.lerp(tickDelta.toDouble(), this.prevPosX, this.x) - cameraPos.getX()).toFloat()
        val y = (MathHelper.lerp(tickDelta.toDouble(), this.prevPosY, this.y) - cameraPos.getY()).toFloat()
        val z = (MathHelper.lerp(tickDelta.toDouble(), this.prevPosZ, this.z) - cameraPos.getZ()).toFloat()
        val q = Quaternionf()
        q.rotateXYZ(
            MathHelper.lerp(tickDelta, this.previewAngleX, this.currentAngleX),
            MathHelper.lerp(tickDelta, this.previewAngleY, this.currentAngleY),
            MathHelper.lerp(tickDelta, this.previewAngleZ, this.currentAngleZ)
        )
        // 构建顶点几何
        val light = this.getBrightness(tickDelta)
        setParticleTexture(vertexConsumer, q, x, y, z, tickDelta, light)
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
        val s = getSize(tickDelta)

        addVertex(
            vertexConsumer, q, x, y, z, 1f, -1f, maxU, maxV, s, light
        )
        addVertex(
            vertexConsumer, q, x, y, z, 1f, 1f, maxU, minV, s, light
        )
        addVertex(
            vertexConsumer, q, x, y, z, -1f, 1f, minU, minV, s, light
        )
        addVertex(
            vertexConsumer, q, x, y, z, -1f, -1f, minU, maxV, s, light
        )

        // 背面
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = -1f, // 左下角 X
            vy = -1f,
            tu = minU, // UV 镜像
            tv = maxV,
            size = s,
            light = light
        )
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = -1f, // 左上角 X
            vy = 1f,
            tu = minU,
            tv = minV,
            size = s,
            light = light
        )
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = 1f,  // 右上角 X
            vy = 1f,
            tu = maxU, // UV 镜像
            tv = minV,
            size = s,
            light = light
        )
        addVertex(
            vertexConsumer, q, x, y, z,
            vx = 1f,  // 右下角 X
            vy = -1f,
            tu = maxU,
            tv = maxV,
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
        consumer.vertex(pos.x, pos.y, pos.z)
            .texture(tu, tv)
            .color(red, green, blue, alpha)
            .light(light)
    }

    override fun getType(): ParticleTextureSheet? {
        return textureSheet
    }

    private fun cloneVec(vec: Vec3d): Vec3d {
        return Vec3d(vec.x, vec.y, vec.z)
    }

    /**
     * 在黑夜里粒子也会很亮
     */
    override fun getBrightness(tint: Float): Int {
        return if (light == -1) {
            world.getLightLevel(BlockPos.ofFloored(pos))
        } else {
            LightmapTextureManager.pack(light,light)
        }
    }

}
