# RenderEntity 使用文档

`RenderEntity` 用于“自定义渲染实体 + 服务端同步”。它和 `DisplayEntity` 的区别在于：
- 运行在 `RenderEntity` 管线中，可与 `ShaderPipe` 结合
- 支持更细粒度的同步（`dirty` / `tracked` / `alwaysToggle`）

## 1. 最小实现
```kotlin
class MyRenderEntity(world: Level?) : RenderEntity(world) {
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath("yourmod", "my_render_entity")
        val CODEC = RenderEntity.createCodec(
            { MyRenderEntity(null) },
            encodeExtra = { buf, e -> buf.writeFloat(e.radius) },
            decodeExtra = { buf, e -> e.radius = buf.readFloat() }
        )
    }

    var radius by tracked(1.0f) // 自动标记 dirty

    override fun initialize() {
        // 客户端初始化：加载 shader / buffer
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = CODEC
    override fun getRenderID(): ResourceLocation = ID

    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        // 自定义渲染逻辑
    }

    override fun release() {
        // 释放资源
    }
}
```

## 2. 注册与管线绑定（客户端）
```kotlin
ClientRenderEntityManager.register(MyRenderEntity.ID, MyRenderEntity.CODEC)
ClientRenderEntityManager.bindEntityRenderPipe(MyRenderEntity.ID, ShaderPipeManagers.simpleBloom.pipeID)
```

## 3. 服务端生成与同步
```kotlin
val entity = MyRenderEntity(serverLevel)
entity.spawn(serverLevel, pos)
```
同步规则：
- `dirty = true` 才会发送 toggle 包
- `alwaysToggle = true` 则每 tick 都发送
- `requestSync()` 发送一次后自动清除 `dirty`

## 4. tracked 委托
`tracked` 会在字段改变时自动 `markDirty()`：
```kotlin
var color by tracked(Vector3f(1f, 1f, 1f))
```

如果不使用 `tracked`，需要手动调用：
- `markDirty()`：持续同步
- `requestSync()`：同步一次

## 5. 生命周期
- `initialize()`：客户端首次收到时调用，用于初始化资源
- `clientTick()` / `serverTick()`：区分客户端/服务端逻辑
- `remove()`：标记 `canceled = true`，管理器会移除

## 6. 视距控制
`renderRange` 控制服务端“可视范围”与客户端渲染范围：
- 玩家离开范围会收到 `REMOVE` 包
- 重新进入范围会收到 `CREATE`

## 7. 常见提示
- `RenderEntity.createCodec` 已包含基础字段，不要重复写入。
- `getTime(delta)` 可用作 shader 时间参数（`(age + delta) / 20`）。
