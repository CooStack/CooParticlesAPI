# RenderEntity 渲染与 Bloom 教程

这份文档面向两类人：

1. 第一次接触这个项目的开发者。
2. 对 shader、后处理、渲染管线几乎没有基础，但希望做出自定义 `RenderEntity`、Bloom、Glow 效果的人。

如果你现在的目标是下面这几种之一，这份文档就是给你准备的：

- 做一个自定义的渲染实体，让服务端生成、客户端显示。
- 做一个会发光的球、法阵、能量体、束流、黑洞、光晕。
- 让效果既能跟随世界位置，又能在帧尾做 bloom / glow。
- 排查“为什么效果不显示”“为什么被水 / 粒子 / 云影响”“为什么 Iris 下不一样”。

这份文档不会假设你已经懂 GLSL。
相反，它会先告诉你“整个系统怎么想”，再告诉你“具体怎么写”。

## 1. 先建立正确心智模型

在当前版本里，`RenderEntity` 不是“一个自己既同步又渲染的黑盒对象”。
它被拆成了几层职责明确的部件：

- `RenderEntity`
  负责服务端权威状态、编解码、同步、客户端镜像回写。
- `RenderEntityRenderer`
  负责真正的渲染生命周期。
- `RenderEntityInstance`
  负责在客户端持有“实体镜像 + renderer + 运行时状态”。
- `ClientRenderEntityManager`
  负责每帧调度 world pass 和 frame-post。
- glow / bloom provider 与公共 renderer
  负责把常见的发光效果转成统一的帧尾任务。

你可以把它想象成：

- `RenderEntity` 是“网络同步的状态对象”。
- `RenderEntityRenderer` 是“这个状态对象该怎么画”。
- `RenderEntityInstance` 是“客户端正在运行的这一个实例”。

### 1.1 一条 RenderEntity 从创建到显示会经过什么

一条 `RenderEntity` 的完整链路可以概括成下面 7 步：

1. 服务端创建实体，设置字段，然后调用 `spawn(world, pos)`。
2. `ServerRenderEntityManager.tick()` 判断哪些玩家能看到它。
3. 服务端向可见玩家发送 `CREATE / TOGGLE / REMOVE` 包。
4. 客户端收到包后，用 codec 解码出实体镜像。
5. 客户端把这个镜像包进 `RenderEntityInstance`。
6. 每帧 world pass 阶段，runtime 调用 `renderLocal(...)`。
7. 每帧 frame-post 阶段，runtime 调用 `collectFrameEffects(...)`，并自动收集 `ScreenGlow` / `PersistentBloom` / `WorldLight` 这类内建 provider。

这 7 步里最容易搞混的是第 6 步和第 7 步。

- `renderLocal(...)`
  表示在世界渲染阶段直接画东西。
- `collectFrameEffects(...)`
  表示在世界画完之后，把一个“帧尾效果任务”提交出去。

这两者的差别，决定了你做出来的是：

- 一个真正画进世界的几何体。
- 还是一个帧尾叠加的 glow / bloom / 屏幕特效。

## 2. 零基础也能懂的渲染前置概念

如果你完全没有 shader 基础，先看这一节。

### 2.1 顶点着色器在做什么

顶点着色器可以理解成：

“把模型的每个顶点，从它自己的局部坐标，变成最终屏幕上的位置。”

它通常负责三件事：

1. 接收模型顶点。
2. 用矩阵把顶点从局部空间变到观察空间。
3. 把结果传给 GPU 后续阶段。

对于一个球体来说，顶点着色器不会决定它亮不亮，它主要决定：

- 这个球在世界哪里。
- 它有多大。
- 它当前在相机面前是什么姿态。

### 2.2 片元着色器在做什么

片元着色器可以理解成：

“对于屏幕上的每一个像素，算出它应该是什么颜色、什么透明度。”

这才是 glow / bloom 外观真正形成的地方。

比如：

- 中心更亮还是边缘更亮。
- 是纯色还是带噪声流动。
- halo 是往外散还是往中心堆。
- alpha 多大，发光强度多大。

### 2.3 什么是 world pass

world pass 就是“跟普通世界几何一起画”的那一段。

如果你在 `renderLocal(...)` 里画一个球，那它更像一个真正存在于世界中的模型：

- 会参与常规的深度测试。
- 会和其他世界内对象一起按当前阶段被绘制。
- 适合实体本体、束流、模型、网格。

### 2.4 什么是 frame-post

frame-post 可以理解成：

“世界主体已经画完了，我现在拿着这一整帧的结果，再做额外处理。”

这类效果适合：

- glow
- bloom
- 屏幕空间 halo
- 基于场景颜色 / 深度的合成效果

它的优点是：

- 更适合做发光和后处理。
- 不需要把一切都塞进 world pass。

它的代价是：

- 你要更清楚自己依赖的是场景颜色、深度，还是只依赖最终帧。
- 某些透明物体和光影环境下，表现会和 world pass 不完全一样。

### 2.5 什么是 Bloom

Bloom 的核心思路不是“把物体画亮”，而是：

1. 先提取高亮区域。
2. 对高亮区域做模糊。
3. 再把模糊结果叠回原场景。

所以 Bloom 更像“外溢的光”。

它和“物体本体发光”不是一回事。

### 2.6 什么是 Glow

Glow 是一个更泛的词，通常表示“看起来在发光”。

在这个项目里，常见有三类：

1. 直接绘制的发光球体
   例如 `PostGlowSphereRenderer`。
2. 屏幕空间 glow
   例如 `ScreenGlowProvider`。
3. 帧尾 blur + composite 的 bloom
   例如 `PersistentBloomContextProvider`。

它们看起来都像“发光”，但实现原理不同，环境兼容性也不同。

## 3. 写自定义 RenderEntity 前，先选路线

这是最重要的一张表。
零基础开发时，不要一上来就自己写 shader。
先判断你到底要哪一类效果。

| 需求 | 建议入口 | 适合原因 | 代价 |
| --- | --- | --- | --- |
| 画一个真正存在于世界里的几何体 | `renderLocal(...)` | 逻辑直接，和普通模型思路接近 | 你要自己处理 shader / buffer / 渲染状态 |
| 画一个带本体、带外扩发光的光球 | `collectFrameEffects(...)` + `PostGlowSphereRenderer` | 已有公共实现，可直接复用，Iris 下也更稳 | 它是“发光球体”方案，不是通用 bloom API |
| 只想做帧尾模糊后的稳定外辉光 | `PersistentBloomContextProvider` | 适合能量核心、法阵、远处亮源 | 依赖场景颜色 / 深度，当前 Iris safe backend 下不会执行 |
| 只想给一个点状亮源加屏幕 halo | `ScreenGlowContextProvider` 或 `ScreenGlowProvider` | 适合小型光源、屏幕空间 aura | 同样依赖场景颜色 / 深度 |

如果你是第一次做：

- 想做“看得见球体本体的光球”，优先用 `PostGlowSphereRenderer`。
- 想做“只有外层 bloom，没有明确几何本体”，用 `PersistentBloomContextProvider`。
- 想做“纯世界模型”，从 `renderLocal(...)` 开始。

## 4. 最简单的自定义 RenderEntity 写法

这一节先讲最适合入门的方案：

- 继承 `AutoRenderEntity`
- 自己实现 `RenderEntityRenderer<T>`
- 使用 `@CooAutoRegister`
- 用 `@CodecField` 自动处理编解码和镜像回写

这样做的好处是：

- 你不用手写 codec。
- 你不用额外再拆一个 renderer 类。
- 代码集中，学习成本最低。

### 4.1 最小骨架

```kotlin
@CooAutoRegister
class MyGlowOrbEntity(
    world: Level? = null,
    pos: Vec3 = Vec3.ZERO
) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<MyGlowOrbEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "yourmod",
            "my_glow_orb"
        )
    }

    @field:CodecField
    var radius: Float = 4.0f

    @field:CodecField
    var intensity: Float = 6.0f

    @field:CodecField
    var haloIntensity: Float = 3.0f

    @field:CodecField
    var glowColor: Vector3f = Vector3f(0.6f, 0.9f, 1.2f)

    override fun getRenderID(): ResourceLocation = ID
}
```

这段代码做了什么：

- `@CooAutoRegister`
  让扫描器自动发现这个类型，并为 codec 注册做准备。
- `AutoRenderEntity`
  自动帮你生成 codec，并在客户端镜像更新时自动回写 `@CodecField` 字段。
- `RenderEntityRenderer<MyGlowOrbEntity>`
  表示这个实体自己就是自己的 renderer。
- 无参构造
  给反射和解码用。
- `ID`
  是这个类型在客户端注册和网络包中的稳定标识。
- `@CodecField`
  表示这些字段需要被同步。

### 4.2 `@CodecField` 自动做了什么，没做什么

`@CodecField` 自动做了两件事：

1. 把字段纳入编解码。
2. 在客户端收到新镜像后，把字段从临时对象复制回当前实体。

但是它没有自动做一件非常重要的事情：

“当你在服务端修改字段时，自动触发同步。”

也就是说，下面这段代码虽然能改值，但不一定会立刻同步到客户端：

```kotlin
entity.intensity = 10.0f
```

如果这个字段变化后你希望客户端马上看到，你还要自己调用：

```kotlin
entity.markDirty()
```

或者：

```kotlin
entity.requestSync()
```

这点非常重要。
很多人以为 `@CodecField` 等于“自动同步”，其实它只负责“字段被编码”和“字段能回写”，不负责“字段变化时主动发包”。

### 4.3 实体要怎么在服务端生成

你必须在服务端世界中生成它。

最简单的方式：

```kotlin
val entity = MyGlowOrbEntity()
entity.radius = 5.0f
entity.intensity = 7.5f
entity.glowColor = Vector3f(1.2f, 0.7f, 0.3f)
entity.spawn(serverLevel, Vec3(x, y, z))
```

`spawn(world, pos)` 底层会做的事很简单：

- 检查 `world` 是否是 `ServerLevel`
- 写入世界和位置
- 把实体交给 `ServerRenderEntityManager`

之后它会由服务端 manager 负责：

- 判断玩家是否在可视范围内
- 发送 `CREATE`
- 在字段变化时发送 `TOGGLE`
- 删除时发送 `REMOVE`

### 4.4 `renderRange` 决定客户端能不能看到

`RenderEntity` 有一个很关键的字段：

```kotlin
entity.renderRange = 256.0
```

如果玩家距离实体超过这个范围，即使你的 renderer 完全正确，客户端也根本拿不到实体镜像。

所以遇到“效果不存在”的第一批排查项应该包括：

- 是否真的在服务端生成了
- 玩家是否与实体在同一个世界
- 是否超出 `renderRange`
- 实体是否已经被 `canceled`

## 5. 最推荐的入门发光球写法：复用 PostGlowSphereRenderer

如果你要做的是“一个本体清晰、还能外扩发光的球”，
最适合从这里开始。

原因很简单：

- 你不用自己写 shader。
- 你不用自己管球体网格。
- 这套实现现在已经从 `test` 提炼成公共 renderer 了。
- 它只依赖 `FINAL_FRAME_POST`，在当前 Vanilla / Iris safe backend 下都更容易稳定工作。

### 5.1 完整示例

```kotlin
@CooAutoRegister
class MyGlowOrbEntity(
    world: Level? = null,
    pos: Vec3 = Vec3.ZERO
) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<MyGlowOrbEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "yourmod",
            "my_glow_orb"
        )
    }

    @field:CodecField
    var radius: Float = 4.0f

    @field:CodecField
    var intensity: Float = 6.0f

    @field:CodecField
    var haloIntensity: Float = 3.2f

    @field:CodecField
    var haloRadiusScale: Float = 1.8f

    @field:CodecField
    var fresnelStrength: Float = 1.3f

    @field:CodecField
    var animationSpeed: Float = 1.0f

    @field:CodecField
    var overbrightClamp: Float = 6.0f

    @field:CodecField
    var glowColor: Vector3f = Vector3f(0.6f, 0.9f, 1.2f)

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<MyGlowOrbEntity>) {
        PostGlowSphereRenderer.initialize()
    }

    override fun collectFrameEffects(
        input: FrameEffectInput<MyGlowOrbEntity>,
        collector: FrameEffectCollector
    ) {
        val entity = input.instance.entity
        PostGlowSphereRenderer.submit(
            collector = collector,
            effectId = ID.toString(),
            sourceInstanceId = entity.uuid.toString(),
            entity = entity,
            frameContext = input.frameContext,
            config = PostGlowSphereConfig(
                radius = entity.radius,
                intensity = entity.intensity,
                haloIntensity = entity.haloIntensity,
                haloRadiusScale = entity.haloRadiusScale,
                fresnelStrength = entity.fresnelStrength,
                animationSpeed = entity.animationSpeed,
                overbrightClamp = entity.overbrightClamp,
                glowColor = Vector3f(entity.glowColor)
            )
        )
    }
}
```

### 5.2 这段代码到底做了什么

`initialize(...)`：

- 只做一件事，确保共享 glow renderer 已经初始化。
- 它会准备球体网格和公共 shader。

`collectFrameEffects(...)`：

- 不直接画球。
- 它把一次“请在帧尾画一个发光球”的请求提交给 runtime。

`PostGlowSphereConfig`：

- 描述这个光球的总样式。
- 包括半径、亮度、halo 强度、颜色、动画速度等。

`PostGlowSphereRenderer.submit(...)`：

- 向 `FrameEffectCollector` 提交一个帧尾任务。
- 真正执行时，会在 `FINAL_FRAME_POST` 阶段把球画到主渲染目标上。

### 5.3 这种方案适合什么，不适合什么

适合：

- 能量球
- 光核
- 法阵核心
- 明确能看见“球体本体”的效果

不适合：

- 你只想要“外层模糊辉光”，不想要任何明确几何本体
- 你要做非常规几何而不是球

如果你只是想做柔和外晕，看下一节的 `PersistentBloom`。

## 6. 如何做真正的 Bloom：PersistentBloomContextProvider

如果你的目标不是“一个发光球”，而是“一个亮源在帧尾向外扩散出柔和模糊的外辉光”，
那么更接近 Bloom 语义的入口是：

- `PersistentBloomContextProvider`

它的基本原理是：

1. 收集 bloom 采样数据。
2. 在帧尾生成 mask。
3. 对 mask 做 blur。
4. 再把 blur 结果叠回场景。

### 6.1 最小示例

```kotlin
@CooAutoRegister
class MyBloomSourceEntity(
    world: Level? = null,
    pos: Vec3 = Vec3.ZERO
) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<MyBloomSourceEntity>,
    PersistentBloomContextProvider {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "yourmod",
            "my_bloom_source"
        )
    }

    @field:CodecField
    var bloomRadius: Float = 4.0f

    @field:CodecField
    var bloomIntensity: Float = 1.8f

    @field:CodecField
    var bloomColor: Vector3f = Vector3f(0.7f, 0.9f, 1.3f)

    override fun getRenderID(): ResourceLocation = ID

    override fun collectPersistentBlooms(
        context: ScreenGlowRenderContext,
        output: MutableList<PersistentBloom>
    ) {
        output.add(
            PersistentBloom(
                position = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
                color = Vector3f(bloomColor),
                radius = bloomRadius,
                intensity = bloomIntensity,
                softness = 0.58f,
                softOcclusionFloor = 0.10f,
                haloRadiusScale = 2.6f,
                brightnessNormalization = 0.9f,
                haloOpacity = 0.45f,
                blurSigma = 4.8f,
                blurRange = 4.2f
            )
        )
    }
}
```

### 6.2 为什么这个接口没有写进 `collectFrameEffects(...)`

因为 `PersistentBloomContextProvider` 是一个内建 provider。

在当前 runtime 里，`RenderEntityInstance.collectFrameEffects(...)` 会自动检查：

- 这个实体是不是 `PersistentBloomContextProvider`

如果是，它会自动把它交给 `ClientPersistentBloomManager`。

所以你不需要自己在 `collectFrameEffects(...)` 里手动再提交一次。

### 6.3 PersistentBloom 的优缺点

优点：

- 真正有 blur + composite 的外溢辉光感
- 适合远处亮源、法阵、能量核心
- 本体可以很小，但 halo 仍然稳定

缺点：

- 依赖 `SCENE_COLOR_COPY`
- 依赖 `SCENE_DEPTH_READ`
- 依赖帧尾 blur 管线

这三个条件意味着它不是所有 backend 都能跑。

## 7. ScreenGlow、PersistentBloom、PostGlowSphereRenderer 到底怎么选

这一节非常重要。

### 7.1 `PostGlowSphereRenderer`

本质：

- 一个“直接绘制到帧尾”的发光球体 renderer。
- 不是 blur bloom，而是一个带本体、带 halo 的后处理球体。

特点：

- 本体清楚
- 有边缘、有层次
- 依赖少
- 当前 Iris safe backend 下也更有机会稳定运行

### 7.2 `PersistentBloom`

本质：

- 一个“先做 mask，再 blur，再 composite”的 bloom 系统。

特点：

- 外晕柔和
- 模糊感强
- 更像真正的 bloom
- 依赖场景颜色和深度

### 7.3 `ScreenGlow`

本质：

- 一个更轻量的屏幕空间 glow 系统。

特点：

- 更适合小型光源
- 主要从屏幕空间观感出发
- 不适合替代实体本体

### 7.4 最简单的选择建议

- 你想“看见球体本体”，用 `PostGlowSphereRenderer`
- 你想“只要外层辉光”，用 `PersistentBloom`
- 你想“给一个点源补屏幕 halo”，用 `ScreenGlow`

## 8. 如果你要自己在 world pass 里绘制几何体

如果你不是要 glow / bloom，而是真正自己画几何体，
那就把主要逻辑放进：

- `renderLocal(...)`

最小骨架长这样：

```kotlin
override fun renderLocal(input: LocalRenderInput<MyEntity>) {
    myShader.useOnContext {
        setMatrix4("projMat", input.projMatrix)
        setMatrix4("viewMat", input.viewMatrix)
        setMatrix4("transMat", input.modelMatrix)
        myBuffer.draw()
    }
}
```

它的含义是：

- `input.modelMatrix`
  是当前实体在世界中的模型矩阵
- `input.viewMatrix`
  是当前相机观察矩阵
- `input.projMatrix`
  是投影矩阵

如果你把一个对象放在 `renderLocal(...)` 里画，
它更像“世界里的真实模型”。

如果你把一个对象放在 `collectFrameEffects(...)` 里提交，
它更像“帧尾叠加的后处理特效”。

## 9. 客户端注册：自动和手动怎么理解

### 9.1 使用 `@CooAutoRegister + AutoRenderEntity`

这是当前最省事的路径。

自动注册会帮你做的事情：

- 扫描到这个 `RenderEntity` 类型
- 把 codec 注册到 `ClientRenderEntityRegistry`

但是它不会自动帮你注册独立 renderer。

### 9.2 如果实体自己就是 renderer

如果你的实体自己实现了：

```kotlin
RenderEntityRenderer<MyEntity>
```

那么客户端在解码后会直接把这个实体当 renderer 使用。

这意味着：

- codec 自动注册后就够了
- 不需要再额外写一个 `rendererFactory`

### 9.3 如果实体和 renderer 分离

那你就需要显式注册 renderer：

```kotlin
ClientRenderEntityRegistry.register(
    id = MyEntity.ID,
    codec = MyEntity().getCodec()
)

ClientRenderEntityRegistry.registerRenderer(MyEntity.ID) {
    MyEntityRenderer()
}
```

如果 codec 注册了但 renderer 没注册，而实体本身又不实现 `RenderEntityRenderer`，
客户端会直接抛异常，而不是静默失败。

## 10. 环境如何影响你的效果

这一节是全文最容易救命的一节。

很多“效果不对”的问题，不是参数错了，而是环境条件根本变了。

### 10.1 世界、距离、可视范围

先看最基础的三件事：

- 玩家和实体是不是在同一个世界
- 玩家是否在 `renderRange` 内
- 实体是不是已经 `canceled`

只要这三者有一个不满足，客户端根本不一定会持有这个实体。

### 10.2 深度与遮挡

你的 glow / bloom 最终能不能正确被地形、方块、实体遮住，取决于你走哪条路径。

#### `renderLocal(...)`

- 依赖当前 world pass 的深度测试
- 更接近普通世界几何

#### `PostGlowSphereRenderer`

- 在帧尾绘制
- 仍然启用了 depth test
- 关闭了 depth write

这意味着它通常比纯屏幕空间 glow 更容易保持“不会直接穿墙”，
同时又不会把深度缓冲本身污染掉。

#### `PersistentBloom` / `ScreenGlow`

- 不是直接画几何
- 它们是读场景深度，再做 mask / composite

因此它们的遮挡正确性强依赖“这一帧的深度纹理到底记录了什么”。

### 10.3 水、玻璃、粒子、云等透明物体

这类对象最容易让人误判为“API 有 bug”。

根本原因是：

- 透明对象不一定像实心方块那样稳定写入深度
- 后处理效果读到的 scene depth，不一定完整代表所有透明层
- 不同 backend、不同渲染顺序、不同光影环境，对透明层处理会不同

这会带来几类典型现象：

- glow 看起来穿过了水
- 粒子和光晕叠加关系不稳定
- 云雾区域里的 glow 对比度突然变差

这不是说系统完全没法处理透明对象，
而是你要接受一个事实：

“纯后处理效果对透明物体的遮挡，天然比直接 world geometry 更脆弱。”

如果你的效果必须尽量稳定地与世界前景交互：

- 优先考虑 `renderLocal(...)` 或 `PostGlowSphereRenderer`
- 不要一开始就只依赖 `PersistentBloom` 或 `ScreenGlow`

### 10.4 Vanilla backend 与 Iris backend 的差异

这是当前项目里最关键的环境差异之一。

#### `VanillaSafeRenderBackend` 的能力

- `SCENE_COLOR_COPY`
- `SCENE_DEPTH_READ`
- `SAFE_WORLD_COMPOSITE`
- `FINAL_FRAME_POST`
- `EARLY_WORLD_HOOK`

#### `IrisSafeRenderBackend` 的能力

- `SAFE_WORLD_COMPOSITE`
- `FINAL_FRAME_POST`

你应该立刻注意到：

当前 `IrisSafeRenderBackend` 没有：

- `SCENE_COLOR_COPY`
- `SCENE_DEPTH_READ`

这会直接影响两类效果：

- `ScreenGlow`
- `PersistentBloom`

因为这两个 manager 的任务提交都声明了必需能力：

- `FINAL_FRAME_POST`
- `SCENE_COLOR_COPY`
- `SCENE_DEPTH_READ`

所以在当前实现下：

- `PostGlowSphereRenderer` 这类只要求 `FINAL_FRAME_POST` 的效果，更适合跨 Vanilla / Iris 使用。
- `PersistentBloom` 和 `ScreenGlow` 在 Vanilla safe backend 下更完整，在 Iris safe backend 下当前不会执行。

这不是参数问题，而是 backend capability 问题。

### 10.5 光影包、色调映射、曝光

即使你的效果“已经渲染出来了”，它在不同光影环境里仍然可能看起来完全不同。

原因包括：

- tone mapping
- 曝光
- 色彩曲线
- 雾和体积光
- 后续光影包自己的 bloom / glow / color grading

这会导致：

- 同一个颜色，在某个光影里发白
- 同一个强度，在某个环境里看起来发灰
- 边缘 halo 明明存在，但因为对比度下降而像是消失了

因此你调 glow 时不要只盯着：

- `intensity`

还要一起看：

- `overbrightClamp`
- 颜色值是否高于 `1.0`
- halo 和 core 的占比
- 当前光影是否自己也在做额外 bloom

### 10.6 距离、FOV、分辨率

世界效果最终都会被投影到屏幕上。

这意味着：

- 距离越远，物体屏幕尺寸越小
- FOV 越大，物体看起来越小
- 分辨率变化会影响屏幕空间效果的表现

对三类效果的影响不一样：

#### 直接世界几何

- 远了就是变小
- 这是正常现象

#### `PostGlowSphereRenderer`

- 它本身不依赖老的 distance-adaptive screen glow 方案
- 但视觉上仍然会因为投影尺寸变化而显得更集中或更分散

#### `PersistentBloom` / `ScreenGlow`

- 因为它们直接与屏幕空间分析强相关
- 距离、FOV、屏幕尺寸对它们更敏感

### 10.7 服务端字段更新与客户端镜像不同步

这类问题的典型现象是：

- 第一次生成有效
- 后来改了颜色 / 半径 / 强度，客户端却没变化

常见原因只有几个：

1. 字段没标 `@CodecField`
2. 字段值改了但没 `markDirty()`
3. 自定义 codec 没把字段编码进去
4. 自己手写了 `loadProfileFromEntity(...)` 但没正确回写字段

如果你用的是 `AutoRenderEntity`，第 3 和第 4 个问题通常会少很多，
但第 2 个问题依然非常常见。

## 11. Bloom / Glow 开发时最常见的错误

### 11.1 效果完全不显示

先按这个顺序排查：

1. 服务端是否真的 `spawn(...)`
2. 玩家是否在 `renderRange` 内
3. `getRenderID()` 是否稳定且正确
4. codec 是否注册
5. 如果不是“实体自带 renderer”，renderer 是否注册
6. 当前 backend 是否支持你需要的 capability

### 11.2 在 Vanilla 有效果，在 Iris 下没效果

先看你是不是用了：

- `PersistentBloomContextProvider`
- `ScreenGlowContextProvider`
- `ScreenGlowProvider`

如果是，先确认你是否依赖：

- `SCENE_COLOR_COPY`
- `SCENE_DEPTH_READ`

当前 `IrisSafeRenderBackend` 不提供这两个能力。

### 11.3 光球看起来够大，但亮度还是往中心缩

这通常是“halo 分布形状”问题，不是“球半径不够大”。

处理方向应该是：

- 增大外层 halo 的 `radiusScale`
- 增大 `haloIntensityScale`
- 开启或调大 `haloCenterSuppress`
- 调大 `haloCenterSuppressRadius`

而不是一味继续放大核心层半径。

### 11.4 外晕太大，把整个场景洗灰

常见原因：

- `intensity` 太大
- `haloIntensity` 太大
- `overbrightClamp` 太高
- 多个加色发光体叠加
- 光影包本身还有额外 bloom

### 11.5 明明是后处理 glow，结果像穿透前景

优先确认：

- 你用的是 `PostGlowSphereRenderer` 还是纯屏幕空间 glow
- 场景里是不是透明层对象
- 当前深度纹理是不是完整包含了你期望的前景

## 12. 给零基础用户的实际开发建议

如果你完全从 0 开始，不要一上来就自己写 GLSL。
建议按这个顺序学习：

1. 先用 `AutoRenderEntity + @CodecField + @CooAutoRegister` 跑通一个最小实体。
2. 再用 `PostGlowSphereRenderer` 做一个能看见本体的光球。
3. 之后再尝试 `PersistentBloomContextProvider`，理解真正的 bloom 语义。
4. 最后再去写自己的 world pass mesh 和自定义 shader。

最稳妥的入门顺序就是：

- 先学同步
- 再学 renderer 生命周期
- 再学帧尾效果
- 最后学 shader 细节

## 13. 你应该优先看的源码

如果你想按“从简单到复杂”的顺序看仓库实现，建议看这些文件：

- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/RenderEntity.kt`
  看同步基类和 `spawn(...)`
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/AutoRenderEntity.kt`
  看 `@CodecField` 自动编解码
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt`
  看 renderer 生命周期接口
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt`
  看客户端实例如何调度 world pass / frame-post / provider
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt`
  看每帧调度入口
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/glow/PostGlowSphereRenderer.kt`
  看复用型发光球 renderer
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/glow/PersistentBloomContextProvider.kt`
  看 bloom provider 接口
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientPersistentBloomManager.kt`
  看 blur + composite 的具体执行链路
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/TestRendererEntity.kt`
  看最小 post-glow 样板
- `common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/TestPersistentGlowSphereEntity.kt`
  看可配置的发光球样板

## 14. 一句话总结

你可以把当前系统记成一句话：

“`RenderEntity` 负责同步，`RenderEntityRenderer` 负责画；world pass 画实体本体，frame-post 做 glow / bloom；如果你是新手，先从 `AutoRenderEntity + PostGlowSphereRenderer` 开始。”
