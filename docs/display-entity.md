# DisplayEntity 使用文档

`DisplayEntity` 是一种“自定义展示实体”框架，适合在世界中渲染自定义模型/几何，并支持服务端同步到客户端。

## 1. 核心概念
- **服务端**：维护实体数据并同步（`DisplayEntityManager` 负责发送包）。
- **客户端**：渲染 `DisplayEntity.render(...)`。

`DisplayEntity` 自带位置、旋转、缩放与生命周期管理。

## 2. 最小实现
```kotlin
@CooAutoRegister
class MyDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    @CodecField var lookDir = Vec3(0.0, 1.0, 0.0)

    override fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: Float,
        camera: Camera
    ) {
        // 自定义渲染逻辑
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, DisplayEntity> {
        return DisplayEntityHelper.generateCodec(this)
    }
}
```

## 3. 自动同步字段
使用 `@CodecField` 标记字段即可自动同步：
- 字段必须 `var` 且类型已在 `CodecHelper` 注册
- 需要 `(Vec3, Level?)` 或无参构造

## 4. 自动注册与扫描
`@CooAutoRegister` 会被自动扫描注册，无需手动调用扫描函数。Fabric 只需：
```kotlin
CooAPIScanner.registerPacket("your.mod.package")
```

## 5. 旋转与变换
`DisplayEntity` 默认会自动管理旋转（`manageRotation = true`）。
- 如果你在 `render()` 内自己做旋转，请设置 `manageRotation = false`，避免重复旋转。
- `renderCenterOffset()` 控制旋转中心。
- `transformOffset()` 控制整体偏移。

## 6. 生命周期与生成
- `spawn(world, pos)`：服务端生成并同步。
- `tick()`：默认更新 `prevPos/prevYaw/prevPitch/prevRoll`。
- `remove()`：设置 `valid = false`，客户端会移除。

## 7. 常见用法提示
- 若需要“面向目标”，可在 `tick()` 中调用 `lookAt(direction)`。
- `DisplayEntityManager` 会自动每 tick 同步服务端实例到客户端。
