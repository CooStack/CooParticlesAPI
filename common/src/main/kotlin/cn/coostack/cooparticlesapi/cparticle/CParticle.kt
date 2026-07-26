package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.core.particles.ParticleOptions
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
     * 粒子贴图 (粒子图集 sprite id, 例如 `minecraft:end_rod` / `minecraft:glitter_0`)
     *
     * 为 null 时使用 [CParticleSprites.DEFAULT] (end_rod)
     */
    var sprite: ResourceLocation? = null

    /**
     * 原版粒子类型对应的 SpriteSet。未指定 [sprite] 时按 age/maxAge 选择帧。
     */
    var effect: ParticleOptions? = null

    fun sprite(namespace: String, path: String): CParticle {
        sprite = ResourceLocation.fromNamespaceAndPath(namespace, path)
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
        target.sprite = sprite
        target.effect = effect
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
            }
        }
    }
}
