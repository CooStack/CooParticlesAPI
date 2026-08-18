# CooFX 导出格式 v1

本文固定 Blender Add-on 输出的 CooFX JSON 形状。运行时边界、线程模型和渲染约束以 `architecture.md` 为准。

运行时会保留选定 scene 中每个 primitive 的 glTF node 绑定、精确静态 `matrix` 或 TRS、父子层级及 clip transform tracks。纯模型实例和每个粒子分别拥有独立 clip time，并通过单独的 48-byte 仿射矩阵 sidecar 进入绘制；既有 144-byte 实例 ABI 不变。标准 glTF `cameras` 及 `node.camera` 会独立编译为 camera-node binding，不进入 mesh ABI；camera node 的 TRS clip 可驱动客户端位置、旋转和透视 FOV。带 transform 动画通道的 node 必须使用 TRS，`matrix` node 作为动画目标会被拒绝。

静态 camera 是合法输出：当 camera node 及其祖先没有 transform animation 时，导出器保留 camera 并在提交结果中发出非阻塞的绑定姿势 warning；不会人为生成 no-op keyframe，也不会因该 warning 拒绝资产。只有导出的 glTF 无法满足 importer/compiler profile 时才会在发布前失败。


面板中的“资源命名空间目录”必须指向 `assets/<namespace>`。导出器生成：

```text
assets/<namespace>/coofx/<asset-name>.coofx.json
assets/<namespace>/coofx/models/<asset-name>.gltf
assets/<namespace>/coofx/models/<asset-name>.bin
```

`asset-name` 可以包含安全的子目录。纹理由美术放入 `assets/<namespace>/textures/coofx/`，JSON 使用完整 ResourceLocation 引用。运行时 v1 不读取 `.blend`；importer 接受 glTF 2.0 `.gltf`/`.bin` 和 GLB 2.0，但两种容器都只接受 PNG URI，不能把图片放入 `image.bufferView`。

Blender GLB Operator 仅生成离线检查快照，不会把 `.glb` 写入自动生成的 CooFX 清单；标准 runtime bundle 始终调用 Blender 官方 glTF exporter 的 `GLTF_SEPARATE` 格式。手工清单或其他工具可以把 `model` 指向合法的 `.glb` ResourceLocation，但 GLB 的 BIN chunk 只承载 importer 支持的 buffer/accessor 数据，不会为内嵌图片创建 Minecraft `ResourceLocation`。

## 坐标转换

Blender 为右手 Z-up，CooFX 为右手 Y-up。官方 glTF exporter 负责标准 Y-up 转换，其概念映射为：

```text
(x, y, z) -> (x, z, -y)
```

转换不改变手性和三角形绕序。官方 glTF exporter 转换 mesh/node；CooFX emitter extractor 用相同 `unitScale` 转换 Geometry Nodes velocity，以 `B * R * B^-1` 四元数基变换转换 XYZ Euler rotation，并按无符号轴映射转换 scale。`cooparticlesapi:blender_export` 扩展记录源/目标坐标系、向量映射、`unitScale` 和两个转换所有者。运行时不得再次换轴。

## 顶层字段

- `$schema`：固定为 `cooparticlesapi:coofx/schema/v1`。
- `schemaVersion`：固定为整数 `1`。
- `coordinateSystem`：固定为 `coofx_rh_y_up_z_south`。
- `assetSeed`：固定 16 位小写十六进制字符串。
- `model`：指向 `.gltf` 或 `.glb` 的安全 ResourceLocation；Blender 自动导出固定使用分离 `.gltf`。
- `scene`：非负 glTF scene 索引；规范资产只包含该 scene 根节点可达的 node、mesh 和动画通道，发射器引用闭包外 node/mesh 时导入失败。
- `clips`：可为空；通过唯一 glTF animation 名称或索引绑定动画，loop mode 允许 `ONCE`、`LOOP` 和 `PING_PONG`。纯模型实例可以播放 clip，不依赖 emitter。运行时 bundle 发布前会读取已导出的 glTF：每个未显式配置的动画都会补一个 `ONCE` clip；唯一非空名称使用名称引用，重名或空名使用动画索引。用户显式配置的 clip ID 和 loop mode 保留不变。
- `materials`：可为空；通过唯一 glTF material 名称或索引覆盖基础颜色纹理、Alpha 和双面状态。glTF 本体提供的 `emissiveFactor`、`emissiveTexture` 及 `KHR_materials_emissive_strength` 会进入 compiled material；compiler 只执行 `OPAQUE`/`MASK` 并明确拒绝 `BLEND`。
- `emitters`：可为空；只有 Blender 场景实际配置粒子发射器时才记录确定性发射语义，调度单位为 tick，旋转单位为弧度。
- `requiredExtensions`：当前 CooFX importer 不接受非空列表；导出器会在发布前拒绝该字段。glTF 自身的 `extensionsRequired` 只允许 importer 已支持的 `KHR_materials_emissive_strength`。
- `extensions`：按 ResourceLocation 命名的扩展对象；非 required 扩展（包括 `cooparticlesapi:blender_export`）以序列化 JSON 元数据保存在 `CooFxSourceAsset`，同时产生未执行 warning。

评审 schema 位于 `docs/coofx/schema/coofx-v1.schema.json`，示例位于 `docs/coofx/examples/rigid-burst.coofx.json`。

## 发射器抽取

### 经典 Particle System

Add-on 只抽取可无歧义映射为 v1 突发发射器的参数：

- `count` -> `count`。
- 单帧 `frame_start == frame_end` -> `delayTicks`。
- `lifetime` -> `lifetimeTicks`。
- `particle_size` 和 `size_random` -> 均匀三轴 `scale.min/max`。

连续时间窗、HAIR、非零 normal/tangent/object velocity 会被跳过并产生中文提示，因为 v1 全局速度向量不能表达依赖每个发射面法线的方向。

### 官方 Geometry Nodes 组

任意同名节点组不会被猜测为 CooFX 发射器。组必须设置自定义属性：

```text
coofx_official_group = "cooparticlesapi:coofx/emitter_v1"
```

必需输入名为 `Count`、`Delay Ticks`、`Lifetime Ticks`、`Mesh` 和 `Node`。可选输入为 `Emitter ID`、`Velocity Min/Max`、`Rotation Min/Max`、`Scale Min/Max`。Add-on 通过接口 socket 的稳定 `identifier` 读取 modifier 值；缺少必需输入时跳过并报告。

## VAT 元数据

VAT 当前只提供离线固定拓扑校验与元数据，不提供可见烘焙按钮。每个采样帧必须保持：

- 相同顶点数量。
- 完全相同的三角形索引顺序。
- 相同法线布局。
- 所有位置和法线分量为有限数。

`meshTopologySha256` 绑定顶点数量和索引顺序；`frameDataSha256` 绑定全部帧的位置与法线。元数据还记录帧数、fps、时长、bounds、纹理尺寸、loop mode 和坐标系，但不预设未经目标 OpenGL 能力验证的纹理格式。

启用 VAT 输出时应放入非 required 的 `cooparticlesapi:vat` 扩展。当前 Minecraft 运行时会保留或诊断该扩展，但不会执行 VAT 变形。

## 稳定性

导出器在写文件前构造纯 Python 模型并校验；JSON 使用 UTF-8 无 BOM、固定字段顺序、两个空格缩进和结尾换行。官方 exporter 先写入临时目录，确认 `.gltf` 与同名 `.bin` 存在后才替换目标文件，JSON 最后提交。
