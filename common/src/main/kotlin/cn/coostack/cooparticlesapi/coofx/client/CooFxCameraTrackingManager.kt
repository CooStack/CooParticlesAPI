package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledCameraProjection
import cn.coostack.cooparticlesapi.utils.ClientCameraUtil
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.lang.Math.toDegrees
import java.util.UUID
import kotlin.math.asin
import kotlin.math.atan2

/**
 * 客户端 CooFX asset camera 的唯一竞争仲裁器。
 *
 * 同一客户端可同时收到多个服务端 scene；每个 tick 只选择 priority 最高的 claim，并在所有
 * RenderEntity mirror tick 完成后一次性应用到 Minecraft camera，避免 HashMap 遍历顺序决定视角。
 */
internal object CooFxCameraTrackingManager {
    private data class Claim(
        val sceneId: UUID,
        val priority: Int,
        val pose: CooFxCameraPose,
        val epoch: Long,
    )

    private val claims = LinkedHashMap<UUID, Claim>()
    private var epoch = 0L
    private var applied = false
    private var perspectiveFovDegrees: Double? = null

    fun beginTick() {
        epoch++
    }

    fun submit(sceneId: UUID, priority: Int, pose: CooFxCameraPose) {
        claims[sceneId] = Claim(sceneId, priority, pose, epoch)
    }

    fun apply() {
        val winner = claims.values
            .filter { claim -> claim.epoch == epoch }
            .maxWithOrNull(compareBy<Claim> { claim -> claim.priority }.thenBy { claim -> claim.sceneId.toString() })
        if (winner == null) {
            perspectiveFovDegrees = null
            if (applied) {
                ClientCameraUtil.resetForcedCameraPositionNow()
                ClientCameraUtil.resetOffsetNow()
                applied = false
            }
            return
        }
        val position = Vector3f()
        val forward = Vector3f(0F, 0F, -1F)
        winner.pose.worldMatrix.getTranslation(position)
        winner.pose.worldMatrix.transformDirection(forward).normalize()
        val player = Minecraft.getInstance().player
        if (player == null) {
            perspectiveFovDegrees = null
            if (applied) {
                ClientCameraUtil.resetForcedCameraPositionNow()
                ClientCameraUtil.resetOffsetNow()
                applied = false
            }
            return
        }
        val yaw = toDegrees(atan2(-forward.x.toDouble(), forward.z.toDouble())).toFloat()
        val pitch = toDegrees(asin((-forward.y).coerceIn(-1F, 1F).toDouble())).toFloat()
        ClientCameraUtil.setForcedCameraPositionNow(Vec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()))
        ClientCameraUtil.setOffsetNow(
            Vec3.ZERO,
            Mth.wrapDegrees(yaw - player.yRot),
            Mth.wrapDegrees(pitch - player.xRot),
        )
        perspectiveFovDegrees = when (val projection = winner.pose.camera.projection) {
            is CooFxCompiledCameraProjection.Perspective -> toDegrees(projection.yfovRadians.toDouble())
            is CooFxCompiledCameraProjection.Orthographic -> null
        }
        applied = true
    }

    /** @return 当前是否存在已应用的 camera claim，供 client-only 鼠标输入边界使用。 */
    @JvmStatic
    fun isMouseSuppressed(): Boolean = applied

    /** @return 当前获胜透视 camera 的垂直 FOV 角度；没有覆盖时返回 `null`。 */
    @JvmStatic
    fun activePerspectiveFovDegrees(): Double? = perspectiveFovDegrees

    fun remove(sceneId: UUID) {
        claims.remove(sceneId)
    }

    fun clear() {
        claims.clear()
        perspectiveFovDegrees = null
        if (applied) {
            ClientCameraUtil.resetForcedCameraPositionNow()
            ClientCameraUtil.resetOffsetNow()
            applied = false
        }
    }
}
