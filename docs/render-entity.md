# RenderEntity 快速上手

这份文档给“会写代码，但没有 shader 基础”的人。

先记住 3 句话：

- `renderLocal(...)` = 像普通世界模型那样直接画
- `collectFrameEffects(...)` = 场景画完后再叠一层效果
- bloom = 先提亮，再模糊，再叠回去

当前 V2 模型也只要记一件事：

- `RenderEntity` 负责同步
- 客户端真正跑起来的是 `RenderEntityInstance`

## 1. 先选路线

| 你要的效果 | 入口 | 一句话理解 |
| --- | --- | --- |
| 真正画进世界里的几何体 | `renderLocal(...)` | 像普通模型那样画 |
| 带本体的发光球 | `collectFrameEffects(...)` + `PostGlowSphereRenderer` | 场景画完后补一颗会发光的球 |
| 只有柔和外辉光 | `PersistentBloomContextProvider` | 先采样亮源，再做 blur |

如果你是第一次做，优先顺序建议是：

1. 发光球：`PostGlowSphereRenderer`
2. 纯外辉光：`PersistentBloomContextProvider`
3. 自己写 world pass：`renderLocal(...)`

## 2. 最小骨架

先把同步骨架搭起来：

```kotlin
@CooAutoRegister
class MyRenderEntity(
    world: Level? = null,
    pos: Vec3 = Vec3.ZERO
) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<MyRenderEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "yourmod",
            "my_render_entity"
        )
    }

    @field:CodecField
    var radius: Float = 4.0f

    @field:CodecField
    var intensity: Float = 6.0f

    @field:CodecField
    var glowColor: Vector3f = Vector3f(0.6f, 0.9f, 1.2f)

    override fun getRenderID(): ResourceLocation = ID
}
```

服务端生成：

```kotlin
val entity = MyRenderEntity().apply {
    radius = 5.0f
    intensity = 7.5f
    glowColor = Vector3f(1.0f, 0.7f, 0.3f)
    renderRange = 256.0
}
entity.spawn(serverLevel, Vec3(x, y, z))
```

这段代码只解决两件事：

- `AutoRenderEntity` 自动处理 `@CodecField` 的 codec 和镜像回写
- `spawn(serverLevel, pos)` 把它交给 `ServerRenderEntityManager`

它还不会显示任何东西。真正的显示逻辑写在下面 3 条路线里。

## 3. 最短可用的发光球

如果你要的是“能看见球体本体，还带 halo”，直接抄这条。

把下面的方法加到上面的类里：

```kotlin
override fun initialize(instance: RenderEntityInstance<MyRenderEntity>) {
    PostGlowSphereRenderer.initialize()
}

override fun collectFrameEffects(
    input: FrameEffectInput<MyRenderEntity>,
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
            haloIntensity = 3.2f,
            haloRadiusScale = 1.8f,
            fresnelStrength = 1.3f,
            animationSpeed = 1.0f,
            overbrightClamp = 6.0f,
            glowColor = Vector3f(entity.glowColor)
        )
    )
}
```

可以直接这样生成：

```kotlin
MyRenderEntity().apply {
    radius = 4.5f
    intensity = 6.8f
    glowColor = Vector3f(0.58f, 0.88f, 1.30f)
}.spawn(serverLevel, explosionCenter)
```

这条路线的特点：

- 不用自己写 glow shader
- 不用自己管球体网格
- 是 frame-post 路线，不是 world pass
- 画出来的是“有本体的发光球”，不是 blur bloom

## 4. 只要柔和外辉光

如果你不要球体本体，只想要“亮源外面一圈软的辉光”，用 `PersistentBloomContextProvider`。

```kotlin
@CooAutoRegister
class MyBloomEntity(
    world: Level? = null,
    pos: Vec3 = Vec3.ZERO
) : AutoRenderEntity(world, pos),
    RenderEntityRenderer<MyBloomEntity>,
    PersistentBloomContextProvider {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "yourmod",
            "my_bloom_entity"
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
        output += PersistentBloom(
            position = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
            color = Vector3f(bloomColor),
            radius = bloomRadius,
            intensity = bloomIntensity,
            softness = 0.58f,
            haloRadiusScale = 3.0f,
            blurSigma = 4.6f,
            blurRange = 4.2f
        )
    }
}
```

这条路线要注意两点：

- 你不用自己手写 `collectFrameEffects(...)`，`RenderEntityInstance` 会自动识别 `PersistentBloomContextProvider`
- 它依赖 `FINAL_FRAME_POST + SCENE_COLOR_COPY + SCENE_DEPTH_READ`

当前后端上，这基本意味着：

- `VanillaSafeRenderBackend` 可以跑
- `IrisSafeRenderBackend` 只有 `FINAL_FRAME_POST`，所以这类 bloom 现在不会生效

## 5. 真要 world pass，再用 `renderLocal(...)`

这条路线最自由，但也是最像“自己写渲染器”。

```kotlin
override fun renderLocal(input: LocalRenderInput<MyRenderEntity>) {
    myShader.useOnContext {
        setMatrix4("projMat", input.projMatrix)
        setMatrix4("viewMat", input.viewMatrix)
        setMatrix4("transMat", Matrix4f(input.modelMatrix))
        setFloat("time", input.instance.entity.getTime(input.tickDelta))
        myBuffer.draw()
    }
}
```

你可以把它理解成：

- 顶点着色器决定“东西画在哪”
- 片元着色器决定“这个像素长什么样”

如果你现在还不熟 shader，别从这条开始。先把第 3 节跑通，再回来看它会轻松很多。

## 6. 什么时候需要 `markDirty()`

最容易踩坑的一点是：

- `@CodecField` 会参与同步
- 但“字段改了”不会自动发包

也就是说，这样写还不够：

```kotlin
entity.radius = 8.0f
entity.intensity = 10.0f
```

如果你要客户端马上看到变化，还要手动同步：

```kotlin
entity.radius = 8.0f
entity.intensity = 10.0f
entity.markDirty()
```

只想同步一次时：

```kotlin
entity.glowColor = Vector3f(1.0f, 0.3f, 0.3f)
entity.requestSync()
```

位置更新可以直接用：

```kotlin
entity.setPosition(newPos)
```

因为 `setPosition(...)` 内部已经会 `markDirty()`。

## 7. 不显示时先查这几个

- 是否真的在服务端调用了 `spawn(serverLevel, pos)`，不是客户端世界
- 是否加了 `@CooAutoRegister`
- `getRenderID()` 是否稳定、唯一
- 玩家是否超出 `renderRange`
- 改字段后是否忘了 `markDirty()` 或 `requestSync()`
- 发光球路线是否调用了 `PostGlowSphereRenderer.initialize()`
- 你选的是不是错误路线
- `PersistentBloom` 是否跑在 `IrisSafeRenderBackend`
- 实体是否已经 `canceled`

最常见的误判是：

- “类已经写好了，但没有任何渲染逻辑”
- “值改了，但没同步”
- “选了 bloom，却在不支持 `SCENE_COLOR_COPY / SCENE_DEPTH_READ` 的后端里测试”

## 8. 术语速查

- `RenderEntity`：服务端权威同步对象
- `RenderEntityInstance`：客户端真正持有的运行时实例
- world pass：像普通世界模型那样直接画
- frame-post：场景画完后再叠效果
- glow：看起来在发光
- bloom：高亮区域模糊后再叠回去
