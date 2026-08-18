package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.Math.toRadians

/**
 * CooFX 客户端玩家相对变换工具。
 *
 * 传入的 [blenderLocalTransform] 使用 Blender 效果本地坐标：本地 `+Z` 始终对应玩家面朝方向，
 * `+X` 对应玩家右侧，`+Y` 对应上方。该工具只读取当前视角角度并返回新的世界变换；不会注册鼠标
 * 回调、修改玩家旋转、拦截输入或申请 CooFX camera，因此可在每个客户端 tick 或渲染帧中安全调用。
 *
 * 用于动态模型或 camera 时，将返回值传给既有的场景 patch、[CooFxWorldTransform] 请求或 camera
 * 采样流程即可。调用方保留自己的输入与生命周期所有权，并在原版鼠标处理完成后再次调用本工具，便能
 * 让 Blender 效果随当前视角更新。
 */
object CooFxPlayerRelativeTransforms {
    /** 使用本地玩家当前鼠标视角计算 Blender 效果的世界变换。 */
    @JvmStatic
    fun fromPlayerView(
        player: LocalPlayer,
        blenderLocalTransform: CooFxWorldTransform,
    ): CooFxWorldTransform = fromView(
        playerPosition = player.position(),
        viewYawDegrees = player.yRot,
        viewPitchDegrees = player.xRot,
        blenderLocalTransform = blenderLocalTransform,
    )

    /**
     * 使用调用方提供的视角计算 Blender 效果的世界变换。
     *
     * 该重载适合调用方已经缓存插值后的鼠标或 camera 角度的渲染路径；角度沿用 Minecraft 的 `yRot` 和
     * `xRot` 语义，因而不需要也不应修改输入处理。
     */
    @JvmStatic
    fun fromView(
        playerPosition: Vec3,
        viewYawDegrees: Float,
        viewPitchDegrees: Float,
        blenderLocalTransform: CooFxWorldTransform,
    ): CooFxWorldTransform {
        val playerViewRotation = Quaternionf().rotationYXZ(
            toRadians((-viewYawDegrees).toDouble()).toFloat(),
            toRadians(viewPitchDegrees.toDouble()).toFloat(),
            0F,
        )
        val localPosition = playerViewRotation.transform(
            Vector3f(
                blenderLocalTransform.x.toFloat(),
                blenderLocalTransform.y.toFloat(),
                blenderLocalTransform.z.toFloat(),
            )
        )
        val localRotation = Quaternionf(
            blenderLocalTransform.rotationX,
            blenderLocalTransform.rotationY,
            blenderLocalTransform.rotationZ,
            blenderLocalTransform.rotationW,
        )
        val worldRotation = playerViewRotation.mul(localRotation).normalize()
        return CooFxWorldTransform(
            x = playerPosition.x + localPosition.x.toDouble(),
            y = playerPosition.y + localPosition.y.toDouble(),
            z = playerPosition.z + localPosition.z.toDouble(),
            rotationX = worldRotation.x,
            rotationY = worldRotation.y,
            rotationZ = worldRotation.z,
            rotationW = worldRotation.w,
            scaleX = blenderLocalTransform.scaleX,
            scaleY = blenderLocalTransform.scaleY,
            scaleZ = blenderLocalTransform.scaleZ,
        )
    }
}
