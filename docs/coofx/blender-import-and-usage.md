# CooFX Blender 导入与使用完全教程

本文是 CooFX v1 的端到端使用指南，面向把 Blender 资产接入 Minecraft 1.21.1 的 Fabric 与 NeoForge 开发者。内容只使用当前仓库审计确认的公开 API 和运行时行为。

适用版本：

- Blender 4.2 或更高版本
- Minecraft 1.21.1
- Java 21
- CooParticlesAPI `2.5.6-SNAPSHOT`（仓库当前 `gradle.properties` 版本）
- Fabric API `0.115.1+1.21.1` 或 NeoForge `21.1.200`
- CooFX v1

> 本文是使用手册，不是完整 Blender 模拟器说明。CooFX 不读取 `.blend`，不翻译任意 Shader Nodes 或 Geometry Nodes，也不承诺任意 Iris shaderpack 的视觉结果。

## 1. 先选择正确的播放路径

CooFX 有两条公开消费路径：

| 场景 | 入口 | 逻辑侧 | 适用情况 |
|---|---|---|---|
| 单个客户端本地效果 | `CooFXClient.playModel` / `CooFXClient.play` | 客户端 | UI、局部提示、只需本地看到的模型或 emitter |
| 服务端权威联机效果 | `CooFxSceneManager.spawn` | 服务端 | 多人同步、可见范围、相机目标和生命周期由服务端决定 |

不要在专用服务器调用 `CooFXClient`，也不要在客户端直接修改服务端场景的镜像。服务端场景必须通过 `CooFxSceneManager` 管理。

普通使用者不需要直接调用 `CooFxAssetImporter`、`MinecraftCooFxResourceProvider` 或 `CooFxAssetCompiler`。这些公开类适合资源工具和集成层；正常播放应优先使用上表的入口。

## 2. 前置条件与 loader 分工

### 2.1 共用工程条件

项目当前使用：

- Minecraft `1.21.1`
- Java `21`
- CooParticlesAPI `2.5.6-SNAPSHOT`
- CooFX 资源放在 `common/src/main/resources/assets/<namespace>/`

Fabric 与 NeoForge 共用资源和 common API，但客户端初始化由各 loader 接线：

- Fabric 客户端初始化 common client，并在 `CLIENT_STOPPING` 显式调用 `CooFXClient.stopClient()`。
- NeoForge 在 `FMLClientSetup` 初始化 common client，并接入 tick、世界与断开事件；当前审计源码没有发现等价的 NeoForge client-stopping `stopClient()` 调用，这是已知实现缺口。

这意味着业务代码应使用公开生命周期入口，不应自行假设 loader 的关闭回调细节。若需要确认具体接线，参阅 [`CooParticlesAPIClient.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt)、Fabric client 初始化和 NeoForge client listener 源码。

### 2.2 不启动 Minecraft 也能完成的验证

可以先完成以下静态检查：

1. Blender Add-on 校验通过。
2. `.coofx.json`、`.gltf`、`.bin` 和 PNG 路径一致。
3. 资源 ID 使用正确的 namespace 和相对路径。
4. 直接播放代码只出现在客户端入口或客户端调用链。
5. 服务端场景代码只在 Minecraft server thread 调用。

Minecraft 客户端的 Vanilla/Iris 组合视觉结果仍需要开发者在目标环境中人工验收；静态测试不能代替实际画面验证。

## 3. 安装 Blender Add-on

仓库工具位于：

```text
tools/blender_coofx/
```

在仓库根目录生成安装包：

```text
python tools/blender_coofx/build_extension.py
```

输出：

```text
build/distributions/coofx_exporter-1.0.5.zip
```

ZIP 根目录应直接包含 `__init__.py`、`blender_manifest.toml`、`LICENSE`、`core/` 和 `blender/`，不能再套一层 `tools/blender_coofx` 目录。

Blender 中的安装步骤：

1. 打开 `Edit > Preferences`。
2. 进入 `Get Extensions` 或 `Extensions`。
3. 选择 `Install from Disk`。
4. 选择 CooFX ZIP 并启用 `CooFX Exporter`。
5. 重新打开场景。
6. 在 `Scene Properties` 或 3D View 的 `N > CooFX` 中确认面板出现。

面板至少应提供资产校验、CooFX 导出和 GLB 快照导出入口。

## 4. Blender 场景准备

### 4.1 模型与材质

首次验证建议使用一个简单的 Cube、Icosphere 或低模网格：

- 至少有一个面；四边面和 n-gon 会由官方 glTF exporter 三角化。
- 纹理材质必须有 UV0。
- 使用 PNG 基础颜色纹理。
- 材质使用 `OPAQUE` 或 `MASK`。
- 材质名保持稳定，因为 `.coofx.json` 会引用导出的 glTF 材质名。

`BLEND`、第二套 UV、JPEG、KTX、Draco、meshopt 和 data URI 不属于当前可保证的运行时路径。

### 4.2 Camera

需要资产相机时：

1. 把一个或多个 Camera 放在导出集合中。
2. 使用稳定且唯一的名称，例如 `Camera_Main`。
3. 需要镜头运动时，给 Camera node 制作 location/rotation 动画。
4. 运行时使用 `cameraId` 选择镜头；为空时选择第一个 camera。

`cameraId` 先匹配编译后的稳定 glTF camera-node ID，再匹配唯一的可读 Blender Camera 名称。透视相机驱动位置、旋转和垂直 FOV；正交相机当前只驱动姿态，不替换 Minecraft 主投影，也不覆盖主 FOV。

### 4.3 Blender 坐标与单位

Blender 源场景是右手 Z-up；导出的 glTF/CooFX 运行时约定是右手 Y-up：

```text
Blender (x, y, z) -> CooFX (x, z, -y)
```

该换轴由官方 glTF exporter 负责。Minecraft runtime 不应再换轴，否则会发生二次旋转。

默认单位是：

```text
1 Blender unit = 1 Minecraft block
```

如果 Blender 使用厘米或毫米，CooFX 面板的 `unitScale` 必须与 Blender 场景单位一致。资产的世界变换仍使用 Minecraft 世界坐标。

CooFX 世界变换使用位置、四元数和缩放，而不是欧拉角：

```kotlin
CooFxWorldTransform(
    x = 10.0,
    y = 64.0,
    z = -4.0,
    rotationX = 0F,
    rotationY = 0F,
    rotationZ = 0F,
    rotationW = 1F,
    scaleX = 1F,
    scaleY = 1F,
    scaleZ = 1F,
)
```

位置和四元数分量必须是有限值；四元数必须归一化到允许范围；三个缩放分量必须是有限且严格为正。模型播放的浮点实例 ABI 还要求位置可表示为运行时使用的 float。

## 5. 导出与资源布局

### 5.1 推荐导出顺序

1. 在 CooFX 面板填入 namespace、资产 ID、单位缩放和固定 seed。
2. 先点击 `校验 CooFX 资产`。
3. 修复 error；warning 必须理解后再继续。
4. 点击 `导出 CooFX 资产`。
5. 把 PNG 放进 Minecraft 资源目录。
6. 检查 JSON 中引用的模型和纹理 ResourceLocation。

运行时清单通常是分离文件：

```text
.coofx.json + .gltf + .bin + .png
```

GLB 快照只用于离线检查，不会自动成为 CooFX 运行时清单。

### 5.2 `coofxAsset` 的固定约定

推荐使用：

```kotlin
val resourceId = coofxAsset("examplemod", "people")
```

它解析为：

```text
examplemod:coofx/people/people.coofx.json
```

因此资源文件应位于：

```text
assets/examplemod/coofx/people/people.coofx.json
assets/examplemod/coofx/people/people.gltf
assets/examplemod/coofx/people/people.bin
```

`assetID` 必须是一个小写段，并匹配 `[a-z0-9][a-z0-9._-]*`。如果资产使用嵌套路径或入口文件与目录不完全同名，应传入显式入口 ResourceLocation，并确保 JSON 中的模型引用是安全的相对资源 ID。

例如显式入口：

```text
cooparticlesapi:coofx/test/test_moudles.coofx.json
```

不要使用旧示例中的：

```text
assets/<namespace>/coofx/<name>.coofx.json
```

对于 `coofxAsset("mod", "name")`，正确路径一定包含同名目录：

```text
assets/mod/coofx/name/name.coofx.json
```

### 5.3 资源 ID 检查表

- JSON 后缀是 `.coofx.json`。
- 模型引用是 `.gltf` 或当前契约支持的 `.glb` ResourceLocation，而不是 Windows 路径。
- glTF 的 BIN URI 是相对路径，且实际文件名一致。
- 图片是 PNG，ResourceLocation 使用 `/`。
- 不要把资源放在 `common/src/test/resources`，除非它是测试 fixture。

更严格的 schema、字段和路径契约见 [`format-v1.md`](format-v1.md)。

## 6. 服务端权威场景

### 6.1 创建一个模型场景

以下代码只能在 Minecraft server thread 执行：

```kotlin
val scene = CooFxSceneManager.spawn(
    level = serverLevel,
    spec = CooFxSceneSpec(
        resourceId = coofxAsset("examplemod", "people"),
        transform = CooFxWorldTransform(
            x = 10.0,
            y = 64.0,
            z = -4.0,
        ),
        requestSeed = 0x5EEDL,
        mode = CooFxSceneMode.MODEL,
        clipId = "idle",
        playbackSpeed = 1F,
        renderRange = 256.0,
    ),
)
```

`CooFxSceneSpec` 的 ID 不能为空；播放速度必须有限且非负；render range 必须有限且为正。emitter 相关的数量和延迟不能为负，生命周期必须为正。

`spawn` 会注册场景的 RenderEntity 可见性，并立即应用维度和距离过滤。相同 `ownerKey` 或相同 `sceneId` 的旧场景会先停止。默认 scene ID 是随机 UUID，也可以由调用者传入稳定 UUID。

### 6.2 所有场景模式

`CooFxSceneMode` 当前支持：

| 模式 | 模型 | 相机 | emitter |
|---|---:|---:|---:|
| `MODEL` | 是 | 否 | 否 |
| `CAMERA_TRACKING` | 否 | 是 | 否 |
| `CAMERA_ONLY` | 否 | 是 | 否 |
| `MODEL_AND_CAMERA` | 是 | 是 | 否 |
| `MODEL_AND_EMITTER` | 是 | 否 | 是 |
| `MODEL_CAMERA_AND_EMITTER` | 是 | 是 | 是 |
| `EMITTER` | 否 | 否 | 是 |
| `CAMERA_AND_EMITTER` | 否 | 是 | 是 |

`CAMERA_ONLY` 是 `CAMERA_TRACKING` 的 JVM 字段别名。模式只决定场景参与哪些客户端子流程；emitter 模式还必须提供有效的 `emitterId` 才能启动 emitter。

### 6.3 相机接收者：所有、一个、多个

相机目标只决定本地镜头接管，不决定模型发送对象。三种明确写法如下。

所有收到场景的客户端都可以竞争相机：

```kotlin
CooFxSceneSpec(
    resourceId = coofxAsset("examplemod", "people"),
    transform = transform,
    requestSeed = seed,
    mode = CooFxSceneMode.MODEL_AND_CAMERA,
    cameraId = "Camera_Main",
    cameraTargetPlayer = null,
    cameraTargetPlayers = null,
)
```

只允许一个玩家接管：

```kotlin
CooFxSceneSpec(
    resourceId = resourceId,
    transform = transform,
    requestSeed = seed,
    mode = CooFxSceneMode.MODEL_AND_CAMERA,
    cameraTargetPlayer = targetPlayer.uuid,
)
```

允许多个指定玩家接管：

```kotlin
CooFxSceneSpec(
    resourceId = resourceId,
    transform = transform,
    requestSeed = seed,
    mode = CooFxSceneMode.MODEL_AND_CAMERA,
    cameraTargetPlayers = setOf(playerOne.uuid, playerTwo.uuid),
)
```

`cameraTargetPlayer` 与 `cameraTargetPlayers` 互斥；multiple 集合必须非空。两个字段都为 `null` 表示所有收到场景的客户端都可以参与相机竞争。目标玩家离开可见范围时不会再接管镜头，但场景模型是否发送仍由维度、距离和 RenderEntity 可见性决定。

同时可见的相机由每个客户端逐 tick 选择：`cameraPriority` 更高者胜出；同优先级按 scene UUID 字符串稳定决胜。相机目标过滤只作用于本地 camera claim，不会隐藏模型。

### 6.4 动态更新、替换和停止

句柄提供 `sceneId`、`isActive`、`update`、`replace` 和 `stop`：

```kotlin
scene.update(
    CooFxScenePatch(
        transform = nextTransform,
        playbackSpeed = 0.5F,
        cameraId = "Camera_Close",
        cameraPriority = 20,
        emitterCount = 48,
    ),
)
```

`CooFxSceneManager.update(sceneId, patch)` 与句柄的 `update` 等价；返回 `false` 表示场景不存在或已经停止。

Patch 是增量更新：字段为 `null` 时保留现值，不能用 `null` 清除 clip、camera 或 emitter，也不能用它改变资源 ID 或 seed。Patch 中的单玩家与多玩家 camera target 字段同样互斥，multiple 集合不能是空集合；Patch 不能清除现有 camera target。需要清除字段、换资源或换 seed 时，构造完整快照：

```kotlin
scene.replace(
    CooFxSceneSpec(
        resourceId = newResourceId,
        transform = nextTransform,
        requestSeed = newSeed,
        mode = CooFxSceneMode.MODEL,
        clipId = null,
        cameraId = null,
        emitterId = null,
    ),
)
```

`replace` 会返回布尔值；场景已经不存在时为 `false`。完成后调用：

```kotlin
scene.stop()
// 或 CooFxSceneManager.stop(scene.sceneId)
```

服务端还提供 `syncTo(player)`、`isActive(sceneId)`、`size()` 和 `clearServer()`。登录玩家会由现有同步 listener 接收可见场景；需要针对特定玩家立即补发时使用 `syncTo`，同样必须在 server thread。

### 6.5 生命周期时间

`lifetimeTicks` 是服务端场景 TTL；它不是 emitter 粒子生命周期。`emitterLifetimeTicks` 只是 emitter 粒子的生命周期覆盖值。两者都必须遵守各自的正值约束。

默认 `renderRange` 是 `256.0`。场景发送给同维度且在范围内的玩家；离开范围会移除，重新进入范围会重新同步。`cameraTargetPlayer` 和 `cameraTargetPlayers` 不改变这一模型可见性规则。

## 7. 客户端本地播放

### 7.1 模型播放

纯模型资产使用 `CooFXClient.playModel`。这段代码只能在客户端：

```kotlin
val result = CooFXClient.playModel(
    CooFxModelPlayRequest(
        resourceId = coofxAsset("examplemod", "people"),
        transform = CooFxWorldTransform(
            x = position.x,
            y = position.y,
            z = position.z,
        ),
        requestSeed = 0x5EEDL,
        clipId = "idle",
        playbackSpeed = 1F,
    ),
)
```

### 7.2 `Started`、`Queued`、`Failed`

播放结果是 sealed interface，必须分别处理：

```kotlin
when (result) {
    is CooFxModelPlayResult.Started -> {
        val handle = result.handle
        // 保存 handle；需要结束时调用 handle.stop()。
    }
    is CooFxModelPlayResult.Queued -> {
        // 资源 snapshot 或 GPU generation 尚未准备好。
        // 这是正常异步状态，不是失败。
    }
    is CooFxModelPlayResult.Failed -> {
        val failure = result.failure
        // 记录 resourceId、stage、message 和可选 cause。
    }
}
```

`Queued` 请求会在有效的 `WORLD_PASS` 中由运行时重试和启动；调用者不应自行解析 JSON、创建 GL 对象或建立另一个 shader/framebuffer 路径。失败应记录结构化 `CooFxPlaybackFailure`，不要在 render loop 中无限重试格式错误的资产。

`CooFxPlaybackHandle` 提供 `instanceId`、`isAlive` 和 `stop()`。资源重载、断开连接、世界切换和 `stopClient()` 后，旧 handle 必须视为无效。

包含 emitter 的入口使用 `CooFXClient.play` 和 `CooFxPlayRequest`。只有资源确实带 emitter 并且需要发射时才使用它；不要把 `play` 当成纯模型 API。

### 7.3 原地更新模型

`CooFXClient.updateModel(handle, request)` 只能在客户端线程执行：

```kotlin
val updated = CooFXClient.updateModel(
    handle = handle,
    request = CooFxModelPlayRequest(
        resourceId = coofxAsset("examplemod", "people"),
        transform = nextTransform,
        requestSeed = 0x5EEDL,
        clipId = "idle",
        playbackSpeed = 1F,
    ),
)

if (!updated) {
    // handle 在 stop、清理、世界切换或资源重载后失效。
    handle.stop()
}
```

该方法更新已有模型实例，不创建实例，不接管 camera，不拦截输入，也不调用服务器。返回 `false` 时应停止并丢弃 handle，而不是继续提交更新。

## 8. 被动的玩家相对变换

`CooFxPlayerRelativeTransforms` 是一个纯变换工具，不是 camera callback。它读取玩家位置和视角角度，返回新的世界变换；不会修改玩家旋转，不写输入，不注册鼠标回调，也不会接管 CooFX camera。

Blender 局部约定：

```text
+Z = 前方
+X = 玩家右侧
+Y = 上方
```

### 8.1 使用当前本地玩家视角

```kotlin
val worldTransform = CooFxPlayerRelativeTransforms.fromPlayerView(
    player = localPlayer,
    blenderLocalTransform = CooFxWorldTransform(
        x = 0.0,
        y = 1.2,
        z = 2.0,
        scaleX = 0.5F,
        scaleY = 0.5F,
        scaleZ = 0.5F,
    ),
)
```

### 8.2 充能效果示例

充能效果可以在客户端 tick 中根据当前视角重新计算位置，再调用 `updateModel`：

```kotlin
val localTransform = CooFxWorldTransform(
    x = 0.0,
    y = 1.2,
    z = 2.0,
    scaleX = 0.5F,
    scaleY = 0.5F,
    scaleZ = 0.5F,
)

val nextTransform = CooFxPlayerRelativeTransforms.fromView(
    playerPosition = localPlayer.position(),
    viewYawDegrees = localPlayer.yRot,
    viewPitchDegrees = localPlayer.xRot,
    blenderLocalTransform = localTransform,
)

CooFXClient.updateModel(
    handle = chargeHandle,
    request = CooFxModelPlayRequest(
        resourceId = coofxAsset("examplemod", "charge"),
        transform = nextTransform,
        requestSeed = 0xCAFEL,
        clipId = "charge",
        playbackSpeed = 1F,
    ),
)
```

上例中的 seed 应写成合法 Kotlin 数字字面量 `0xCAFEL`，不要在实际代码中保留空格。这个流程是被动的玩家相对模型，不会触发外部相机行为。

## 9. 外部相机行为

只有场景模式包含 camera，且当前客户端赢得 camera claim 时，CooFX 外部相机才会生效：

- 透视资产相机会驱动 camera 位置、旋转和垂直 FOV。
- 当前玩家身体仍由游戏世界渲染；camera claim 不会删除或隐藏玩家实体。
- 第一人称手部渲染会被取消，因此不会出现在被接管的镜头中。
- Vanilla walking bob 和 hurt bob 会被取消。
- 鼠标移动会被取消，玩家不会用鼠标改变该外部镜头。
- 该行为与 `fromPlayerView/fromView` 完全不同；后者永远不拥有输入或镜头。
- 正交 camera 目前只提供姿态，不替换 Minecraft 主投影，也不提供主投影 FOV 覆盖。

相机优先级冲突时检查 `cameraPriority`、目标玩家集合和 scene UUID。相机没有匹配到有效 camera、场景被移除或 registry 被清理时，claim 会失效并恢复普通视角。

## 10. 线程、逻辑侧与生命周期

| 操作 | 允许线程/逻辑侧 | 不应做的事 |
|---|---|---|
| `CooFxSceneManager.spawn/update/replace/stop/syncTo/clearServer` | Minecraft server thread，服务端 | 触碰 GPU 或客户端状态 |
| `CooFXClient.init/play/playModel/stopClient` | 客户端生命周期/客户端调用链 | 在专用服务器调用 |
| `CooFXClient.updateModel` | 客户端线程 | 跨线程提交或调用服务器 |
| JSON、glTF、BIN、PNG 读取和 CPU 资源处理 | 资源线程或普通 CPU 线程 | 创建 GL 对象 |
| GPU generation 创建、上传、绘制、释放 | Coo Pipeline 的 `WORLD_PASS` / render thread | 在业务线程创建 VAO、VBO、纹理或 shader |

客户端流程由 common client 初始化，资源 reload 时释放并重新建立渲染资源，世界切换和断开连接时清理瞬态模型、emitter handle 与 camera claim。客户端关闭时应在 GL context 销毁前调用 `CooFXClient.stopClient()`；Fabric 已有明确 wiring，NeoForge 当前源码的 stop hook 是实现缺口。

不要解析 JSON、创建 OpenGL 对象、修改 VAO 或建立平行 vanilla shader/framebuffer 路径来绕过这些边界。CooFX 必须继续使用现有 Coo Pipeline `WORLD_PASS` 与 render-thread 生命周期。

## 11. Iris、材质和已知限制

### 11.1 运行时保证范围

当前可靠能力包括：

- rigid node/TRS
- `TRIANGLES`
- `OPAQUE` 与 `MASK`
- POSITION、NORMAL、TEXCOORD_0、COLOR_0
- 基础 PNG 颜色纹理
- glTF 节点层级与 translation/rotation/scale
- 受支持的刚性动画 clip
- 受约束的 emitter 语义

### 11.2 明确不保证或不执行

以下功能会被拒绝、告警或只保留元数据：

- skin runtime
- morph target runtime
- VAT runtime 纹理采样和顶点变形
- `BLEND` 网格粒子材质
- shadows
- 第二套 UV 执行
- arbitrary Shader Nodes 或 Geometry Nodes
- Blender 刚体、流体、烟雾、布料、完整物理和 Boids
- JPEG、KTX、Draco、meshopt、data URI
- 未知的 required glTF extension
- light、audio

### 11.3 Iris 兼容边界

CooFX 只知道当前是否有 Iris shaderpack 活动，不知道 shaderpack 的身份和能力协商结果。运行时会对 Iris `NEW_ENTITY` 路径提供通用中性属性，但不能保证任意 shaderpack 对以下内容的解释：

- entity color modulation
- UV/overlay
- PBR
- fog
- deferred/G-buffer
- vertex displacement
- emissive second pass
- shadows 或 bloom

进入 Iris entity bridge 时，当前客户端 session 会发出一次 warning。没有对目标 shaderpack 做实际视觉验证时，应将其 shader 部分视为未验证，而不是宣称通用兼容。正交相机也不会替换 Minecraft 主投影。

## 12. 性能与 VAO 状态边界

普通消费者不应直接使用内部 renderer 或 GPU package。若你在 CooParticlesAPI 的集成层实现渲染扩展，必须遵守：

- 模型播放会绘制导出 scene 中的每个 mesh node。
- mesh particle 实例数据是独立的 144-byte ABI；另有 48-byte affine node-matrix sidecar。
- 静态 VAO/EBO、shader、纹理、framebuffer、viewport 和 Pipeline 状态由对应生命周期所有者管理。
- 实例数据按稳定 batch 批量绘制，不要为每个粒子创建 RenderEntity。
- 任何状态保存与恢复都必须包含 VAO 0 的 generic attributes，以及 live VAO 的 enabled attributes/divisors。
- 不要在绘制结束时把 OpenGL 状态硬编码重置为“默认值”；调用方必须恢复进入绘制前的真实状态。

这些是集成层约束，不是让普通业务调用方自行创建 GL 资源的授权。直接 GL、vanilla `ShaderInstance`、平行 framebuffer 或绕过 Coo Pipeline 的示例都不属于公共 CooFX 使用方式。

## 13. 清理、重载与关闭

### 13.1 本地句柄

保存 `Started.handle`，在效果结束时调用 `handle.stop()`。停止后不要继续调用 `updateModel`。

以下边界会使旧句柄失效：

- `CooFXClient.stopClient()`
- 资源重载
- 世界切换
- 断开连接
- 客户端 registry 或瞬态状态清理

### 13.2 服务端场景

服务端逻辑结束时调用 scene handle 的 `stop()`，或调用 `CooFxSceneManager.stop(sceneId)`。服务器关闭、世界清理或模组状态重置时可调用 `clearServer()`。不要直接构造或修改 RenderEntity carrier，也不要手动修改客户端镜像。

### 13.3 资源重载

资源解析和 CPU 编译可以在非 render thread；GPU generation 的创建、替换、旧 generation retirement 和 release 必须回到 render thread。上传失败时不应把仍可用的旧 generation 误替换掉。调用方只需处理 `Started/Queued/Failed` 和失效 handle，不需要管理 GPU package。

## 14. 结构化诊断与排障矩阵

| 现象 | 优先检查 | 修复方向 |
|---|---|---|
| `Queued` 长时间存在 | 资源 snapshot、GPU generation、有效 WORLD_PASS | 不要把 `Queued` 当失败；确认客户端 render lifecycle 正常 |
| `Failed` | `failure.resourceId`、`stage`、`message`、`cause` | 修复对应资源或请求；不要在 render loop 无限重试 |
| 找不到 `.coofx.json` | `coofxAsset` 目录约定和 namespace | 使用 `assets/<ns>/coofx/<id>/<id>.coofx.json` |
| 找不到 glTF 或 BIN | JSON 的 `model`、相对 BIN URI、文件名大小写 | ResourceLocation 使用 `/`，不要写磁盘绝对路径 |
| 找不到 PNG | `baseColorTexture` ResourceLocation、PNG 文件位置、UV0 | 放入 `assets/<ns>/textures/...`，确认 JSON 使用完整 ID |
| 资源导入失败 | schemaVersion、coordinateSystem、路径安全、primitive、accessor | 查看 `CooFxDiagnostic`，不要删除字段后继续绘制 |
| compiler 拒绝 BLEND/skin/morph/VAT | 当前能力边界 | 改为 OPAQUE/MASK 或刚性节点；不要伪造 metadata 绕过拒绝 |
| 相机没有接管 | mode、cameraId、目标 UUID、优先级、相机是否可见 | `null` 选择第一个 camera；确认目标只影响本地 camera claim |
| 模型不显示但相机可用 | renderRange、维度、RenderEntity 可见性、资源是否为模型参与模式 | 模型可见性与 camera target 是两套规则 |
| 多个相机结果不稳定 | `cameraPriority` 和 UUID 决胜 | 提高目标场景 priority；同优先级由 UUID 字符串稳定决胜 |
| 旧 handle 更新返回 `false` | reload、world change、disconnect、stop | 丢弃旧 handle，重新发起合法播放请求 |
| `Invalid VAO` 或 GL 状态日志 | 是否由业务代码自行创建/释放 GL，是否跨线程绘制 | 移除直接 GL；交给 Coo Pipeline/render-thread 生命周期；不要重置到假定默认状态 |
| Iris 画面与 Vanilla 不同 | shaderpack 的 G-buffer、PBR、fog、emissive、vertex 行为 | 把任意 shaderpack 视为未验证，进行目标环境人工视觉验收 |
| 正交镜头大小不符合预期 | 当前只采样 pose | 不要期待它替换 Minecraft 主 projection 或提供 FOV override |

## 15. 最小发布前检查清单

### Blender

- [ ] Blender 至少 4.2。
- [ ] CooFX Add-on 已启用。
- [ ] 目标 mesh 至少有一个面。
- [ ] 纹理 mesh 有 UV0。
- [ ] 材质使用 OPAQUE 或 MASK。
- [ ] Camera 名称稳定且唯一。
- [ ] unitScale 与 Blender 场景单位一致。
- [ ] asset seed 固定且符合格式。

### 资源

- [ ] JSON、glTF、BIN 和 PNG 都存在。
- [ ] JSON 是 UTF-8 无 BOM。
- [ ] `coofxAsset` 使用同名目录约定，或显式 ID 已逐项核对。
- [ ] JSON 没有 Windows 绝对路径、反斜杠或 `..`。
- [ ] glTF 的 BIN URI 和实际文件一致。
- [ ] 没有依赖 GLB 快照代替运行时清单。

### 代码

- [ ] 服务端场景调用在 server thread。
- [ ] `CooFXClient` 调用只在客户端。
- [ ] `updateModel` 在 client thread。
- [ ] 所有 transform 位置和四元数有限，四元数归一化，scale 严格为正。
- [ ] 以 `Started/Queued/Failed` 处理播放结果。
- [ ] reload/world change/disconnect 后不复用旧 handle。
- [ ] camera target 没有被误当作模型可见性过滤。
- [ ] 没有直接创建 GL、vanilla shader 或平行 framebuffer 路径。

## 16. 相关文档与源码

- 资产主指南：本文
- [`format-v1.md`](format-v1.md)：CooFX JSON/glTF 资产契约
- [`architecture.md`](architecture.md)：线程、pipeline、GPU generation 与生命周期架构
- [`CooFXClient.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFXClient.kt)：客户端公开入口
- [`CooFxPlayerRelativeTransforms.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxPlayerRelativeTransforms.kt)：被动玩家相对变换
- [`CooFxSceneManager.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/server/CooFxSceneManager.kt)：服务端权威场景管理
- [`CooFxSceneSpec.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/server/CooFxSceneSpec.kt)：场景规格、Patch、Handle 和模式
- [`CooFxConsumerContracts.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/adapter/CooFxConsumerContracts.kt)：公开消费契约和变换约束
- [`CooFxAssets.kt`](../../common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/CooFxAssets.kt)：`coofxAsset` 路径约定

## 17. 结论

推荐的最短路径是：在 Blender 中使用受约束的模型、刚性节点动画和 emitter，导出 `.coofx.json + .gltf + .bin + PNG`，按 `coofxAsset` 约定放入 common 资源目录；单机本地效果使用 `CooFXClient.playModel/play`，多人同步效果使用 server-thread 的 `CooFxSceneManager`。把 `Queued` 当作正常异步状态，把资源重载和世界切换后的 handle 当作失效状态，并把 Vanilla/Iris 实际画面验证留在目标客户端环境中完成。
