# RenderEntity V2 指南

这份文档描述当前仓库已经收敛后的 `RenderEntity` V2 真相，而不是旧的 V1 渲染习惯。

## 1. 先记住边界

- `RenderEntity` 是服务端权威状态和客户端镜像同步基类。
- `RenderEntityInstance` 是客户端真正持有的运行时实例。
- `RenderEntityRenderer` 负责声明视觉能力并提交渲染贡献。
- `ClientRenderPipelineManager` 负责帧阶段、场景资源解析和 backend 调度。
- framebuffer、scene copy、depth read、post graph、final composite 都属于平台层，不属于 `RenderEntity` 本体。

如果你在 `RenderEntity` 里直接做这些事，方向就是错的：

- 自己读写 `minecraft.mainRenderTarget`
- 自己做整屏 copy / blur / composite
- 把某个实体绑定成“负责最终合成”的执行器
- 在实体本体里维护 framebuffer 生命周期

## 2. 当前主路径

### World Pass

- `LevelRendererMixin` 调 `ClientRenderPipelineManager.beginFrame(...)`
- 同一帧内 `finishLevelRender(...)` 触发 `RenderFrameStage.WORLD_PASS`
- `ClientRenderEntityManager.renderWorldPass(...)`
- `RenderEntityInstance.renderLocal(...)`
- `RenderEntityRenderer.renderLocal(...)`

这是“像普通世界模型一样直接画”的路径。

### Frame Post

- `LevelRendererMixin` 在尾部调 `ClientRenderPipelineManager.endFrame()`
- backend 进入 `RenderFrameStage.FRAME_POST`
- `ClientRenderEntityManager.runFramePost(...)`
- 每个 instance 调 `collectRenderContributions(...)`
- contribution 进入 `RenderEffectGraph`
- `RenderEffectRegistry` 统一分发到 builtin effect executor

这是当前的统一帧后处理主路径。

## 3. 现在应该写哪个接口

- `RenderEntityRenderer`
  只放共享生命周期和能力声明，不再把 world pass / frame-post 做成接口默认空实现。
- `WorldPassRenderEntityRenderer`
  只有真的要画本地几何时才实现它。
- `FramePostRenderEntityRenderer`
  只有真的要往 effect graph 提交 descriptor 时才实现它。
- `renderLocal(...)`
  适合真正的 world pass 几何绘制。
- `collectRenderContributions(...)`
  这是 V2 当前推荐的 frame-post 入口。你提交的是 `RenderEffectDescriptor`，不是直接执行整屏后处理。
- 自定义 frame-post 效果
  直接定义 `effectType + payload + executor`，然后仍然通过 `collectRenderContributions(...)` 提交，不要再把执行 lambda 塞回实体接口。

## 4. Stage 与 Scene Resource

当前统一阶段枚举是 `renderer.backend.RenderFrameStage`：

- `FRAME_BEGIN`
- `WORLD_PASS`
- `POST_PROCESS_PREPARE`
- `FRAME_POST`
- `FRAME_END`

当前统一场景资源命名表是 `renderer.backend.RenderSceneTargets`：

- `MAIN`
- `POST`
- `SCENE_COLOR`
- `SCENE_DEPTH`
- `BLOOM`
- `LIGHT`
- `TRANSLUCENT_TARGET`
- `ITEM_ENTITY_TARGET`
- `PARTICLES_TARGET`
- `WEATHER_TARGET`
- `CLOUDS_TARGET`

`ClientRenderPipelineManager` 每个阶段都会构造 `RenderFrameContext`，其中携带：

- 当前 backend
- 当前 stage
- 当前 `RenderSceneResources`
- 当前解析出的 color/depth/final target

这套命名资源思路是当前仓库向 Veil 靠拢的核心之一。

## 5. Builtin Effect 如何挂接

当前内建 glow / bloom / world light 的 provider 判断已经收敛到 `BuiltinRenderEffectDescriptors`，而不是散落在 runtime core 里。

它负责三件事：

- `describeEntity(entity)`：把实体扩展成 `RenderEntityFeatureSet`
- `collectEntity(...)`：把 provider 扩展成统一的 `RenderEffectDescriptor`
- `screenGlow(...) / persistentBloom(...) / worldLight(...) / sharedModelMaskBloom(...) / customGlowMaskBloom(...) / computeDispatch(...)`：构造 typed descriptor

这意味着 `RenderEntityInstance` 不再直接认识 `ScreenGlowProvider`、`PersistentBloomContextProvider`、`WorldLightProvider` 的细节分支。

RenderEntity glow 目前推荐的 builtin 路径已经切到 `MASK_BLOOM`，并分成两类明确语义：

- `SharedModelMaskBloomRenderEntityRenderer`
  适用于“已有 world-pass 模型路径”的实体。它复用同一套模型绘制逻辑，把模型实际颜色/alpha/贴图内容直接写进 glow mask。
- `DedicatedGlowMaskRenderEntityRenderer`
  适用于“没有现成 world-pass 模型路径，但仍需要局部 glow”的实体。它只实现 `renderGlowMask(...)`，专门把局部模型内容绘制到 mask target。

`postGlowSphere(...)` 现在只保留兼容残留，不再是 RenderEntity glow 的默认或推荐实现。

## 6. 最小示例

`TestRendererEntity` 是当前新 API 下的 smoke example。它展示的是：

- `RenderEntity` 仍然只是同步对象
- renderer 通过 `SharedModelMaskBloomRenderEntityRenderer` 复用同一套模型绘制
- glow descriptor 走 `MASK_BLOOM`，颜色来自模型纹理内容本身

核心模式如下：

```kotlin
class TestRendererEntity(...) :
    AutoRenderEntity(...),
    SharedModelMaskBloomRenderEntityRenderer<TestRendererEntity> {

    override fun renderSharedModel(input: SharedModelMaskBloomInput<TestRendererEntity>) {
        texturedBillboardShader.useOnContext {
            setMatrix4("projMat", input.projMatrix)
            setMatrix4("viewMat", input.viewMatrix)
            setMatrix4("transMat", input.modelMatrix)
            setFloat2("size", billboardSize)
            setInt("tex", 0)
            magicTexture.drawWith {
                RenderEntityExampleSupport.billboardBuffer().draw()
            }
        }
    }

    override fun glowMaskConfig(entity: TestRendererEntity): MaskBloomConfig {
        return BuiltinRenderEffectDescriptors.defaultRenderEntityModelGlowConfig().copy(
            blurSigma = 15.0f,
            blurRange = 10.0f,
            intensity = 2.8f,
            baseMaskIntensity = 0.24f
        )
    }
}
```

如果你没有现成的 world-pass 模型路径，但仍需要局部 glow，则改用第二类接口：

```kotlin
class MyGlowMaskOnlyEntity(...) :
    AutoRenderEntity(...),
    DedicatedGlowMaskRenderEntityRenderer<MyGlowMaskOnlyEntity> {

    override fun renderGlowMask(input: GlowMaskRenderInput<MyGlowMaskOnlyEntity>) {
        input.maskContext.drawTexturedBillboard(
            MaskBloomTexturedBillboard(
                textures = runeTextures,
                modelMatrix = input.modelMatrix,
                tint = Vector4f(1.0f, 0.85f, 0.45f, 1.0f)
            )
        )
    )
}
```

### 旧 demo 备份

迁移前注释备份保存在：

- `.ai/backups/renderentity/TestRendererEntity.v2-commented-backup.kt`

## 7. 什么时候还会碰到 `ShaderPipeManager`

`ShaderPipeManager` 还在用，但它现在是 effect executor 的低层工具，不是 `RenderEntity` 作者应该优先面向的顶层 API。

正确心智模型是：

- `RenderEntity` / `RenderEntityRenderer`：声明需求
- `RenderEffectGraph` / `RenderEffectRegistry`：统一调度
- `Client*Manager`：具体执行器
- `ShaderPipeManager`：执行器内部的 pipe DAG 工具

## 8. Backend 能力注意点

当前 capability 仍然重要：

- `SCENE_COLOR_COPY`
- `SCENE_DEPTH_READ`
- `SAFE_WORLD_COMPOSITE`
- `FINAL_FRAME_POST`

例如：

- `VanillaSafeRenderBackend` 具备 `SCENE_COLOR_COPY` 和 `SCENE_DEPTH_READ`
- `IrisSafeRenderBackend` 当前只有 `FINAL_FRAME_POST` 和 `SAFE_WORLD_COMPOSITE`

所以依赖 scene color / depth 的 glow/bloom 效果，不能假设在所有 backend 上都一定可用。

## 9. Veil 对照检测

按 Veil 官方 wiki、javadocs 和 Context7 复核后，当前仓库并不拥有 Veil 的“所有着色功能”。当前状态应被准确理解为“部分对齐统一渲染平台方向”，而不是“全量 Veil parity”。

### 已覆盖或部分覆盖

- 命名 scene target / framebuffer 语义：部分覆盖
- frame-post descriptor + registry + graph：部分覆盖
- world pass / frame-post 两段式 stage：部分覆盖
- 低层 shader pipe / ping-pong / offscreen FBO：已覆盖

### 仍然缺失

- layered / data-driven render type
- Veil 式更细粒度的 render stage hook / fixed buffer stage
- 资源驱动的 framebuffer / post pipeline descriptor

### 本轮已补的着色能力

- 新增 Veil 风格的 `CooRenderTypeDescriptor` / `CooLayeredRenderType`
- 新增 data-driven `rendertypes/*.json` + `CooRenderTypeResourceRegistry`
- 新增 `AdvancedShaderProgramBuilder`
- 新增 `GEOMETRY / TESSELLATION_CONTROL / TESSELLATION_EVALUATION / COMPUTE` stage API
- 新增 `CooComputeShaderProgram` / `ComputeShaderProgram`
- 新增 Veil 风格的 `ShaderBufferLayout` / `ShaderBufferRegistry`
- 新增 `ShaderBufferObject` / `ShaderBufferCache`
- 新增 compute `RenderEffectDescriptor` / `ComputeDispatchRenderer`

所以当前缺口已经缩成：

- 还没有真正的 shader buffer upload/cache runtime
- 还没有更细粒度 stage hook / fixed buffer stage
- 还没有资源驱动的 framebuffer / post pipeline descriptor

其中 shader buffer 这块现在已经从“只有 layout”推进到了“layout + runtime object + cache”，剩余缺口主要在于和 shader 编译/重编译生命周期做更深绑定。

### compute 已接入 effect graph

当前 compute 不再只是独立 program API，而是已经能通过 builtin descriptor 进入 `RenderEffectGraph`：

- `BuiltinRenderEffectTypes.COMPUTE_DISPATCH`
- `BuiltinRenderEffectDescriptors.computeDispatch(...)`
- `ComputeDispatchRenderer`

并且仓库里已经有一个最小 smoke entity：

- `common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/TestComputeShaderEntity.kt`

### data-driven render type 入口

当前已经新增：

- `common/src/main/resources/assets/cooparticlesapi/rendertypes/index.json`
- `common/src/main/resources/assets/cooparticlesapi/rendertypes/*.json`
- `CooRenderTypeResourceRegistry`

provider 现在既支持代码 builder，也支持按资源 id 取命名 render type：

```kotlin
val glow = CooParticlesServices.PLATFORM.getRenderTypesProvider()
    .named(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "glow"))

val layered = CooParticlesServices.PLATFORM.getRenderTypesProvider()
    .layered(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "glow_layered"))
```

当前仓库内的真实使用样例：

- `common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/display/TestShapeDisplayEntity.kt`

它会优先使用命名的 `glow_layered` render type，找不到时再回退到旧 `glow()`。

### 本轮删除的多余内容

- 删除了未接线的 `FrameEffectStack` 旧包装层
- 删除了从未实现过、会误导调用方的 `EARLY_WORLD_HOOK` capability

这些删除的目的不是“砍功能”，而是让公开 API 与真实能力保持一致。

## 10. 开发建议

优先顺序：

1. 如果你只是想提交后处理效果，用 `collectRenderContributions(...)`
2. 如果你只是想画世界几何，用 `renderLocal(...)`
3. 只有在实现新 effect executor 时，才直接碰 `ShaderPipeManager`

避免这些回退行为：

- 把 provider 判断重新写回 `RenderEntityInstance`
- 让每个新效果再复制一套 manager 生命周期
- 把“直接执行的 lambda payload”重新塞回 descriptor graph
- 把 framebuffer/scene target 逻辑重新塞回实体

## 11. 验证基线

当前与 V2 contract 直接相关的回归测试：

- `RenderEntityV2ContractTest`
- `RenderEntityInstanceLifecycleTest`
- `ClientRenderEntityPacketHandlerV2Test`

推荐验证命令：

```powershell
gradlew.bat :common:test --no-daemon -Dkotlin.incremental=false --tests cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityV2ContractTest --tests cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstanceLifecycleTest --tests cn.coostack.cooparticlesapi.network.packet.client.ClientRenderEntityPacketHandlerV2Test
```
