package cn.coostack.cooparticlesapi.renderer.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPolygonOffset;

/**
 * 为地形覆盖 RenderType 提供按渲染阶段切换的状态片段。
 *
 * <p>普通覆盖绘制沿用原版 terrain 输出和写入规则；捕获 attachment 时保持调用方 FBO，
 * Iris 最终合成覆盖阶段则只写颜色并启用共面深度偏移。平台 RenderType Provider 的基本组装方式如下：
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
     * 创建仅在 Iris 最终合成覆盖阶段启用的共面深度偏移。
     *
     * <p>该状态用于避免覆盖几何与保留的原版 terrain 几何产生深度闪烁，离开阶段时会恢复为无偏移。
     *
     * @return 仅在 Iris 最终合成覆盖阶段启用的共面深度偏移
     */
    public static LayeringStateShard terrainLayering() {
        return new LayeringStateShard(
                "coo_terrain_overlay_layering",
                () -> {
                    if (CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()) {
                        glEnable(GL_POLYGON_OFFSET_FILL);
                        glPolygonOffset(-1F, -10F);
                    }
                },
                () -> {
                    if (CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()) {
                        glPolygonOffset(0F, 0F);
                        glDisable(GL_POLYGON_OFFSET_FILL);
                    }
                }
        );
    }

    /**
     * 创建按当前地形覆盖阶段选择颜色和深度写入范围的状态。
     *
     * <p>Iris 最终合成覆盖阶段只写颜色，避免改动 shader pack 已生成的深度；
     * 其他阶段沿用基础层是否排序上传对应的原版写入规则。
     *
     * @param baseLayer 原方块几何所属的 terrain layer
     * @return Iris 最终覆盖只写颜色，其他阶段沿用原 terrain 写入规则
     */
    public static WriteMaskStateShard terrainWriteMask(RenderType baseLayer) {
        final WriteMaskStateShard delegate = baseLayer.sortOnUpload() ? COLOR_WRITE : COLOR_DEPTH_WRITE;
        return new WriteMaskStateShard(true, true) {
            @Override
            public void setupRenderState() {
                if (CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()) {
                    COLOR_WRITE.setupRenderState();
                } else {
                    delegate.setupRenderState();
                }
            }

            @Override
            public void clearRenderState() {
                if (CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()) {
                    COLOR_WRITE.clearRenderState();
                } else {
                    delegate.clearRenderState();
                }
            }
        };
    }
}
