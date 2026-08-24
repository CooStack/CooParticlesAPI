package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.coofx.coofxAsset
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneHandle
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneManager
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneMode
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneSpec
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * 在方块测试玩家位置启动 CooFX 模型或摄像头控制复核。
 *
 * 模型项和摄像头项都复用 people 资产；摄像头项只解析 people glTF 内置的静态 Camera，
 * 不创建模型实例、不创建 model batch，静态姿态使用 bind pose。
 *
 * @property player 当前方块测试提供的服务端或模拟玩家
 */
class CooFxModelBlockTestOption(
    private val player: Player,
    private val sceneMode: CooFxSceneMode = CooFxSceneMode.MODEL,
    private val testOptionId: String = OPTION_ID,
) : TestOption<Player> {
    private var sceneHandle: CooFxSceneHandle? = null
    private var activeLevel: ServerLevel? = null
    private var modelPosition = Vec3.ZERO
    private var expiresAt = Long.MIN_VALUE

    /** 该测试玩家与测试项共同构成逻辑 owner，避免不同玩家的同名测试互相替换。 */
    private val logicalOwner: String
        get() = "${player.uuid}:$testOptionId"

    /** 在测试玩家位置向同维度客户端启动纯模型实例。 */
    override fun start() {
        clearModel()
        val level = player.level() as? ServerLevel
            ?: error("CooFxModelBlockTestOption requires a server-side player")
        activeLevel = level
        modelPosition = player.position()
        expiresAt = level.gameTime + 80L
        sceneHandle = CooFxSceneManager.spawn(
            level = level,
            spec = CooFxSceneSpec(
                resourceId = coofxAsset(CooParticlesConstants.MOD_ID, "emitter"),
                transform = CooFxWorldTransform(
                    x = modelPosition.x,
                    y = modelPosition.y,
                    z = modelPosition.z,
                ),
                requestSeed = 0x5EEDL,
                mode = sceneMode,
                cameraId = "Camera",
                cameraTargetPlayer = null,
                cameraPriority = if (sceneMode == CooFxSceneMode.MODEL) 0 else 10,
                clipId = null,
                playbackSpeed = 1f
            ),
            ownerKey = logicalOwner,
        )
    }

    /** 测试计时结束后清理模型，人工复核阶段不再保留服务端 scene。 */
    override fun stop() = clearModel()

    /** @return 模型展示计时尚未结束时返回 `true`。 */
    override fun isValid(): Boolean {
        return activeLevel?.gameTime?.let { gameTime -> gameTime < expiresAt } == true
    }

    /** 失败、跳过或取消时停止本次模型实例。 */
    override fun onFailed() = clearModel()

    /** 人工复核通过后停止本次模型实例。 */
    override fun onSuccess() = clearModel()

    /** @return 方块测试控制器显示和选择使用的稳定测试项 ID。 */
    override fun optionID(): String = testOptionId

    /** 模型动画由 CooFX 客户端 tick 推进，服务端 option 不需要逐 tick 更新。 */
    override fun doTick() = Unit

    /** @return 该模型测试需要人工检查实际 WORLD_PASS 绘制结果。 */
    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    /** @return 人工复核时应观察的模型节点或 people glTF 内置 Camera。 */
    override fun reviewDescription(): String = if (sceneMode == CooFxSceneMode.MODEL) {
        "观察测试玩家位置是否同时显示 Cube、球体和苏珊娜，确认 emitters 为空时模型仍可见；复核结束后模型应消失"
    } else {
        "观察是否复用 people glTF 内置 Camera；CAMERA_ONLY 不创建模型实例或 model batch，使用静态 bind pose 接管测试玩家镜头，结束后应恢复玩家 camera、鼠标和视角"
    }

    /** @return 与构造参数相同的方块测试玩家。 */
    override fun paramTarget(): Player = player

    /** 向同维度客户端停止本次模型实例；重复调用不会重复发送。 */
    private fun clearModel() {
        sceneHandle?.stop()
        sceneHandle = null
        activeLevel = null
    }

    companion object {
        const val OPTION_ID = "coofx/model/test_moudles"
        const val CAMERA_OPTION_ID = "coofx/model-camera/people-camera"
    }
}
