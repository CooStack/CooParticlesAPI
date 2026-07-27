package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * # CParticle — GPU 粒子系统的粒子基类(生成描述符)
 *
 * 与 [cn.coostack.cooparticlesapi.particles.ControlableParticle] 不同,
 * [updateMode] 为 [CParticleUpdateMode.DYNAMIC] 时，系统会保留本对象，
 * 并在绘制前同步可变的渲染字段。[CParticleUpdateMode.STATIC] 只在生成时写入一次，
 * 适合不需要逐粒子更新的大型粒子池。位置与速度始终由模拟器或控制句柄管理。
 *
 * 若需要在生成后持续控制单个粒子(composition 语义), 使用
 * [cn.coostack.cooparticlesapi.cparticle.compat.CParticleDisplayer] 返回的句柄
 * ([cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable]).
 *
 * 字段语义与 [ControlableParticleData] 一一对应, 可用 [from] 直接转换.
 */
open class CParticle {
    /** 数据更新方式。默认允许生成后继续修改渲染字段。 */
    var updateMode: CParticleUpdateMode = CParticleUpdateMode.DYNAMIC

    /** 生成位置(世界坐标) */
    var pos: Vec3 = Vec3.ZERO

    /** 初速度 (每tick位移) */
    var velocity: Vec3 = Vec3.ZERO

    /** 是否保持宽高等比 */
    var uniformSize = true

    private var currentWeightSize = 0.2f
    private var currentHeightSize = 0.2f

    /** 粒子宽度 */
    var weightSize: Float
        get() = currentWeightSize
        set(value) {
            currentWeightSize = value
            if (uniformSize) currentHeightSize = value
        }

    /** 粒子高度 */
    var heightSize: Float
        get() = currentHeightSize
        set(value) {
            currentHeightSize = value
            if (uniformSize) currentWeightSize = value
        }

    /** 快捷 size (同时设置宽高) */
    var size: Float
        get() = (currentWeightSize + currentHeightSize) / 2f
        set(value) {
            currentWeightSize = value
            currentHeightSize = value
        }

    /** 颜色 (0..1) */
    var color = Vector3f(1f, 1f, 1f)

    /** 不透明度 (0..1) */
    var alpha = 1f

    /** 初始age */
    var age = 0

    /** 最大生命周期 (tick) */
    var maxAge = 120

    /**
     * 亮度 0..15; -1 = 使用生成位置的世界光照(在生成时采样一次)
     */
    var light = 15

    /** 相机朝向模式 (BILLBOARD / AXIS_BILLBOARD / ROTATION) */
    var cameraOption: ParticleCameraOption = ParticleCameraOption.BILLBOARD

    /** AXIS_BILLBOARD 的固定轴 */
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)

    /** ROTATION 模式水平朝向 (弧度) */
    var yaw = 0f

    /** ROTATION 模式垂直朝向 (弧度) */
    var pitch = 0f

    /** 滚转 (弧度, 所有模式生效) */
    var roll = 0f

    /**
     * ROTATION 使用的指向向量。为 null 时使用 [yaw] 和 [pitch]。
     * 零向量稳定回退到 yaw=0、pitch=0。
     */
    var rotationDirection: Vector3f? = null

    /** 欧拉角速度，单位 rad/tick；x=pitch、y=yaw、z=roll。 */
    var angularVelocity = Vector3f()

    /** 每个整数 tick 为纹理重新选择一个随机帧，不改变真实年龄。 */
    var randomAgePreTick = false

    /** 随机纹理序列的种子。null 表示生成粒子时自动分配。 */
    var randomSeed: Int? = null

    /**
     * 此粒子的模拟速度上限；`null` 表示使用所属 system 的 [CParticleSystem.speedLimit]。
     *
     * Example: emitter data 会把自己的 `speedLimit` 写入此字段，从而允许同池粒子使用不同限速。
     * Forbidden: 不要设置负值；负值会被夹到 `0`。
     */
    var speedLimit: Float? = null

    private var dynamicDataSource: ControlableCParticleData? = null

    private var appearanceRevisionCounter = 0
    private var cachedAppearanceRevision = Int.MIN_VALUE
    private var cachedAppearanceDescriptorId = CParticleAppearanceDescriptors.IDENTITY_DESCRIPTOR_ID

    /** 按生命周期在 GPU 中采样的不透明度乘数曲线。 */
    var alphaCurve: CParticleCurve? = null
        set(value) {
            if (field === value) return
            field = value
            appearanceRevisionCounter++
        }

    /** 按生命周期同时缩放 X/Y 尺寸的 GPU 曲线。 */
    var sizeCurve: CParticleCurve? = null
        set(value) {
            if (field === value) return
            field = value
            appearanceRevisionCounter++
        }

    /** 按生命周期与基础颜色相乘的 GPU RGB 曲线。 */
    var colorCurve: CParticleColorCurve? = null
        set(value) {
            if (field === value) return
            field = value
            appearanceRevisionCounter++
        }

    /** 当前逐粒子生命周期外观的单调修订号。 */
    internal val appearanceRevision: Int
        get() = appearanceRevisionCounter

    /**
     * 注册或复用当前生命周期外观描述符。
     *
     * Example: 不同粒子持有等值曲线时返回同一个 ID。
     * Forbidden: age 推进不调用本方法，也不会改变 descriptor。
     */
    internal fun appearanceDescriptorId(): Int {
        if (cachedAppearanceRevision == appearanceRevisionCounter) return cachedAppearanceDescriptorId
        cachedAppearanceDescriptorId = CParticleAppearanceDescriptors.register(alphaCurve, sizeCurve, colorCurve)
        cachedAppearanceRevision = appearanceRevisionCounter
        return cachedAppearanceDescriptorId
    }

    private var textureRevisionCounter = 0
    private var cachedLegacyTextureRevision = Int.MIN_VALUE
    private var cachedLegacyTextureSource: CParticleTextureSource? = null

    /**
     * 显式通用纹理来源，优先于旧 [sprite] 和 [effect]。
     *
     * 粒子进入系统后，DYNAMIC 只允许把来源改成相同 bindingKey 的来源；跨 binding 会安全移除槽位。
     * Example: `particle.textureSource = textureOfBlock(state)`。
     * Forbidden: 不要在同一存活槽位中从方块图集切换到独立纹理。
     */
    var textureSource: CParticleTextureSource? = null
        set(value) {
            if (field === value) return
            field = value
            textureRevisionCounter++
        }

    /**
     * 粒子贴图 (粒子图集 sprite id, 例如 `minecraft:end_rod` / `minecraft:glitter_0`)
     *
     * 为 null 时使用 [CParticleSprites.DEFAULT] (end_rod)
     */
    var sprite: ResourceLocation? = null
        set(value) {
            if (field == value) return
            field = value
            if (textureSource == null) textureRevisionCounter++
        }

    /**
     * 原版粒子类型对应的 SpriteSet。未指定 [sprite] 时按 age/maxAge 选择帧。
     * 实现 [CParticleTextureSourceProvider] 的 effect 会改用它声明的通用来源。
     */
    var effect: ParticleOptions? = null
        set(value) {
            val oldValue = field
            field = value
            val sourceChanged = if (
                oldValue is CParticleTextureSourceProvider || value is CParticleTextureSourceProvider
            ) {
                oldValue !== value
            } else {
                oldValue?.type != value?.type
            }
            if (textureSource == null && sprite == null && sourceChanged) {
                textureRevisionCounter++
            }
        }

    /**
     * 当前纹理配置的单调修订号。
     *
     * Example: DYNAMIC store 只在此值或资源代数变化时重新解析纹理。
     * Forbidden: age、颜色和旋转变化不会增加该值。
     */
    internal val textureRevision: Int
        get() = textureRevisionCounter

    /**
     * 返回通用来源，同时保留旧 `sprite > effect > default` 规则。
     *
     * 旧来源会按 [textureRevision] 缓存，普通 DYNAMIC 帧不会创建临时对象。
     * Example: 只设置 [effect] 时返回 [CParticleTextureSource.ParticleEffect]。
     * Forbidden: 调用方不得修改返回来源内部持有的 ItemStack 快照。
     *
     * @return 当前实际纹理来源
     */
    internal fun effectiveTextureSource(): CParticleTextureSource {
        textureSource?.let { return it }
        if (cachedLegacyTextureRevision == textureRevisionCounter) {
            cachedLegacyTextureSource?.let { return it }
        }
        val resolved = when {
            sprite != null -> textureOfParticleSprite(sprite!!)
            effect != null -> textureOfEffect(effect!!)
            else -> CParticleTextureSource.ParticleEffect(ParticleTypes.END_ROD, animateByAge = false)
        }
        cachedLegacyTextureSource = resolved
        cachedLegacyTextureRevision = textureRevisionCounter
        return resolved
    }

    fun sprite(namespace: String, path: String): CParticle {
        sprite = ResourceLocation.fromNamespaceAndPath(namespace, path)
        return this
    }

    /**
     * 设置通用纹理来源并返回当前粒子。
     *
     * Example: `CParticle().texture(textureOf(textureId))`。
     * Forbidden: 存活 DYNAMIC 粒子不能用此方法切换到另一 binding。
     *
     * @param source 新纹理来源
     * @return 当前粒子，便于链式配置
     */
    fun texture(source: CParticleTextureSource): CParticle {
        textureSource = source
        return this
    }

    open fun clone(): CParticle {
        return CParticle().also { copyTo(it) }
    }

    protected fun copyTo(target: CParticle) {
        target.updateMode = updateMode
        target.pos = pos
        target.velocity = velocity
        target.uniformSize = uniformSize
        target.weightSize = currentWeightSize
        target.heightSize = currentHeightSize
        target.color = Vector3f(color)
        target.alpha = alpha
        target.age = age
        target.maxAge = maxAge
        target.light = light
        target.cameraOption = cameraOption
        target.axis = axis
        target.yaw = yaw
        target.pitch = pitch
        target.roll = roll
        target.rotationDirection = rotationDirection?.let(::Vector3f)
        target.angularVelocity = Vector3f(angularVelocity)
        target.randomAgePreTick = randomAgePreTick
        target.randomSeed = randomSeed
        target.speedLimit = speedLimit
        target.alphaCurve = alphaCurve
        target.sizeCurve = sizeCurve
        target.colorCurve = colorCurve
        target.sprite = sprite
        target.effect = effect
        target.textureSource = textureSource
    }

    /**
     * 从 emitter 的 DYNAMIC data 刷新允许逐帧同步的字段。
     *
     * Example: 调用方修改原 data 的颜色或同 binding 纹理后，store 在下一次准备阶段读取新值。
     * Forbidden: 位置、速度、age 和 maxAge 由模拟器管理，不在这里覆盖。
     */
    internal fun refreshDynamicDataSource() {
        val data = dynamicDataSource ?: return
        uniformSize = data.uniformSize
        weightSize = data.weightSize
        heightSize = data.heightSize
        color.set(data.color)
        alpha = data.alpha
        light = data.light
        cameraOption = data.cameraOption
        axis = data.axis
        yaw = data.yaw
        pitch = data.pitch
        roll = data.roll
        effect = data.effect
        textureSource = data.textureSource
        alphaCurve = data.alphaCurve
        sizeCurve = data.sizeCurve
        colorCurve = data.colorCurve
        val direction = data.rotationDirection
        if (direction == null) {
            rotationDirection = null
        } else {
            val current = rotationDirection
            if (current == null) rotationDirection = Vector3f(direction) else current.set(direction)
        }
        angularVelocity.set(data.angularVelocity)
        randomAgePreTick = data.randomAgePreTick
        randomSeed = data.randomSeed
        speedLimit = data.speedLimit.toFloat().coerceAtLeast(0f)
    }

    /**
     * 把模拟器推进后的年龄发布回关联的 emitter DYNAMIC data。
     *
     * Example: 保留 data 引用的调用方可观察当前 age。
     * Forbidden: STATIC 粒子不会关联 data，也不会执行写回。
     */
    internal fun publishAgeToDynamicData() {
        dynamicDataSource?.age = age
    }

    companion object {
        /**
         * 从现有 emitter 数据 ([ControlableParticleData]) 转换.
         * effect 的 SpriteSet 在客户端生成和动态更新时解析。
         */
        @JvmStatic
        fun from(data: ControlableParticleData): CParticle {
            return CParticle().also {
                it.velocity = data.velocity
                it.uniformSize = data.uniformSize
                it.weightSize = data.weightSize
                it.heightSize = data.heightSize
                it.color = Vector3f(data.color)
                it.alpha = data.alpha
                it.age = data.age
                it.maxAge = data.maxAge
                it.light = data.light
                it.cameraOption = data.cameraOption
                it.axis = data.axis
                it.yaw = data.yaw
                it.pitch = data.pitch
                it.roll = data.roll
                it.effect = data.effect
                if (data is ControlableCParticleData) {
                    it.updateMode = data.updateMode
                    it.textureSource = data.textureSource
                    it.alphaCurve = data.alphaCurve
                    it.sizeCurve = data.sizeCurve
                    it.colorCurve = data.colorCurve
                    it.rotationDirection = data.rotationDirection?.let(::Vector3f)
                    it.angularVelocity = Vector3f(data.angularVelocity)
                    it.randomAgePreTick = data.randomAgePreTick
                    it.randomSeed = data.randomSeed
                    it.speedLimit = data.speedLimit.toFloat().coerceAtLeast(0f)
                    if (data.updateMode == CParticleUpdateMode.DYNAMIC) {
                        it.dynamicDataSource = data
                    }
                }
            }
        }
    }
}
