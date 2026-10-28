package cn.coostack.cooparticlesapi.accessor

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.renderer.RenderBuffers

/**
 * 暴露 26.3 世界绘制器的真实资源能力，不保留已移除的旧版分离场景目标。
 *
 * 临时目标只能在当前主 FrameGraph 执行回调内使用。OIT 累积与透射率不是最终场景颜色，
 * 不能当作半透明、粒子或天气目标的别名，也不能跨帧保存。
 */
interface LevelRendererAccessor {
    /** @return 原版共享的渲染缓冲管理器，不转移所有权 */
    fun renderBuffers(): RenderBuffers

    /**
     * 返回当前主 pass 已分配的帧内目标，作用域以外返回空表。
     *
     * 示例：`availableFrameTargets()["main"]` 读取本回调的主合成目标。
     * 键为 `main`、`oit_accumulate`、`oit_transmittance_<索引>`、`oit_depth_bounds`
     * 和 `oit_depth_bounds_culled` 等用途名，值仅在本次同步回调中有效。
     *
     * @return 不可修改的用途到目标映射；不解引用未分配或上一帧已释放的 handle
     */
    fun availableFrameTargets(): Map<String, RenderTarget>
}
