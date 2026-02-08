# AutoParticleEmitters 使用文档

`AutoParticleEmitters` 是基于 `ClassParticleEmitters` 的“自动编解码版发射器”。它自动使用 `@CodecField` 生成 codec，并通过
`@CooAutoRegister` 自动注册到 `ParticleEmittersManager`。

## 1. 何时使用

- 需要服务端/客户端同步发射器参数
- 想减少手写 codec 的工作量
- 希望使用 `@CodecField` 自动更新

## 2. 基础实现结构

```kotlin
@CooAutoRegister
class MyEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    @CodecField
    var speed = 0.35
    @CodecField
    var color = Vector3f(0.8f, 0.4f, 1.0f)

    override fun doTick() {
        // 服务端/客户端都会执行，可在这里更新位置或参数
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return PointsBuilder()
            .addCircle(3.0, 60)
            .create()
            .map { point ->
                ControlableParticleData().apply {
                    this.speed = this@MyEmitter.speed
                } to point
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
        // 对单个粒子的控制器做初始化
    }
}
```

要求：

- 构造函数必须提供 `(Vec3, Level?)` 或无参构造。
- `@CodecField` 字段必须是 `var` 且类型已在 `CodecHelper` 注册。
- 类上使用 `@CooAutoRegister` 以便自动扫描注册。

## 3. 发射器生命周期

- `spawn(world, pos)`：服务端生成并同步到客户端。
- `start()`：开始发射（服务端会触发同步）。
- `stop()`：取消发射。
- `maxTick = -1` 表示不受生命周期限制。

服务端更新参数后，通常需要调用：

```kotlin
ParticleEmittersManager.updateEmitters(this)
```

## 4. 插值器（平滑轨迹）

当发射器移动速度较快时，客户端可能出现“断点”。可开启插值：

```kotlin
init {
    enableInterpolator = true
    emittersInterpolator.setRefiner(10.0)
}
```

`lerpProgress` 会在 `genParticles(...)` 中传入，用于插值驱动的渐变或路径逻辑。

## 5. 粒子事件处理

`ClassParticleEmitters` 支持粒子事件处理器：

```kotlin
addEventHandler(MyParticleHandler(), innerClass = false)
```

建议把处理器类标注 `@CooAutoRegister`，扫描由 API 自动完成；Fabric 只需 `CooAPIScanner.registerPacket(...)`。

## 6. 常见注意事项

- `doTick()` 在服务端与客户端都会执行，写逻辑时注意区分：
  ```kotlin
  if (!world!!.isClientSide) { /* server */ }
  ```
- 重写 `update(...)` 时必须调用 `super.update(...)`，否则 `@CodecField` 不会更新。
- `genParticles(...)` 返回的是 **相对坐标**（`RelativeLocation`）。
