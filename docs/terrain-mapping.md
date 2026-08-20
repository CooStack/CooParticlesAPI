# CooTerrainMapping 详细使用手册

> 版本基线：Minecraft 1.21.1、Java 21、Fabric / NeoForge、Mojmap + Parchment。本文以当前仓库 `2.5.6-SNAPSHOT` 的源码为准。
>
> `CooTerrainMapping` 的正式含义是“区域化世界空间 terrain overlay”。它把一个 Pipeline 应用到某个动态区域内的真实方块表面，适合做扫描环、能量场、选区高亮、裂纹、溶解边缘、范围提示和带 Bloom 的地形特效。
>
> 它不会修改 `BlockState`，不会替换方块模型，也不会把区域内的方块复制成一份位置列表。

---

## 1. 先看结论

一个 Mapping 由四层组成：

```text
CooTerrainMapping 模板
    = 稳定 ID + CooRenderPipeline<BlockState> + 区域类型 + 默认实例参数

CooTerrainMappingInstance 实例
    = 维度 + instanceId + region + uniforms + 生命周期 + composition

客户端渲染
    = 原版/Sodium terrain 几何 + worldPosition 区域判断 + terrain fragment shader

可选后处理
    = world attachment + mask attachment -> bloom extract -> blur atlas -> composite
```

最重要的使用原则：

1. Pipeline 模板在服务端和客户端都要用相同的 ID 注册。
2. `create`、`updateRegion`、`updateUniforms`、`remove` 都应从服务端调用。
3. Region 只描述几何区域；服务端不扫描方块，客户端也不枚举方块体积。
4. `terrainLayer` 默认使用 `INHERIT`，这样花草、树叶等 cutout 纹理会保留原版 alpha 阈值。
5. 自定义 shader 采样 `BaseSampler` 后，必须在产生效果前执行 `atlasColor.a < CooAlphaCutoff` 的丢弃判断。
6. `updateUniforms` 是完整替换，不是增量合并；每次更新都传入需要继续存在的全部 uniform。
7. 带 fullscreen/post 的 `ADDITIVE` Mapping 只捕获一次地形几何，不要再把同一个 `geometry.color()` 连到 `worldTarget()`。
8. Mapping 的客户端模板缺失时，网络包可以到达，但渲染器找不到 Pipeline，效果会静默消失。

---

## 2. 什么时候用 Mapping

### 2.1 适合的需求

使用 Mapping 的典型描述是：

- “以玩家为中心，半径 32 的地面出现一个扫描环。”
- “技能范围内的真实方块表面变成蓝色能量材质。”
- “一个球形区域从小到大扩张，边缘产生 Bloom。”
- “只对某个玩家显示一个地形选区。”
- “区域中心和半径每 tick 改变，但不想重新生成方块列表。”
- “保留原版地形，只在其表面增加裂纹、边缘光或流动纹理。”

### 2.2 不适合的需求

以下内容不应该用 Terrain Mapping：

- 空中独立球体、光柱、体积雾或不依赖方块表面的模型。
- 需要自己的顶点拓扑、骨骼、模型面或自由空间几何。
- 只绑定一种 Block 永久使用同一材质。
- 需要精确的离散方块集合、每个位置不同参数，且集合变化频繁。

选择建议：

| 需求 | 推荐入口 |
| --- | --- |
| 某种 Block 永久绑定 Pipeline | `CooBlockPipelines.bind(...)` |
| 离散 BlockPos 集合 | `CooTerrainEffectGroup` |
| 动态球/AABB/区域 + 世界空间 shader | `CooTerrainMapping` |
| 自由空间模型/体积/实体效果 | `RenderEntity` 或其他实体域 Pipeline |

不要为了做区域效果临时修改 `BlockState`，也不要为区域中的每个方块创建一个独立 Pipeline。

---

## 3. 工程与依赖

### 3.1 当前项目基线

```text
Minecraft       1.21.1
Java            21
Fabric          Fabric Loader + Fabric API + Fabric Language Kotlin
NeoForge        NeoForge + Kotlin for Forge
Mappings        Mojmap + Parchment
CooParticlesAPI 2.5.6-SNAPSHOT（当前仓库）
```

### 3.2 Gradle 依赖

使用发布版本时，版本号以 Release 为准：

```kotlin
repositories {
    maven("https://nexus.jsdu.cn/repository/maven-public/")
}

dependencies {
    // Fabric 模块
    implementation("cn.coostack:cooparticlesapi-fabric:<version>")

    // NeoForge 模块
    implementation("cn.coostack:cooparticlesapi-neoforge:<version>")
}
```

不要在单 loader 的运行时同时放入两个 loader artifact。多平台项目应让 Fabric 和 NeoForge 各自的 source set 使用对应 artifact。

### 3.3 初始化时机

Mapping 模板不是由 `create` 临时创建的。推荐在 common 初始化阶段构造一次：

```kotlin
internal object MyTerrainMappings {
    val mapping = CooTerrainMappingManager.register(
        id = ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan_ring"),
        pipeline = scanRingPipeline,
        regionType = CooTerrainMappingRegionType.SPHERE,
        defaults = CooTerrainMappingDefaults(
            uniforms = mapOf(
                "RingRadius" to CooUniformValue.FloatValue(4F),
                "RingWidth" to CooUniformValue.FloatValue(0.8F),
                "RingIntensity" to CooUniformValue.FloatValue(1F),
                "RingColor" to CooUniformValue.Vec3Value(0.25F, 0.8F, 1F),
                "RingProgress" to CooUniformValue.FloatValue(1F)
            ),
            priority = 0,
            composition = CooTerrainEffectComposition.ADDITIVE,
            durationTicks = null
        )
    )

    fun ensureRegistered() {
        // 访问 val，确保 object 已初始化。
        mapping
    }
}
```

需要保证：

- 服务端第一次创建实例前，服务端模板已经注册。
- 客户端收到 `PacketTerrainMappingS2C` 前，客户端同 ID 模板已经注册。
- `mappingId`、Pipeline 的资源 ID、shader 资源路径在两端保持一致。
- 注册同一个 ID 两次会抛出异常；不要在每个玩家、每个实例、每个 tick 中重复注册。

Fabric 项目如果使用 API 的自动扫描机制，仍需按项目初始化约定注册自己的扫描包；Mapping 模板本身通常更适合由一个明确的 common 初始化入口调用，而不是通过玩家事件临时注册。

---

## 4. 核心概念

### 4.1 `CooTerrainMapping`：不可变模板

模板描述“这个效果是什么”，不描述“它当前出现在什么位置”：

```kotlin
data class CooTerrainMapping(
    val id: ResourceLocation,
    val pipeline: CooRenderPipeline<BlockState>,
    val regionType: CooTerrainMappingRegionType,
    val defaults: CooTerrainMappingDefaults = CooTerrainMappingDefaults()
)
```

字段含义：

| 字段 | 含义 |
| --- | --- |
| `id` | 网络和运行时使用的稳定 Mapping ID |
| `pipeline` | terrain 域 Pipeline，类型必须是 `CooRenderPipeline<BlockState>` |
| `regionType` | 允许的 Region 类型；当前内置 `SPHERE` |
| `defaults` | 创建实例时复制的默认 uniforms、优先级、合成方式、持续时间 |

模板可以跨世界复用。位置、时间、暂停状态都属于实例，不要把运行时状态塞进模板对象。

### 4.2 `CooTerrainMappingInstance`：一次运行

实例是服务端发布、客户端渲染的不可变快照：

```kotlin
data class CooTerrainMappingInstance(
    val instanceId: ResourceLocation,
    val mappingId: ResourceLocation,
    val dimension: ResourceLocation,
    val region: CooTerrainMappingRegion,
    val uniforms: Map<String, CooUniformValue>,
    val priority: Int,
    val composition: CooTerrainEffectComposition,
    val startedAt: Long,
    val expiresAt: Long?,
    val sequence: Long,
    val revision: Long,
    val pausedAt: Long? = null
)
```

`instanceId` 的唯一性范围是“维度 + ID”，不是整个服务器的单一全局命名空间。推荐使用可读且稳定的 ID，例如：

```kotlin
val instanceId = ResourceLocation.fromNamespaceAndPath(
    "my_mod",
    "spell/${spellId}/terrain"
)
```

同一维度中重复创建同一个 `instanceId` 会失败。要替换实例，应先 `remove`，再 `create`，或者使用一个稳定实例并调用更新方法。

### 4.3 `CooTerrainMappingRegion`：区域 tagged union

当前版本支持：

```kotlin
CooTerrainMappingRegion.Sphere(
    center = Vec3(x, y, z),
    radius = 32.0
)
```

约束：

- `center` 的三个分量必须是有限数。
- `radius` 必须是有限且大于零的数。
- 中心使用绝对世界坐标，不是相对于玩家或相机的坐标。
- 球体成员测试使用三维欧氏距离。
- Region 更新会同步整个 tagged union，不发送 BlockPos 列表。

判定公式等价于：

```text
(dx * dx + dy * dy + dz * dz) <= radius * radius
```

球体也提供用于 section 筛选的包围盒和精确 Sphere-vs-AABB 相交测试。包围盒只是候选范围，最终 shader 仍以世界坐标进行判断。

当前版本还没有公开的圆柱、AABB 或自定义 Region 注册入口。若要扩展新的 wire 类型，需要同时修改编码、解码、类型表和客户端渲染判断，不能只在消费方新增一个 Kotlin 子类。

### 4.4 `CooTerrainMappingDefaults`：模板默认值

```kotlin
data class CooTerrainMappingDefaults(
    val uniforms: Map<String, CooUniformValue> = emptyMap(),
    val priority: Int = 0,
    val composition: CooTerrainEffectComposition = REPLACE,
    val durationTicks: Long? = null
)
```

默认值在 `create` 时复制到实例。实例 builder 可以覆盖它们，但不会改变模板本身。

### 4.5 `CooUniformValue`：可同步的 shader 参数

常用类型：

```kotlin
CooUniformValue.BoolValue(true)
CooUniformValue.IntValue(3)
CooUniformValue.UIntValue(3u)
CooUniformValue.FloatValue(0.75F)
CooUniformValue.DoubleValue(1.0)
CooUniformValue.Vec2Value(x, y)
CooUniformValue.Vec3Value(x, y, z)
CooUniformValue.Vec4Value(x, y, z, w)
CooUniformValue.IVecValue(1, 2, 3)
```

uniform 名称必须非空。Kotlin `Float` 对应 GLSL `float`，不要把 `IntValue` 传给声明为 `float` 的 shader uniform。

Pipeline 中声明的 uniform 默认值与 Mapping 实例的 uniform 合并规则是：

```text
先写入 Pipeline 节点默认值
再写入 mapping.uniforms
同名时 mapping.uniforms 胜出
```

因此可以在 Pipeline 里提供安全默认值，再在每个实例中覆盖。实例更新时，`updateUniforms` 会替换整个 Map，不会自动保留旧 Map 中没有传入的键。

### 4.6 `CooTerrainEffectComposition`：多个 Mapping 的合成

```kotlin
enum class CooTerrainEffectComposition {
    REPLACE,
    ALPHA_OVER,
    ADDITIVE
}
```

#### `REPLACE`

当前 Mapping 成为累计结果的基底。按优先级和序号排序后，后续 `REPLACE` 会被抑制，只保留选中的第一层；`ALPHA_OVER` 和 `ADDITIVE` 仍可按计划参与。

适合：

- 完整替换区域材质。
- 需要让 Mapping 取代原版 terrain 输出的效果。

风险：

- 可能不再保留 vanilla terrain 几何。
- 如果 shader 非效果区域输出错误颜色，会造成黑块或地形消失。

#### `ALPHA_OVER`

在前面结果上进行透明层叠加。非效果区域应保留正确的 base color 和 alpha 语义。

适合：

- 半透明选区。
- 地形上的颜色覆盖、扫描线、淡色遮罩。

#### `ADDITIVE`

按顺序追加加色层。对于带 fullscreen/post 的 Pipeline，terrain 几何只在 capture 阶段绘制一次，核心颜色和 mask 再交给后处理合成。

适合：

- 发光环。
- 能量边缘。
- Bloom mask。
- 不想遮挡原版地形的高亮。

当前程序化 ring 示例使用 `ADDITIVE`：

```kotlin
composition(CooTerrainEffectComposition.ADDITIVE)
```

---

## 5. 构建 Terrain Pipeline

### 5.1 最小的直接 terrain overlay

下面是没有 fullscreen Bloom 的最小结构。它把 world 节点输出直接送到 terrain world target：

```kotlin
private val scanPipeline = CooPipelines.block(
    ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan")
) {
    // 不调用 terrainLayer() 时默认是 INHERIT。
    val geometry = world("geometry") {
        shader(ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan"))
        inputBlockAtlas("BaseSampler")

        uniform("RingRadius", CooUniformValue.FloatValue(4F))
        uniform("RingWidth", CooUniformValue.FloatValue(0.8F))
        uniform("RingIntensity", CooUniformValue.FloatValue(1F))
        uniform("RingColor", CooUniformValue.Vec3Value(0.25F, 0.8F, 1F))
    }

    line(geometry.color(), worldTarget())
}
```

这里的两个 `ResourceLocation` 对应：

```text
assets/my_mod/shaders/core/terrain/scan.fsh
```

`shader(...)` 使用 shader 的逻辑 ID，不要把 `assets/` 或 `shaders/core/` 写进 ID。

### 5.2 `terrainLayer` 的选择

```kotlin
terrainLayer(CooTerrainLayer.INHERIT)       // 推荐默认值
terrainLayer(CooTerrainLayer.SOLID)
terrainLayer(CooTerrainLayer.CUTOUT_MIPPED)
terrainLayer(CooTerrainLayer.CUTOUT)
terrainLayer(CooTerrainLayer.TRANSLUCENT)
```

| 层 | alpha cutoff | 适用场景 |
| --- | ---: | --- |
| `INHERIT` | 由原始 RenderType 决定 | Mapping 最推荐；保持花草/树叶 cutout 语义 |
| `SOLID` | `0.0` | 所有参与纹理都保证完全不透明 |
| `CUTOUT_MIPPED` | `0.1` | 统一使用带 mipmap 的硬裁剪材质 |
| `CUTOUT` | `0.1` | 统一使用不带 mipmap 的硬裁剪材质 |
| `TRANSLUCENT` | `0.0` | 真正的半透明混合材质 |

**不要把包含花草、树叶、铁栏杆等透明纹理的 Mapping 强制设成 `SOLID`。**

在 `SOLID` 下，透明 texel 的 alpha 通常是 `0`，但 additive blend 仍可能把 shader 产生的 RGB 加到屏幕上，于是会看到整张四边形的矩形光。`INHERIT` 会根据 Sodium/vanilla 原始 pass 选择 cutout 层，并将 `CooAlphaCutoff` 设置为 `0.1`。

### 5.3 带 Bloom 的 Pipeline 图

完整的后处理路径可以按以下关系组织：

```text
geometry.color()  -> composite.EffectColor
geometry.mask()   -> bloom_extract.scene
bloom_extract     -> bloom_bsl_atlas.BloomInput
sceneColor()      -> composite.SceneColor
bloom_bsl_atlas   -> composite.BloomAtlas
composite         -> screenTarget()
```

当前仓库中可工作的完整结构：

```kotlin
internal object ScanRingTerrain {
    private val pipeline = CooPipelines.block(
        ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan_ring")
    ) {
        // 当前仓库的 common source set 可以使用该配置。
        // 独立消费方若 Kotlin 可见性不允许直接调用，请使用公开的 post preset，
        // 或在 API 侧暴露对应 facade。
        postInScene()

        val geometry = world("geometry") {
            shader(ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan_ring"))
            inputBlockAtlas("BaseSampler")
            maskOutput()
            outputFormat(CooTextureFormat.RGBA16F)

            uniform("RingRadius", CooUniformValue.FloatValue(4F))
            uniform("RingWidth", CooUniformValue.FloatValue(0.8F))
            uniform("RingIntensity", CooUniformValue.FloatValue(0F))
            uniform("RingColor", CooUniformValue.Vec3Value(0.32F, 0.82F, 1F))
            uniform("RingProgress", CooUniformValue.FloatValue(0F))
        }

        val extract = pass("bloom_extract") {
            fragment(ResourceLocation.fromNamespaceAndPath("my_mod", "post/bloom_bright_extract.fsh"))
            input("scene", format = CooTextureFormat.RGBA16F)
            outputFormat(CooTextureFormat.RGBA16F)
            mipLevels(4)
            uniform("threshold", 0F)
            uniform("softKnee", 0.5F)
            uniform("PremultipliedInput", CooUniformValue.BoolValue(true))
            uniform("Intensity", 3F)
        }

        val bloomAtlas = pass("bloom_bsl_atlas") {
            fragment(ResourceLocation.fromNamespaceAndPath("my_mod", "post/bloom_bsl_atlas.fsh"))
            input("BloomInput", format = CooTextureFormat.RGBA16F, mipLevels = 4)
            outputFormat(CooTextureFormat.RGBA16F)
            uniform("BloomLevels", CooUniformValue.IntValue(3))
        }

        val composite = pass("composite") {
            fragment(ResourceLocation.fromNamespaceAndPath("my_mod", "post/scan_ring_composite.fsh"))
            input("SceneColor")
            input("EffectColor", format = CooTextureFormat.RGBA16F)
            input("BloomAtlas", format = CooTextureFormat.RGBA16F)
            uniform("MipLevels", CooUniformValue.IntValue(3))
            outputFormat(CooTextureFormat.RGBA8)
        }

        line(geometry.color(), composite.input("EffectColor"))
        line(geometry.mask(), extract.input("scene"))
        line(extract.color(), bloomAtlas.input("BloomInput"))
        line(sceneColor(), composite.input("SceneColor"))
        line(bloomAtlas.color(), composite.input("BloomAtlas"))
        line(composite.color(), screenTarget())

        parameter("bloomThreshold", extract, "threshold")
        parameter("bloomSoftKnee", extract, "softKnee")
        parameter("intensity", extract, "Intensity")
        parameter("bloomMipLevels", bloomAtlas, "BloomLevels")
        parameter("bloomMipLevels", composite, "MipLevels")
    }

    val mapping = CooTerrainMappingManager.register(
        ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan_ring"),
        pipeline,
        CooTerrainMappingRegionType.SPHERE
    )
}
```

### 5.4 为什么不能重复连接 `worldTarget`

对于上面的 post pipeline，`geometry.color()` 是核心效果 attachment，随后被作为 `EffectColor` 读取。它已经会在 terrain capture 阶段保存一次。

不要再写：

```kotlin
line(geometry.color(), worldTarget())
```

否则同一份 terrain 核心结果可能同时走直接 world 绘制和 post capture，表现为：

- ring 亮度翻倍；
- terrain geometry 绘制两次；
- FPS 明显下降；
- Iris 下出现重复合成或深度阶段不一致。

带 fullscreen/post 的 `ADDITIVE` Pipeline 应让 composite 节点负责最终屏幕输出：

```kotlin
line(composite.color(), screenTarget())
```

只有没有后处理、确实要直接输出 world 的 Pipeline，才使用 `worldTarget()`。

### 5.5 Pipeline 输入输出契约

常用 builder 方法：

| 方法 | 作用 |
| --- | --- |
| `world(name) {}` | 创建 terrain/实体世界几何节点 |
| `pass(name) {}` | 创建 fullscreen 节点 |
| `shader(id)` | world 节点使用 core shader |
| `fragment(id)` | fullscreen 节点使用 fragment shader |
| `inputBlockAtlas("BaseSampler")` | 绑定 Minecraft block atlas |
| `inputSceneColor("SceneColor")` | 声明场景颜色输入 |
| `inputSceneDepth("SceneDepth")` | 声明最终场景深度输入 |
| `inputSceneDepthNoHand("SceneDepthNoHand")` | 声明 Iris hand 绘制前的场景深度，非 Iris 回退最终场景深度 |
| `inputTerrainDepth("TerrainDepth")` | 声明兼容用 terrain depth，默认可选 |
| `inputTerrainOpaqueDepth("TerrainOpaqueDepth")` | 声明不含实体的 opaque terrain 深度快照 |
| `inputTerrainTranslucentDepthBefore("TerrainTranslucentDepthBefore")` | 声明 translucent terrain 绘制前快照 |
| `inputTerrainTranslucentDepthAfter("TerrainTranslucentDepthAfter")` | 声明 translucent terrain 绘制后快照 |
| `maskOutput()` | world 节点增加独立 mask attachment |
| `outputFormat(format)` | 设置节点 attachment 格式 |
| `mipLevels(levels)` | 设置节点输出 mip 层数 |
| `line(from, to)` | 连接 DAG 中的纹理输入输出 |
| `parameter(name, node, uniform)` | 暴露可复用 Pipeline 参数 |
| `screenTarget()` | 输出到最终屏幕 |
| `worldTarget()` | 输出到 world target |

如果上游输出是 `RGBA16F`，下游输入最好显式声明同样格式：

```kotlin
input("BloomInput", format = CooTextureFormat.RGBA16F, mipLevels = 4)
```

不要为了“看起来能跑”把所有 attachment 都改成 `RGBA8`。核心发光和 Bloom 需要 HDR 范围时，过早量化会导致高亮压扁或条带。

---

## 6. 注册模板与创建实例

### 6.1 最小注册

```kotlin
private val scanPipeline = CooPipelines.block(
    ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan")
) {
    val geometry = world("geometry") {
        shader(ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan"))
        inputBlockAtlas("BaseSampler")
    }
    line(geometry.color(), worldTarget())
}

private val scanMapping = CooTerrainMappingManager.register(
    id = ResourceLocation.fromNamespaceAndPath("my_mod", "terrain/scan"),
    pipeline = scanPipeline,
    regionType = CooTerrainMappingRegionType.SPHERE,
    defaults = CooTerrainMappingDefaults(
        uniforms = mapOf(
            "Strength" to CooUniformValue.FloatValue(1F)
        ),
        priority = 10,
        composition = CooTerrainEffectComposition.ADDITIVE,
        durationTicks = null
    )
)
```

### 6.2 创建一个持久实例

```kotlin
val instance = CooTerrainMappingManager.create(
    level = serverLevel,
    mappingId = scanMapping.id,
    instanceId = ResourceLocation.fromNamespaceAndPath(
        "my_mod",
        "active_scan/${player.uuid}"
    ),
    region = CooTerrainMappingRegion.Sphere(
        center = player.position().add(0.0, 0.5, 0.0),
        radius = 32.0
    )
) {
    uniforms(
        mapOf(
            "Strength" to CooUniformValue.FloatValue(1.2F),
            "Color" to CooUniformValue.Vec3Value(0.2F, 0.75F, 1F)
        )
    )
    priority(20)
    composition(CooTerrainEffectComposition.ADDITIVE)
    duration(null)
}
```

`duration(null)` 表示持久；也可以省略，因为默认继承模板值。

### 6.3 创建一个定时实例

```kotlin
CooTerrainMappingManager.create(
    level = serverLevel,
    mappingId = scanMapping.id,
    instanceId = ResourceLocation.fromNamespaceAndPath("my_mod", "spell/$spellId"),
    region = CooTerrainMappingRegion.Sphere(center, 48.0)
) {
    duration(240L) // 12 秒，20 tick = 1 秒
    composition(CooTerrainEffectComposition.ADDITIVE)
}
```

生命周期由服务端 `gameTime` 驱动。实例到期后由 Mapping manager tick 清除，并发送删除包。

### 6.4 只发给一个玩家

```kotlin
CooTerrainMappingManager.createTo(
    player = serverPlayer,
    mappingId = scanMapping.id,
    instanceId = ResourceLocation.fromNamespaceAndPath(
        "my_mod",
        "private/selection/${serverPlayer.uuid}"
    ),
    region = CooTerrainMappingRegion.Sphere(serverPlayer.position(), 24.0)
) {
    composition(CooTerrainEffectComposition.ALPHA_OVER)
    duration(100L)
}
```

`createTo` 的后续 `updateRegion`、`updateUniforms`、`pause`、`resume`、`remove` 也只会向该实例的接收者发送。接收者限制保存在服务端实例管理器中。

---

## 7. 生命周期 API

### 7.1 更新区域

```kotlin
CooTerrainMappingManager.updateRegion(
    level = serverLevel,
    instanceId = instanceId,
    region = CooTerrainMappingRegion.Sphere(
        center = center,
        radius = nextRadius
    )
)
```

返回 `Boolean`：

- `true`：实例存在并已更新。
- `false`：实例不存在。
- 区域类型不匹配会抛出异常。

Region 更新的特点：

- 发送完整实例快照。
- 不发送 BlockPos 增量。
- 不触发 section 几何重建。
- 客户端下一帧直接使用新 region 进行 section 筛选和片元成员判断。

因此可以每 tick 更新球心或半径，而不需要重建整个 terrain mesh。

### 7.2 更新 uniforms

```kotlin
CooTerrainMappingManager.updateUniforms(
    level = serverLevel,
    instanceId = instanceId,
    uniforms = mapOf(
        "RingRadius" to CooUniformValue.FloatValue(radius),
        "RingWidth" to CooUniformValue.FloatValue(width),
        "RingIntensity" to CooUniformValue.FloatValue(intensity),
        "RingColor" to CooUniformValue.Vec3Value(red, green, blue),
        "RingProgress" to CooUniformValue.FloatValue(progress)
    )
)
```

这是完整替换：

```text
旧 uniforms = {A, B, C}
updateUniforms({A, C})
新 uniforms = {A, C}
B 不会自动保留
```

返回值同样是 `Boolean`。uniform 名称必须在 shader 中声明，并且类型匹配。

### 7.3 暂停和恢复

```kotlin
CooTerrainMappingManager.pause(serverLevel, instanceId)
CooTerrainMappingManager.resume(serverLevel, instanceId)
```

暂停不会删除实例：

- 客户端不把实例加入 active render plan。
- `expiresAt` 不会在暂停期间消耗。
- 恢复时 `startedAt` 和 `expiresAt` 会平移暂停时长。
- 暂停/恢复不需要重新编译 shader program。

重复暂停或恢复会返回 `false`。

### 7.4 删除

```kotlin
val removed = CooTerrainMappingManager.remove(serverLevel, instanceId)
```

删除会发送 tombstone revision。客户端删除后不再绘制该 Mapping，并只标记旧 region 覆盖的 section dirty。

### 7.5 重新同步

玩家登录或换维度时，可以补发当前有效 Mapping：

```kotlin
CooTerrainMappingManager.syncTo(serverPlayer)
```

只补发一个实例：

```kotlin
CooTerrainMappingManager.syncTo(serverPlayer, instanceId)
```

`create` 的普通实例只同步给符合维度的玩家；`createTo` 实例还会检查玩家是否在指定接收者集合中。

### 7.6 查询当前维度实例

```kotlin
val active = CooTerrainMappingManager.active(serverLevel)
```

返回按以下顺序排列的快照：

1. `priority` 降序。
2. `sequence` 降序，后创建的实例优先。
3. `instanceId.toString()` 升序，用于稳定打破平局。

---

## 8. 一个完整的展开-持续-收缩示例

下面的服务端逻辑展示了推荐的动态更新方式：区域变化用 `updateRegion`，shader 参数变化用 `updateUniforms`，而不是重新创建实例。

```kotlin
class ScanSpell(
    private val level: ServerLevel,
    private val center: Vec3,
    private val instanceId: ResourceLocation
) {
    private var startedAt = level.gameTime

    fun start() {
        CooTerrainMappingManager.create(
            level = level,
            mappingId = ScanRingTerrain.mapping.id,
            instanceId = instanceId,
            region = CooTerrainMappingRegion.Sphere(center, 96.0)
        ) {
            priority(10)
            composition(CooTerrainEffectComposition.ADDITIVE)
            duration(240L)
            uniforms(phase(0L).uniforms)
        }
    }

    fun tick() {
        val elapsed = level.gameTime - startedAt
        if (elapsed >= 240L) {
            stop()
            return
        }

        val phase = phase(elapsed)
        CooTerrainMappingManager.updateRegion(
            level,
            instanceId,
            CooTerrainMappingRegion.Sphere(center, phase.regionRadius)
        )
        CooTerrainMappingManager.updateUniforms(level, instanceId, phase.uniforms)
    }

    fun stop() {
        CooTerrainMappingManager.remove(level, instanceId)
    }

    private fun phase(elapsed: Long): Phase {
        return when {
            elapsed < 60L -> {
                val p = elapsed / 60F
                Phase(
                    regionRadius = 32.0 + 64.0 * p,
                    ringRadius = 4F + 68F * p,
                    intensity = 0.25F + 0.75F * p,
                    progress = p
                )
            }
            elapsed < 180L -> {
                val p = (elapsed - 60L) / 120F
                Phase(
                    regionRadius = 96.0,
                    ringRadius = 18F + 70F * p,
                    intensity = 1F,
                    progress = p
                )
            }
            else -> {
                val p = (elapsed - 180L) / 60F
                Phase(
                    regionRadius = 96.0,
                    ringRadius = 88F - 80F * p,
                    intensity = 1F - p,
                    progress = 1F - p
                )
            }
        }
    }

    private data class Phase(
        val regionRadius: Double,
        val ringRadius: Float,
        val intensity: Float,
        val progress: Float
    ) {
        val uniforms: Map<String, CooUniformValue> = mapOf(
            "RingRadius" to CooUniformValue.FloatValue(ringRadius),
            "RingWidth" to CooUniformValue.FloatValue(0.8F),
            "RingIntensity" to CooUniformValue.FloatValue(intensity),
            "RingColor" to CooUniformValue.Vec3Value(0.32F, 0.82F, 1F),
            "RingProgress" to CooUniformValue.FloatValue(progress)
        )
    }
}
```

注意：上例中的 `duration(240L)`、`elapsed` 和 shader 内的 `CooMappingProgress` 是三个相关但不同的概念：

- `duration` 决定实例何时过期。
- `elapsed` 是业务动画自己计算的 tick 数。
- `CooMappingProgress` 是渲染器根据 `startedAt / expiresAt` 自动计算的生命周期进度，范围为 `0..1`。

如果实例是持久实例（`duration = null`），自动的 `CooMappingProgress` 会是 `0`。这时需要使用自定义的 `RingProgress` 或其他 uniform 驱动动画。

---

## 9. Terrain Shader 编写规范

### 9.1 顶点输入和传递变量

默认 terrain vertex shader 会提供：

```glsl
in vec3 Position;
in vec4 Color;
in vec2 BaseUV;
in ivec2 EffectUV;
in ivec2 LightUV;
in vec3 Normal;
```

并传递给 fragment shader：

```glsl
in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec3 worldPosition;
in vec3 worldNormal;
```

其中：

- `baseUv` 是方块 atlas UV。
- `vertexColor` 已包含顶点颜色和 lightmap 光照。
- `worldPosition` 是世界空间位置。
- `worldNormal` 是归一化法线。
- `vertexDistance` 用于 vanilla fog。

不要把 `baseUv` 当成自己的世界 UV。需要独立的效果 UV 时，可以使用 Pipeline 的 `effectUv(CooEffectUvMode.WORLD_XZ)`、`WORLD_XY`、`WORLD_YZ` 或在 shader 中使用 `worldPosition` 自行计算。

### 9.2 当前 Mapping 内置 uniform

渲染器会在 terrain shader apply 阶段尝试写入这些 uniform：

| Uniform | GLSL 类型 | 语义 |
| --- | --- | --- |
| `CameraPosition` | `vec3` | 当前相机世界坐标 |
| `CooEffectUvCameraPosition` | `vec3` | 周期化的效果 UV 相机坐标 |
| `CooEffectUvMode` | `int` | Effect UV 模式 |
| `CooGameTime` | `float` | 当前游戏时间，取模 65536 |
| `CooAlphaCutoff` | `float` | 原始 terrain layer 的 alpha 丢弃阈值 |
| `CooMappingRegion` | `vec4` | Sphere 的 xyz 中心和 w 半径 |
| `CooMappingProgress` | `float` | 根据实例生命周期计算的 `0..1` 进度 |
| `CooMappingComposition` | `int` | `REPLACE=0`、`ALPHA_OVER=1`、`ADDITIVE=2` |
| `CooIrisComposite` | `int` | Iris 场景颜色合成输入是否可用，`0/1` |
| `ScreenSize` | `vec2` | 当前 terrain scene attachment 尺寸 |
| `FogStart` / `FogEnd` | `float` | 原版雾参数 |
| `FogColor` | `vec4` | 原版雾颜色 |
| `ColorModulator` | `vec4` | 原版颜色调制 |

未声明的 uniform 不会强制报错；但 shader 需要的自定义 uniform 必须在 Pipeline 节点声明默认值，实例才能覆盖它。

### 9.3 正确的 cutout 处理顺序

这是所有会采样方块 atlas 的 Mapping shader 都应遵循的核心顺序：

```glsl
vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;

// 必须在产生任何 additive RGB 之前执行。
if (atlasColor.a < CooAlphaCutoff) {
    discard;
}

// 之后才计算 ring、边缘光、颜色叠加和 mask。
```

不要这样写：

```glsl
vec3 emissive = RingColor * ring * RingIntensity;
FragColor = vec4(emissive, atlasColor.a);
// 最后才检查 alpha，透明 texel 已经可能产生 additive RGB。
```

也不要在包含植物/树叶的 Pipeline 上强制：

```kotlin
terrainLayer(CooTerrainLayer.SOLID)
```

除非你已经确定所有参与 terrain 的 atlas texel 都是完全不透明的。

### 9.4 推荐的 ring fragment 结构

```glsl
#version 330 core

#coo_import <terrain_light_fog.glsl>

uniform sampler2D BaseSampler;
uniform vec4 ColorModulator;
uniform vec4 CooMappingRegion;
uniform float CooMappingProgress;
uniform int CooMappingComposition;
uniform float CooAlphaCutoff;
uniform float RingRadius;
uniform float RingWidth;
uniform float RingIntensity;
uniform vec3 RingColor;
uniform float RingProgress;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec3 worldPosition;
in vec3 worldNormal;

layout(location = 0) out vec4 FragColor;
layout(location = 1) out vec4 MaskColor;

float ringBand(float distanceValue, float width) {
    return 1.0 - smoothstep(width, width * 2.2, abs(distanceValue));
}

void main() {
    vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
    if (atlasColor.a < CooAlphaCutoff) {
        discard;
    }

    float distanceToCenter = distance(worldPosition, CooMappingRegion.xyz);
    float regionMask = 1.0 - smoothstep(
        CooMappingRegion.w,
        CooMappingRegion.w + max(RingWidth, 0.01),
        distanceToCenter
    );
    float ring = ringBand(
        distanceToCenter - RingRadius,
        max(RingWidth, 0.01)
    );
    ring *= regionMask;
    ring *= clamp(CooMappingProgress, 0.0, 1.0);
    ring *= clamp(RingProgress, 0.0, 1.0);

    if (CooMappingComposition == 2 && ring <= 0.0) {
        discard;
    }

    float facing = mix(0.72, 1.0, abs(normalize(worldNormal).y));
    vec3 emissive = RingColor * ring * RingIntensity * facing;
    vec3 outputColor = atlasColor.rgb + emissive;
    float outputAlpha = atlasColor.a;

    if (CooMappingComposition != 0) {
        outputAlpha *= clamp(ring, 0.0, 1.0);
    }

    FragColor = vec4(outputColor, outputAlpha);
    MaskColor = vec4(emissive, atlasColor.a * clamp(ring, 0.0, 1.0));
}
```

实际工程中还要按需求加入 vanilla fog 和 Iris SceneColor 分支；上面重点展示 alpha、区域和两 attachment 的契约。

### 9.5 区域 mask 的边界

不要把区域 mask 写成只允许半径 `0.88R..R` 的形式：

```glsl
// 错误示例：环只能在球体最外侧 12% 出现。
1.0 - smoothstep(R * 0.88, R, distanceToCenter)
```

正确思路是让区域边界只负责在 `R` 外淡出，而 ring 自己决定距离中心的半径：

```glsl
1.0 - smoothstep(
    CooMappingRegion.w,
    CooMappingRegion.w + max(RingWidth, 0.01),
    distanceToCenter
)
```

### 9.6 保留原版颜色还是只输出效果

根据 composition 选择不同输出策略：

- `REPLACE`：非效果区域仍需要有正确的 base color，除非你明确想隐藏 vanilla terrain。
- `ALPHA_OVER`：通常保留 base color，并将效果 alpha 乘上 ring mask。
- `ADDITIVE`：可以在 ring 外 `discard`，只把 emissive RGB 和 mask 写入 capture attachment；原版 terrain 由 vanilla draw 保留。

深度输入由 Pipeline sampler 的可用性决定。required 的 terrain snapshot 缺失时，post pass 会被跳过；不要把未绑定的 sampler 当作有效深度，也不要重新引入共面 terrain overlay。

---

## 10. Bloom / 后处理的正确连接

### 10.1 两种输出的含义

带 `maskOutput()` 的 world 节点至少有：

```text
geometry.color()  -> 核心颜色 attachment
geometry.mask()   -> 独立 mask attachment
```

推荐语义：

- `color()`：用于直接核心效果，例如 ring 的清晰边缘。
- `mask()`：用于亮部提取和 Bloom，不要把整张原版 terrain 颜色写进去。

这样可以避免 Bloom 把普通地形纹理也当成亮部。

### 10.2 `EffectColor` 为什么要单独保留

如果 composite 只读取 Bloom atlas，ring 的直接核心会被模糊、压缩或衰减。推荐在 composite 中保留：

```glsl
uniform sampler2D EffectColor;
uniform sampler2D BloomAtlas;

vec3 effectCore = max(texture(EffectColor, screen_uv).rgb, vec3(0.0));
vec3 hdrBloom = reconstructBloom();
vec3 result = compositeBloom(sceneColor, hdrBloom) + effectCore;
```

当前程序化 Mapping 示例就是先合成 Bloom，再把 `effectCore` 直接加回结果。

### 10.3 Bloom 级数和性能

当前示例配置：

```kotlin
bloom_extract.mipLevels(4)
bloom_bsl_atlas.input("BloomInput", mipLevels = 4)
BloomLevels = 3
MipLevels = 3
```

级数越高，模糊范围和 attachment 成本越大。建议：

- 先用 3 个 Bloom level 验证效果。
- 只有需要更远光晕时再增加。
- extract、atlas input 和 composite 的级数保持契约一致。
- 不要只改 composite 的 `MipLevels`，却不增加上游实际分配的 mip。
- 不要为很小的 ring 使用 12 级 atlas。

---

## 11. Vanilla、Sodium 和 Iris 的执行差异

### 11.1 Vanilla terrain

Vanilla 路径在 terrain quad 编译/绘制时解析 Mapping overlay RenderType。Mapping 使用原版 terrain quad 作为候选几何，片元 shader 再用 `worldPosition` 判断是否处于区域内。

因此：

- 不需要服务端枚举区域内的方块。
- 不需要客户端查询 Heightmap。
- Region 变化不需要重新生成 BlockPos 集合。
- 区域外片元应该尽早 discard 或输出正确的 base color。

### 11.2 Sodium

Sodium 路径会为 overlay 捕获 terrain quad，并按当前 Mapping region 与 section AABB 的相交关系过滤可见 section。

当前实现使用：

```text
先做 section 候选筛选
再做 shader worldPosition 成员判断
```

球体使用精确 Sphere-vs-AABB 测试，不只是粗略包围盒判断。

`CooTerrainMapping` 的实例增删、composition/priority/暂停状态等 topology 变化，只会将旧/新 region 覆盖的 section 标记为 dirty；不会对整个世界调用 `LevelRenderer.allChanged()`。`updateRegion` 和 `updateUniforms` 只更新运行时快照。

### 11.3 Iris / shader pack

Iris 的场景颜色和深度 attachment 可用性受当前渲染阶段和 shader pack 影响。shader 应该使用显式状态：

```glsl
if (CooIrisComposite != 0) {
    baseColor = texture(SceneColor, screenUv).rgb;
}
```

需要 SceneColor 时，Pipeline 必须声明对应输入：

```kotlin
inputSceneColor("SceneColor", optional = true)
```

需要 terrain-only 深度快照时，Pipeline 应声明实际 opaque/translucent 输入；不要再把最终场景深度当作 terrain 深度：

```kotlin
inputSceneDepth("SceneDepth")
inputSceneDepthNoHand("SceneDepthNoHand")
inputTerrainOpaqueDepth("TerrainOpaqueDepth")
inputTerrainTranslucentDepthBefore("TerrainTranslucentDepthBefore")
inputTerrainTranslucentDepthAfter("TerrainTranslucentDepthAfter")
```

`TerrainOpaqueDepth` 在实体和 RenderEntity world pass 前捕获，`TerrainTranslucentDepthBefore/After` 包围半透明 terrain 层。`SceneDepthNoHand` 在 Iris 下来自 `getDepthTextureNoHand()`，非 Iris 回退 `SceneDepth`；shader 只有在最终深度与手前深度一致时才修改像素，可排除通常写入深度的第三人称实体、Coo RenderEntity 和第一人称手。标准 Iris hand path 会在 `beginHand()` 前保存 no-hand depth；若自定义 hand 材质改变颜色但完全不写深度，Iris 公开的 depth 输入无法单独生成像素级 hand mask，该材质还需提供独立 mask。

对于旧的通用 terrain depth 输入，可以继续声明：

```kotlin
inputTerrainDepth("TerrainDepth", optional = true)
```

但它只代表 backend 能提供的通用 terrain depth，不足以区分实体、半透明 terrain 和 hand。需要精确分类时使用上面的三个 terrain 快照。

不要：

- 把不可用的 sampler 当成有效深度。
- 因为 depth 不可用而将整个效果设为透明。
- 在 terrain overlay 中写入 terrain depth。
- 同时直接 world 绘制和 post capture 同一份效果。

对于 shader pack 可能改写 terrain 顶点的效果（例如 Iris/BSL 移动草、改变植被摆动位置），不要使用会重放 vanilla/Sodium terrain 顶点的 world overlay。应使用只含 fullscreen 节点的 `screenOnly()` Mapping：API 会直接把活动 Mapping 提交到 post graph，使用最终 SceneColor 和深度重建世界坐标。这样效果跟随 shader pack 最终画面中的草，而不是跟随未经光影改写的原始 section 顶点，也不会参与共面 depth test。

```kotlin
val pipeline = CooPipelines.block(id("terrain/domain_screen")) {
    postInScene()
    screenOnly()
    val composite = pass("domain_screen") {
        fragment(id("post/domain_screen.fsh"))
        inputSceneColor("SceneColor")
        inputSceneDepth("SceneDepth")
        inputSceneDepthNoHand("SceneDepthNoHand")
        inputTerrainOpaqueDepth("TerrainOpaqueDepth")
        inputTerrainTranslucentDepthBefore("TerrainTranslucentDepthBefore")
        inputTerrainTranslucentDepthAfter("TerrainTranslucentDepthAfter")
        uniform("CooMappingRegion", CooUniformValue.Vec4Value(0F, 0F, 0F, 0F))
        uniform("TerrainDiffusionProgress", CooUniformValue.FloatValue(0F))
    }
    line(composite.color(), screenTarget())
}
```

`screenOnly()` 适合整体压暗、染色、径向扩散和屏幕空间遮罩；需要真实 block atlas、面法线、cutout alpha 或只对指定 terrain quad 输出的图案，仍使用普通 world terrain Mapping。screen post 没有 block atlas 的 alpha 信息。Iris 下 `SceneDepth` 是最终场景深度，`SceneDepthNoHand` 是手部绘制前的深度，三个 terrain snapshot 则分别描述 opaque terrain 和 translucent terrain 的绘制边界。shader 应同时检查 terrain snapshot 与最终深度，且要求最终深度未在 hand 阶段改变，借此排除前景实体、Coo RenderEntity 和第一人称手。`postInScene()` 的 scene-post 执行顺序是 Terrain Mapping 先合成、RenderEntity scene-post 后合成；RenderEntity 因此不会被 Terrain Mapping 覆盖。深度纹理不可用时，required 输入会跳过该 pass，不能重新引入共面 terrain overlay。

Iris 兼容性必须用实际安装的 shader pack 进行客户端验证；Kotlin 编译通过不能证明 shader pack 下的视觉结果正确。

---

## 12. 性能建议

### 12.1 影响性能的主要因素

Terrain Mapping 的成本主要来自：

1. 参与 overlay 的 terrain quad 数量。
2. active Mapping 数量。
3. 每个 Mapping 的 composition 层数。
4. 是否需要 post capture。
5. Bloom attachment 的分辨率、格式和 mip 数量。
6. shader 中的纹理采样和复杂数学。
7. 是否重复绘制同一份 geometry。

### 12.2 推荐做法

- 一个效果共用一个 Pipeline 和一组合理的 uniforms。
- 尽量合并同阶段的颜色、边缘和 mask 逻辑。
- 让 region 尽可能贴合实际效果，不要无意义地使用半径 256 或 512。
- 先降低 Bloom level，再考虑减少画面分辨率。
- `RGBA16F` 只用于确实需要 HDR 的 attachment。
- `updateUniforms` 不必每个 render frame 调用；大多数动画每 tick 一次足够。
- 对于只显示给一个玩家的效果使用 `createTo`，避免不必要的网络同步和其他客户端 draw。
- 结束实例后及时 `remove`，不要长期保留强度为零的实例。
- 多个 Mapping 需要相同 shader 时，优先用 uniform 控制，而不是复制 Pipeline。
- opaque/translucent terrain depth 快照只会在当前帧存在声明这些输入的 screen-only Mapping 时捕获；普通 block/world Pipeline 不会承担三次全屏 depth blit。

### 12.3 不要用这些方式“优化”

以下做法很容易重新引入之前的 bug：

```kotlin
// 可能破坏花草/树叶 cutout 的错误优化
terrainLayer(CooTerrainLayer.SOLID)
```

```kotlin
// 可能导致 additive/post 重复绘制的错误连接
line(geometry.color(), worldTarget())
line(geometry.color(), composite.input("EffectColor"))
```

```kotlin
// 不必要的重建：区域变化本身不需要重建 section geometry
remove(level, id)
create(level, id, id, newRegion)
```

正确方式是保留实例并调用：

```kotlin
updateRegion(level, id, newRegion)
updateUniforms(level, id, newUniforms)
```

---

## 13. 常见问题排查

### 13.1 完全没有效果，但 FPS 下降

检查顺序：

1. 客户端是否注册了相同的 `mappingId`。
2. Pipeline 是否真的包含 world 节点和 terrain shader。
3. shader 资源路径是否对应 `assets/<namespace>/shaders/core/...`。
4. 自定义 uniform 名称是否和 GLSL 完全一致。
5. `ADDITIVE` shader 的 `RingIntensity`、`RingProgress` 是否为零。
6. Pipeline 默认值是否被实例 Map 覆盖成了零。
7. 是否使用了 `CooMappingRegion` 的绝对世界坐标。
8. 是否把效果颜色只写入了 mask，却没有接入 composite。

`mapping.uniforms` 现在会在 Pipeline 默认 uniform 写入后覆盖同名默认值。若看到 shader 有 draw 成本但没有颜色，优先打印最终实例 uniform Map。

### 13.2 整张花草/树叶矩形发光

根因通常是：

- pipeline 强制使用 `SOLID`；
- shader 没有检查 `CooAlphaCutoff`；
- shader 在 alpha discard 前已经计算并输出了 additive RGB。

修复：

```kotlin
// 删除 SOLID，使用默认 INHERIT
// 或明确使用 CUTOUT / CUTOUT_MIPPED
```

并在 shader 中：

```glsl
vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
if (atlasColor.a < CooAlphaCutoff) discard;
```

### 13.3 Ring 出现在很远的位置

确认中心是不是用了准星命中点：

```kotlin
// 这是远处方块的位置，不一定是玩家脚下
player.pick(96.0, 0F, false).location
```

如果需求是玩家中心，应使用：

```kotlin
player.position().add(0.0, 0.5, 0.0)
```

### 13.4 Ring 很淡或只剩 Bloom

常见原因：

- composite 只读取 Bloom，没有读取 `EffectColor`。
- 直接核心被 `exp(-bloom * factor)` 一起衰减。
- extract threshold 太高。
- `RingIntensity` 或 `Intensity` 太低。
- `RGBA8` 过早量化了核心 attachment。

推荐：

```glsl
vec3 bloomResult = compositeHdrBloom(scene.rgb, bloom.rgb);
vec3 result = bloomResult + effectCore;
```

### 13.5 画面变黑或 vanilla 地形消失

检查：

- `REPLACE` 是否被误用。
- shader 非效果区域是否仍输出 `baseColor`。
- `SceneColor` 是否在 Pipeline 中声明并正确连线。
- composite 是否输出到 `screenTarget()`。
- 是否把一个空 attachment 当作 SceneColor。
- 是否在 Iris 下无条件采样不可用的深度/场景纹理。

### 13.6 亮度翻倍、重复地形、FPS 下降

检查是否同时存在：

```kotlin
line(geometry.color(), worldTarget())
line(geometry.color(), composite.input("EffectColor"))
```

带 post 的 additive Mapping 应只在 capture 阶段重放一次。删除 direct world 输出，确保 geometry color 只进入后处理 composite。

### 13.7 开启效果时整片区块闪烁

不要在 Mapping topology 改变时无条件调用：

```kotlin
levelRenderer.allChanged()
```

当前实现会记录旧/新 region 并对相关 section 调用 `setSectionDirty`。如果仍出现大范围闪烁，检查：

- 是否有其他模块同时调用 `allChanged()`。
- 是否在业务代码中循环 remove/create 实例。
- region 半径是否大到覆盖了整个可见距离。
- Sodium 是否正在同时处理其他全局 chunk rebuild。

### 13.8 只有部分玩家看得到

对照 API：

- `create(level, ...)`：发送给该 `ServerLevel` 的普通接收者。
- `createTo(player, ...)`：只发送给指定玩家。
- `syncTo(player)`：按当前维度补发仍有效实例。
- `syncTo(player, instanceId)`：补发单个实例。

同时确认实例的 `dimension` 与玩家当前维度一致。

### 13.9 `create` 抛出异常

| 异常情形 | 原因 |
| --- | --- |
| `Terrain mapping is not registered` | 模板注册顺序错误或 ID 不一致 |
| `instance already exists` | 同维度重复使用 `instanceId` |
| `does not accept region type` | Region 类型与模板 `regionType` 不匹配 |
| `duration ... greater than zero` | 传入了零或负数 duration |
| `uniform name must not be blank` | Map 中存在空白键 |

### 13.10 改了 uniform 但画面不变

检查三件事：

1. GLSL 声明名称和 Map 键完全相同，大小写也相同。
2. GLSL 类型和 `CooUniformValue` 类型相同。
3. `updateUniforms` 的 Map 中是否包含其他仍需使用的 uniform。

例如 GLSL：

```glsl
uniform vec3 RingColor;
```

Kotlin：

```kotlin
"RingColor" to CooUniformValue.Vec3Value(0.3F, 0.8F, 1F)
```

---

## 14. 网络和版本兼容

Mapping 使用专用 `PacketTerrainMappingS2C`：

- `REPLACE`：完整实例快照，包括 region、生命周期、composition 和 uniforms。
- `UPDATE_UNIFORMS`：替换完整 uniform 集合。
- `REMOVE`：删除实例并携带 revision tombstone。

Region 使用版本化 tagged union：

```text
wire version
region type id
region-specific fields
```

当前 Sphere 的字段为：

```text
center.x
center.y
center.z
radius
```

协议不会发送：

- `BlockPos` 列表。
- 非空气方块列表。
- Heightmap 结果。
- 客户端生成的 mesh。

### 14.1 revision 和乱序包

客户端 registry 会拒绝同一 `(dimension, instanceId)` 上 revision 不递增的更新。业务代码不需要手动维护 revision；只要通过 `CooTerrainMappingManager` 更新即可。

### 14.2 模板版本必须同步

当前客户端不是收到 Pipeline ID 后动态下载 Pipeline。它会根据 `mappingId` 找到本地已注册模板和 Pipeline。因此发布新版本时，服务端和客户端必须同时拥有：

- 相同的 Mapping ID。
- 相同的 region 类型。
- 兼容的 shader 资源。
- 兼容的自定义 uniform 名称和类型。

只更新服务端而不更新客户端，会导致实例同步成功但渲染器无法找到 Pipeline。

---

## 15. 测试清单

### 15.1 代码级测试

至少覆盖：

- 注册相同 Mapping ID 会拒绝。
- Region 类型不匹配会拒绝。
- Sphere 中心和半径的边界校验。
- `updateUniforms` 是完整替换。
- `updateRegion` 不触发 section geometry rebuild。
- `REPLACE` 的选择和 priority/sequence 顺序。
- `CooAlphaCutoff` 对 cutout layer 为 `0.1`。
- world color、mask、extract、blur、composite 的 DAG 连线完整。
- post pipeline 不同时输出 direct world 和 capture duplicate。

### 15.2 客户端手工测试

每次修改 terrain shader 后，至少测试：

1. 原版 renderer，无 shader pack。
2. Sodium，无 Iris。
3. Iris + BSL 或项目实际支持的 shader pack。
4. 普通石头、泥土等 solid 方块。
5. 花草、树叶、铁栏杆等 cutout 方块。
6. 玻璃和其他 translucent 方块。
7. 地下、地表、不同高度和不同视距。
8. Mapping 创建、更新、暂停、恢复、删除。
9. 多玩家 `create` 与单玩家 `createTo`。
10. 开启/关闭时是否只刷新局部 section，是否有整片闪烁。

### 15.3 推荐 Gradle 命令

```text
gradle :common:test --no-parallel --quiet
gradle :fabric:test --no-parallel --quiet
gradle :fabric:assemble --no-parallel --quiet
gradle :neoforge:assemble --no-parallel --quiet
git diff --check
```

视觉结果必须在实际 Minecraft 客户端中确认。Gradle 测试只能证明源码契约和编译正确，不能证明 Iris shader pack 下没有闪烁。

---

## 16. 当前仓库示例和实现入口

完整的程序化 Sphere Mapping 示例：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingTerrain.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingBlockTestOption.kt
common/src/main/resources/assets/cooparticlesapi/shaders/pipeline/vertexes/procedural_mapping_screen.vsh
common/src/main/resources/assets/cooparticlesapi/shaders/post/procedural_mapping_screen.fsh
```

核心运行时：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMapping.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingInstance.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingManager.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegistry.kt
common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt
common/src/main/java/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainRenderStateShard.java
```

协议入口：

```text
common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/PacketTerrainMappingS2C.kt
```

测试入口：

```text
common/src/test/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingContractTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegionTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineContractTest.kt
common/src/test/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingBlockTestOptionContractTest.kt
fabric/src/test/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/ProceduralTerrainMappingShaderCompileTest.kt
```

---

## 17. API 速查表

### 模板

```kotlin
CooTerrainMappingManager.register(mapping)
CooTerrainMappingManager.register(id, pipeline, regionType, defaults)
```

### 实例

```kotlin
CooTerrainMappingManager.create(level, mappingId, instanceId, region) { }
CooTerrainMappingManager.createTo(player, mappingId, instanceId, region) { }
CooTerrainMappingManager.remove(level, instanceId)
```

### 实例更新

```kotlin
CooTerrainMappingManager.updateRegion(level, instanceId, region)
CooTerrainMappingManager.updateUniforms(level, instanceId, uniforms)
CooTerrainMappingManager.pause(level, instanceId)
CooTerrainMappingManager.resume(level, instanceId)
```

### 查询与同步

```kotlin
CooTerrainMappingManager.active(level)
CooTerrainMappingManager.syncTo(player)
CooTerrainMappingManager.syncTo(player, instanceId)
```

### Region

```kotlin
CooTerrainMappingRegion.Sphere(center, radius)
```

### Composition

```kotlin
CooTerrainEffectComposition.REPLACE
CooTerrainEffectComposition.ALPHA_OVER
CooTerrainEffectComposition.ADDITIVE
```

### Layer

```kotlin
CooTerrainLayer.INHERIT
CooTerrainLayer.SOLID
CooTerrainLayer.CUTOUT_MIPPED
CooTerrainLayer.CUTOUT
CooTerrainLayer.TRANSLUCENT
```

---

## 18. 最终检查表

提交一个 Terrain Mapping 功能前，逐项确认：

- [ ] 模板只注册一次。
- [ ] 服务端和客户端 Mapping ID 一致。
- [ ] Pipeline 是 `CooRenderPipeline<BlockState>`。
- [ ] Region 类型和模板声明一致。
- [ ] `instanceId` 在目标维度内唯一。
- [ ] duration 为 `null` 或正数。
- [ ] `updateUniforms` 每次传完整 Map。
- [ ] Pipeline 默认 uniform 和实例 uniform 类型匹配。
- [ ] 包含花草/树叶时使用 `INHERIT` 或明确 cutout layer。
- [ ] shader 在任何效果 RGB 输出前执行 alpha cutoff。
- [ ] `BaseSampler` 已声明并连接到 block atlas。
- [ ] `maskOutput()` 与 GLSL `layout(location = 1)` 对应。
- [ ] post pipeline 的 `EffectColor`、mask、SceneColor 和 Bloom 输入均已连线。
- [ ] additive post pipeline 没有重复连接 `worldTarget()`。
- [ ] Bloom mip allocation 与采样级数一致。
- [ ] Iris 下声明并正确使用 SceneColor、SceneDepth、SceneDepthNoHand 和 terrain-only depth snapshots。
- [ ] 创建、更新、暂停、恢复、删除均在服务端执行。
- [ ] vanilla、Sodium、Iris/BSL、solid、cutout、translucent 都完成实际客户端验证。
- [ ] 没有用全局 `allChanged()` 处理普通 Mapping topology 变化。

如果这些检查全部通过，Mapping 通常就具备正确的网络生命周期、透明纹理语义、vanilla/Sodium terrain 兼容和可控的后处理成本。
