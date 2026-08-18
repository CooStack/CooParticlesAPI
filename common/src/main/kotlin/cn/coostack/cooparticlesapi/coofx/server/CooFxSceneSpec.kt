package cn.coostack.cooparticlesapi.coofx.server

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * CooFX 场景的服务端权威播放模式。
 *
 * 模式只决定客户端是否创建模型、是否跟踪资产 camera；emitter 仍由 [CooFxSceneSpec.emitterId]
 * 是否存在单独控制，避免把粒子发射语义伪装成模型或 camera。
 */
enum class CooFxSceneMode(val id: Int) {
    /** 只绘制模型节点和模型动画。 */
    MODEL(0),

    /** 只跟踪选定资产 camera，不绘制模型。正交 camera 仍会读取，但暂不替换 Minecraft 投影。 */
    CAMERA_TRACKING(1),

    /** 同时绘制模型并跟踪选定资产 camera。 */
    MODEL_AND_CAMERA(2),

    /** 绘制模型并启动资产内置 emitter。 */
    MODEL_AND_EMITTER(3),

    /** 同时绘制模型、跟踪 camera 并启动资产内置 emitter。 */
    MODEL_CAMERA_AND_EMITTER(4),

    /** 只启动资产内置 emitter，不创建模型实例。 */
    EMITTER(5),

    /** 跟踪资产 camera，同时启动资产内置 emitter。 */
    CAMERA_AND_EMITTER(6),
    ;

    companion object {
        /** 只跟踪摄像头、不绘制模型的易用别名。 */
        @JvmField
        val CAMERA_ONLY: CooFxSceneMode = CAMERA_TRACKING

        /** 将网络中的模式 ID 安全映射为本地模式，未知值回退到模型模式。 */
        fun fromId(id: Int): CooFxSceneMode = values().firstOrNull { mode -> mode.id == id } ?: MODEL
    }
}

/**
 * 由服务端创建的 CooFX 场景初始快照。
 *
 * 所有客户端播放入口可表达的模型变换、clip、速度和 emitter 基础覆盖都通过该对象进入服务端，
 * 客户端不会拥有一份可以绕过服务端的权威状态。
 */
data class CooFxSceneSpec(
    val resourceId: ResourceLocation,
    val transform: CooFxWorldTransform,
    val requestSeed: Long,
    val mode: CooFxSceneMode = CooFxSceneMode.MODEL,
    val clipId: String? = null,
    val playbackSpeed: Float = 1F,
    val cameraId: String? = null,
    val cameraPriority: Int = 0,
    /**
     * 只允许该玩家在本地接管 camera；模型仍会同步给所有可见玩家。
     * `null` 表示所有收到场景的玩家都跟踪 camera。
     */
    val cameraTargetPlayer: UUID? = null,
    val emitterId: String? = null,
    val emitterCount: Int? = null,
    val emitterDelayTicks: Int? = null,
    val emitterLifetimeTicks: Int? = null,
    val lifetimeTicks: Long? = null,
    val renderRange: Double = 256.0,
) {
    init {
        require(clipId == null || clipId.isNotBlank()) { "CooFX scene clip id 不能为空" }
        require(playbackSpeed.isFinite() && playbackSpeed >= 0F) { "CooFX scene playbackSpeed 必须非负且有限" }
        require(cameraId == null || cameraId.isNotBlank()) { "CooFX scene camera id 不能为空" }
        require(emitterId == null || emitterId.isNotBlank()) { "CooFX scene emitter id 不能为空" }
        require(emitterCount == null || emitterCount >= 0) { "CooFX scene emitter count 不能为负数" }
        require(emitterDelayTicks == null || emitterDelayTicks >= 0) { "CooFX scene emitter delay 不能为负数" }
        require(emitterLifetimeTicks == null || emitterLifetimeTicks > 0) {
            "CooFX scene emitter lifetime 必须为正数"
        }
        require(lifetimeTicks == null || lifetimeTicks > 0L) { "CooFX scene lifetime 必须为正数" }
        require(renderRange.isFinite() && renderRange > 0.0) { "CooFX scene renderRange 必须为正有限数" }
    }
}

/** 服务端场景的增量更新；为空的字段保持原值。 */
data class CooFxScenePatch(
    val transform: CooFxWorldTransform? = null,
    val mode: CooFxSceneMode? = null,
    val clipId: String? = null,
    val playbackSpeed: Float? = null,
    val cameraId: String? = null,
    val cameraPriority: Int? = null,
    /** 只更新 camera 跟踪目标；清空目标请使用完整 [CooFxSceneSpec] 替换。 */
    val cameraTargetPlayer: UUID? = null,
    val emitterId: String? = null,
    val emitterCount: Int? = null,
    val emitterDelayTicks: Int? = null,
    val emitterLifetimeTicks: Int? = null,
) {
    init {
        require(clipId == null || clipId.isNotBlank()) { "CooFX scene patch clip id 不能为空" }
        require(playbackSpeed == null || playbackSpeed.isFinite() && playbackSpeed >= 0F) {
            "CooFX scene patch playbackSpeed 必须非负且有限"
        }
        require(cameraId == null || cameraId.isNotBlank()) { "CooFX scene patch camera id 不能为空" }
        require(emitterId == null || emitterId.isNotBlank()) { "CooFX scene patch emitter id 不能为空" }
        require(emitterCount == null || emitterCount >= 0) { "CooFX scene patch emitter count 不能为负数" }
        require(emitterDelayTicks == null || emitterDelayTicks >= 0) { "CooFX scene patch emitter delay 不能为负数" }
        require(emitterLifetimeTicks == null || emitterLifetimeTicks > 0) {
            "CooFX scene patch emitter lifetime 必须为正数"
        }
    }
}

/** 服务端场景实例的最小控制句柄，不暴露客户端 GPU 或播放句柄。 */
interface CooFxSceneHandle {
    val sceneId: UUID
    val isActive: Boolean

    fun update(patch: CooFxScenePatch): Boolean

    /** 用完整服务端快照替换资源、模式和全部可配置字段。 */
    fun replace(spec: CooFxSceneSpec): Boolean

    fun stop(): Boolean
}
