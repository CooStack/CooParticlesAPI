package cn.coostack.cooparticlesapi.renderer.backend

enum class RenderBackendCapability {
    SCENE_COLOR_COPY,
    SCENE_DEPTH_READ,
    SAFE_WORLD_COMPOSITE,
    FINAL_FRAME_POST,
    EARLY_WORLD_HOOK
}
