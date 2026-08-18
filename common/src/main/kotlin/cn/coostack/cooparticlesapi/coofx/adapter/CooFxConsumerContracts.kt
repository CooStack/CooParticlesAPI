package cn.coostack.cooparticlesapi.coofx.adapter

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Matrix4f
import org.joml.Matrix4fc
import kotlin.math.sqrt

sealed interface CooFxParameterValue {
    data class FloatValue(val value: Float) : CooFxParameterValue {
        init {
            require(value.isFinite()) { "Float parameter must be finite" }
        }
    }

    data class IntValue(val value: Int) : CooFxParameterValue
    data class BooleanValue(val value: Boolean) : CooFxParameterValue
    data class ResourceValue(val value: ResourceLocation) : CooFxParameterValue
}

data class CooFxWorldTransform(
    val x: Double,
    val y: Double,
    val z: Double,
    val rotationX: Float = 0.0F,
    val rotationY: Float = 0.0F,
    val rotationZ: Float = 0.0F,
    val rotationW: Float = 1.0F,
    val scaleX: Float = 1.0F,
    val scaleY: Float = 1.0F,
    val scaleZ: Float = 1.0F
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "World position must be finite" }
        require(rotationX.isFinite() && rotationY.isFinite() && rotationZ.isFinite() && rotationW.isFinite()) {
            "World rotation must be finite"
        }
        val rotationLength = sqrt(
            rotationX * rotationX + rotationY * rotationY + rotationZ * rotationZ + rotationW * rotationW
        )
        require(rotationLength in 0.9999F..1.0001F) { "World rotation must be normalized" }
        require(scaleX.isFinite() && scaleY.isFinite() && scaleZ.isFinite()) { "World scale must be finite" }
        require(scaleX > 0.0F && scaleY > 0.0F && scaleZ > 0.0F) { "World scale must be positive" }
    }
}

object CooFxEmitterParameterNames {
    /** 覆盖一次 burst 的发射数量。 */
    const val COUNT = "count"

    /** 覆盖 burst 的延迟 tick。 */
    const val DELAY_TICKS = "delayTicks"

    /** 覆盖 burst 粒子的统一 lifetime tick。 */
    const val LIFETIME_TICKS = "lifetimeTicks"

    /** 覆盖 emitter 粒子节点 clip 的播放速度。 */
    const val PLAYBACK_SPEED = "playbackSpeed"
}

class CooFxPlayRequest(
    val resourceId: ResourceLocation,
    val transform: CooFxWorldTransform,
    val requestSeed: Long,
    val clipId: String? = null,
    val emitterId: String? = null,
    parameterOverrides: Map<String, CooFxParameterValue> = emptyMap()
) {
    val parameterOverrides: Map<String, CooFxParameterValue> = parameterOverrides.toMap()

    init {
        require(clipId == null || clipId.isNotBlank()) { "Clip id must not be blank" }
        require(emitterId == null || emitterId.isNotBlank()) { "Emitter id must not be blank" }
        require(this.parameterOverrides.keys.all { it.isNotBlank() }) { "Parameter name must not be blank" }
    }
}

/**
 * 创建一个独立模型实例的客户端请求。
 *
 * 请求渲染资产选定 scene 中的全部 mesh node，不读取或伪造 emitter。[clipId] 为空时使用第一个
 * compiled clip；资产没有动画时保持 bind pose。[playbackSpeed] 为零时暂停在起始姿态。
 */
class CooFxModelPlayRequest(
    val resourceId: ResourceLocation,
    val transform: CooFxWorldTransform,
    val requestSeed: Long,
    val clipId: String? = null,
    val playbackSpeed: Float = 1F,
) {
    init {
        require(clipId == null || clipId.isNotBlank()) { "Clip id must not be blank" }
        require(playbackSpeed.isFinite() && playbackSpeed >= 0F) {
            "Model playback speed must be finite and non-negative"
        }
        require(transform.x.toFloat().isFinite() && transform.y.toFloat().isFinite() && transform.z.toFloat().isFinite()) {
            "Model world position must fit the float instance ABI"
        }
    }
}

data class CooFxEmitterRequest(
    val resourceId: ResourceLocation,
    val emitterId: String,
    val transform: CooFxWorldTransform,
    val requestSeed: Long
) {
    init {
        require(emitterId.isNotBlank()) { "Emitter id must not be blank" }
    }

    fun asPlayRequest(): CooFxPlayRequest = CooFxPlayRequest(
        resourceId = resourceId,
        transform = transform,
        requestSeed = requestSeed,
        emitterId = emitterId
    )
}

data class CooFxPlaybackFailure(
    val resourceId: ResourceLocation,
    val stage: String,
    val message: String,
    val cause: Throwable? = null
) {
    init {
        require(stage.isNotBlank()) { "Failure stage must not be blank" }
        require(message.isNotBlank()) { "Failure message must not be blank" }
    }
}

interface CooFxPlaybackHandle {
    val instanceId: Long
    val isAlive: Boolean
    fun stop()
}

sealed interface CooFxPlayResult {
    data class Started(val handle: CooFxPlaybackHandle) : CooFxPlayResult
    data class Queued(val requestId: Long) : CooFxPlayResult
    data class Failed(val failure: CooFxPlaybackFailure) : CooFxPlayResult
}

/** 纯模型实例创建结果；与需要 emitter 的粒子播放结果保持类型隔离。 */
sealed interface CooFxModelPlayResult {
    data class Started(val handle: CooFxPlaybackHandle) : CooFxModelPlayResult
    data class Queued(val requestId: Long) : CooFxModelPlayResult
    data class Failed(val failure: CooFxPlaybackFailure) : CooFxModelPlayResult
}

class CooFxFrameRequest(
    val partialTick: Float,
    val backendCapabilitySignature: String,
    viewMatrix: Matrix4fc,
    projectionMatrix: Matrix4fc,
    val cameraX: Double = 0.0,
    val cameraY: Double = 0.0,
    val cameraZ: Double = 0.0,
) {
    val viewMatrix = Matrix4f(viewMatrix)
    val projectionMatrix = Matrix4f(projectionMatrix)

    init {
        require(partialTick.isFinite() && partialTick in 0.0F..1.0F) {
            "Partial tick must be finite and between 0 and 1"
        }
        require(backendCapabilitySignature.isNotBlank()) { "Backend capability signature must not be blank" }
        require(viewMatrix.isFinite && projectionMatrix.isFinite) { "Frame matrices must be finite" }
        require(cameraX.isFinite() && cameraY.isFinite() && cameraZ.isFinite()) {
            "Frame camera position must be finite"
        }
    }
}

/**
 * CooFX client runtime 暴露给生命周期适配层的最小端口。
 *
 * 实现拥有 playback 与 mesh runtime；loader 和现有 manager 只调用对应生命周期方法，不复制 importer、
 * compiler、上传或模拟逻辑。[renderWorldFrame] 由现有 Coo Pipeline world pass 内部委托触发。
 */
interface CooFxConsumerRuntime {
    fun play(request: CooFxPlayRequest): CooFxPlayResult
    fun tickClient()
    fun renderWorldFrame(request: CooFxFrameRequest)
    fun clearTransientWorldState()
    fun reloadResources(resourceManager: ResourceManager)
    fun releaseRenderResources()
    fun stopClient()
}
