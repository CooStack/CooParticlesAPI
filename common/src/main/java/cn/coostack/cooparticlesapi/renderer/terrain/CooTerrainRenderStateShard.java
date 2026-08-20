package cn.coostack.cooparticlesapi.renderer.terrain;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FACTOR;
import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_UNITS;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetFloat;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glPolygonOffset;

/**
 * 为地形覆盖 RenderType 提供按渲染阶段切换的状态片段。
 *
 * <p>普通覆盖绘制沿用原版 terrain 输出和写入规则；attachment 捕获和 Iris 最终合成覆盖阶段保持调用方 FBO，
 * 并对共面地形启用深度偏移。平台 RenderType Provider 的基本组装方式如下：
 * {@code builder.setOutputState(terrainOutput(baseLayer))
 * .setLayeringState(terrainLayering())
 * .setWriteMaskState(terrainWriteMask(baseLayer))}。
 * 普通模组渲染代码不应单独启用这些状态片段，因为它们依赖 {@link CooTerrainPipelineManager} 的阶段标记。
 */
public final class CooTerrainRenderStateShard extends RenderStateShard {
    /**
     * 禁止实例化；本类只提供无状态的 RenderStateShard 工厂方法。
     */
    private CooTerrainRenderStateShard() {
        super("coo_terrain_state", () -> {
        }, () -> {
        });
    }

    /**
     * 创建按当前地形捕获阶段决定是否切换 FBO 的输出状态。
     *
     * <p>attachment 捕获和 Iris 最终合成覆盖阶段保持当前 FBO；其他阶段委托给原版基础层的输出状态。
     *
     * @param baseLayer 原方块几何所属的 terrain layer
     * @return 捕获时不切换 FBO、普通绘制时沿用原目标的输出状态
     */
    public static OutputStateShard terrainOutput(RenderType baseLayer) {
        final OutputStateShard delegate;
        if (baseLayer == RenderType.translucent()) {
            delegate = TRANSLUCENT_TARGET;
        } else if (baseLayer == RenderType.tripwire()) {
            delegate = WEATHER_TARGET;
        } else {
            delegate = MAIN_TARGET;
        }
        return new OutputStateShard(
                "coo_terrain_output",
                () -> {
                    if (!CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()
                            && !CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()) {
                        delegate.setupRenderState();
                    }
                },
                () -> {
                    if (!CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()
                            && !CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()) {
                        delegate.clearRenderState();
                    }
                }
        );
    }

    /**
     * 创建在 attachment 捕获和 Iris 最终合成覆盖阶段启用的共面深度偏移。
     *
     * <p>覆盖几何与原版 terrain 共面；捕获 bloom mask 时同样需要偏移，否则 Iris terrain depth
     * 会让片元在帧间交替通过 LEQUAL，产生发光闪烁。离开对应阶段时恢复调用方原有的 enable、factor 和 units。
     *
     * @return attachment 捕获和 Iris 最终覆盖阶段使用的共面深度偏移
     */
    public static LayeringStateShard terrainLayering() {
        final boolean[] applied = {false};
        final boolean[] previousEnabled = {false};
        final float[] previousFactor = {0F};
        final float[] previousUnits = {0F};
        return new LayeringStateShard(
                "coo_terrain_overlay_layering",
                () -> {
                    if (isOffsetStageActive()) {
                        applied[0] = true;
                        previousEnabled[0] = glIsEnabled(GL_POLYGON_OFFSET_FILL);
                        previousFactor[0] = glGetFloat(GL_POLYGON_OFFSET_FACTOR);
                        previousUnits[0] = glGetFloat(GL_POLYGON_OFFSET_UNITS);
                        glEnable(GL_POLYGON_OFFSET_FILL);
                        glPolygonOffset(-1F, -10F);
                    }
                },
                () -> {
                    if (applied[0]) {
                        glPolygonOffset(previousFactor[0], previousUnits[0]);
                        if (previousEnabled[0]) {
                            glEnable(GL_POLYGON_OFFSET_FILL);
                        } else {
                            glDisable(GL_POLYGON_OFFSET_FILL);
                        }
                        applied[0] = false;
                    }
                }
        );
    }

    private static boolean isOffsetStageActive() {
        return CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()
                || CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive();
    }

    /**
     * 创建地形覆盖层的只写颜色状态。
     *
     * <p>捕获阶段和最终合成阶段都等价于 {@code COLOR_WRITE.setupRenderState()}。
     * 捕获和最终合成阶段仍使用相同的颜色写入状态，保证所有阶段一致。
     *
     * @param baseLayer 原方块几何所属的 terrain layer
     * @return 始终只写颜色、不写深度的覆盖状态
     */
    public static WriteMaskStateShard terrainWriteMask(RenderType baseLayer) {
        return COLOR_WRITE;
    }
}
