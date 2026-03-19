# ShaderPipe 使用文档

`ShaderPipeManager` 仍然存在，但它现在是低层执行器工具，不再代表整个 `RenderEntity` 渲染平台。

当前 shader 基础设施已经补到了更接近 Veil 的方向：

- `CooRenderTypeDescriptor` / `CooLayeredRenderType`
- `CooRenderTypeResourceRegistry`
- `AdvancedShaderProgramBuilder`
- `ShaderProgramRegistry`
- `CooComputeShaderProgram` / `ComputeShaderProgram`
- `ShaderBufferLayout` / `ShaderBufferRegistry`
- `ShaderBufferObject` / `ShaderBufferCache`
- `ComputeDispatchRenderer`

## 1. 当前分层

从高到低应这样理解：

1. `ClientRenderPipelineManager`
   统一帧阶段、backend、scene resource 和 target 解析。
2. `RenderEffectGraph` + `RenderEffectRegistry`
   统一 frame-post descriptor 收集、排序和执行。
3. builtin effect executor
   例如 `ClientScreenGlowManager`、`ClientPersistentBloomManager`、`ClientWorldLightManager`、`PostGlowSphereRenderer`。
4. `ShaderPipeManager`
   只负责某个具体 executor 内部的多 pass / ping-pong / uniform 注入 / output graph。

所以 `ShaderPipeManager` 是“怎么执行一个效果”的工具，不是“什么时候执行哪些效果”的总调度器。

## 2. 什么时候应该直接使用它

适合：

- 你在实现新的 builtin effect executor
- 这个效果内部确实需要多 pass DAG
- 你要在 executor 内部管理 ping-pong、output pipe、全局 uniform

不适合：

- 在 `RenderEntity` 本体里直接建一个 manager 负责整帧后处理
- 为了一个 demo 直接绕过 `RenderEffectGraph`
- 把 scene target 解析写进实体或 provider

## 3. 当前推荐接入方式

新效果优先走这条链：

- renderer 或 provider 提交 `RenderEffectDescriptor`
- `RenderEffectGraph` 统一排序
- `RenderEffectRegistry.register(effectType) { effects -> ... }`
- 在 executor 内部决定是否使用 `ShaderPipeManager`

这比“每个实体自己 new 一个管线并直接 render”更接近当前平台化方向。

## 4. 最小执行器骨架

```kotlin
object ExampleEffectExecutor {
    private val pipeline = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath("yourmod", "example_effect")
    )

    fun initOnClient() {
        ClientRenderPipelineManager.register(pipeline)
    }

    fun renderRequests(requests: List<ExampleRenderRequest>) {
        val first = requests.firstOrNull() ?: return
        val context = first.frameContext

        pipeline.writeFrame {
            // 根据请求写入输入 pass
        }
        pipeline.render()
    }
}
```

关键点：

- `renderRequests(...)` 是 effect executor 的批量入口
- `frameContext` 和 `sceneResources` 来自平台层，不应该在 executor 外部重复解析
- scene color / depth / final target 应优先从 `RenderFrameContext` 或 `ClientRenderPipelineManager` 获取

## 5. 和 Veil 风格的关系

当前仓库对 `ShaderPipeManager` 的正确定位，更接近 Veil 的“pipeline 内部 stage/executor”，而不是 Veil 的平台级 `PostProcessingManager`。

向 Veil 靠拢时要优先补的是：

- 命名 scene resource / framebuffer 语义
- 统一 effect descriptor / graph
- 更清晰的 stage hook

而不是继续把更多顶层职责加到 `ShaderPipeManager` 上。

## 6. 初始化与约束

- `setLinkerFunc { ... }` 仍然是必填
- `valueOutput(...)` 仍然必须设置
- `ClientRenderPipelineManager.init()` 会统一初始化已注册管线
- `depthSupplier` 会由平台层注入当前 scene depth

## 7. 新增的 Veil 风格 shader 能力

### 多 stage graphics shader

兼容路径里的 `ShaderProgramBuilder` 仍然保留，但新的首选入口是 `AdvancedShaderProgramBuilder`。

它支持：

- `VERTEX`
- `FRAGMENT`
- `GEOMETRY`
- `TESSELLATION_CONTROL`
- `TESSELLATION_EVALUATION`

并额外提供独立的：

- `COMPUTE`

graphics pipeline 示例：

```kotlin
val program = AdvancedShaderProgramBuilder()
    .vertex("core/example/example.vsh")
    .tessellationControl("core/example/example.tesc")
    .tessellationEvaluation("core/example/example.tese")
    .geometry("core/example/example.geom")
    .fragment("core/example/example.fsh")
    .build()
```

compute 示例：

```kotlin
val compute = AdvancedShaderProgramBuilder()
    .compute("core/example/example.comp")
    .buildCompute()
```

compute 已经进入统一 effect graph，可通过：

```kotlin
collector.submit(
    BuiltinRenderEffectDescriptors.computeDispatch(
        effectId = "example:compute",
        sourceInstanceId = entity.uuid.toString(),
        program = compute,
        groupX = 1
    )
)
```

### Veil 风格 shader buffer layout

新增了：

- `ShaderBufferBinding`
- `ShaderBufferMemoryLayout`
- `ShaderBufferFieldType`
- `ShaderBufferLayout`
- `ShaderBufferRegistry`
- `ShaderBufferObject`
- `ShaderBufferCache`

示例：

```kotlin
val layout = ShaderBufferRegistry.register(
    ShaderBufferLayout.builder<MyOrbData>("OrbData")
        .float("time") { it.time }
        .vec3("color")
        .mat4("transform")
        .build(
            requestedBinding = ShaderBufferBinding.UNIFORM_BUFFER,
            memoryLayout = ShaderBufferMemoryLayout.STD140
        )
)

val glsl = layout.createGlslBlock(
    shaderStorageSupported = true,
    interfaceName = "OrbBlock"
)
```

runtime 示例：

```kotlin
val layout = ShaderBufferRegistry.register(
    ShaderBufferLayout.builder<MyOrbData>("OrbData")
        .float("time") { it.time }
        .vec3("color") { it.color }
        .build()
)

val buffer = ShaderBufferCache.getOrCreate(layout)
buffer.upload(myOrbData)
buffer.bindBase()
```

`AdvancedShaderProgramBuilder` 还支持把 layout 直接声明到 program 上：

```kotlin
val program = AdvancedShaderProgramBuilder()
    .vertex("core/example/example.vsh")
    .fragment("core/example/example.fsh")
    .bufferLayout(layout)
    .build()
```

这样 program 在 `use()` / `dispatch()` 时会自动通过 `ShaderBufferCache` 绑定已声明 layouts。

同时，builder 产出的 program 现在会自动注册到 `ShaderProgramRegistry`，shader reload 时会统一 `releaseAll()`。

### 受管 program 元数据

`AdvancedShaderProgramBuilder` 与兼容层 `ShaderProgramBuilder` 现在都支持：

- `managedId(ResourceLocation)`
- `managedId(path: String)`
- `bufferLayout(...)`

program 也会暴露：

- `managedProgramId()`
- `shaderSources()`

`ShaderProgramRegistry` 现在不仅能整批 `invalidateAll()` / `reinitializeAll()`，还支持：

- `invalidateProgramsById(...)`
- `invalidateProgramsBySource(...)`

这已经把 compile-event 风格的增量失效接口预留好了，只是当前 reload 流程还没有真正传入 `updatedPrograms`。

### Veil 风格 layered render type

新增了：

- `CooRenderTypeDescriptor`
- `CooRenderTypeDescriptorBuilder`
- `CooLayeredRenderType`
- `LayeredVertexConsumer`
- `CooRenderTypeResourceRegistry`

并补了一条 data-driven 资源路径：

- `assets/cooparticlesapi/rendertypes/index.json`
- `assets/cooparticlesapi/rendertypes/*.json`

它的目标不是立即复制 Veil 的所有 render type builder 细节，而是先把“descriptor + layered pass fan-out”能力拉进当前框架。

示例：

```kotlin
val glow = CooParticlesServices.PLATFORM.getRenderTypesProvider().glow()
val solid = CooParticlesServices.PLATFORM.getRenderTypesProvider().create(
    CooRenderTypeDescriptor.builder("coo_solid_overlay")
        .build()
)
val layered = CooParticlesServices.PLATFORM.getRenderTypesProvider()
    .layered("coo_layered_overlay", solid, glow)

val consumer = layered.consumer(bufferSource)
```

命名资源取法：

```kotlin
val named = CooParticlesServices.PLATFORM.getRenderTypesProvider()
    .named(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "glow"))
```

## 8. 已知局限

- 当前 effect executor 仍然有历史 manager 形态，没有完全收敛成 Veil 那种统一 post manager
- `ShaderPipeManager` 目前仍偏 imperative，尚未进入数据驱动 descriptor 化
- 已经补上多 stage graphics shader 和 compute program 入口，并已进入 effect graph；但还没有更复杂的 compute 资源编排层
- 已经补上 `ShaderBufferLayout` / `ShaderBufferRegistry` / `ShaderBufferObject` / `ShaderBufferCache`，并且 program `use()` 会自动绑定已声明 layouts；registry 也已有按 id/source 的增量失效接口，但还没有真正的 VeilShaderCompileEvent 式 `updatedPrograms` 接入
- 已经有 data-driven render type 资源格式，但暂时还是 classpath/index 驱动，不是完整 resource-pack override / hot-reload runtime

这些是后续平台化工作的重点，但不应回退到“实体自己管理 framebuffer”。
