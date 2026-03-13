# RenderEntity API 知识库

`RenderEntity` 是一条“服务端权威状态 + 客户端自定义渲染”的运行时管线。
它和 `DisplayEntity` 的核心区别不是“都能显示东西”，而是：

- `RenderEntity` 的状态由服务端维护，客户端只持有镜像。
- 渲染不走原版实体 renderer，而是走 `ClientRenderEntityManager` 的自定义 pipe / pass。
- 它天然支持 `WORLD` / `POST_PROCESS` 双 pass，以及额外的 `PersistentBloom`、`ScreenGlow`、`WorldLight` 复合效果。

## 1. API 心智模型

一条 `RenderEntity` 的完整生命周期可以压缩成下面 6 步：

1. 服务端创建实例，设置位置和参数，然后 `spawn(...)` 或直接交给 `ServerRenderEntityManager`。
2. 服务端 tick 时检查玩家是否进入 `renderRange`，向可见玩家发送 `CREATE` 包。
3. 客户端通过 `getRenderID()` 找到已注册的 codec，解码出实体镜像并加入 `ClientRenderEntityManager`。
4. 客户端按 `RenderPass -> Pipe -> Entity` 分类，由管理器先调用 `renderOnWorld(...)`，再进入子类自己的 `render(...)`。
5. 如果实体实现了 `PersistentBloomContextProvider` / `ScreenGlowContextProvider` / `WorldLightProvider`，帧尾会额外参与对应的 composite 管线。
6. 服务端属性变化后通过 `TOGGLE` 同步，或者通过 `REMOVE` 让客户端移除镜像。

## 2. 最小实现模板

下面这个模板包含最容易漏掉的部分：`createCodec(...)`、`tracked(...)`、`loadProfileFromEntity(...)`。

```kotlin
class MyRenderEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath("yourmod", "my_render_entity")
        val CODEC = RenderEntity.createCodec(
            { MyRenderEntity(null) },
            encodeExtra = { buf, e ->
                buf.writeFloat(e.radius)
                buf.writeFloat(e.intensity)
            },
            decodeExtra = { buf, e ->
                e.radius = buf.readFloat()
                e.intensity = buf.readFloat()
            }
        )
    }

    var radius by tracked(1.0f)
    var intensity by tracked(2.0f)

    override fun initialize() {
        // 客户端首次 add 时初始化 shader / vbo / texture
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = CODEC

    override fun getRenderID(): ResourceLocation = ID

    override fun getRenderPass(): RenderEntityRenderPass = RenderEntityRenderPass.WORLD

    override fun loadProfileFromEntity(another: RenderEntity) {
        super.loadProfileFromEntity(another)
        another as MyRenderEntity
        radius = another.radius
        intensity = another.intensity
    }

    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        // 自定义绘制
    }

    override fun release() {
        // 客户端移除镜像时释放资源
    }
}
```

## 3. 客户端注册

客户端至少要做两件事：

```kotlin
ClientRenderEntityManager.register(MyRenderEntity.ID, MyRenderEntity.CODEC)
ClientRenderEntityManager.bindEntityRenderPipe(MyRenderEntity.ID, ShaderPipeManagers.simpleBloom.pipeID)
```

也可以一次性注册到指定 pipe：

```kotlin
ClientRenderEntityManager.register(
    MyRenderEntity.ID,
    MyRenderEntity.CODEC,
    ShaderPipeManagers.simpleBloom.pipeID
)
```

`ClientRenderEntityManager` 内部会保存两张表：

- `entityCodecs`：`RenderID -> codec`
- `entityPipeType`：`RenderID -> pipeID`

`CREATE/TOGGLE/REMOVE` 包到达客户端后，都是先靠这两张表定位实体类型和渲染管线。

## 4. 服务端生成与可见性

标准写法：

```kotlin
val entity = MyRenderEntity(serverLevel)
entity.spawn(serverLevel, pos)
```

`spawn(world, pos)` 做的事情很少：

- 填充 `world`
- 写入 `pos`
- 调用 `ServerRenderEntityManager.spawn(this)`

之后真正的同步逻辑由 `ServerRenderEntityManager.tick()` 驱动：

- `updateVisible(entity)`：根据 `renderRange` 和玩家维度判断谁应该看见。
- 首次可见时发送 `CREATE`。
- 属性变化且 `shouldSync()` 为 `true` 时发送 `TOGGLE`。
- `canceled = true` 时发送 `REMOVE` 并从管理器移除。

`APITestGroupBuilder` 里的 demo harness 走的是更直接的测试路径：实体先手动 `setPosition(...)`，然后 `SimpleRendererEntityOption.start()` 直接调用 `ServerRenderEntityManager.spawn(testEntity)`。这对游戏内测试没问题，但业务代码里通常还是推荐 `spawn(world, pos)`。

## 5. 同步协议与 `tracked(...)`

### 5.1 `createCodec(...)`

`RenderEntity.createCodec(...)` 会自动包含以下基础字段：

- `uuid`
- `pos`
- `canceled`
- `age`

所以你的 `encodeExtra` / `decodeExtra` 只负责子类自己的字段，不要重复写基础字段。

### 5.2 `tracked(...)`

`tracked(initial)` 的作用只有一个：字段变化时自动标记为需要同步。

- 默认行为是 `markDirty()`，表示后续服务端 tick 可以持续发 `TOGGLE`。
- `tracked(initial, syncOnce = true)` 会改成 `requestSync()`，表示只发一次。

不使用 `tracked(...)` 也没关系，但你必须自己调用：

- `markDirty()`：持续同步
- `requestSync()`：只同步一次
- `clearDirty()`：手动清空 dirty 状态

### 5.3 `alwaysToggle` 与 `shouldSync()`

默认情况下，服务端本 tick 是否发送 `TOGGLE` 由 `shouldSync()` 决定。

基类默认实现是：

```kotlin
override fun shouldSync(): Boolean {
    return alwaysToggle || dirty
}
```

这表示有三种常见同步策略：

- 普通字段同步：字段变化时 `tracked(...)` 或手动 `markDirty()`，本帧发送 `TOGGLE`
- 一次性同步：调用 `requestSync()`，发送一次后自动清掉 dirty
- 常驻强制同步：把 `alwaysToggle = true`，服务端每 tick 都尝试发 `TOGGLE`

`alwaysToggle` 适合少量、持续变化、又不想每次手动标 dirty 的状态。
但它会提高同步频率，所以更推荐优先使用 `tracked(...)` 或自定义 `shouldSync()`。

如果默认规则不适合你的实体，可以直接覆盖：

```kotlin
override fun shouldSync(): Boolean {
    return age % 5 == 0 || dirty
}
```

这样可以把 `TOGGLE` 改成固定频率同步，而不是每 tick 都发。

### 5.4 重要约束：额外字段同步要靠 `loadProfileFromEntity(...)`

这是 `RenderEntity` API 当前最容易踩坑的点。

客户端收到 `TOGGLE` 包时，流程不是“直接替换实体实例”，而是：

1. 先用 codec 解码出一个临时实体。
2. 再对现有实例调用 `loadProfileFromEntity(decodedEntity)`。

而基类 `RenderEntity.loadProfileFromEntity(...)` 只会复制：

- `age`
- `canceled`
- `pos`
- `uuid`
- `world`

它不会自动复制你的自定义字段。

所以只要你希望 `TOGGLE` 真正更新 `radius`、`color`、`intensity` 这类字段，就必须在子类里覆盖 `loadProfileFromEntity(...)` 并把这些字段手动写回去。

如果不覆盖，会出现这种现象：

- 服务端字段已经变化，并且 `tracked(...)` 也确实让服务器发出了 `TOGGLE`
- 客户端镜像仍然保持旧值
- 看起来像“同步失效”，其实是镜像更新钩子没有补齐

## 6. 渲染 pass 与帧生命周期

除了 `WORLD / POST_PROCESS` 两个 pass，`RenderEntity` 还有一组固定的基类生命周期：

- `serverTick()`：服务端权威状态推进，适合改同步字段
- `clientTick()`：客户端视觉状态推进，适合本地动画和缓存
- `initialize()`：客户端镜像首次 `add(...)` 时执行一次
- `release()`：客户端镜像被移除时执行
- `renderOnWorld(...)`：管理器调用的统一入口，会更新 `lastRenderPos`、设置基础渲染状态，再转入子类 `render(...)`

### 6.1 共享 pipe 输入混合模式

从现在开始，`RenderEntity` 额外暴露了 `getInputBlendMode()`：

- 默认是 `RenderEntityInputBlendMode.REPLACE`
- 如果多个实体共享同一个 glow / bloom / emissive mask pipe，并且希望输入能叠加，应覆盖成 `RenderEntityInputBlendMode.ADDITIVE`

典型写法：

```kotlin
override fun getInputBlendMode(): RenderEntityInputBlendMode {
    return RenderEntityInputBlendMode.ADDITIVE
}
```

这解决的是“多个 RenderEntity 共用同一个 pipe 输入 FBO 时，后绘制实体把前一个实体覆盖掉”的 API 空洞。

注意两点：

- `getInputBlendMode()` 只负责给 `render(...)` 设置进入时的默认 blend 状态
- 如果你在 `render(...)` 里再次手动 `disableBlend()` 或改别的 blendFunc，那么以你手动设置为准

因此，对 glow sphere 这一类需要多实例共同写入共享 mask 的实体，不应在 `render(...)` 开头再次强制关闭 blend。

所以子类通常不应该自己调用 `renderOnWorld(...)`。
你真正需要实现的是 `render(...)`，而 `renderOnWorld(...)` 属于 manager 和框架内部的调度层。

`RenderEntityRenderPass` 只有两个值：

- `WORLD`
- `POST_PROCESS`

它们不是“不同 API”，而是同一个实体在不同帧阶段被调度。

### 6.1 `WORLD` pass

默认 pass 是 `WORLD`。

`LevelRendererMixin` 在世界实体渲染阶段前会：

1. `cacheFrameState(tickDelta, viewMatrix, projMatrix)`
2. 如果没有启用 Iris shader pack，立即执行 `renderWorldPass(...)`

这适合：

- 世界中直接出现的几何体
- 需要深度测试的体积或 billboard
- 不依赖帧尾 scene copy / composite 的效果

### 6.2 `POST_PROCESS` pass

如果实体覆盖：

```kotlin
override fun getRenderPass(): RenderEntityRenderPass = RenderEntityRenderPass.POST_PROCESS
```

那么 `LevelRendererMixin` 在 `renderLevel` 尾部只会做“准备”：

1. 再次缓存帧状态
2. 调用 `preparePostProcess(...)`

这里不会立刻把结果合成到主屏幕，而是先让对应 pipe 把实体绘制进自己的 frame buffer。

真正的统一合成发生在 `GameRendererMixin`：

1. `flushFrameComposites()`
2. `renderWorldLighting(...)`
3. `flushPostProcess()`
4. `renderPersistentBlooms(...)`
5. `renderScreenGlows(...)`

因此，`POST_PROCESS` 更适合：

- 需要 scene copy / depth texture 的扭曲、辉光、合成
- 需要和 fullscreen shader 联动的效果
- 需要把“直接绘制”和“额外光效”拆开处理的效果

## 7. `TestPersistentGlowSphereEntity` 整条 API 执行链

`TestPersistentGlowSphereEntity` 是当前仓库里最典型的“`POST_PROCESS` 直接绘制 + `PersistentBloom` 帧尾辉光”的样板。

### 7.1 测试入口

`APITestGroupBuilder` 会创建 `TestPersistentGlowSphereEntity(player.level())`，然后设置：

- 位置
- 半径
- 强度
- halo 参数
- fresnel / compensation / clamp / color

随后把它包进 `SimpleRendererEntityOption`。

当测试项启动时：

```kotlin
ServerRenderEntityManager.spawn(testEntity)
```

实体此时已经在服务端管理器里等待可见性同步。

### 7.2 服务端同步

`ServerRenderEntityManager.tick()` 每 tick 会执行：

1. `updateVisible(entity)` 判断哪些玩家进入了 `renderRange`
2. 首次进入视野时发送 `CREATE`
3. 如果后续属性变化且 `shouldSync()` 为真，发送 `TOGGLE`
4. 如果 `remove()` 之后 `canceled = true`，发送 `REMOVE`

`CREATE/TOGGLE/REMOVE` 都统一打包成 `PacketRenderEntityS2C`：

- `uuid`
- `method`
- `RenderID`
- codec 编码后的字节数组

这里要额外提醒一件事：`TestPersistentGlowSphereEntity` 自身没有覆盖 `loadProfileFromEntity(...)`。
所以它更适合“创建时一次性配置好参数，然后长期显示”的稳定光球样例；
如果你准备在运行时频繁修改半径、颜色、强度，再依赖 `TOGGLE` 推到客户端，就必须把该钩子补上。

### 7.3 客户端实例化

客户端启动时，`CooParticlesAPIClient.initRender()` 会调用 `TestShaderInit.initOnClient()`。

这里完成两件关键事情：

1. 注册 `TestShaderPipelines.glowSphereDistortion`
2. 注册实体类型：

```kotlin
ClientRenderEntityManager.register(
    TestPersistentGlowSphereEntity.id,
    TestPersistentGlowSphereEntity.codec,
    TestShaderPipelines.glowSphereDistortion.pipeID
)
```

所以当 `PacketRenderEntityS2C` 到达客户端时：

1. `ClientRenderEntityPacketHandler.receive(...)` 先按 `packet.id` 找到 codec
2. 解码出一个 `TestPersistentGlowSphereEntity`
3. `CREATE` 时调用 `ClientRenderEntityManager.add(entity)`

`add(...)` 内部会继续做三件事：

1. 把 `world` 改成当前客户端世界
2. 调用 `init()`，从而触发 `initialize()` 初始化静态 shader / buffer
3. 按 `getRenderPass()` 和 pipe 归类到 `POST_PROCESS -> glowSphereDistortion -> entity`

### 7.4 为什么它走 `POST_PROCESS`

`TestPersistentGlowSphereEntity.getRenderPass()` 明确返回 `POST_PROCESS`。

这意味着它不会在 `renderWorldPass(...)` 阶段直接合成到主屏幕，而是先由 `glowSphereDistortion` pipe 收集。

`render(...)` 本身做的是“直接可见球体”那一层：

1. 通过 `createGlowContext(...)` 构造当前帧的投影上下文
2. 用 `DistanceAdaptiveGlow.computeOrbBlend(...)` 算出当前球体在屏幕上的像素半径与直绘权重
3. 如果 `directWeight` 太小，直接跳过直绘
4. 否则把半径、强度、Fresnel、补偿、时间等 uniform 写进 `glowShader`
5. 调用 `sphereBuffer.draw()` 输出到当前 pipe 的 mask / distortion 目标

这一步只负责“球本体和扭曲遮罩”，还不是最终的外发光。

### 7.5 Persistent Bloom 是怎么接上的

`TestPersistentGlowSphereEntity` 实现了 `PersistentBloomContextProvider`，所以在帧尾：

1. `GameRendererMixin` 调用 `ClientRenderEntityManager.flushFrameComposites()`
2. `flushPostProcess()` 先把 `glowSphereDistortion` pipe composite 到主屏幕
3. `renderPersistentBlooms(...)` 再遍历全部实体
4. `ClientPersistentBloomManager.collectBlooms(...)` 发现该实体实现了 `PersistentBloomContextProvider`
5. 调用 `collectPersistentBlooms(context, output)`

`collectPersistentBlooms(...)` 里做的事情是：

1. 再次根据 `projectedRadiusPx` 计算远距补偿
2. 根据亮源 profile 和 halo profile 生成 bloom 参数
3. 输出一个 `PersistentBloom(position, color, radius, intensity, softness, ...)`

之后 `ClientPersistentBloomManager` 会：

1. 把所有 bloom 按距离排序，截断到最多 8 个
2. 写入位置、颜色、风格数组 uniform
3. 运行 `persistent_glow_bloom` 管线
4. 用 `persistent_glow_mask.fsh + blur + persistent_glow_composite.fsh` 把 halo 合成到主屏幕

### 7.6 它没有做什么

`TestPersistentGlowSphereEntity` 有意没有实现：

- `ScreenGlowContextProvider`
- `ScreenGlowProvider`
- `WorldLightProvider`

所以它的最终效果是：

- `POST_PROCESS` 里的球体本体和扭曲
- 帧尾 `PersistentBloom` 带来的稳定外辉光

但不会额外产生：

- 屏幕空间 halo
- 世界空间补光

如果你想要“更像 `TestGlowSphereEntity` 那样的强烈能量球”，需要在这个基础上再补 `ScreenGlow` 或 `WorldLight`。

## 8. 如何复用这条链开发类似效果

### 8.1 做一个“稳定发光体”

如果你的目标是“近处能看到球体本体，远处仍然保留柔和 halo”，直接照着 `TestPersistentGlowSphereEntity` 这套组合：

- `getRenderPass() = POST_PROCESS`
- 在 `render(...)` 里输出本体 / mask
- 实现 `PersistentBloomContextProvider`
- 在 `collectPersistentBlooms(...)` 里输出一组 `PersistentBloom`

这类效果适合：

- 能量球
- 法阵核心
- 传送门核心亮源
- 高亮道具节点

### 8.2 做一个“近处本体 + 远处屏幕辉光”

如果你希望远距离时辉光更多是屏幕空间的 halo，而不是 persistent blur，应该实现：

- `ScreenGlowContextProvider`

返回 `ScreenGlow` 的时机和参数可以参考：

- `TestGlowSphereEntity`
- `TestHybridGlowPipeEntity`

常见做法是：

1. 先用 `DistanceAdaptiveGlow.computeBlend(...)` 或 `computeOrbBlend(...)`
2. `directWeight` 控制本体直绘
3. `screenGlowWeight` 控制屏幕 halo

### 8.3 做一个“会照亮场景”的发光体

如果你还希望这个实体真的给场景打补光，实现：

- `WorldLightProvider`

客户端帧尾会由 `ClientWorldLightManager` 自动收集并合成。

### 8.4 选择 pass 的建议

- 只需要普通世界绘制，不需要 fullscreen composite：`WORLD`
- 需要 scene copy、depth、扭曲、mask 或帧尾 bloom：`POST_PROCESS`

一个经验判断：

- “看起来像几何体”优先 `WORLD`
- “看起来像屏幕后处理或发光层”优先 `POST_PROCESS`

## 9. 开发注意事项

- `initialize()` 只在客户端首次 `add(...)` 时调用一次，适合初始化 shader / vbo / texture。
- `release()` 只在客户端镜像被移除时调用，适合释放资源。
- `remove()` 只会把 `canceled` 设为 `true`，真正移除发生在服务端管理器和客户端管理器的后续 tick。
- `renderRange` 同时影响服务端可见性同步和客户端是否还保留镜像。
- `getTime(delta)` 已经把 `age + tickDelta` 转成秒制，适合直接喂 shader 时间参数。
- 如果实体需要运行时动态变色、变半径、变强度，务必同时完成：
  `codec encode/decode`、`tracked/requestSync`、`loadProfileFromEntity`
