# CooFX Blender 导入与使用完全教程

本文是 CooFX Blender 工具链的完整使用手册，针对本仓库当前实现编写。

适用版本：

- Blender 4.2 或更高版本
- Minecraft 1.21.1
- Java 21
- CooParticlesAPI 2.5.5.3
- Fabric 或 NeoForge
- CooFX v1

本文同时说明当前实现边界。CooFX 的目标是把 Blender 中制作的模型、刚性动画和受约束的粒子发射语义转换成 Minecraft 可以加载的资源；它不是在 Minecraft 中运行 Blender，也不会读取 `.blend` 文件，更不会自动翻译任意 Shader Nodes 或 Geometry Nodes 节点图。

## 1. CooFX Blender 工具是什么

工具目录：

```text
tools/blender_coofx/
```

它是一个 Blender 4.2+ Add-on，负责完成以下工作：

1. 在 Blender 场景属性中提供 CooFX 导出设置。
2. 检查网格是否至少包含可三角化面、对象变换是否有限、是否缺少 UV0；普通四边面和 n-gon 交给官方 glTF exporter 自动三角化，负缩放允许由 exporter 应用。
3. 从 Blender 经典 Particle System 中抽取首版可以无歧义表达的突发发射器参数。
4. 识别带有 CooFX 官方标记和固定输入接口的 Geometry Nodes 发射器组。
5. 调用 Blender 官方 glTF 导出器生成分离的 `.gltf` 和 `.bin`。
6. 生成运行时使用的 `.coofx.json`。
7. 生成仅供离线检查的 `.glb` 快照。
8. 校验 VAT 的固定拓扑，并生成 VAT 元数据。
9. 把 Blender 的 Z-up 坐标约定转换为 CooFX 的 Y-up 坐标约定。

工具的纯 Python 核心位于：

```text
tools/blender_coofx/core/
```

这部分不导入 `bpy`，可以在普通 Python 中运行测试。

Blender 专属部分位于：

```text
tools/blender_coofx/blender/
```

Add-on 入口是：

```text
tools/blender_coofx/__init__.py
```

## 2. 当前实现和未实现边界

### 2.1 当前可以使用的能力

当前 v1 可以使用：

- 静态 mesh；Blender 四边面和 n-gon 会在 glTF 导出时转换为三角形 primitive。
- POSITION 顶点位置。
- NORMAL 法线。
- TEXCOORD_0 UV0。
- COLOR_0 顶点色。
- 多个 glTF primitive 和基础材质引用。
- `OPAQUE` 不透明材质。
- `MASK` Alpha 裁剪材质。
- 单张 PNG 基础颜色纹理。
- glTF 节点层级。
- 节点的 translation、rotation、scale。
- `STEP`、`LINEAR`、`CUBICSPLINE` 动画轨道的导入和播放数学。
- 单帧经典粒子突发发射器。
- 受约束 Geometry Nodes 发射器组。
- `BURST` 和 `CONTINUOUS` 两类运行时网格粒子调度。
- `LOCAL` 和 `WORLD` 粒子模拟空间。
- 速度、加速度、重力、风、阻力和确定性噪声。
- 旋转、角速度、缩放、生命周期、颜色和材质变体。
- CPU 粒子模拟和独立 144-byte 网格粒子实例布局。
- `glDrawElementsInstanced` 形式的网格实例绘制器。
- 资源内容摘要、GPU generation、lease 和资源释放抽象。
- VAT 固定拓扑校验和元数据生成。

### 2.2 当前不会伪装支持的能力

以下能力当前会被拒绝、告警或只保留元数据：

- 运行时读取 `.blend`。
- 任意 Blender Shader Nodes 自动翻译。
- 任意 Geometry Nodes 节点图解释。
- Blender 刚体、流体、烟雾、布料和完整物理模拟。
- Boids。
- 非单帧的经典 Particle System 发射窗口导出。
- 依赖每个发射面法线、切线或对象方向的经典粒子速度。
- 任意名字但没有官方标记的 Geometry Nodes 组。
- glTF skin 的运行时蒙皮。
- glTF morph target 的运行时变形。
- VAT 的运行时纹理采样和顶点变形。
- mesh particle 的 `BLEND` 透明混合。
- 任意未知的 glTF `extensionsRequired`。
- JPEG、KTX、Draco、meshopt 和 data URI。
- light、audio。
- 第二套 UV 的执行。

需要特别注意：当前 Kotlin 客户端已经接通资源 reload、compiled package、真实 VAO/VBO/EBO upload、144-byte 实例缓冲、每实例 48-byte affine node matrix sidecar、GLSL 150 shader、WORLD_PASS、GPU generation lease，以及 Fabric/NeoForge 共用的客户端关闭释放。纯模型资产通过 `CooFXClient.playModel` 创建持久静态/动画实例；带 emitter 的资产通过 `CooFXClient.play` 启动刚性 mesh particle。长期联机实例通过服务端 `CooFxSceneManager` 创建，复用 RenderEntity 的 CREATE/TOGGLE/REMOVE 同步位置、旋转、缩放、clip、camera 选择和 emitter 基础参数。Blender 1.0.5 开始把多个标准 glTF camera 一起导出，camera node 可以沿用已有 clip 动画驱动视角位置、旋转和透视 FOV。`skin`、`morph`、VAT 和 `BLEND` 继续明确拒绝；Vanilla/Iris 实机视觉矩阵仍需人工验收。

## 3. 安装 Blender Add-on

### 3.1 生成快速安装包

仓库提供确定性打包脚本。在仓库根目录执行：

```text
python tools/blender_coofx/build_extension.py
```

输出文件固定为：

```text
build/distributions/coofx_exporter-1.0.5.zip
```

该 ZIP 的根目录直接包含 `__init__.py`、`blender_manifest.toml`、`LICENSE`、`core/` 和 `blender/`，可以直接用于 Blender 的 `Install from Disk`。不要把整个 `tools/blender_coofx` 再套一层目录压缩，也不要只复制 `core` 或 `blender`。

`blender_manifest.toml` 声明的最低版本是 Blender 4.2.0，Extension ID 是 `coofx_exporter`。打包测试会拒绝嵌套 package root、`tests/`、`__pycache__/` 和 `.pyc` 文件进入安装包。

### 3.2 通过 Blender Extension 安装

推荐把 `tools/blender_coofx` 的内容打包为一个 ZIP，然后在 Blender 中安装：

1. 打开 Blender。
2. 进入 `Edit > Preferences`。
3. 打开 `Get Extensions` 或 `Extensions` 页面。
4. 选择右上角菜单中的 `Install from Disk`。
5. 选择 CooFX Add-on ZIP。
6. 安装后启用 `CooFX Exporter`。
7. 新建或重新打开一个场景。
8. 在 `Scene Properties` 中确认存在 `CooFX` 面板；也可以把鼠标移到 3D View，按 `N`，切换到 `CooFX` 标签。

如果 Scene Properties 页面较长，先点击场景属性图标并滚动到顶部；新版扩展还会在 3D View 的 N 侧栏提供同一套面板。

如果 Blender 版本或发行版没有 Extension 页面，也可以在 `Add-ons` 页面使用 `Install...` 选择 ZIP，然后启用该 Add-on。

### 3.3 安装后的验证

在 Blender 中检查：

- Add-on 名称是 `CooFX Exporter`。
- 最低版本提示不高于当前 Blender 版本。
- 场景属性中出现 `CooFX` 面板。
- 面板中存在 `校验 CooFX 资产`、`导出 CooFX 资产` 和 `导出 GLB 快照` 按钮。

如果普通 Python 运行 `import blender_coofx`，它不应该因为缺少 `bpy` 而在导入阶段崩溃；只有调用 `register()` 时才会提示必须在 Blender 环境中注册。这是设计行为。

## 4. 推荐的 Blender 场景准备流程

### 4.1 建立最小模型

建议第一次使用时只制作一个简单模型：

1. 添加一个 Cube、Icosphere、Suzanne 或自定义低模。
2. 删除不需要的灯光和辅助对象；需要运行时 camera tracking 时，把一个或多个 Camera 保留在导出集合中。
3. 确保模型至少包含一个面；四边面和 n-gon 可以保留。
4. 确保模型具有 UV0。
5. 确保法线方向正确。
6. 为模型创建一个材质。
7. 如果需要纹理，在材质中使用 Image Texture 节点。

CooFX runtime 的 glTF primitive 最终仍然是 `TRIANGLES`，但 Blender 源模型不再要求用户手动三角化。官方 glTF exporter 会在导出临时结果时转换四边面和 n-gon，不会修改当前 `.blend` 中的原始网格。只有完全没有面的 mesh 才会在场景校验阶段被拒绝。

#### 4.1.1 准备运行时摄像机

1. 在导出集合中保留需要的 Camera 对象；1.0.5 会全部交给官方 glTF exporter。
2. 给 Camera 对象设置唯一、稳定的名称，例如 `Camera_Main`、`Camera_Close`。
3. Camera 可以挂在 Empty 或其他父节点下，父子 TRS 会参与最终 world pose。
4. 给 Camera node 制作 location/rotation 动画时，它会沿用同一个 clip；镜头参数本身暂不做动画。
5. Perspective camera 会驱动位置、旋转和垂直 FOV；Orthographic camera 当前只驱动位置和旋转。
6. 导出后在服务端 `CooFxSceneSpec.cameraId` 中填写 Camera 对象名称；为空时选择第一个。多个 Camera 可以分别创建场景，CooFX 会按名称提取各自的节点轨迹。

旧版导出的 glTF 不会自动补出 camera。升级插件后必须重新执行一次“导出 CooFX 资产”。

### 4.2 处理缩放

CooFX 允许模型对象使用负缩放，例如：

```text
Scale X = -1
Scale Y = 1
Scale Z = 1
```

导出器使用 `export_apply=True` 把对象变换应用到 glTF 几何，并负责镜像变换对应的 winding。无需为了导出强制执行 `Object > Apply > Scale`；仍应在 Blender 中确认法线方向符合预期。发射器的 `scale.min/max` 是另一套粒子初始尺寸范围，仍要求为正值。

### 4.3 UV 和纹理

如果材质使用图片纹理，mesh 必须有 UV0。当前运行时只读取 glTF 的 `TEXCOORD_0`，不会执行第二套 UV。

建议：

1. 进入 UV Editing 工作区。
2. 为所有需要纹理的面展开 UV。
3. 确认至少存在一个 UV Map。
4. 材质中使用 Image Texture 节点。
5. 使用 PNG 图片。
6. 把最终 PNG 复制到 Minecraft 资源目录的 `textures/coofx/` 下。

CooFX 运行时使用 glTF UV 数值，不会隐式翻转 V。不要在 Minecraft 侧再额外做一次 UV 翻转。

### 4.4 材质模式

首版推荐使用两种材质模式：

```text
OPAQUE：完全不透明
MASK：按 alphaCutoff 丢弃像素
```

`BLEND` 目前不能进入网格粒子编译。即使 glTF 能保存透明材质，CooFX compiler 也会拒绝它，以避免把透明排序问题伪装成已解决。

如果要做裁剪材质：

1. 在 Blender 材质中提供 Alpha。
2. 在 CooFX 面板把 Alpha 模式设为 `裁剪`。
3. 设置 Alpha Cutoff，例如 `0.5`。
4. 确认纹理实际包含透明通道。
5. 在 Minecraft 侧使用 PNG，而不是 JPEG。

### 4.5 自发光

CooFX 读取 glTF 的 `emissiveFactor`、`emissiveTexture` 和 `KHR_materials_emissive_strength`。在 Blender 的 Principled BSDF 中设置 Emission Color 和 Emission Strength 即可；强度大于 `1` 时，官方 exporter 通常会写入 emissive-strength 扩展。当前渲染只支持 `TEXCOORD_0`；如果材质纹理声明 `texCoord=1`，导入器会明确拒绝资源，避免把第二套 UV 静默当成第一套 UV 使用。

材质进入游戏后的计算是：

```text
最终颜色 = 基础颜色 * Minecraft lightmap + 自发光贴图 * 自发光颜色 * 自发光强度
```

基础颜色会随方块光、天空光和昼夜变化。自发光部分不受黑暗影响。使用自发光贴图时仍需 UV0，并应导出 PNG。

Iris 开启光影包后，基础材质会进入 entity G-buffer，自发光再向当前主颜色附件追加亮度。CooFX 会隔离并恢复其他 G-buffer 附件，但部分 deferred 光影包会在后续阶段重新解释主附件，因此只保证表面自发光，不保证出现 bloom 光晕。原版 Vanilla 路径同样会显示亮起的表面，但不会自动增加模糊光晕。

CooFX 的基础 mesh/material ABI 已验证，但不能无条件保证任意 Iris shaderpack 的 entity color modulation、UV/overlay、PBR、雾、deferred/G-buffer、顶点位移或 emissive second pass 结果。当前资产的着色结果可能与无光影不同；CooFX 阴影暂不支持。没有通过目标 shaderpack 实际验证时，应将该 shaderpack 视为 shader 部分不兼容。运行时不会虚构 shaderpack identity，也不会把普通 CooFX shader 静默当作通用 fallback；首次进入活动 Iris entity bridge 时只会在当前客户端 session 发出一次 warning。

Blender 的自发光描述的是表面亮度，不是 Minecraft 动态光源。它不会提高附近方块的 block light；需要照亮周围环境时，应另接动态光源模组或光影包专用能力。

### 4.6 对象命名

对象名会影响导出文档中的 mesh、node 和 emitter 引用。建议使用稳定的小写或易读名称：

```text
CooFX_BurstMesh
CooFX_BurstNode
```

不要频繁改名，否则 `.coofx.json` 中的 emitter 引用需要重新生成。运行时的批次键不会把粒子位置、年龄和 seed 放进去，但 mesh、primitive 和材质引用必须稳定。

## 5. 坐标、单位和方向

### 5.1 CooFX 坐标约定

CooFX 使用右手坐标系：

```text
+X = Minecraft 世界东
+Y = 世界上方
+Z = Minecraft 世界南，也是局部前方
1 CooFX unit = 1 Minecraft block
```

角度使用弧度，四元数顺序是：

```text
(x, y, z, w)
```

局部变换组合语义是：

```text
world = parentWorld * local
local = T * R * S
```

### 5.2 Blender 到 CooFX

Blender 原生是右手 Z-up。官方 glTF exporter 负责转换到标准 Y-up glTF。概念映射为：

```text
(x_blender, y_blender, z_blender)
  -> (x_coofx, y_coofx, z_coofx)
  -> (x_blender, z_blender, -y_blender)
```

这个转换由 Blender 官方 glTF exporter 负责。运行时读取 glTF 后不能再次换轴，否则模型会旋转两次。

### 5.3 单位缩放

默认情况下：

```text
1 Blender unit = 1 Minecraft block
```

如果你的 Blender 项目使用厘米、毫米或其他单位，先在 `Scene Properties > Units > Unit Scale` 设置场景单位，再把 CooFX 面板的 `单位缩放` 设为相同数值。校验器会拒绝两者不一致的场景，防止官方 glTF geometry 与 CooFX emitter 速度使用不同单位。

导出时，Blender 官方 glTF exporter 负责 mesh/node 的 Y-up 与场景单位转换；CooFX emitter extractor 使用同一 `单位缩放` 转换 Geometry Nodes 的 velocity，通过 `B * R * B^-1` 四元数基变换转换 XYZ Euler rotation，并按无符号轴映射转换 scale。扩展审计元数据会记录：

```json
"extensions": {
  "cooparticlesapi:blender_export": {
    "unitScale": 1.0,
    "vectorMapping": ["x", "z", "-y"],
    "conversionOwner": "blender_official_gltf_exporter_and_coofx_emitter_extractor"
  }
}
```

Minecraft runtime 不会再做第二次换轴或单位缩放。

## 6. CooFX 面板设置

在 `Scene Properties > CooFX` 中配置以下字段。

### 6.1 命名空间

例如：

```text
cooparticlesapi
```

命名空间只能使用小写字母、数字、点、下划线和连字符。

### 6.2 资产名称

例如：

```text
examples/rigid_burst
```

资产名称可以包含安全的子目录，但不能包含 `..`，不能使用反斜杠，不能使用绝对路径。

### 6.3 资源命名空间目录

点击面板中的 `选择资源目录`，选择某个 namespace 对应的 `assets/<namespace>` 目录，例如：

```text
D:/CodeSources/java/mods/CooParticlesAPI-MultiPlatform/common/src/main/resources/assets/cooparticlesapi
```

注意这里已经包含 `assets/cooparticlesapi`。不要再选择仓库根目录，也不要选择只读的 `assets` 父目录。选择结果会保存在当前 `.blend` 场景中；以后可以再次点击按钮调整。

如果目录为空，点击 `导出 CooFX 资产` 会先自动打开目录选择器，不再回退到 Blender 或 Steam 的工作目录。选择正确后，导出器会在这个目录下创建：

```text
coofx/
coofx/models/
```

### 6.4 资产种子

资产种子必须是 16 位小写十六进制字符串，例如：

```text
0123456789abcdef
```

不要使用十进制数字、带 `0x` 前缀的字符串或大写字母。

这个 seed 是资源级确定性随机的基础。相同资源、相同请求 seed、相同 tick 输入应产生相同的发射序列。

### 6.5 基础颜色纹理

面板中的基础颜色纹理字段应该填写完整 ResourceLocation，例如：

```text
cooparticlesapi:textures/coofx/rigid_burst.png
```

对应文件应该放在：

```text
common/src/main/resources/assets/cooparticlesapi/textures/coofx/rigid_burst.png
```

不要填写 Windows 文件系统绝对路径。这里填的是 Minecraft 资源 ID，不是本机路径。

### 6.6 glTF 材质名

这个字段必须与 Blender 材质导出后的 glTF 材质名一致。默认值是：

```text
Material
```

如果你在 Blender 中把材质命名为 `RigidBurstMaterial`，就把面板中的 glTF 材质改为相同名称。

### 6.7 动画 Clip

启用“导出动画 Clip”后，填写稳定的 Clip ID 和 Blender 官方 glTF exporter 生成的 animation 名称，再选择 `ONCE`、`LOOP` 或 `PING_PONG`。当前面板支持一个可选 clip；纯 Python 模型和 JSON 格式支持多个 clip，但多项列表编辑 UI 尚未实现。

## 7. 经典 Particle System 导出

### 7.1 当前支持的经典粒子范围

当前 Add-on 只抽取可以无歧义表达成 CooFX 突发发射器的经典粒子系统。

支持的主要字段是：

| Blender 参数 | CooFX 字段 |
|---|---|
| Number | `count` |
| Frame Start，且必须等于 Frame End | `delayTicks` |
| Lifetime | `lifetimeTicks` |
| Particle Size | `scale.min/max` 的基础值 |
| Size Random | `scale.min/max` 的范围 |

### 7.2 创建单帧 Burst

操作步骤：

1. 选中一个 mesh 对象。
2. 添加 Particle System。
3. 类型选择 `Emitter`。
4. 把 `Frame Start` 和 `Frame End` 设置为同一个值，例如都为 `1`。
5. 设置 `Number`，例如 `16`。
6. 设置 `Lifetime`，例如 `2` 秒。
7. 设置 `Particle Size`，例如 `1.0`。
8. 设置 `Size Random`，例如 `0.25`。
9. 把 Normal、Tangent、Object 速度方向系数设为 `0`。
10. 点击 CooFX 面板的 `校验 CooFX 资产`。

对于 20 FPS 的 Minecraft tick，帧延迟和生命周期会按下式转换：

```text
ticks = round(frame_or_seconds * 20 / Blender FPS)
```

### 7.3 当前会跳过的经典粒子设置

以下情况会产生中文 warning 并跳过该粒子系统：

- `Frame Start != Frame End`。
- 粒子类型不是 `EMITTER`，例如 HAIR。
- Normal、Tangent 或 Object 速度系数非零。

原因是当前 v1 emitter 使用全局向量范围，无法无损表达“每个面按照自己的法线或切线发射”。静默把它改成统一方向会改变美术结果，因此工具选择明确跳过。

### 7.4 Object 和 Collection 实例化的当前边界

CooFX runtime 的 mesh emitter 定义已经有 `OBJECT` 和 `COLLECTION` 选择模式，collection 变体也使用稳定 seed 选择。但是当前 Blender Add-on 的经典 Particle System 抽取器还没有把 Blender 的 `Render As Object`、`Render As Collection` 成员列表完整写入 v1 文档。

因此当前推荐：

- 经典 Particle System 只用于验证 count、delay、lifetime、scale 等基础 burst 语义。
- 真正需要多个对象或集合变体时，使用受约束 Geometry Nodes 输入，或在 Kotlin runtime 中显式配置 `CooFxMeshVariant` 列表。
- 不要根据对象名称猜测 collection 成员。

## 8. 受约束 Geometry Nodes 导出

### 8.1 为什么不能导出任意 Geometry Nodes

Geometry Nodes 是通用节点图系统。任意节点图可能包含几何生成、实例化、场、时间、随机、属性传递和自定义逻辑，Minecraft runtime 无法安全地解释所有节点。

CooFX 只识别明确标记的官方组：

```text
cooparticlesapi:coofx/emitter_v1
```

没有这个标记，即使节点组名字叫 `CooFX Mesh Emitter`，也不会被自动识别。

### 8.2 官方组标记

在 Geometry Nodes node group 上设置自定义属性：

```text
coofx_official_group = "cooparticlesapi:coofx/emitter_v1"
```

属性应设置在 node group 数据块上，而不是只设置在对象或 modifier 上。

### 8.3 必需接口输入

官方发射器组必须提供以下输入 socket，名称必须完全一致：

```text
Count
Delay Ticks
Lifetime Ticks
Mesh
Node
```

可选输入：

```text
Emitter ID
Velocity Min
Velocity Max
Rotation Min
Rotation Max
Scale Min
Scale Max
```

Add-on 使用 Geometry Nodes interface socket 的稳定 identifier 读取 modifier 值，不依赖 UI 顺序。

### 8.4 推荐节点组制作步骤

1. 创建 Geometry Nodes Modifier。
2. 新建一个独立 node group。
3. 在 group interface 中创建上述输入 socket。
4. 设置 group 自定义属性 `coofx_official_group`。
5. 使用输入值驱动实例数量、局部速度和局部变换。
6. 不要引入未定义的自定义输出语义。
7. 保持 Mesh 和 Node 输入可以映射到导出对象或节点名称。
8. 保存并执行 CooFX 校验。

如果缺少任意必需输入，工具会报告缺少的 socket 并跳过该组。

### 8.5 非官方 Geometry Nodes 的两条路线

对于任意节点图，只能选择下面的路线之一：

1. 在 Blender 中 `Realize Instances`，把最终结果导出为静态 mesh。
2. 先烘焙成固定拓扑 VAT，再通过 VAT 元数据流程处理。

不要把任意节点图直接交给 Minecraft runtime，也不要只改节点组名字来绕过官方标记。

## 9. 导出运行时资产

### 9.1 第一次导出推荐配置

使用下面的示例配置：

```text
命名空间：cooparticlesapi
资产名称：examples/rigid_burst
单位缩放：1.0
资产种子：0123456789abcdef
glTF 材质：RigidBurstMaterial
基础颜色纹理：cooparticlesapi:textures/coofx/rigid_burst.png
Alpha 模式：OPAQUE
双面：关闭
```

### 9.2 校验

先点击：

```text
校验 CooFX 资产
```

通过后再点击：

```text
导出 CooFX 资产
```

校验会检查：

- 对象路径和名称。
- 对象变换是否有限；负缩放允许由官方 glTF exporter 应用。
- 是否至少包含一个可由 glTF exporter 三角化的面。
- 纹理网格的 UV0。
- namespace 和 asset name。
- asset seed。
- emitter 数值范围。
- 材质 Alpha 模式。
- ResourceLocation 格式。
- 生成文档的 schemaVersion。

### 9.3 导出结果

如果资源根目录是：

```text
common/src/main/resources/assets/cooparticlesapi
```

资产名称是：

```text
examples/rigid_burst
```

则会生成：

```text
common/src/main/resources/assets/cooparticlesapi/
  coofx/
    examples/
      rigid_burst.coofx.json
    models/
      examples/
        rigid_burst.gltf
        rigid_burst.bin
```

纹理需要单独确保存在：

```text
common/src/main/resources/assets/cooparticlesapi/
  textures/
    coofx/
      rigid_burst.png
```

导出过程先在临时目录中生成 glTF 和 JSON，确认文件完整后再替换目标文件。这样不会在官方 exporter 中途失败时留下半个资产。

新资产如果只填写单段名称，例如 `people`，导出器会直接使用 `coofxAsset` 的同名目录约定：

```text
coofx/people/people.coofx.json
coofx/models/people/people.gltf
coofx/models/people/people.bin
```

带 `/` 的资产名称继续使用原有路径布局，已有项目不需要迁移。

### 9.4 GLB 快照和运行时包的区别

`导出 GLB 快照` 用于：

- 离线检查模型。
- 交给外部 glTF 检查器查看。
- 为未来 VAT 流程保留一个单文件快照。

它不会自动写入 CooFX 运行时清单。

运行时资产流程推荐始终使用：

```text
.gltf + .bin + PNG + .coofx.json
```

不要把生成的 GLB 当成已经接入游戏资源清单的运行时资产。

## 10. 生成 JSON 的结构说明

一个最小 CooFX v1 文档如下：

```json
{
  "$schema": "cooparticlesapi:coofx/schema/v1",
  "schemaVersion": 1,
  "coordinateSystem": "coofx_rh_y_up_z_south",
  "assetSeed": "0123456789abcdef",
  "model": "cooparticlesapi:coofx/models/examples/rigid_burst.gltf",
  "scene": 0,
  "clips": [],
  "materials": [
    {
      "id": "default",
      "gltfMaterial": "RigidBurstMaterial",
      "baseColorTexture": "cooparticlesapi:textures/coofx/rigid_burst.png",
      "alphaMode": "OPAQUE",
      "doubleSided": false
    }
  ],
  "emitters": [
    {
      "id": "rigid_burst",
      "mesh": "RigidBurstMesh",
      "node": "RigidBurstNode",
      "count": 16,
      "delayTicks": 0,
      "lifetimeTicks": 40,
      "velocity": {
        "min": [-0.05, 0.1, -0.05],
        "max": [0.05, 0.2, 0.05]
      },
      "rotationRadians": {
        "min": [0.0, 0.0, 0.0],
        "max": [0.0, 6.283185307179586, 0.0]
      },
      "scale": {
        "min": [0.8, 0.8, 0.8],
        "max": [1.2, 1.2, 1.2]
      }
    }
  ],
  "requiredExtensions": [],
  "extensions": {}
}
```

字段规则：

- `schemaVersion` 必须是整数 `1`。
- `coordinateSystem` 必须是 `coofx_rh_y_up_z_south`。
- `assetSeed` 必须是 16 位小写十六进制字符串。
- `model` 必须引用安全的 `.gltf` 或 `.glb` ResourceLocation；Blender runtime bundle 默认生成分离 `.gltf`。
- `scene` 是非负 scene 索引。
- `clips` 的 loop mode 接受 `ONCE`、`LOOP` 和 `PING_PONG`，animation 可以使用唯一名称或索引。
- `materials` 当前只接受 `OPAQUE` 和 `MASK` 进入网格粒子 compiler。
- `emitters` 的时间单位是 tick。
- 旋转范围的单位是弧度。
- `requiredExtensions` 中的未知扩展会导致硬失败。
- 非 required 的未知扩展可以保留，但应产生诊断。

## 11. Minecraft 资源放置

### 11.1 Common 资源

如果该资产属于两个 loader 共用资源，放入：

```text
common/src/main/resources/assets/<namespace>/
```

完整示例：

```text
common/src/main/resources/assets/cooparticlesapi/coofx/examples/rigid_burst.coofx.json
common/src/main/resources/assets/cooparticlesapi/coofx/models/examples/rigid_burst.gltf
common/src/main/resources/assets/cooparticlesapi/coofx/models/examples/rigid_burst.bin
common/src/main/resources/assets/cooparticlesapi/textures/coofx/rigid_burst.png
```

### 11.2 资源 ID

入口 JSON 的完整 ResourceLocation 是：

```text
cooparticlesapi:coofx/examples/rigid_burst.coofx.json
```

模型的完整 ResourceLocation 是：

```text
cooparticlesapi:coofx/models/examples/rigid_burst.gltf
```

纹理的完整 ResourceLocation 是：

```text
cooparticlesapi:textures/coofx/rigid_burst.png
```

当前 importer 会检查 `.coofx.json` 后缀，因此在直接调用 importer 时应传入完整入口资源 ID。

### 11.3 不要使用的路径

不要把运行时资源放到：

```text
common/src/test/resources/
```

除非它只是测试 fixture。

不要把资源放到：

```text
assets/<namespace>/models/
```

来代替 CooFX 约定的：

```text
assets/<namespace>/coofx/models/
```

不要把 Windows 绝对路径写进 JSON：

```json
"model": "D:/model/rigid_burst.gltf"
```

这种路径会被拒绝。

## 12. Kotlin runtime 导入流程

Python 只在 Blender 中生成资源文件，不参与 Minecraft 启动、资源重载或渲染。下面的 importer/compiler 说明用于理解内部资产链；普通客户端调用方只需把导出文件放入 `assets/<namespace>`。只有模型时调用 `CooFXClient.playModel`，资产包含 emitter 且需要粒子发射时调用 `CooFXClient.play`。

### 12.1 ResourceProvider 适配

导入器的核心只依赖：

```kotlin
fun interface CooFxResourceProvider {
    fun read(resource: ResourceLocation): ByteArray
}
```

在 Minecraft 资源加载环境中使用：

```kotlin
val provider = MinecraftCooFxResourceProvider(resourceProvider)
val importer = CooFxAssetImporter(provider)
```

其中 `resourceProvider` 是现有资源重载或资源访问阶段提供的 `ResourceProvider`。

### 12.2 导入 CooFX JSON 和 glTF

示例：

```kotlin
val assetId = ResourceLocation.fromNamespaceAndPath(
    "cooparticlesapi",
    "coofx/examples/rigid_burst.coofx.json",
)
val result = CooFxAssetImporter(
    MinecraftCooFxResourceProvider(resourceProvider),
).import(assetId)

if (result.asset == null) {
    val message = result.diagnostics.joinToString("\n") { diagnostic ->
        "${diagnostic.code}: ${diagnostic.message}"
    }
    error("CooFX 导入失败：$message")
}

val sourceAsset = result.asset
```

导入器会依次读取：

1. `.coofx.json`。
2. JSON 中的 `.gltf`。
3. glTF 引用的外部 `.bin`。
4. glTF 引用的 PNG。
5. glTF accessor、mesh、node、material 和 animation。

导入器会把结果规范化为 `CooFxSourceAsset`，下游不应继续传递 Gson `JsonObject`。

### 12.3 导入阶段会拒绝的错误

常见错误包括：

- JSON schemaVersion 不是 1。
- coordinateSystem 不匹配。
- assetSeed 格式不正确。
- 资源路径为空、绝对路径、反斜杠或包含 `..`。
- glTF 版本不是 2.0。
- buffer 不存在或长度不足。
- accessor 越界。
- POSITION 不是 VEC3。
- primitive 不是 TRIANGLES。
- 索引超出顶点范围。
- animation 时间不递增。
- 节点图存在循环。
- material 索引越界。
- 使用非 PNG 图片。
- light、audio 或未知 required extension。

错误应该作为 `CooFxDiagnostic` 记录，不能删除问题字段后继续绘制。

### 12.4 播放纯模型资产

`emitters: []` 表示资产只有模型，这是合法状态。以下代码直接播放本教程中实际导出的 `test_moudles` 资产，不会创建 emitter：

```kotlin
val result = CooFXClient.playModel(
    CooFxModelPlayRequest(
        resourceId = coofxAsset("cooparticlesapi", "test_moudles"),
        transform = CooFxWorldTransform(
            x = position.x,
            y = position.y,
            z = position.z,
        ),
        requestSeed = 0x5EEDL,
        clipId = null,
        playbackSpeed = 1F,
    )
)

val handle = when (result) {
    is CooFxModelPlayResult.Started -> result.handle
    is CooFxModelPlayResult.Queued -> null
    is CooFxModelPlayResult.Failed -> error(result.failure.message)
}
```

模型实例会绘制导出 scene 中的全部 mesh node，并一直存在到 `handle.stop()`、退出世界或资源重载。`clipId = null` 时，有动画就播放第一个 compiled clip，没有动画就保持静态 bind pose。每个模型实例拥有独立 tick 时钟。

资源 snapshot 或 GPU generation 尚未准备时返回 `Queued`，下一次有效 WORLD_PASS 会自动启动；已经准备时返回 `Started`。该入口只能在客户端调用。联机业务不要手写临时 S2C 包，应使用 12.6 节的服务端权威 `CooFxSceneManager`。

仓库已把当前资产接入 BlockTest。打开测试控制器，在 `Block API` 测试组中选择 `coofx/model/test_moudles` 检查固定世界坐标模型；重新使用 1.0.5 插件导出包含 camera 后，选择 `coofx/model-camera/test_moudles` 检查模型与资产 camera 跟踪。通过、失败、跳过、取消、断线或切换世界都会停止本次场景并恢复玩家视角。

### 12.5 播放资产内置 emitter

只有 `.coofx.json` 的 `emitters` 非空时才使用粒子入口：

```kotlin
val result = CooFXClient.play(
    CooFxPlayRequest(
        resourceId = ResourceLocation.fromNamespaceAndPath(
            "cooparticlesapi",
            "coofx/examples/rigid_burst.coofx.json",
        ),
        transform = CooFxWorldTransform(position.x, position.y, position.z),
        requestSeed = 0x5EEDL,
        clipId = null,
        emitterId = "rigid_burst",
    )
)
```

粒子入口返回 `CooFxPlayResult`，并按 emitter 的 count、lifetime、velocity 等语义进入确定性粒子模拟。不要在调用方运行 Python、手动解析 JSON、伪造 emitter 或直接创建 GL 对象。

### 12.6 服务端权威同步实例

联机玩法使用 `CooFxSceneManager`。它复用 RenderEntity 的可见范围与 CREATE/TOGGLE/REMOVE，同步的服务端字段包括资源、位置、四元数旋转、缩放、clip、播放速度、模式、camera、camera 优先级、camera 目标玩家、emitter ID、count、delay 和 lifetime：

```kotlin
val scene = CooFxSceneManager.spawn(
    level = serverLevel,
    spec = CooFxSceneSpec(
        resourceId = ResourceLocation.fromNamespaceAndPath(
            "cooparticlesapi",
            "coofx/test/test_moudles.coofx.json",
        ),
        transform = CooFxWorldTransform(position.x, position.y, position.z),
        requestSeed = 0x5EEDL,
        mode = CooFxSceneMode.MODEL_CAMERA_AND_EMITTER,
        clipId = null,
        playbackSpeed = 1F,
        // 对应 Blender Camera 对象名；多个 Camera 时手动指定要播放的轨迹。
        cameraId = "Camera",
        // 只有该玩家接管镜头；留空则所有收到场景的玩家都接管镜头。
        cameraTargetPlayer = targetPlayer.uuid,
        cameraPriority = 10,
        emitterId = "sparks",
        emitterCount = 24,
        emitterDelayTicks = 0,
        emitterLifetimeTicks = 40,
    ),
)
```

服务端更新时使用 patch，不要在客户端直接改镜像：

```kotlin
scene.update(
    CooFxScenePatch(
        transform = CooFxWorldTransform(nextX, nextY, nextZ),
        playbackSpeed = 0.5F,
        cameraId = "Camera_Close",
        emitterCount = 48,
    )
)

// 需要更换资源、seed，或显式清空 clip/camera/emitter 时使用完整快照：
scene.replace(nextSceneSpec)
scene.stop()
```

API 必须在 Minecraft server thread 调用。`coofxAsset(modid, assetID)` 是推荐的资源入口构造方法：例如 `coofxAsset("examplemod", "people")` 固定解析为 `examplemod:coofx/people/people.coofx.json`。`assetID` 只能是小写单段资源名，目录和入口文件必须同名；Blender 面板把资产名称填写为 `people` 时会直接生成这套目录。

`cameraId` 优先匹配 glTF camera node 的稳定 ID，也可匹配唯一 Blender Camera 对象名称；选择器为空时使用场景中的第一个 camera。多个 camera tracking scene 同时可见时，客户端使用 `cameraPriority` 最大者。`CooFxSceneMode.CAMERA_ONLY` 是只跟踪镜头、不绘制模型的易用别名（等同于 `CAMERA_TRACKING`）。透视 camera 会同步位置、旋转和垂直 FOV；正交 camera 当前只同步姿态，不替换 Minecraft 主投影。

场景的模型仍按同世界且距离不超过 `renderRange`（默认 256）发送给每个可见玩家；`cameraTargetPlayer` 只在客户端过滤镜头接管，不会隐藏模型。目标玩家离开可见范围后会收到场景移除，重新进入范围会恢复模型和镜头状态。

## 13. CPU compiled render package

导入得到 source asset 后，进入 compiler：

```kotlin
val compiled = CooFxAssetCompiler().compile(sourceAsset)
```

`CooFxCompiledRenderPackage` 是不可变的 CPU 包，包含：

- 扁平顶点字节。
- 索引字节。
- 顶点布局。
- primitive draw range。
- material 编译描述。
- animation clip 元数据与可执行轨道。
- scene 中每个模型 primitive 到 node 的绑定。
- 多个 perspective/orthographic camera 描述符及 camera node 绑定。
- 可选 emitter 到 primitive 的映射。
- batch template。
- deformation plan。
- content digest。

它不包含：

- VAO。
- VBO。
- EBO。
- OpenGL texture handle。
- shader program handle。
- framebuffer。

这保证 JSON/glTF 解析和 CPU 编译可以在非渲染线程完成，而 GPU 对象只在渲染线程建立。

当前 compiler 的能力边界是：

```text
RIGID node + OPAQUE/MASK + TRIANGLES
```

以下内容会在 compiler 阶段明确拒绝：

```text
skin
morph target
weights animation
VAT extension
BLEND
```

## 14. Playback 使用

### 14.1 播放时钟

`CooFxPlaybackClock` 使用绝对 tick 计算 clip 时间，不累计每帧浮点误差：

```kotlin
val clock = CooFxPlaybackClock(
    startTick = effectStartTick,
    durationSeconds = 2.0F,
    speed = 1.0F,
    loopMode = CooFxLoopMode.LOOP,
)

val time = clock.timeAt(currentTick, partialTick)
val seconds = time.seconds
```

时间换算是：

```text
seconds = (tick - startTick + partialTick) / 20 * speed
```

`ONCE` 到达末尾后将 `completed` 设为 `true`；`LOOP` 会回到起点；`PING_PONG` 支持往返采样。

### 14.2 轨道插值

CooFX 不使用现有 `Animate` 保存 glTF 轨道。播放模块独立支持：

- `STEP`：保持前一关键帧。
- `LINEAR`：平移和缩放使用线性插值，旋转使用归一化球面插值。
- `CUBICSPLINE`：使用 glTF 的 in tangent、value、out tangent 布局做 Hermite 采样。

动画时间是秒，粒子生命周期和发射调度是 tick。不要把两种时间单位混用。

### 14.3 VAT 和 morph 采样边界

当前播放模块已经有 VAT 相邻帧采样和 morph weight track 的纯数学类型，用于后端规划和测试：

```kotlin
val sample = CooFxVatFrameSampler.sample(
    timeSeconds = seconds,
    frameCount = frameCount,
    framesPerSecond = fps,
    loopMode = CooFxLoopMode.LOOP,
)
```

但 compiler 当前不会执行 VAT 或 morph 变形。调用这些类型不能表示 GPU deformation 已经接线。

## 15. 网格粒子 runtime 使用

### 15.1 一个 controller 管理全部粒子

模型粒子不应为每个粒子创建一个 RenderEntity。当前 runtime 的核心控制器是：

```kotlin
val manager = CooFxMeshParticleManager(capacity = 1024)
```

它统一管理：

- dense particle store。
- emitter 状态。
- stable particle id。
- emission ordinal。
- deterministic seed。
- CPU tick。
- batch 构建。

### 15.2 发射器定义

`CooFxMeshEmitterDefinition` 的重要字段包括：

```text
emitterId
simulationSpace: WORLD 或 LOCAL
emissionMode: BURST 或 CONTINUOUS
selectionMode: OBJECT 或 COLLECTION
variants
延迟、持续时间、数量、每 tick 数量
生命周期范围
位置、速度、加速度范围
旋转、角速度、缩放范围
颜色范围
gravity、wind、drag、noise
clipIndex、playbackSpeed
```

所有范围都是逐分量的闭区间。`COLLECTION` 模式要求变体列表顺序稳定；运行时使用粒子 seed 选择成员，不依赖集合迭代顺序。

### 15.3 WORLD 和 LOCAL

`WORLD`：

- 粒子生成时把发射器变换应用到位置、速度、加速度和旋转。
- 生成后不再跟随发射器。

`LOCAL`：

- 粒子保留局部模拟数据。
- 构建批次时使用发射器 previous/current transform 转换到世界空间。
- 适合附着在移动 RenderEntity 或移动特效主体上的局部粒子。

### 15.4 逻辑 tick

每个客户端逻辑 tick 调用：

```kotlin
manager.tick()
```

tick 顺序是：

1. 推进已有粒子。
2. 回收达到 lifetime 的粒子。
3. 按 emitter 注册顺序处理当前 tick 的 burst 或 continuous 发射。
4. 增加 emitter age。

模拟器不会读取系统时间、世界随机或外部随机单例。

### 15.5 构建实例批次

渲染阶段调用：

```kotlin
val batches = manager.buildBatches()
```

批次按 `CooFxMeshBatchKey` 和 stable particle id 排序。批次键只包含共享资源和光栅状态，不包含：

- 粒子位置。
- 粒子年龄。
- 粒子颜色。
- 粒子 seed。
- clip time。
- 相机距离。

### 15.6 实例布局

CooFX 网格实例使用独立 ABI，不复用 CParticle billboard 的 36-float 语义。

当前布局是：

```text
9 个 vec4
36 个 float
144 bytes
```

布局包含 previous/current 位置、旋转、缩放、颜色、年龄、生命周期、clip time、seed 等实例数据。具体字段顺序由 `CooFxMeshInstanceLayout` 统一定义，shader 和 Kotlin 端必须共享同一个版本号。

### 15.7 GPU 绘制

`CooFxMeshParticleRenderer` 对每个非空 batch：

1. 上传当前 batch 的实例数据。
2. 绑定静态 mesh VAO/EBO。
3. 设置 instance vertex attributes。
4. 设置 divisor。
5. 执行一次：

```text
glDrawElementsInstanced
```

静态 mesh、material、shader/pipeline 和实例 VBO 必须由对应的 GPU package 和 Coo Pipeline 生命周期管理。不要在每帧把不可变 glTF mesh 重新转成临时顶点集合。

## 16. GL、Shader 和 CooRenderPipeline 约束

CooFX 不能建立平行的 vanilla `ShaderInstance` 入口。

所有 shader 必须：

- 通过 CooParticlesAPI 的 shader source loader 加载。
- 使用现有 `CooRenderPipeline` 或底层 Coo shader API。
- 在 Coo Pipeline 的 world pass 中绑定。
- 复用现有 Iris/Sodium 兼容路径。

直接修改 blend、depth、cull、scissor、color mask 或 polygon offset 时，必须在：

```kotlin
CooGLSLStateManager.useState {
    // 绘制调用
}
```

中完成，或者使用严格配对的 `createState/resetState` 生命周期。不能在 Renderer 末尾写“恢复默认 OpenGL 状态”的硬编码代码，因为用户当前状态不一定是默认状态。

`CooFxMeshParticleRenderer` 自身只负责 144-byte instance VBO、48-byte affine node matrix sidecar VBO、VAO/VBO 绑定恢复和 instanced draw。调用方仍然必须负责：

- Coo Pipeline program。
- texture binding。
- framebuffer/viewport 生命周期。
- CooGLSLStateManager 状态生命周期。
- Iris world pass 适配。

当前 `CooFXClient.play` 已把请求排队到资源 snapshot，在 WORLD_PASS 中完成 GPU generation upload 后启动 emitter；调用方无需也不得直接持有 GL package。暂不支持的 `parameterOverrides` 会返回结构化失败。clip node pose 已按每粒子时间在 batcher 中求值并通过 sidecar 合成到 instanced draw。

## 17. RenderEntity 和 CParticle 适配原则

### 17.1 RenderEntity

RenderEntity 只应该同步整个特效的业务状态，例如：

```text
effectId
startTick
seed
world transform
动态参数
```

它不应该逐粒子同步位置、速度、旋转和颜色。客户端根据 seed、startTick 和当前 tick 确定性生成粒子。

适配边界是：

```kotlin
fun interface CooFxRenderEntityRequestAdapter<T : Any> {
    fun createRequest(entity: T): CooFxPlayRequest?
}
```

这个适配器只读取实体状态并构造不可变请求，不持有当前实体的可变渲染状态，也不承诺跨 RenderEntity 合批。

### 17.2 CParticle

CParticle 触发适配器是：

```kotlin
fun interface CooFxCParticleTriggerAdapter<T : Any> {
    fun createEmitterRequest(trigger: T): CooFxEmitterRequest?
}
```

它只负责把已有触发上下文转换为 CooFX emitter request，不读取或写入 CParticleStore 的 36-float ABI，也不推进 CooFX 模拟。

不要把 CooFX 网格实例直接塞进 CParticle billboard 的字段布局中。

## 18. 资源重载和 GPU generation

CooFX 资产分三层：

```text
CooFxSourceAsset
  -> CooFxCompiledRenderPackage
  -> CooFxGpuPackage
```

资源重载推荐流程：

1. 在资源线程读取 JSON、glTF、BIN 和 PNG。
2. 解析为 source asset。
3. 编译成 CPU compiled package。
4. 等待渲染线程。
5. 在渲染线程上传 GPU package。
6. 原子替换 active generation。
7. 旧 generation 进入 retired。
8. 等已有 lease 释放后再 release。

`CooFxGpuPackageRegistry` 用 resource ID、content digest 和 backend capability signature 区分 generation。上传失败时不能替换仍然可用的旧 generation。

GPU 包的 `release()` 只能在渲染线程调用，并且必须幂等。

## 19. VAT 工作流

### 19.1 当前 VAT 功能

当前 Blender 工具的 VAT 支持是：

- 采样评估后的网格帧。
- 转换到 CooFX 坐标。
- 校验固定拓扑。
- 计算 topology SHA-256。
- 计算 frame data SHA-256。
- 生成帧数、FPS、时长、bounds 和纹理尺寸等元数据。

当前没有可见的“一键烘焙 VAT 纹理”按钮，runtime compiler 也不会执行 VAT deformation。

### 19.2 固定拓扑要求

所有帧必须满足：

- 顶点数量相同。
- 三角形索引顺序相同。
- 法线布局相同。
- 所有位置和法线分量是有限数。

以下动画不适合直接 VAT：

- 会改变拓扑的 Boolean。
- 不同帧顶点数量变化。
- 动态重拓扑。
- 不稳定的实例 realize 结果。

如果固定拓扑校验失败，不能通过删除某些帧或重新排序索引来掩盖问题；应修复烘焙输入。

### 19.3 VAT 元数据示例

```python
metadata = build_vat_metadata(
    frames=frames,
    fps=24.0,
    loop_mode="LOOP",
)
```

返回的数据包含：

```text
version
vertexCount
frameCount
fps
durationSeconds
bounds
textureDimensions
loopMode
hasNormals
meshTopologySha256
frameDataSha256
coordinateSystem
```

## 20. 完整示例：从 Blender 到资源目录

下面以 `rigid_burst` 为例。

### 步骤 A：创建场景

1. 创建一个三角化的低模网格。
2. 命名为 `RigidBurstMesh`。
3. 确认 UV0 存在。
4. 创建材质 `RigidBurstMaterial`。
5. 使用 PNG 基础颜色纹理。
6. 可选：创建名为 `Spin` 的刚性节点动画。
7. 添加单帧 Particle System，设置 count 和 lifetime。
8. 或创建带官方标记的 Geometry Nodes emitter。

### 步骤 B：安装并打开面板

1. 启用 `CooFX Exporter`。
2. 打开 Scene Properties。
3. 找到 `CooFX` 面板。
4. 填写：

```text
namespace = cooparticlesapi
asset_name = examples/rigid_burst
output_root = .../common/src/main/resources/assets/cooparticlesapi
unit_scale = 1.0
asset_seed = 0123456789abcdef
gltf_material = RigidBurstMaterial
base_color_texture = cooparticlesapi:textures/coofx/rigid_burst.png
alpha_mode = OPAQUE
double_sided = false
```

### 步骤 C：校验

点击 `校验 CooFX 资产`。如果出现错误，先处理错误再导出。warning 不一定阻止导出，但必须理解它代表哪一项被跳过。

### 步骤 D：导出

点击 `导出 CooFX 资产`。确认出现：

```text
coofx/examples/rigid_burst.coofx.json
coofx/models/examples/rigid_burst.gltf
coofx/models/examples/rigid_burst.bin
```

### 步骤 E：复制纹理

确认：

```text
textures/coofx/rigid_burst.png
```

与 JSON 中的 ResourceLocation 一致。

### 步骤 F：资源重载

在开发环境中启动资源重载后，查看日志中是否存在：

```text
找不到资源
CooFX import failed
schemaVersion
model 路径非法
primitive 索引越界
```

CooFX importer 必须在 import 或 compile 阶段报错，不应该等到真正 draw 时才发现资源坏了。

## 21. Python 离线验证

工具自带测试位于：

```text
tools/blender_coofx/tests/
```

可以在仓库根目录执行：

```powershell
python -m unittest discover -s tools/blender_coofx/tests -p "test_*.py"
```

如果只想检查核心模型、坐标和序列化：

```powershell
python -m unittest tools.blender_coofx.tests.test_core
```

如果要在 Blender 无界面模式中执行 Add-on smoke test，使用项目允许的 Blender 可执行文件：

```powershell
blender --background --factory-startup --python tools/blender_coofx/tests/blender_export_smoke.py
```

该命令只验证 Blender API 和 Add-on 入口，不代表 Minecraft 客户端的视觉渲染已经验证。

## 22. 常见错误排查

### 22.1 面板没有出现

检查：

1. 当前 Blender 版本是否至少 4.2。
2. ZIP 内是否直接包含 `__init__.py` 和 `blender_manifest.toml`。
3. 是否启用了 Add-on。
4. 是否重新打开了 Preferences 或场景。
5. 是否在 Scene Properties 中寻找，而不是 Object Properties。

### 22.2 提示网格必须至少包含一个可三角化的面

这表示选中的 mesh 没有任何 polygon，只有顶点或边。进入 Edit Mode 创建至少一个面后重新校验。普通四边面和 n-gon 不需要手动处理，官方 glTF exporter 会在导出结果中自动三角化。

### 22.3 提示使用图片纹理但没有 UV0

处理：

1. 选择 mesh。
2. 进入 UV Editing。
3. 创建 UV Map。
4. 展开全部面。
5. 保存并重新校验。

### 22.4 负缩放模型的面朝向

CooFX 不会因为对象 scale 含负分量而拒绝导出。官方 glTF exporter 会在 `export_apply=True` 下应用对象变换并调整导出几何；如果模型仍出现背面消失或法线方向异常，请在 Blender 中检查法线，或对对象执行 Apply Scale 后重新导出。

### 22.5 经典粒子系统被跳过

检查 warning：

- 是否是 `EMITTER`。
- Frame Start 是否等于 Frame End。
- Normal/Tangent/Object velocity 是否都是零。

如果需要持续发射，应把发射语义迁移到 CooFX runtime 的 `CONTINUOUS` definition，而不是强行让 v1 Add-on 把连续系统伪装成 burst。

### 22.6 Geometry Nodes 没有被识别

逐项检查：

```text
node_group["coofx_official_group"]
  == "cooparticlesapi:coofx/emitter_v1"
```

并确认接口中存在：

```text
Count
Delay Ticks
Lifetime Ticks
Mesh
Node
```

输入名称必须匹配，不能只靠同义词。

### 22.7 Minecraft 找不到 glTF 或 BIN

检查资源布局：

```text
assets/<namespace>/coofx/<name>.coofx.json
assets/<namespace>/coofx/models/<name>.gltf
assets/<namespace>/coofx/models/<name>.bin
```

检查 JSON 中 `model` 是否使用：

```text
<namespace>:coofx/models/<name>.gltf
```

检查 `.gltf` 中的 BIN URI 是否是相对路径，并且文件名和实际文件一致。

### 22.8 找不到 PNG

检查：

1. PNG 是否位于 `assets/<namespace>/textures/coofx/`。
2. JSON 中是否写完整 ResourceLocation。
3. ResourceLocation 是否使用 `/`，而不是 `\`。
4. 文件扩展名是否为 `.png`。
5. 是否误写了本机绝对路径。

### 22.9 compiler 拒绝 BLEND、skin、morph 或 VAT

这不是导入器随机失败，而是当前能力边界。解决方式是：

- 将材质改为 OPAQUE 或 MASK。
- 将 skin/morph 烘焙为刚性节点或静态结果。
- 将动态形变单独准备为 VAT，并等待 VAT runtime backend 接入。
- 不要删除 metadata 来绕过 compiler。

## 23. 资源发布前检查清单

### Blender 检查

- [ ] Blender 版本至少 4.2。
- [ ] Add-on 已启用。
- [ ] 目标对象只包含需要导出的 mesh。
- [ ] 每个目标 mesh 至少包含一个面；四边面和 n-gon 可由 glTF exporter 自动三角化。
- [ ] 纹理 mesh 具有 UV0。
- [ ] 负缩放对象的法线和背面剔除结果已在 Blender 中确认。
- [ ] 材质名稳定。
- [ ] 基础颜色纹理是 PNG。
- [ ] Alpha 使用 OPAQUE 或 MASK。
- [ ] Particle System 的限制已确认。
- [ ] Geometry Nodes 使用官方组标记。
- [ ] unitScale 已明确设置。
- [ ] asset seed 已固定。

### 导出检查

- [ ] 先点击校验，再点击导出。
- [ ] `.coofx.json` 存在。
- [ ] `.gltf` 存在。
- [ ] 同名 `.bin` 存在。
- [ ] 纹理存在。
- [ ] JSON 是 UTF-8 无 BOM。
- [ ] JSON 中资源 ID 没有绝对路径。
- [ ] 不依赖 GLB 快照作为运行时清单。

### Minecraft 检查

- [ ] 资源放在 common 或正确 loader 的 assets 目录。
- [ ] namespace 与 JSON 一致。
- [ ] glTF 相对 BIN URI 正确。
- [ ] PNG ResourceLocation 正确。
- [ ] 资源重载没有 CooFX diagnostic error。
- [ ] importer 成功返回 `CooFxSourceAsset`。
- [ ] compiler 成功生成 `CooFxCompiledRenderPackage`。
- [ ] GPU upload 在渲染线程执行。
- [ ] renderer 使用 CooRenderPipeline 和 CooGLSLStateManager。
- [ ] 没有为单个粒子创建 RenderEntity。
- [ ] 没有把 CooFX 实例布局混入 CParticle 36-float ABI。

## 24. 当前推荐的开发顺序

如果要继续完善 CooFX，推荐按以下顺序推进：

1. 先用本教程导出一个静态三角网格和 OPAQUE 材质。
2. 在 common test 中验证 JSON、glTF、BIN、PNG 引用和 diagnostics。
3. 验证 `CooFxAssetCompiler` 能生成不可变 CPU compiled package。
4. 使用 `CooFxPlaybackClock` 和轨道 sampler 验证动画时间。
5. 使用 `CooFxMeshParticleManager` 验证 burst、continuous、WORLD、LOCAL 和确定性 seed。
6. 在渲染线程实现 CooFX GPU package uploader。
7. 把 shader 接入现有 CooRenderPipeline world pass。
8. 把资源 reload 和 GPU generation 接到现有 ShaderReloadBus/client lifecycle。
9. 再实现 RenderEntity 和 CParticle 的触发入口。
10. 最后才接入 VAT、morph 和 skin 的具体 deformation backend。

不要先实现“看起来能画”的独立 vanilla shader，再回头处理 Coo Pipeline 或 Iris 兼容。CooFX 的资源语义、CPU 模拟、编译包和 GPU lifecycle 必须保持分层。

## 25. 相关文件

核心文档：

```text
docs/coofx/architecture.md
docs/coofx/format-v1.md
docs/coofx/schema/coofx-v1.schema.json
docs/coofx/examples/rigid-burst.coofx.json
```

Blender 工具：

```text
tools/blender_coofx/__init__.py
tools/blender_coofx/blender/addon.py
tools/blender_coofx/blender/extract.py
tools/blender_coofx/blender/build.py
tools/blender_coofx/blender/export.py
tools/blender_coofx/blender/scene.py
tools/blender_coofx/core/model.py
tools/blender_coofx/core/validation.py
tools/blender_coofx/core/coordinates.py
tools/blender_coofx/core/vat.py
```

Kotlin runtime：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/asset/
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/playback/
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/render/
common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/adapter/
```

本教程描述的是当前仓库真实存在的工具和 runtime 边界。新增能力后，应同时更新本文、`format-v1.md` 和 `architecture.md`，尤其是支持矩阵、资源布局、shader 接线和客户端生命周期部分。

## 26. 一句话总结

`tools/blender_coofx` 的作用是：把 Blender 中经过约束的模型、材质、刚性动画和发射器设置，校验并导出为 CooFX JSON + 分离 glTF/BIN + PNG 资源，让 Minecraft 侧的 CooFX asset importer、playback、mesh particle runtime 和已接通的 CooRenderPipeline WORLD_PASS GPU backend 使用同一套稳定资产契约。

它不是 Blender 文件读取器，不是完整 Blender 粒子物理模拟器，也不是任意节点图翻译器。

可继续扩展的方向包括：完整 Object/Collection 导出、连续粒子参数导出、Blender clip/loop authoring 控件、官方 Geometry Nodes 组库、VAT 纹理烘焙、morph/skin backend、RenderEntity/CParticle 业务触发封装，以及 Vanilla/Iris/Sodium 实机组合验证。

文档状态：针对当前 CooFX v1 首版实现。
