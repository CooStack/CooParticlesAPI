package cn.coostack.cooparticlesapi.compat.iris;

/**
 * Iris 场景深度、地形深度、无手部深度和 final 颜色的不可变资源描述。
 *
 * <p>所有 ID 都是借用的 OpenGL 名称，不转移资源所有权。零 ID 表示尚未捕获；
 * 尺寸与对应 ID 一起发布。每次替换某组资源时保留其余资源并推进 generation。
 * 快照仅适用于捕获它的 Iris 生命周期，调用方不得跨世界缓存这些 GL 名称。
 */
public final class CooIrisRenderSnapshot {
    /** 生命周期重置使用的共享不可变空快照，不持有任何 GL 资源。 */
    public static final CooIrisRenderSnapshot EMPTY = new CooIrisRenderSnapshot(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    );

    private final int sceneDepthTextureId;
    private final int sceneDepthWidth;
    private final int sceneDepthHeight;
    private final int terrainDepthTextureId;
    private final int terrainDepthWidth;
    private final int terrainDepthHeight;
    private final int noHandDepthTextureId;
    private final int noHandDepthWidth;
    private final int noHandDepthHeight;
    private final int finalColorTextureId;
    private final int finalColorFramebufferId;
    private final int finalColorWidth;
    private final int finalColorHeight;
    private final long generation;

    CooIrisRenderSnapshot(
            int sceneDepthTextureId, int sceneDepthWidth, int sceneDepthHeight,
            int terrainDepthTextureId, int terrainDepthWidth, int terrainDepthHeight,
            int noHandDepthTextureId, int noHandDepthWidth, int noHandDepthHeight,
            int finalColorTextureId, int finalColorFramebufferId, int finalColorWidth, int finalColorHeight,
            long generation
    ) {
        this.sceneDepthTextureId = sceneDepthTextureId;
        this.sceneDepthWidth = sceneDepthWidth;
        this.sceneDepthHeight = sceneDepthHeight;
        this.terrainDepthTextureId = terrainDepthTextureId;
        this.terrainDepthWidth = terrainDepthWidth;
        this.terrainDepthHeight = terrainDepthHeight;
        this.noHandDepthTextureId = noHandDepthTextureId;
        this.noHandDepthWidth = noHandDepthWidth;
        this.noHandDepthHeight = noHandDepthHeight;
        this.finalColorTextureId = finalColorTextureId;
        this.finalColorFramebufferId = finalColorFramebufferId;
        this.finalColorWidth = finalColorWidth;
        this.finalColorHeight = finalColorHeight;
        this.generation = generation;
    }

    /** @return 场景 raw 深度纹理名称；零表示尚未捕获 */
    public int sceneDepthTextureId() { return sceneDepthTextureId; }
    /** @return 场景深度宽度 */
    public int sceneDepthWidth() { return sceneDepthWidth; }
    /** @return 场景深度高度 */
    public int sceneDepthHeight() { return sceneDepthHeight; }
    /** @return 透明阶段之前的地形深度纹理名称；零表示尚未捕获 */
    public int terrainDepthTextureId() { return terrainDepthTextureId; }
    /** @return 地形深度宽度 */
    public int terrainDepthWidth() { return terrainDepthWidth; }
    /** @return 地形深度高度 */
    public int terrainDepthHeight() { return terrainDepthHeight; }
    /** @return 排除手部的深度纹理名称；零表示尚未捕获 */
    public int noHandDepthTextureId() { return noHandDepthTextureId; }
    /** @return 无手部深度宽度 */
    public int noHandDepthWidth() { return noHandDepthWidth; }
    /** @return 无手部深度高度 */
    public int noHandDepthHeight() { return noHandDepthHeight; }
    /** @return final 颜色纹理名称；零表示尚未捕获 */
    public int finalColorTextureId() { return finalColorTextureId; }
    /** @return final 合成 framebuffer 名称；零表示尚未捕获 */
    public int finalColorFramebufferId() { return finalColorFramebufferId; }
    /** @return final 输出宽度 */
    public int finalColorWidth() { return finalColorWidth; }
    /** @return final 输出高度 */
    public int finalColorHeight() { return finalColorHeight; }
    /** @return 资源快照代次；普通更新递增，完整 clear 恢复零 */
    public long generation() { return generation; }

    CooIrisRenderSnapshot withSceneDepth(int textureId, int width, int height) {
        return new CooIrisRenderSnapshot(
                textureId, width, height,
                terrainDepthTextureId, terrainDepthWidth, terrainDepthHeight,
                noHandDepthTextureId, noHandDepthWidth, noHandDepthHeight,
                finalColorTextureId, finalColorFramebufferId, finalColorWidth, finalColorHeight,
                generation + 1
        );
    }

    CooIrisRenderSnapshot withTerrainDepth(int textureId, int width, int height) {
        return new CooIrisRenderSnapshot(
                sceneDepthTextureId, sceneDepthWidth, sceneDepthHeight,
                textureId, width, height,
                noHandDepthTextureId, noHandDepthWidth, noHandDepthHeight,
                finalColorTextureId, finalColorFramebufferId, finalColorWidth, finalColorHeight,
                generation + 1
        );
    }

    CooIrisRenderSnapshot withNoHandDepth(int textureId, int width, int height) {
        return new CooIrisRenderSnapshot(
                sceneDepthTextureId, sceneDepthWidth, sceneDepthHeight,
                terrainDepthTextureId, terrainDepthWidth, terrainDepthHeight,
                textureId, width, height,
                finalColorTextureId, finalColorFramebufferId, finalColorWidth, finalColorHeight,
                generation + 1
        );
    }

    CooIrisRenderSnapshot withFinalColor(int textureId, int framebufferId, int width, int height) {
        return new CooIrisRenderSnapshot(
                sceneDepthTextureId, sceneDepthWidth, sceneDepthHeight,
                terrainDepthTextureId, terrainDepthWidth, terrainDepthHeight,
                noHandDepthTextureId, noHandDepthWidth, noHandDepthHeight,
                textureId, framebufferId, width, height,
                generation + 1
        );
    }
}
