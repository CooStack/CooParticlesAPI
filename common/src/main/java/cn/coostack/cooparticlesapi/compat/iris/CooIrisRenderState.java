package cn.coostack.cooparticlesapi.compat.iris;

import cn.coostack.cooparticlesapi.renderer.backend.DepthProjectionContext;
import org.joml.Matrix4fc;

/**
 * 保存 Iris 当前帧资源的公共桥接状态，供非 Iris 类型的渲染代码查询。
 *
 * <p>仅可选 Mixin 读取 Iris 对象；本类只发布不可变的 GL 资源描述与深度投影副本。
 * 纹理和 framebuffer 仍由原有 owner 管理，本类不释放任何捕获资源。
 * 状态更新在渲染线程执行，volatile 仅用于发布完整快照，不提供复合写入的并发合并。
 */
public final class CooIrisRenderState {
    private static volatile CooIrisRenderSnapshot snapshot = CooIrisRenderSnapshot.EMPTY;
    /** 当前帧 raw 深度与 Iris gbuffer 投影的配套数学约定，未捕获时为空。 */
    private static volatile DepthProjectionContext depthProjection;

    private CooIrisRenderState() {
    }

    /**
     * 返回本次读取时已发布的资源快照，不重新解析或持有 Iris 对象。
     * @return 当前快照；零资源 ID 表示对应阶段尚未捕获
     */
    public static CooIrisRenderSnapshot snapshot() {
        return snapshot;
    }

    /** 在新世界帧开始时清空资源与深度投影，并推进资源代次。 */
    public static void beginFrame() {
        depthProjection = null;
        snapshot = new CooIrisRenderSnapshot(
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                snapshot.generation() + 1
        );
    }

    /**
     * 发布场景 raw 深度纹理，保留其他已捕获资源并推进代次。
     * @param textureId Iris 拥有的纹理名称，零表示不可用
     * @param width 纹理宽度
     * @param height 纹理高度
     */
    public static void captureSceneDepth(int textureId, int width, int height) {
        snapshot = snapshot.withSceneDepth(textureId, width, height);
    }

    /**
     * 发布透明阶段之前的地形深度，保留其他资源并推进代次。
     * @param textureId Iris 拥有的地形深度纹理名称
     * @param width 纹理宽度
     * @param height 纹理高度
     */
    public static void captureTerrainDepth(int textureId, int width, int height) {
        snapshot = snapshot.withTerrainDepth(textureId, width, height);
    }

    /**
     * 发布排除手部的深度，保留其他资源并推进代次。
     * @param textureId Iris 拥有的无手部深度纹理名称
     * @param width 纹理宽度
     * @param height 纹理高度
     */
    public static void captureNoHandDepth(int textureId, int width, int height) {
        snapshot = snapshot.withNoHandDepth(textureId, width, height);
    }

    /**
     * 发布 final 输出的颜色与 framebuffer，保留深度资源并推进代次。
     * @param textureId 最终颜色纹理名称
     * @param framebufferId 可供合成写入的 framebuffer 名称
     * @param width 输出宽度
     * @param height 输出高度
     */
    public static void captureFinalColor(int textureId, int framebufferId, int width, int height) {
        snapshot = snapshot.withFinalColor(textureId, framebufferId, width, height);
    }

    /** 清空 final 输出描述并推进代次，保留当前深度与投影。 */
    public static void clearFinalColor() {
        snapshot = snapshot.withFinalColor(0, 0, 0, 0);
    }

    /** 在世界卸载或 pipeline 销毁时恢复空快照并清空深度投影。 */
    public static void clear() {
        snapshot = CooIrisRenderSnapshot.EMPTY;
        depthProjection = null;
    }

    /**
     * 按实际 Iris gbuffer 矩阵记录 raw 反向深度映射，不借用后处理时的原版投影。
     * @param view 捕获的相机相对 gbuffer 视图
     * @param forwardProjection 经 Iris undoRevZ 处理的正向负一至一投影
     */
    public static void captureDepthProjection(Matrix4fc view, Matrix4fc forwardProjection) {
        try {
            depthProjection = view != null && forwardProjection != null
                    ? DepthProjectionContext.iris(view, forwardProjection) : null;
        } catch (IllegalArgumentException invalidProjection) {
            // 无有效投影时仅禁用深度采样，其他后处理继续合成场景颜色。
            depthProjection = null;
        }
    }

    /** @return 当前帧与 raw 深度匹配的上下文，尚未捕获时为空 */
    public static DepthProjectionContext depthProjection() {
        return depthProjection;
    }

}
