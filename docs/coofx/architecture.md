# CooFX 首版纵向切片架构

状态：首版资产链、确定性 mesh runtime、刚性 node pose sidecar、原版 lightmap、glTF 自发光和 Iris NEW_ENTITY 展开路径已接通；Vanilla/Iris 实机视觉验证仍待完成
适用版本：CooParticlesAPI 2.5.5.3、Minecraft 1.21.1、Java 21、Kotlin 2.2.0、Fabric / NeoForge
文档范围：定义架构、边界、文件计划与验收方式，并明确当前实现状态。

> 当前实现边界：importer 支持严格 `.gltf`/`.glb` 校验及 CooFX clip/material/emitter 绑定，CPU compiler 保留精确 bind matrix、稳定节点拓扑和可执行 transform tracks；mesh runtime 完成确定性模拟、多 primitive 批次、144-byte 实例 ABI，以及每实例 48-byte affine node world matrix sidecar。Vanilla 路径采样 Minecraft lightmap，并在受光基础色上叠加 glTF emissive；Iris 路径先用 transform feedback 展开为 36-byte `NEW_ENTITY` 顶点，再交给 entity program，emissive 只向主颜色附件追加 HDR。morph、skin、VAT、BLEND 继续在 compiler 边界拒绝。

## 1. 目标

CooFX 首版必须打通一条真实可用的纵向链路：

```text
Blender 4.2+ 场景
  -> CooFX Blender Add-on
  -> glTF 2.0 分离文件 + CooFX JSON v1
  -> Minecraft ResourceManager
  -> asset/importer
  -> playback
  -> compiled render package
  -> GPU residency
  -> mesh particle runtime
  -> Coo Pipeline world pass
  -> Fabric / NeoForge 客户端
```

首个验收样例由一个静态三角网格、一个刚性节点动画、一个确定性网格粒子发射器和一个不透明或裁剪材质组成。相同资源、种子和 tick 输入必须生成相同的发射序列、轨道采样结果和批次清单。

首版必须保留以下深模块，不能合并成单个 manager 或 renderer：

1. `asset/importer`：资源文档、glTF 解析、访问器解码、诊断和规范化。
2. `playback`：动画轨道、播放时钟、节点姿态和确定性采样。
3. `mesh particle runtime`：实例生命周期、CPU 模拟、批次构建和实例上传。
4. `compiled render package`：把导入数据编译成稳定的 CPU 渲染包，并管理对应 GPU 常驻对象。
5. `consumer adapters`：RenderEntity、直接客户端播放和现有客户端生命周期的适配。
6. Blender Add-on：只生成规范化资产，不直接生成 Kotlin 或调用运行时实现细节。

## 2. 仓库证据与约束

本设计基于当前仓库源码中的以下事实：

- `common`、`fabric`、`neoforge` 是三个正式模块；没有独立 client source set。客户端类型虽然位于 `common/src/main`，但只能从 loader 客户端入口和客户端生命周期触达。
- `RenderEntityRenderer` 按实体类型共享 renderer，pipeline 必须不可变；renderer 不能保存当前实体的普通可变状态。
- `RenderEntity` 当前是逐实体提交，不提供跨实体实例化绘制。因此海量网格粒子不能伪装为 RenderEntity 批处理。
- `CParticleStore` 的 36-float ABI 专用于 billboard 粒子，并包含其生命周期、纹理和控制语义。CooFX 网格实例必须使用独立 ABI，不能修改或复用这 36 个 float 的含义。
- `RenderEntityModel` 是扁平 CPU 几何，默认执行器只上传 position、color、uv。它不保存 glTF node、scene、skin、morph 或动画层级。
- `ObjModelLoader` 不保留材质和节点层级，并且静态几何会进入逐 primitive 动态上传路径。它不作为 CooFX glTF importer 的基础。
- `CooRenderPipeline` 是不可变 DAG，现有 backend 已管理 Vanilla / Iris 阶段、场景资源和后处理。CooFX 必须接入该路径，不能新增平行的 `ShaderInstance` 或 framebuffer 框架。
- `CooShaderSourceLoader` 已提供 ResourceProvider、`#coo_import` 和循环检测。CooFX 自有 shader 必须经该 loader 读取。
- `ShaderReloadBus.FullReload` 汇聚 Fabric 与 NeoForge 的资源重载。其回调线程不能被假定为持有 GL 上下文。
- `CooParticlesAPIClient.reloadShaderPrograms`、`clearTransientClientState` 和 `ClientRenderPipelineManager` 已经是客户端资源、世界切换和帧阶段的稳定内部落点。
- Blockbench 工具已采用“UI 状态 -> 版本化 IR -> 校验 -> 导出”的方向。Blender Add-on 延续相同原则，但 CooFX 资产 schema 与现有 Composition IR v1 是独立协议，不能互相冒充。

## 3. 核心决策

### 3.1 新增独立根包

CooFX 的 Kotlin 根包固定为：

```text
cn.coostack.cooparticlesapi.coofx
```

不把实现放入 `renderer.pipeline`。`CooPipelines` 是 CooFX 的渲染依赖，不是 CooFX 的领域所有者。公开入口采用：

- `cn.coostack.cooparticlesapi.coofx.CooFX`：仅持有服务端安全的标识、请求模型和注册入口，不引用 `net.minecraft.client`、OpenGL 或 loader 类型。
- `cn.coostack.cooparticlesapi.coofx.client.CooFXClient`：客户端播放、预加载和停止入口。

两者不能通过 `CooFX` 的对象初始化互相引用，避免 dedicated server 类加载客户端类型。

### 3.2 三阶段资产形态

资产在运行时有三个严格分离的形态：

1. `CooFxSourceAsset`：JSON 与 glTF 解析后的规范化 CPU 文档，保留节点、轨道、材质和发射器语义。
2. `CooFxCompiledRenderPackage`：验证并编译后的不可变 CPU 包，包含扁平 mesh primitive、批次模板、动画采样表和变形计划，不包含 GL handle。
3. `CooFxGpuPackage`：只在渲染线程创建和释放的 VAO、VBO、EBO、实例缓冲、纹理引用和 program binding。

任何解析线程都不能构造 `CooFxGpuPackage`。任何渲染路径都不能临时解析 JSON 或 glTF。

### 3.3 首版能力边界

首版支持：

- glTF 2.0 JSON `.gltf` + 分离 `.bin`，以及离线/手工资产使用的 GLB 2.0；GLB 的 BIN chunk 支持 accessor 数据，不支持 `image.bufferView` 内嵌纹理。
- 静态 mesh 和刚性实例 TRS。
- node translation、rotation、scale 动画的导入、clip 映射与独立 CPU 采样。
- `STEP`、`LINEAR`、`CUBICSPLINE` 轨道；每粒子 previous/current clip time 在帧 partial tick 下采样，并通过独立 `mat4` sidecar 进入 instanced draw。
- 不透明和 alpha mask 材质。
- base-color/emissive PNG、vertex color、normal、UV0、`emissiveFactor` 和 `KHR_materials_emissive_strength`。
- CPU 网格粒子模拟和 GPU instanced draw。
- Vanilla lightmap，以及把网格展开到 Iris entity G-buffer 的 world-pass 路径。
- 资源重载、世界切换和客户端关闭时的明确释放。
- Blender 4.2+ Extension Add-on 导出。

首版明确不支持：

- 网络 URI、绝对路径、父目录逃逸、data URI；Blender runtime bundle 仍固定输出分离 `.gltf`/`.bin`，GLB 只作为 importer 或离线快照输入。
- 内嵌 image bufferView、JPEG、KTX、Draco、meshopt 扩展。
- glTF light、audio 和除 `KHR_materials_emissive_strength` 外的未知必需扩展。camera 描述符、node 绑定和 TRS clip 已支持；正交 camera 暂不替换 Minecraft 主投影。
- 第二套 UV、PBR metallic-roughness 完整材质、材质图节点翻译。
- mesh particle 的 alpha blend。解析器可以识别 `BLEND`，但编译网格粒子包时必须给出可定位诊断并拒绝，不能静默降级。
- 运行时任意脚本、表达式字符串、网络资源或反射构造。
- morph、skin 或 VAT 的运行时执行。它们必须进入显式变形计划和诊断，不能被忽略。
- 跨 RenderEntity 的自动实例合批。

## 4. 首版纵向切片

### 4.1 用户流程

1. 美术在 Blender 中创建一个或多个 mesh，并可选配置刚性动画 clip 和 CooFX emitter；纯模型资产不需要 emitter。
2. Add-on 调用 Blender 官方 glTF exporter 生成 `.gltf` 和 `.bin`，自身生成 `.coofx.json`。
3. 三个文件和 PNG 被放入 `common/src/main/resources/assets/<namespace>/coofx/...`。
4. Minecraft 完整资源重载读取 CooFX JSON，解析其引用的 glTF、buffer 和 texture。
5. importer 产出规范化 `CooFxSourceAsset`；compiler 产出 `CooFxCompiledRenderPackage`。
6. reload apply 阶段在渲染线程上传新的 `CooFxGpuPackage`，原子替换旧 generation。
7. consumer 通过 `CooFXClient.playModel(request)` 创建纯模型实例，或通过 `CooFXClient.play(request)` 启动资产内置 emitter。
8. playback 按逻辑 tick 独立推进模型 clip 或 emitter/粒子 clip；只有 emitter 路径进入确定性 mesh particle 模拟。
9. world pass 按批次键收集实例，上传实例缓冲并执行 instanced draw。
10. 世界切换只清空播放实例；资源重载和客户端关闭释放 GPU package。

### 4.2 首版样例资产

仓库后续实现应增加一个最小样例：

```text
common/src/test/resources/assets/cooparticlesapi/coofx/examples/rigid_burst.coofx.json
common/src/test/resources/assets/cooparticlesapi/coofx/examples/rigid_burst.gltf
common/src/test/resources/assets/cooparticlesapi/coofx/examples/rigid_burst.bin
common/src/test/resources/assets/cooparticlesapi/coofx/examples/rigid_burst.png
```

样例包含：

- 一个 `TRIANGLES` primitive。
- POSITION、NORMAL、TEXCOORD_0。
- 一个父节点和一个带 LINEAR rotation 的子节点。
- 一个固定 count、固定 lifetime、固定 seed 的 burst emitter。
- 一个 alpha mode 为 `OPAQUE` 或 `MASK` 的材质。

## 5. 坐标系与单位

### 5.1 CooFX 规范坐标系

CooFX 规范空间固定为：

- 右手坐标系。
- `+X`：Minecraft 世界东向。
- `+Y`：Minecraft 世界上方。
- `+Z`：Minecraft 世界南向，同时定义为 CooFX 局部前方。
- `X × Y = Z`。
- 长度单位：1 CooFX unit = 1 Minecraft block。
- 角度：资源中使用弧度；四元数顺序为 `(x, y, z, w)`。
- 矩阵：文档语义使用列向量，组合顺序为 `world = parentWorld * local`，local 为 `T * R * S`。
- 法线使用 inverse-transpose；对象负缩放由官方 glTF exporter 在应用变换时处理 winding，运行时节点 TRS 保留有限负分量。

CooFX 与 glTF 2.0 均采用右手、Y-up 规范。导入 glTF 后不做额外轴交换。glTF 的数值 1 解释为 1 block。

### 5.2 Blender 到 CooFX

Blender 原生为右手、Z-up，约定对象正面为 `-Y`。Add-on 通过 Blender 官方 glTF exporter 输出标准 Y-up glTF，等价的概念转换为：

```text
(x_blender, y_blender, z_blender)
  -> (x_coofx, y_coofx, z_coofx)
  -> (x_blender, z_blender, -y_blender)
```

该转换是纯旋转，不改变 handedness 和三角形 winding。Add-on 默认 1 Blender unit = 1 block；项目设置可以提供显式正数 `unitScale`，并把缩放烘焙进导出节点。运行时不再猜测单位。

### 5.3 UV、颜色与时间

- UV0 按 glTF 数值原样保存，不在 importer 中隐式 flip V。
- PNG 按 Minecraft 纹理资源读取；base-color texture 视为 sRGB，vertex color 和动画数值视为线性数据。
- glTF 动画时间单位为秒。
- runtime 播放时钟内部使用整数 tick 加 partial tick；采样时转换为秒：`seconds = (tick + partialTick) / 20`。
- emitter 的 spawn、lifetime 和 delay 在 CooFX JSON 中使用 tick，避免资源调度受浮点累计误差影响。

## 6. 确定性随机

### 6.1 算法

CooFX v1 固定使用 SplitMix64 作为位级确定性生成器。Kotlin、Python exporter 测试和未来离线工具必须使用相同的无符号 64-bit 溢出语义。

种子链：

```text
assetSeed
  -> effectSeed = mix64(assetSeed xor requestSeed)
  -> emitterSeed = mix64(effectSeed xor fnv1a64(emitterId))
  -> particleSeed = mix64(emitterSeed xor emissionOrdinal)
  -> channelSeed = mix64(particleSeed xor fnv1a64(channelName))
```

规则：

- JSON 中 64-bit seed 使用固定 16 位小写十六进制字符串，不使用 JSON number。
- `requestSeed` 未提供时不读取系统时间；由 consumer 明确提供，或使用文档中声明的 asset seed。
- `emissionOrdinal` 是 emitter 内从 0 单调递增的 64-bit 序号，不依赖数组删除、批次排序或线程调度。
- 字符串哈希固定为 UTF-8 上的 FNV-1a 64。
- `[0, 1)` float 使用随机 64-bit 值的高 24 位除以 `2^24`，保证 Kotlin 与 Python 一致。
- 不使用 `java.util.Random`、`kotlin.random.Random`、`RandomSource`、世界随机或集合迭代顺序决定 CooFX 结果。
- CPU 和未来 GPU 模拟必须共享 golden vectors；GPU 路径不能改变已公开的 seed 流。

确定性只覆盖 CooFX 自身的发射、轨道和无碰撞模拟。首版不承诺跨客户端重现世界碰撞、外部实体查询或 shader 浮点光栅结果。

## 7. CooFX JSON v1

### 7.1 资源位置

运行资源约定：

```text
assets/<namespace>/coofx/<name>.coofx.json
assets/<namespace>/coofx/models/<name>.gltf
assets/<namespace>/coofx/models/<name>.bin
assets/<namespace>/textures/coofx/<name>.png
```

CooFX 资源 ID 使用去掉 `.coofx.json` 后的 `ResourceLocation`。例如：

```text
cooparticlesapi:coofx/examples/rigid_burst.coofx.json
-> cooparticlesapi:examples/rigid_burst
```

### 7.2 顶层结构

v1 顶层字段：

```json
{
  "$schema": "cooparticlesapi:coofx/schema/v1",
  "schemaVersion": 1,
  "coordinateSystem": "coofx_rh_y_up_z_south",
  "assetSeed": "0123456789abcdef",
  "model": "cooparticlesapi:coofx/models/rigid_burst.gltf",
  "scene": 0,
  "clips": [],
  "materials": [],
  "emitters": [],
  "requiredExtensions": [],
  "extensions": {}
}
```

约束：

- `$schema` 仅用于工具定位，不替代 `schemaVersion`。
- `schemaVersion` 必须是整数 `1`。未知版本硬失败，不能强制改写为 1。
- `coordinateSystem` v1 只接受 `coofx_rh_y_up_z_south`。
- 所有 number 必须有限；负 lifetime、负 count、零或负 scale 上限等非法值在 importer 阶段报错。
- Coo 定义对象默认 `additionalProperties: false`。
- 扩展只能进入 `extensions`，key 必须是 ResourceLocation 字符串。
- `requiredExtensions` 中出现未知项时硬失败；非 required 的 CooFX extension（包括 `cooparticlesapi:blender_export`）和 glTF 顶层 extension 以序列化 JSON 元数据保存在 `CooFxSourceAsset`，并给出未执行 warning。
- ResourceLocation URI 只允许同 namespace 或显式 namespace 的资源；拒绝 `http:`、`https:`、`file:`、绝对路径和 `..`。
- 诊断必须带 CooFX 资源 ID、JSON pointer；glTF 诊断还必须带 glTF resource、对象索引和 accessor 索引。

规范 schema 的单一事实源计划放在：

```text
schemas/coofx/coofx-v1.schema.json
```

Blender Add-on 打包时复制该文件并校验 SHA-256，不能手工维护第二份分叉 schema。runtime 使用类型化解析与等价校验，不要求新增 JSON Schema 运行时依赖。

### 7.3 版本演进

- v1 文档和 parser 冻结后只允许增加 optional extension，不允许改变现有字段语义。
- v2 必须有独立 schema 和显式 `v1 -> v2` 离线迁移器。
- runtime 不做隐式跨版本迁移；Add-on 可以在用户确认后迁移项目配置。
- compiled package 带 `sourceSchemaVersion`、`compilerVersion` 和源资源内容摘要，禁止加载版本不匹配的缓存。

## 8. glTF 2.0 支持边界

### 8.1 首版接受

- `asset.version == "2.0"`。
- 单个 CooFX 文档引用一个 `.gltf`。
- 外部 `.bin` buffer，URI 相对当前 glTF 资源目录解析。
- bufferView 的 byteOffset、byteLength、byteStride。
- accessor componentType：BYTE、UNSIGNED_BYTE、SHORT、UNSIGNED_SHORT、UNSIGNED_INT、FLOAT。
- normalized integer attribute。
- SCALAR、VEC2、VEC3、VEC4、MAT4。
- indexed 和 non-indexed primitive。
- primitive mode 只接受 TRIANGLES。
- POSITION 必需；NORMAL、TEXCOORD_0、COLOR_0 可选。
- node matrix 或 TRS；同一 node 不能同时把 matrix 与 TRS 当成两个来源。
- scene、node 层级和刚性 node 动画；importer 只把选定 scene 根节点闭包内的 node、mesh 和动画通道写入规范资产，CooFX emitter 不得引用闭包外 node/mesh。
- animation channel 的 translation、rotation、scale。
- STEP、LINEAR、CUBICSPLINE；rotation LINEAR 使用归一化 slerp，CUBICSPLINE 按 glTF Hermite 规则计算并归一化输出。
- material 的 baseColorFactor、baseColorTexture、alphaMode OPAQUE/MASK、alphaCutoff、doubleSided、emissiveFactor、emissiveTexture 和 emissiveStrength。
- PNG image URI。

### 8.2 首版拒绝

- sparse accessor。
- primitive mode POINTS、LINES、LINE_STRIP、LINE_LOOP、TRIANGLE_STRIP、TRIANGLE_FAN。
- TEXCOORD_1、JOINTS_n、WEIGHTS_n 的执行。
- morph target 的执行。
- skin、inverseBindMatrices 和 joint palette 的执行。
- glTF `BLEND` 用于 mesh particle。
- sampler wrap/filter 的任意组合；首版只允许 compiler 明确映射到现有 Coo texture binding 的组合。
- 任意未知 `extensionsRequired`。

拒绝必须发生在 import 或 compile 阶段，不能在绘制时才抛错，也不能删除不支持的数据后继续。

## 9. asset/importer 模块

包名：

```text
cn.coostack.cooparticlesapi.coofx.asset
cn.coostack.cooparticlesapi.coofx.asset.schema
cn.coostack.cooparticlesapi.coofx.asset.gltf
cn.coostack.cooparticlesapi.coofx.asset.validation
```

职责：

- 枚举 ResourceManager 中的 `.coofx.json`。
- 使用 Gson 读取 JSON；不新增第二套 JSON 库。
- 通过 `ResourceProvider` 解析所有引用。
- 检查 schema version、ResourceLocation、有限数和引用完整性。
- 解码 glTF bufferView/accessor，并复制到拥有明确所有权的 immutable arrays。
- 把 glTF node、material、animation 和 CooFX emitter 规范化为 `CooFxSourceAsset`。
- 产生结构化 `CooFxDiagnostic`，severity、resource、JSON pointer、对象索引和消息分开保存。

不负责：

- GL upload。
- Minecraft tick。
- renderer、pipeline 或 framebuffer 调用。
- 把 skin/morph 静默烘焙为静态姿态。
- 直接生成 CParticle 或 RenderEntity。

Importer 的纯解析核心接收字符串和 byte arrays，ResourceManager adapter 只负责取资源。这样 accessor、动画和错误诊断可以在 `common:test` 中运行，无需客户端。

## 10. playback 模块

包名：

```text
cn.coostack.cooparticlesapi.coofx.playback
cn.coostack.cooparticlesapi.coofx.playback.track
cn.coostack.cooparticlesapi.coofx.playback.pose
cn.coostack.cooparticlesapi.coofx.playback.random
```

职责：

- 表示 clip、channel、sampler 和 node local pose。
- 根据整数 tick、partial tick、speed、loop mode 计算 clip time。
- 计算 local TRS 和按拓扑顺序组合 world matrix。
- 为 emitter 提供 deterministic spawn schedule。
- 提供纯 CPU、无 Minecraft client 依赖的采样 API。

关键规则：

- 不复用 `Animate` 作为轨道存储。`Animate` 是 tick 调度 DSL，不表达 glTF CUBICSPLINE 切线。
- 不复用现有 Bezier 进度曲线实现 glTF CUBICSPLINE。
- translation、scale LINEAR 使用分量线性插值。
- rotation LINEAR 使用最短路径 slerp 并归一化。
- CUBICSPLINE 输入/输出切线按秒缩放，端点和越界行为严格跟随 glTF。
- clip duration 来自轨道最大输入时间，空 clip 在校验阶段拒绝。
- node world pose 按稳定的 node index 拓扑顺序计算，不依赖 map 迭代顺序。
- 播放实例保存 previous/current pose，渲染 partial tick 只做插值，不推进逻辑状态。

首版 loop mode 仅包含 ONCE 和 LOOP。若实现时新增 enum，类型和每个成员都必须有详细简体中文 KDoc/Javadoc，说明默认值、端点和序列化语义。

## 11. compiled render package 模块

包名：

```text
cn.coostack.cooparticlesapi.coofx.render.compiled
cn.coostack.cooparticlesapi.coofx.render.gpu
cn.coostack.cooparticlesapi.coofx.render.material
```

### 11.1 CPU 编译包

`CooFxCompiledRenderPackage` 是不可变、线程安全且不含 GL handle 的产物，至少包含：

- package id、source schema version、compiler version、content digest。
- node parent 数组和稳定拓扑顺序。
- 规范化 clip 与采样元数据。
- mesh primitive 的 interleaved vertex bytes 和 index bytes。
- 显式 vertex layout。
- material records 和 texture ResourceLocation。
- pipeline descriptor 或 pipeline factory key。
- emitter definitions。
- batch templates。
- deformation plan。
- 编译 warning 列表。

静态 mesh 只在 compile 阶段打包一次。每帧不得重新构造 `VertexData`、`FloatArray` 或执行 `glBufferData` 上传静态顶点。

### 11.2 GPU 常驻包

`CooFxGpuPackage` 只允许在渲染线程创建、使用和释放，至少拥有：

- 每个 mesh primitive 的 VAO、VBO、EBO。
- 动态实例 VBO。
- 解析后的纹理 binding 引用。
- shader/program variant 引用。
- generation 和 resource digest。
- 显式 `release()`，且重复调用安全。

GPU package cache 的 key 为 `(resourceId, contentDigest, backendCapabilitySignature)`。Iris 是否启用 shader pack 不是资产内容的一部分，但会影响 backend capability signature 和 program variant。

### 11.3 Coo Pipeline 集成

- CooFX mesh world shader 使用现有 Coo shader program、source loader 和 include 规则。
- CooFX 声明不可变 `CooRenderPipeline`，不直接创建 Vanilla `ShaderInstance` JSON。
- world pass 通过现有 `ClientRenderPipelineManager` / backend 阶段执行。
- mask、scene color、scene depth 和后处理继续由 pipeline 图声明。
- CooFX shader 保留大于 `1` 的自发光计算结果，但实际 framebuffer 是否保存 HDR 由当前渲染后端决定。CooFX 目前没有为 Vanilla 自动启用全屏 bloom，也没有把材质接入 `MASK_BLOOM` 的选择性遮罩。
- `CooGLSLStateManager.useState` 保护 blend、depth、cull 等光栅状态；program、texture、VAO、FBO 和 viewport 由 CooFX GPU 作用域分别保存与恢复。

### 11.4 光照与 Iris

- 纯模型每帧按当前世界位置调用 `LevelRenderer.getLightColor`；网格粒子在构建每帧实例时按当前世界位置重新采样，移动、延迟和局部发射器变换都会更新 packed light；离线或未提供采样器的批处理仍使用发射器启动时的备用值。
- `packedLight` 继续存放在实例槽位 4，不改变 144-byte ABI。Vanilla shader 把它拆成 UV2，并采样 `LightTexture`。
- 最终材质颜色为 `baseColor * lightmap + emissiveTexture * emissiveFactor * emissiveStrength`。自发光不修改 Minecraft 区块光照，也不会照亮附近方块。
- Iris 启用光影包时，CooFX program 只负责把 indexed instancing 展开为 36 字节的 `NEW_ENTITY` 顶点：Position、Color、UV0、Overlay、UV2/lightmap、Normal 对应 0-5 号属性。初始化后显式禁用 Iris 扩展的 6-8 号数组，避免旧 VAO 状态按另一种 stride 读取；这不改变 BSL 或任何 shaderpack 名称分支。
- Iris CooFX 批次与 RenderEntity 共用 final composite 前的早期 world hook；关卡渲染尾部的 WORLD_PASS 只提交 Vanilla CooFX，不能在 final pass 后重新绑定 G-buffer。
- 自发光追加绘制只开放当前 entity framebuffer 的 draw buffer 0，使用独立 indexed blend 状态并完整恢复其他附件。部分 deferred shader pack 会在后续阶段重新解释该附件，因此 CooFX 只保证表面自发光，不能通用保证 bloom 光晕。
- Iris 的 entity cutout program 固定使用 `0.1` Alpha 阈值，因此 `MASK` 材质的自定义 glTF `alphaCutoff` 只在 Vanilla 路径精确执行；OPAQUE 材质使用 entity solid program，不受该阈值影响。

#### 11.4.1 Shaderpack compatibility contract

CooFX 的基础 mesh vertex/material ABI 已验证，但 Iris 只提供一个“当前有 shaderpack”状态，不提供 CooFX 可依赖的 shaderpack identity 或能力协商 API。因此进入 Iris entity bridge 只代表采用 Iris entity program 的未验证路径，不代表任意 shaderpack 都兼容。

- 当前资产的颜色结果可能与无光影路径不同。entity color modulation、UV/overlay 语义以及 Iris entity program 的具体材质处理由活动 shaderpack 决定，CooFX 不无条件保证它们保持一致。
- 36 字节 bridge 不伪造真实 entity identity、midpoint 或几何正确的切线：Iris 扩展 6-8 号属性仅使用 `iris_Entity=(0,0,0)`、`mc_midTexCoord=(0,0)`、`at_tangent=(1,0,0,1)` 中性 generic 值。需要这些真实语义的 shaderpack feature 属于 capability unsupported，必须新增完整 Iris ENTITY 扩展路径后才能承诺；当前路径不得静默读取未初始化数据。
- 自发光第二 pass 只追加当前 entity framebuffer 的主颜色附件。它不保证 shaderpack 的 emissive 合成、泛光或其他附件结果。
- CooFX 阴影暂不支持，也没有提交 CooFX 专用 shadow-map pass。未通过目标 shaderpack 的实际验证时，必须将其视为 shader 部分不兼容。
- 运行时只在同一客户端 session 首次进入活动 Iris entity bridge 时发出一次兼容性 warning，不识别或缓存 shaderpack identity，也不会把普通 CooFX shader 静默当作通用 fallback。

## 12. mesh particle runtime 模块

包名：

```text
cn.coostack.cooparticlesapi.coofx.runtime.mesh
cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage
cn.coostack.cooparticlesapi.coofx.runtime.mesh.simulation
cn.coostack.cooparticlesapi.coofx.runtime.mesh.render
```

首版采用 CPU simulation + instanced draw：

- CPU 负责 spawn、age、position、rotation、scale、color 和 clip time。
- 每个 system 使用 dense storage；删除可 swap-remove，但 particle seed 和 emissionOrdinal 不变。
- 验证快照按 stable particle id 排序，避免内部压缩策略影响测试。
- 每帧按 batch key 收集实例并上传连续实例区间。
- 一个 batch key 对应一次或少量 instanced draw，不为每粒子创建 RenderEntity。
- runtime 不修改 `CParticleStore`、`CParticleSystem` 或现有 shader ABI。
- 首版不要求 compute shader，不具备 GL 4.3 时功能不能缺失。
- 首版不支持世界碰撞、实体查询、网络同步和透明粒子。

### 12.1 批次键

`CooFxMeshBatchKey` 必须是不可变值对象，字段顺序固定：

1. compiled package generation。
2. pipeline id / world node id。
3. mesh primitive id。
4. vertex layout version。
5. index type。
6. material id。
7. base-color texture binding 与 sampler key。
8. shader variant。
9. deformation mode。
10. alpha mode 与 alpha cutoff bucket。
11. cull mode。
12. depth test、depth write。
13. blend mode。
14. light mode。
15. render backend capability signature。
16. instance layout version。

以下数据不能进入批次键：world transform、age、seed、color、clip time、particle id、camera distance。它们属于实例数据。

批次构建顺序必须稳定。Opaque / Mask 先按 batch key 的稳定序列排序；同 key 内按 stable particle id 写入。未来支持 Blend 时，必须先设计跨 key 的全局深度排序或明确近似策略，不能直接复用首版排序。

### 12.2 实例数据布局 v1

实例布局固定为 9 个 `vec4`，36 个 float，144 字节。它在字节数上恰好与 `CParticleStore` 相同，但语义完全独立，禁止类型转换或共享 offset 常量。刚性 node pose 不修改该 ABI；batcher 另行生成每实例 12-float/48-byte affine matrix sidecar，按矩阵前三行绑定到 locations 13–15、divisor 1，shader 补齐仿射末行。

```text
vec4 0: currentPosition.xyz, ageTicks
vec4 1: previousPosition.xyz, lifetimeTicks
vec4 2: currentRotation.xyzw
vec4 3: previousRotation.xyzw
vec4 4: currentScale.xyz, packedLight
vec4 5: previousScale.xyz, materialVariant
vec4 6: color.rgba
vec4 7: clipTimeSeconds, previousClipTimeSeconds, playbackSpeed, clipIndex
vec4 8: seedLow16, seedHigh16, flags, stableParticleIdLow24
```

约束：

- rotation 始终归一化。
- scale 必须有限；首版拒绝负分量。
- packedLight、materialVariant、clipIndex、flags 和 id 片段作为 float 精确整数保存，编译和 spawn 时验证位宽。
- seed 拆成两个 16-bit 值，shader 可无损重组 32-bit 可见随机输入；完整 64-bit seed 保留在 CPU storage。
- layout 由独立的 `CooFxMeshInstanceLayout` 定义，不引用 `CParticleStore.STRIDE`。
- VAO attribute divisor 与 shader location 由同一 layout descriptor 生成并测试，不能在两个文件中复制数字。
- 最低路径使用 GL 3.2 / GLSL 150 instanced attributes；未来 compute/SSBO 路径必须消费同一语义布局或提供显式版本转换。

## 13. VAT、morph 与 skin 变形计划

### 13.1 首版 RIGID

首版唯一可执行的 deformation mode 是 RIGID：

- mesh 顶点不变。
- node 或 particle 的 TRS 通过实例数据应用。
- 多 node 场景在 compile 阶段生成 primitive 到 node 的绑定。
- 一个粒子播放刚性 node clip 时，batcher 使用该粒子的 previous/current clip time 和帧 partial tick 采样完整父子层级 world pose，再把当前 primitive 绑定节点的矩阵写入 sidecar。shader 执行 `particle TRS * node world matrix * vertex`，法线使用组合线性矩阵的 inverse-transpose。

### 13.2 Morph 计划

第二阶段支持 glTF morph target：

- importer 从第一版起必须检测 POSITION/NORMAL/TANGENT target 并保存能力诊断。
- 首版 compiler 遇到实际启用的 morph weight 必须拒绝。
- 后续 GPU 路径把 target delta 放入独立静态 buffer，不改基础 vertex ABI。
- 每实例存 active target index / weight；活跃 target 数上限由 backend capability 与 shader variant 明确声明，不能静默截断。
- animation weights 使用 glTF sampler 语义，测试覆盖 target 数、切线和归一化。

### 13.3 Skin 计划

第三阶段支持 skin：

- importer 支持 JOINTS_0、WEIGHTS_0、skin joints 和 inverseBindMatrices。
- compiler 验证 node 树、joint 引用、权重有限性和归一化。
- GPU 使用 joint matrix palette buffer；每实例引用 palette offset。
- 首选 SSBO / texture buffer 由能力探测决定，GL 3.2 fallback 的 uniform palette 上限必须从实际 backend 查询并在 compile 阶段拒绝超限资源。
- 不修改 RenderEntityModelVertex 来承载 joints/weights。

### 13.4 VAT 计划

VAT 面向大量共享变形动画的网格粒子，是 morph/skin 每实例求解的高吞吐替代：

- Blender Add-on 离线烘焙 position texture；需要正确光照时同时烘焙 normal texture。
- VAT metadata 记录 vertex count、frame count、fps、duration、bounds、texture dimensions、loop mode 和 content digest。
- texture 使用可精确表达所需范围的格式；最终格式必须以目标 OpenGL ceiling 和 Coo texture API 实测后确定，架构不预先硬编码一个未经验证的格式。
- shader 用 vertex id 和当前/下一 frame 采样并插值。
- batch key 加入 VAT clip、texture binding 和 layout version；实例数据沿用 clip time。
- VAT 资产必须与基础 mesh vertex order 和 digest 绑定，次序不匹配时 compile 失败。

优先级为 RIGID -> VAT -> Morph -> Skin。原因是 CooFX 的核心场景是大量网格粒子，VAT 能以共享采样成本覆盖 Blender 复杂变形；morph/skin 保留给需要运行时权重或骨骼交互的较少实例。

如果实现中新增 deformation、alpha、loop、diagnostic 等 enum，必须为 enum 类型及每个成员写详细简体中文 KDoc/Javadoc，说明序列化值、默认值、运行阶段和不支持时的行为。

## 14. GPU 线程与资源重载生命周期

### 14.1 状态机

```text
UNLOADED
  -> PREPARING_CPU
  -> CPU_READY
  -> UPLOAD_QUEUED
  -> GPU_READY
  -> RETIRED
  -> RELEASED

任意准备/上传失败
  -> FAILED（保留上一 generation）
```

规则：

- `PREPARING_CPU` 可以在资源重载 prepare executor 上运行，不调用 RenderSystem、GL 或 Minecraft client singleton。
- `UPLOAD_QUEUED -> GPU_READY` 只能在渲染线程执行。
- 新 generation 全部上传成功后才能原子替换 active registry。
- 上传失败时释放新 generation 的已创建 handle，并继续使用上一 generation；诊断记录资源和阶段。
- frame 开始取得 generation lease，frame 结束释放。旧 generation 只有在不再 active 且 lease 为 0 时才能在渲染线程释放。
- 资源重载不能原地修改正在绘制的 VAO、VBO、texture 或 package maps。
- `ShaderReloadSignal.CompileUpdate` 只重建受影响 program binding；几何 content digest 未变时不重新上传静态 mesh。
- `FullReload` 重新解析资源和纹理依赖，并创建新 generation。

### 14.2 生命周期落点

保留现有公共 API 签名，并以新增 `CooFXClient.playModel(CooFxModelPlayRequest)` 的方式支持无 emitter 模型资产。实现阶段使用以下最小内部适配：

- `CooParticlesAPIClient.init()`：初始化 `CooFxClientLifecycle`，只注册监听器，不编译 GL program。
- `CooParticlesAPIClient.initShaderPrograms()`：在已有 GL 初始化之后允许 CooFX upload coordinator 启动。
- `CooParticlesAPIClient.reloadShaderPrograms()`：不直接重复重载 CooFX；由 `ShaderReloadBus` generation 流程统一处理。
- `CooParticlesAPIClient.tickClient()`：推进 CooFX 逻辑 tick。
- `CooParticlesAPIClient.clearTransientClientState()`：清空播放实例和 mesh systems，不释放仍可跨世界复用的资源包。
- `ClientRenderPipelineManager` 的 world-pass 内部 hook：调用 CooFX frame adapter 收集和绘制；仍由现有 backend 决定 Vanilla / Iris 阶段。
- Fabric `CLIENT_STOPPING` 与 NeoForge 对应客户端关闭事件：在渲染线程释放全部 CooFX GPU package 并注销 reload listener。当前仓库没有现成停止 hook，因此这是 loader 模块唯一必要的新 wiring。

不得在 Fabric 和 NeoForge 复制 importer、playback、compiler、runtime 或 shader 实现。loader 只负责停止事件，现有资源重载继续汇聚到 `ShaderReloadBus`。

## 15. consumer adapters

包名：

```text
cn.coostack.cooparticlesapi.coofx.adapter
cn.coostack.cooparticlesapi.coofx.adapter.renderentity
cn.coostack.cooparticlesapi.coofx.adapter.cparticle
cn.coostack.cooparticlesapi.coofx.client
```

### 15.1 直接客户端播放

`CooFXClient.playModel(request)` 创建不依赖 emitter 的持久模型实例。request 包含 CooFX resource id、world transform、显式 request seed、可选 clip 和播放速度；它会绘制选定 scene 的全部 mesh node。无 clip 时保持 bind pose，有 clip 时每个实例按独立整数 tick 时钟采样。

`CooFXClient.play(request)` 只用于资产内置 emitter，request 另外携带可选 emitter id 和受类型约束的参数覆盖。模型入口不会伪造 emitter，粒子入口不会把模型实例混入粒子模拟。

两种 handle 都提供 stop、isAlive 和只读 instance id。资源未就绪时行为固定为排队到当前 generation ready；资源解析失败则返回结构化失败，不在 render loop 重试。世界切换、资源重载或客户端关闭会清理模型实例。

### 15.2 服务端权威 Scene RenderEntity

`CooFxSceneManager` 创建 `CooFxSceneRenderEntity`，直接复用现有 RenderEntity 的可见范围和 CREATE/TOGGLE/REMOVE 协议。服务端持有资源、world transform、request seed、clip、播放速度、scene mode、camera selector/priority、camera target player 和 emitter 基础覆盖；客户端 `CooFxSceneClientRegistry` 只持有对应的本地模型/emitter handle。场景默认以 `renderRange = 256` 按同世界距离发送给所有可见玩家，camera target 只过滤本地镜头，不复制模型实体。资源入口可用 `coofxAsset(modid, assetID)` 统一生成 `coofx/<assetID>/<assetID>.coofx.json`；Blender 导出器对单段资产名使用同一目录约定。

- `spawn(level, spec)` 返回不暴露 GPU 的 `CooFxSceneHandle`。
- `handle.update(patch)` 通过 RenderEntity dirty 状态增量同步。
- 模型 transform/clip/speed 原位更新，不因服务端移动每 tick 重建模型。
- emitter transform 原位更新；count、delay、lifetime 变化时重建 emitter definition。
- camera node 使用与模型相同的父子 pose/clip 采样；多个场景以 camera priority 确定唯一主视角。
- 资源重载清除本地 generation handle，客户端镜像在下一 tick 从仍有效的服务端 scene 重新创建。
- 退出世界、断线、服务端 stop 或 RenderEntity REMOVE 会同时清理模型、emitter 和 camera claim。

### 15.3 CParticle adapter

`adapter.cparticle` 只负责把现有粒子/Composition 的触发事件转换为 `CooFxEmitterRequest`，不把网格实例写入 `CParticleStore`。首版可以只提供单向触发 adapter；共享模拟、跨 ABI buffer 或修改 CParticle public API 都不在范围内。

## 16. Blender Add-on

### 16.1 版本和分发

目标为 Blender 4.2 LTS 及以上，采用 Extension manifest。目录：

```text
tools/blender_coofx/
```

Add-on 遵循 GPL-3.0 兼容分发，并在 ZIP 中包含许可证。所有项目自有 Python 注释和用户可见诊断说明使用简体中文。

### 16.2 分层

```text
Blender UI / bpy scene
  -> normalized export model
  -> validation
  -> Blender official glTF exporter
  -> CooFX JSON serializer
  -> golden validation
```

Add-on 不能：

- 直接拼 Kotlin/Java。
- 读取 runtime Kotlin 类来猜字段。
- 把任意 Blender shader node 翻译成 Coo shader。
- 把对象名自动猜成 emitter 或 PointsBuilder 操作。
- 生成网络 URI、绝对路径或 `..` 引用。

### 16.3 首版 UI

首版属性：

- namespace、asset name、output root。
- export collection；未指定时使用当前 scene 的对象。
- unitScale，默认 1，并要求与 Blender scene unit scale 一致。
- 一个可选 clip 的 ID、glTF animation 名称和 `ONCE`/`LOOP`/`PING_PONG` loop mode；多 clip 列表编辑仍是后续 UI 扩展。
- asset seed；emitter 参数从受支持的 Classic Particle 或官方 Geometry Nodes socket 提取，不在面板中重复编辑。
- material 映射到 base-color texture、alpha OPAQUE/MASK、double-sided。
- “校验”“导出运行时资产”和“导出 GLB 快照”三个 operator；GLB 快照不进入运行时清单。

Add-on 使用字符串 item 的 `EnumProperty` 表示固定选项，除非确有领域行为，不新增 Python `enum.Enum`。若新增 enum，类型和每个成员同样需要详细简体中文文档。

### 16.4 导出策略

- 使用 Blender 官方 glTF exporter 的 `GLTF_SEPARATE` 路径。
- 限制为选定 collection / scene。
- 应用标准 Y-up 转换。
- 默认导出 normals、UV0、vertex colors 和 animations。
- 官方 exporter 应用对象变换后，首版仍拒绝 non-triangle 最终 mesh、缺失 UV 的纹理材质和 Blend 材质；对象负缩放允许导出，发射器粒子尺寸范围仍要求为正值。
- Add-on 在写文件前构造纯 Python normalized model 并运行 schema 校验。
- 临时文件全部成功后逐文件原子替换，依赖文件先提交、JSON 清单最后发布；普通提交异常会回滚旧文件。目标文件跨目录，因而不承诺断电或进程崩溃时的 filesystem-wide atomicity。
- 输出 UTF-8 无 BOM JSON，key 顺序稳定，浮点格式稳定，便于版本控制。

VAT 面板和烘焙 operator 在首版只保留架构接口，不显示未实现按钮。后续启用时才增加 position/normal texture bake。

## 17. 精确文件计划

### 17.1 文档与 schema

```text
docs/coofx/architecture.md
schemas/coofx/coofx-v1.schema.json
schemas/coofx/examples/rigid-burst.coofx.json
```

### 17.2 asset/importer

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/CooFX.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/CooFxSourceAsset.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/CooFxAssetLoader.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/CooFxDiagnostic.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/schema/CooFxDocumentV1.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/schema/CooFxDocumentParser.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfDocument.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfDocumentParser.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfResourceResolver.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfAccessorDecoder.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfImporter.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/validation/CooFxAssetValidator.kt
```

### 17.3 playback

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/CooFxClip.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/CooFxPlaybackClock.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/CooFxPlaybackState.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/track/CooFxTrack.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/track/CooFxTrackSampler.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/pose/CooFxLocalPose.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/pose/CooFxPoseEvaluator.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/random/CooFxDeterministicRandom.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/random/CooFxSeedDerivation.kt
```

### 17.4 compiled render package

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/compiled/CooFxCompiledRenderPackage.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/compiled/CooFxPackageCompiler.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/compiled/CooFxCompiledMesh.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/compiled/CooFxCompiledMaterial.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/compiled/CooFxDeformationPlan.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/material/CooFxMaterialPipelineFactory.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/gpu/CooFxGpuPackage.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/gpu/CooFxGpuPackageCache.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/gpu/CooFxGpuUploadCoordinator.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/gpu/CooFxGpuGenerationLease.kt
```

### 17.5 mesh particle runtime

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/CooFxMeshParticle.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/CooFxMeshParticleSystem.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/CooFxMeshParticleManager.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/storage/CooFxMeshParticleStore.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/storage/CooFxMeshInstanceLayout.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/simulation/CooFxMeshParticleSimulator.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/CooFxMeshBatchKey.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/CooFxMeshBatcher.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/CooFxMeshParticleRenderer.kt
```

### 17.6 consumer 与生命周期适配

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFXClient.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientLifecycle.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxPlaybackHandle.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/adapter/renderentity/CooFxRenderEntityRendererAdapter.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/adapter/cparticle/CooFxCParticleTriggerAdapter.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/adapter/CooFxFrameAdapter.kt
```

允许增量修改的既有文件：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt
fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt
neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPINeoClientModListener.kt
```

修改限制：只增加初始化、tick、clear、world-pass 和 client-stopping 委托，不改现有公共方法签名、现有 RenderEntity/CParticle 行为或 loader 资源重载入口。

### 17.7 shader 与运行资源

```text
common/src/main/resources/assets/cooparticlesapi/shaders/core/coofx/mesh_particle.vsh
common/src/main/resources/assets/cooparticlesapi/shaders/core/coofx/mesh_particle.fsh
common/src/main/resources/assets/cooparticlesapi/shader/include/coofx_instance.glsl
```

所有 shader 经 `CooShaderSourceLoader`，include 只使用 `#coo_import`。

### 17.8 Blender Add-on

```text
tools/blender_coofx/blender_manifest.toml
tools/blender_coofx/LICENSE
tools/blender_coofx/__init__.py
tools/blender_coofx/properties.py
tools/blender_coofx/ui/panels.py
tools/blender_coofx/operators/validate_asset.py
tools/blender_coofx/operators/export_asset.py
tools/blender_coofx/ir/model.py
tools/blender_coofx/ir/normalize.py
tools/blender_coofx/ir/validate.py
tools/blender_coofx/ir/serialize.py
tools/blender_coofx/gltf/export.py
tools/blender_coofx/tests/test_normalize.py
tools/blender_coofx/tests/test_schema.py
tools/blender_coofx/tests/test_deterministic_random.py
tools/blender_coofx/tests/blender_export_smoke.py
```

### 17.9 JVM 测试

```text
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/asset/CooFxDocumentParserTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfAccessorDecoderTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/asset/gltf/GltfImporterTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/playback/CooFxTrackSamplerTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/playback/CooFxPoseEvaluatorTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/playback/CooFxDeterministicRandomTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/render/compiled/CooFxPackageCompilerTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/CooFxMeshParticleStoreTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/CooFxMeshBatcherTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/render/gpu/CooFxGpuGenerationStateTest.kt
```

## 18. 实现分区

### 分区 A：协议与 importer

拥有路径：

```text
schemas/coofx/**
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/**
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/asset/**
```

交付：schema v1、Gson parser、ResourceProvider resolver、glTF accessor/importer、诊断和 fixtures。该分区不依赖 client 或 GL。

### 分区 B：playback 与确定性

拥有路径：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/**
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/playback/**
```

交付：SplitMix64/FNV golden vectors、STEP/LINEAR/CUBICSPLINE、node pose、tick/second 映射和 emitter schedule。

### 分区 C：compiled package 与 GPU residency

拥有路径：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/**
common/src/main/resources/assets/cooparticlesapi/shaders/core/coofx/**
common/src/main/resources/assets/cooparticlesapi/shader/include/coofx_instance.glsl
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/render/**
```

交付：CPU compiler、batch templates、deformation plan、Coo Pipeline material、GPU generation 与 lease。GL 行为只在 render thread adapter 内发生。

### 分区 D：mesh particle runtime

拥有路径：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/**
common/src/test/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/**
```

交付：dense store、CPU simulator、144-byte 独立实例 ABI、稳定 batch key、instanced renderer。

### 分区 E：consumer 与平台适配

拥有路径：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/**
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/adapter/**
common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderPipelineManager.kt
fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt
neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPINeoClientModListener.kt
```

交付：公开 facade、RenderEntity/CParticle trigger adapters、world-pass delegate、reload/tick/clear/stop wiring。该分区不能复制核心逻辑到 loader 模块。

### 分区 F：Blender Add-on

拥有路径：

```text
tools/blender_coofx/**
```

交付：Blender 4.2+ Extension、纯 Python normalized model、校验、稳定 JSON、官方 glTF 分离导出和 headless smoke fixture。

## 19. 跨分区契约

- A -> B：规范化 node、clip、track 和 emitter 数据；不暴露 Gson 对象。
- A + B -> C：`CooFxSourceAsset` 与已验证 clip；C 不重新解释 JSON。
- C -> D：不可变 compiled package、batch template、instance layout version；D 不读取 glTF。
- B + C -> E：playback state、package handle 和结构化失败；E 不拥有 importer。
- D -> E：spawn/stop/tick/render 生命周期接口；E 决定何时调用，不修改 storage。
- F -> A：只通过 schema v1、glTF 2.0 和 deterministic golden vectors 交互；Python 不依赖 JVM 源码。

任何跨分区类型变更先更新 schema/契约测试。禁止用 map、raw JSON、`Any` 或字符串表达式绕过类型边界。

## 20. 验收标准

### 20.1 静态与纯单元验收

- 未知 `schemaVersion`、未知 required extension、路径逃逸和不支持 glTF feature 都产生稳定诊断。
- accessor 的 offset、stride、normalized、index type 和越界检查有测试。
- STEP、LINEAR、CUBICSPLINE、四元数归一化、端点、ONCE/LOOP 有 epsilon 测试。
- Kotlin 与 Python 的 SplitMix64、FNV-1a 和 float vectors 完全一致。
- 同一 source asset 编译两次得到相同 digest、primitive order、batch templates 和 instance bytes。
- mesh store 删除/压缩后，按 stable id 观察的模拟结果不变。
- batch key 任一资源状态字段变化会拆批；实例字段变化不会拆批。
- CPU compiler 和 playback 测试不加载 Minecraft client class。
- 所有新增 enum 都有类型和成员级详细简体中文 KDoc/Javadoc。
- 所有项目自有源码注释为简体中文。

### 20.2 构建验收命令

只使用系统 Gradle，禁止 wrapper：

```text
gradle :common:test --tests "cn.coostack.cooparticlesapi.coofx.*"
gradle :common:compileKotlin :fabric:compileKotlin :neoforge:compileKotlin
gradle :fabric:test
gradle :neoforge:test
```

完整非客户端回归：

```text
gradle :common:test :fabric:test :neoforge:test
```

禁止使用：

```text
gradlew
gradlew.bat
runClient
runServer
```

本架构阶段不运行 Gradle。以上命令用于后续实现验收。

### 20.3 Blender 验收命令

纯 Python：

```text
python -m unittest discover -s tools/blender_coofx/tests -p "test_*.py"
```

安装 Blender 4.2+ 后的 headless smoke：

```text
blender --background --factory-startup --python tools/blender_coofx/tests/blender_export_smoke.py
```

smoke 必须导出到临时目录，再由测试断言 JSON 为 UTF-8 无 BOM、schemaVersion 为 1、路径合法、glTF/bin 存在且 deterministic random vectors 一致。它不能启动 Minecraft 客户端。

### 20.4 后续人工客户端验收

由于视觉、Iris framebuffer 和驱动行为无法由纯单元测试证明，后续实现完成后由用户启动客户端验证：

- Fabric，无 Iris。
- Fabric，有 Iris 且 shader pack 开启。
- NeoForge，无 Iris。
- NeoForge，有 Iris 且 shader pack 开启。
- F3+T 完整资源重载前后无丢失、重复实例或旧 GL handle。
- 断开世界、切换维度和退出客户端后资源计数回到预期值。

该人工矩阵不是本架构文档阶段的执行项。

## 21. 首版完成定义

只有同时满足以下条件，首版纵向切片才算完成：

1. Blender headless fixture 可稳定导出 `.gltf`、`.bin`、`.coofx.json` 和 PNG 引用。
2. common 纯测试可从这些资源解析、编译并生成 deterministic snapshot。
3. runtime 可用 CPU simulation 生成至少两个相同 mesh/material 的实例，并形成一个 instanced batch。
4. Fabric 与 NeoForge 均编译通过，公共 importer/playback/runtime 没有 loader 分叉。
5. 现有 RenderEntity、CParticle 和 Pipeline 公共 API 签名不变。
6. CooFX shader 经 Coo source loader 与 pipeline 路径执行，没有 Vanilla shader JSON 或平行 framebuffer 管理器。
7. 资源重载使用 generation swap，失败时旧 generation 继续可用。
8. 世界切换清空实例，客户端关闭释放所有 GPU handle 和 listener。
9. morph、skin、VAT 和 Blend 等未支持能力都有显式诊断，不被静默忽略。
10. 文档、schema、golden vectors、JVM 测试与 Blender 测试共同固定 v1 协议。

## 22. 主要风险

- `common` 没有物理 client source set。任何从 `CooFX` 根对象静态触发 client 类型的代码都会破坏 dedicated server 安全。
- 现有 `ClientRenderPipelineManager` 没有通用 frame participant registry。首版应增加最小内部委托，不应为 CooFX 公开一个未经验证的新公共渲染扩展 API。
- Fabric 使用官方 Mojang mappings，common/NeoForge 使用 Parchment。适配代码只能使用两端均可解析的名称。
- Iris 下 world pass、final pass 和 mask 重放存在阶段差异。首版限制为单 world color 输出，降低第一条切片的兼容面。
- glTF CUBICSPLINE、四元数和 byteStride 容易出现看似可用但数学错误的实现，必须先用纯 fixture 固定行为。
- 144-byte 实例布局有明显带宽成本，但能在 GL 3.2 下保留 previous/current TRS 和稳定动画数据。优化前先采集实例数量、上传字节和 draw count。
- Blender 官方 exporter 的版本行为可能变化。Add-on 必须锁定最低 Blender 4.2，并用 headless fixture 检测输出差异。
- VAT texture 格式和 skin palette 上限依赖实际 OpenGL ceiling。未验证前不得把开发机能力写成 Minecraft 1.21.1 的通用保证。

## 23. 不修改现有公共 API 的判定

首版新增 `cn.coostack.cooparticlesapi.coofx` 下的独立公开类型，但不修改下列现有契约：

- `RenderEntityRenderer`。
- `CooRenderPipeline` 与 builder。
- `CParticle`、`CParticleStore`、`CParticleSystem`。
- `RenderEntityModel` 与 `RenderEntityModelExecutor`。
- `ShaderReloadBus` 信号和 listener 接口。

若实现阶段发现 world-pass 无法在不改变公共接口的情况下正确接入，应先停在内部 `CooFxFrameAdapter`，通过现有 manager 的私有 hook 委托。只有多个独立消费者已经证明需要通用 frame participant 时，才另立公共 API 提案；它不属于首版纵向切片。
