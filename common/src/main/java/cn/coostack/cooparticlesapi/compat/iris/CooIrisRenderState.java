package cn.coostack.cooparticlesapi.compat.iris;

/**
 * Iris 当前帧渲染资源的无类型快照。
 *
 * <p>Iris 私有对象只在可选 Mixin 中读取；其余 Coo 渲染逻辑只接触 OpenGL 对象名和尺寸。
 */
public final class CooIrisRenderState {
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private CooIrisRenderState() {
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void beginFrame() {
        snapshot = new Snapshot(
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                snapshot.generation + 1
        );
    }

    public static void captureSceneDepth(int textureId, int width, int height) {
        snapshot = snapshot.withSceneDepth(textureId, width, height);
    }

    public static void captureTerrainDepth(int textureId, int width, int height) {
        snapshot = snapshot.withTerrainDepth(textureId, width, height);
    }

    public static void captureNoHandDepth(int textureId, int width, int height) {
        snapshot = snapshot.withNoHandDepth(textureId, width, height);
    }

    public static void captureFinalColor(int textureId, int framebufferId, int width, int height) {
        snapshot = snapshot.withFinalColor(textureId, framebufferId, width, height);
    }

    public static void clearFinalColor() {
        snapshot = snapshot.withFinalColor(0, 0, 0, 0);
    }

    public static void clear() {
        snapshot = Snapshot.EMPTY;
    }

    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                0
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

        private Snapshot(
                int sceneDepthTextureId,
                int sceneDepthWidth,
                int sceneDepthHeight,
                int terrainDepthTextureId,
                int terrainDepthWidth,
                int terrainDepthHeight,
                int noHandDepthTextureId,
                int noHandDepthWidth,
                int noHandDepthHeight,
                int finalColorTextureId,
                int finalColorFramebufferId,
                int finalColorWidth,
                int finalColorHeight,
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

        public int sceneDepthTextureId() { return sceneDepthTextureId; }
        public int sceneDepthWidth() { return sceneDepthWidth; }
        public int sceneDepthHeight() { return sceneDepthHeight; }
        public int terrainDepthTextureId() { return terrainDepthTextureId; }
        public int terrainDepthWidth() { return terrainDepthWidth; }
        public int terrainDepthHeight() { return terrainDepthHeight; }
        public int noHandDepthTextureId() { return noHandDepthTextureId; }
        public int noHandDepthWidth() { return noHandDepthWidth; }
        public int noHandDepthHeight() { return noHandDepthHeight; }
        public int finalColorTextureId() { return finalColorTextureId; }
        public int finalColorFramebufferId() { return finalColorFramebufferId; }
        public int finalColorWidth() { return finalColorWidth; }
        public int finalColorHeight() { return finalColorHeight; }
        public long generation() { return generation; }

        private Snapshot withSceneDepth(int textureId, int width, int height) {
            return new Snapshot(
                    textureId, width, height,
                    terrainDepthTextureId, terrainDepthWidth, terrainDepthHeight,
                    noHandDepthTextureId, noHandDepthWidth, noHandDepthHeight,
                    finalColorTextureId, finalColorFramebufferId, finalColorWidth, finalColorHeight,
                    generation + 1
            );
        }

        private Snapshot withTerrainDepth(int textureId, int width, int height) {
            return new Snapshot(
                    sceneDepthTextureId, sceneDepthWidth, sceneDepthHeight,
                    textureId, width, height,
                    noHandDepthTextureId, noHandDepthWidth, noHandDepthHeight,
                    finalColorTextureId, finalColorFramebufferId, finalColorWidth, finalColorHeight,
                    generation + 1
            );
        }

        private Snapshot withNoHandDepth(int textureId, int width, int height) {
            return new Snapshot(
                    sceneDepthTextureId, sceneDepthWidth, sceneDepthHeight,
                    terrainDepthTextureId, terrainDepthWidth, terrainDepthHeight,
                    textureId, width, height,
                    finalColorTextureId, finalColorFramebufferId, finalColorWidth, finalColorHeight,
                    generation + 1
            );
        }

        private Snapshot withFinalColor(int textureId, int framebufferId, int width, int height) {
            return new Snapshot(
                    sceneDepthTextureId, sceneDepthWidth, sceneDepthHeight,
                    terrainDepthTextureId, terrainDepthWidth, terrainDepthHeight,
                    noHandDepthTextureId, noHandDepthWidth, noHandDepthHeight,
                    textureId, framebufferId, width, height,
                    generation + 1
            );
        }
    }
}
