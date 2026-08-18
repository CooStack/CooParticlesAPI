package cn.coostack.cooparticlesapi.coofx.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * CooFX 的服务端权威 RenderEntity 载体。
 *
 * 该实体只负责保存可同步状态和参与 [ServerRenderEntityManager] 的
 * 可见性生命周期；模型、emitter、camera 的客户端句柄由独立 client registry 创建，服务端不触碰 GPU。
 */
@CooAutoRegister
class CooFxSceneRenderEntity() : AutoRenderEntity(null, Vec3.ZERO) {
    /** 资源 ID 的字符串编码，避免依赖自动 codec 对 ResourceLocation 的隐式支持。 */
    @CodecField
    var resourceIdText: String = ""
        private set

    /** 服务端请求随机种子。 */
    @CodecField
    var requestSeed: Long = 0L
        private set

    /** 模型局部旋转四元数的 X 分量。 */
    @CodecField
    var rotationX: Float = 0F
        private set

    /** 模型局部旋转四元数的 Y 分量。 */
    @CodecField
    var rotationY: Float = 0F
        private set

    /** 模型局部旋转四元数的 Z 分量。 */
    @CodecField
    var rotationZ: Float = 0F
        private set

    /** 模型局部旋转四元数的 W 分量。 */
    @CodecField
    var rotationW: Float = 1F
        private set

    /** 模型局部缩放 X。 */
    @CodecField
    var scaleX: Float = 1F
        private set

    /** 模型局部缩放 Y。 */
    @CodecField
    var scaleY: Float = 1F
        private set

    /** 模型局部缩放 Z。 */
    @CodecField
    var scaleZ: Float = 1F
        private set

    /** 服务端权威播放模式 ID。 */
    @CodecField
    var modeId: Int = CooFxSceneMode.MODEL.id
        private set

    /** 空字符串表示 bind pose/默认 clip。 */
    @CodecField
    var clipIdText: String = ""
        private set

    /** 模型和 emitter 的播放速度。 */
    @CodecField
    var playbackSpeed: Float = 1F
        private set

    /** 空字符串表示选定 scene 的第一个 camera。 */
    @CodecField
    var cameraIdText: String = ""
        private set

    /** 多个 camera tracking 场景之间的服务端优先级。 */
    @CodecField
    var cameraPriority: Int = 0
        private set

    /** 空字符串表示所有可见玩家都可以跟踪 camera，否则只匹配指定玩家 UUID。 */
    @CodecField
    var cameraTargetPlayerText: String = ""
        private set

    /** 逗号分隔且排序的多个 camera 接收者 UUID；为空时兼容读取 [cameraTargetPlayerText]。 */
    @CodecField
    var cameraTargetPlayersText: String = ""
        private set

    /** 空字符串表示不启动 emitter。 */
    @CodecField
    var emitterIdText: String = ""
        private set

    /** -1 表示使用资产默认 count。 */
    @CodecField
    var emitterCount: Int = -1
        private set

    /** -1 表示使用资产默认 delay。 */
    @CodecField
    var emitterDelayTicks: Int = -1
        private set

    /** -1 表示使用资产默认 lifetime。 */
    @CodecField
    var emitterLifetimeTicks: Int = -1
        private set

    /** -1 表示不使用服务端 TTL。 */
    @CodecField
    var lifetimeTicks: Long = -1L
        private set

    /**
     * @param level 服务端世界
     * @param position 初始世界位置
     * @param spec 初始服务端场景快照
     */
    constructor(level: ServerLevel, position: Vec3, spec: CooFxSceneSpec) : this() {
        world = level
        setSceneSpec(spec)
        pos = position
    }

    /** @return 该实体在 RenderEntity registry 中的稳定类型 ID。 */
    override fun getRenderID(): ResourceLocation = ID

    /** 服务端 TTL 到期后移除场景；客户端 tick 不推进服务端生命周期。 */
    override fun serverTick() {
        if (lifetimeTicks >= 0L && age >= lifetimeTicks) {
            CooFxSceneManager.stop(uuid)
        }
    }

    /** 用完整快照替换服务端状态，并标记下一 tick 同步。 */
    fun setSceneSpec(spec: CooFxSceneSpec) {
        setPosition(Vec3(spec.transform.x, spec.transform.y, spec.transform.z))
        requestSeed = spec.requestSeed
        rotationX = spec.transform.rotationX
        rotationY = spec.transform.rotationY
        rotationZ = spec.transform.rotationZ
        rotationW = spec.transform.rotationW
        scaleX = spec.transform.scaleX
        scaleY = spec.transform.scaleY
        scaleZ = spec.transform.scaleZ
        resourceIdText = spec.resourceId.toString()
        modeId = spec.mode.id
        clipIdText = spec.clipId.orEmpty()
        playbackSpeed = spec.playbackSpeed
        cameraIdText = spec.cameraId.orEmpty()
        cameraPriority = spec.cameraPriority
        cameraTargetPlayerText = spec.cameraTargetPlayer?.toString().orEmpty()
        cameraTargetPlayersText = spec.cameraTargetPlayers
            ?.asSequence()
            ?.map(UUID::toString)
            ?.sorted()
            ?.joinToString(",")
            .orEmpty()
        emitterIdText = spec.emitterId.orEmpty()
        emitterCount = spec.emitterCount ?: -1
        emitterDelayTicks = spec.emitterDelayTicks ?: -1
        emitterLifetimeTicks = spec.emitterLifetimeTicks ?: -1
        lifetimeTicks = spec.lifetimeTicks ?: -1L
        renderRange = spec.renderRange
        validateState()
        markDirty()
    }

    /** 只更新服务端提供的非空字段，其他场景状态保持不变。 */
    fun applyPatch(patch: CooFxScenePatch): Boolean {
        patch.transform?.let { transform ->
            setPosition(Vec3(transform.x, transform.y, transform.z))
            rotationX = transform.rotationX
            rotationY = transform.rotationY
            rotationZ = transform.rotationZ
            rotationW = transform.rotationW
            scaleX = transform.scaleX
            scaleY = transform.scaleY
            scaleZ = transform.scaleZ
        }
        patch.mode?.let { mode -> modeId = mode.id }
        patch.clipId?.let { clipIdText = it }
        patch.playbackSpeed?.let { playbackSpeed = it }
        patch.cameraId?.let { cameraIdText = it }
        patch.cameraPriority?.let { cameraPriority = it }
        patch.cameraTargetPlayer?.let { target ->
            cameraTargetPlayerText = target.toString()
            cameraTargetPlayersText = ""
        }
        patch.cameraTargetPlayers?.let { targets ->
            cameraTargetPlayerText = ""
            cameraTargetPlayersText = targets.asSequence().map(UUID::toString).sorted().joinToString(",")
        }
        patch.emitterId?.let { emitterIdText = it }
        patch.emitterCount?.let { emitterCount = it }
        patch.emitterDelayTicks?.let { emitterDelayTicks = it }
        patch.emitterLifetimeTicks?.let { emitterLifetimeTicks = it }
        validateState()
        markDirty()
        return true
    }

    /** @return 当前实体可转换为客户端请求的资源 ID；非法状态直接拒绝同步。 */
    fun resourceId(): ResourceLocation = requireNotNull(ResourceLocation.tryParse(resourceIdText)) {
        "CooFX scene resource ID 非法：$resourceIdText"
    }

    /** @return 当前模型/摄像机/粒子使用的播放模式。 */
    fun mode(): CooFxSceneMode = CooFxSceneMode.fromId(modeId)

    /** @return 当前 clip，空字符串表示未指定。 */
    fun clipId(): String? = clipIdText.takeIf { value -> value.isNotBlank() }

    /** @return 当前选中的 camera，空字符串表示默认 camera。 */
    fun cameraId(): String? = cameraIdText.takeIf { value -> value.isNotBlank() }

    /** @return 本地 camera 跟踪目标；空字符串表示不限制玩家。 */
    fun cameraTargetPlayer(): UUID? = cameraTargetPlayerText
        .takeIf { value -> value.isNotBlank() }
        ?.let { value -> UUID.fromString(value) }

    /** @return 本地 camera 跟踪接收者；`null` 表示不限制玩家，且兼容旧单玩家字段。 */
    fun cameraTargetPlayers(): Set<UUID>? {
        if (cameraTargetPlayersText.isNotBlank()) {
            return cameraTargetPlayersText.split(',').map(UUID::fromString).toSet()
        }
        return cameraTargetPlayer()?.let(::setOf)
    }

    /** @return 当前玩家是否可在本地接管该 scene 的 camera。 */
    fun isCameraTargetedAt(playerId: UUID): Boolean = cameraTargetPlayers()?.contains(playerId) ?: true

    /** @return 当前 emitter，空字符串表示不播放 emitter。 */
    fun emitterId(): String? = emitterIdText.takeIf { value -> value.isNotBlank() }

    /** @return 将同步字段还原为客户端使用的世界变换。 */
    fun worldTransform(): CooFxWorldTransform =
        CooFxWorldTransform(
            x = pos.x,
            y = pos.y,
            z = pos.z,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            rotationW = rotationW,
            scaleX = scaleX,
            scaleY = scaleY,
            scaleZ = scaleZ,
        )

    private fun validateState() {
        require(playbackSpeed.isFinite() && playbackSpeed >= 0F) { "CooFX scene playbackSpeed 非法" }
        require(emitterCount >= -1) { "CooFX scene emitterCount 非法" }
        require(emitterDelayTicks >= -1) { "CooFX scene emitterDelayTicks 非法" }
        require(emitterLifetimeTicks == -1 || emitterLifetimeTicks > 0) {
            "CooFX scene emitterLifetimeTicks 非法"
        }
        require(lifetimeTicks >= -1L) { "CooFX scene lifetimeTicks 非法" }
        if (cameraTargetPlayerText.isNotBlank()) {
            UUID.fromString(cameraTargetPlayerText)
        }
        if (cameraTargetPlayersText.isNotBlank()) {
            require(cameraTargetPlayerText.isBlank()) { "CooFX scene 不能同时保存单个和多个 camera 接收者" }
            cameraTargetPlayersText.split(',').forEach(UUID::fromString)
        }
    }

    companion object {
        /** RenderEntity registry 使用的稳定类型 ID。 */
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "coofx_scene",
        )
    }
}
