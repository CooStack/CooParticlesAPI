# CodecField 与自动注册

这页说明 `@CodecField` 的自动编解码机制、`CodecHelper` 的注册流程，以及 `@CooAutoRegister` 的扫描与注册方式。

## 1. @CodecField 在哪里生效
`@CodecField` 只在“自动生成 codec”的路径里生效：
- `ParticleEmittersHelper.generateCodec`（`AutoParticleEmitters`）
- `ParticleCompositionHelper.generateCodec`（`AutoParticleComposition` / `AutoSequencedParticleComposition`）
- `DisplayEntityHelper.generateCodec`

## 2. 使用规则
- **只能标在 `var` 且非 `final` 字段**（`val` 不会被写入/更新）。
- **字段类型必须已注册到 `CodecHelper`**，否则会在运行时抛异常。
- **必须提供构造函数**：反射解码要求 `(Vec3, Level?)` 或无参构造。
- **字段顺序按字段名排序**：服务端/客户端字段必须完全一致（同名、同类型）。

## 3. 内置可用类型（节选）
`CodecHelper` 已注册大量常用类型，常用的有：
- 基本类型与包装类：`Int/Long/Float/Double/Boolean/Byte/Short/Char/String`
- 数组：`ByteArray`、`LongArray`
- 数学/坐标：`Vec3`、`Vector3f`、`Quaternionf`、`RelativeLocation`、`AABB`
- 业务类型：`UUID`、`ItemStack`、`HitBox`
- 插值数据：`InterpolatorDouble/Float/Vec3d/Vector3f/RelativeLocation`
- 范围数据：`IntRangeData/FloatRangeData/DoubleRangeData`

完整列表以 `cn.coostack.cooparticlesapi.annotations.codec.CodecHelper` 为准。

## 4. 注册自定义类型
当字段类型不在内置列表时，需要自定义 `StreamCodec` 并注册：

```kotlin
data class MyOption(val id: String, val power: Int)

val MY_CODEC = StreamCodec.of<FriendlyByteBuf, MyOption>(
    { buf, data ->
        buf.writeUtf(data.id)
        buf.writeInt(data.power)
    },
    { buf ->
        MyOption(buf.readUtf(), buf.readInt())
    }
)

CodecHelper.register(MyOption::class.java, MY_CODEC)
```

## 5. 字段更新机制
`CodecHelper.updateFields(current, other)` 会把 `@CodecField` 标注的字段从 `other` 复制到 `current`。在以下情况要注意：
- 你重写了 `update(...)` 时，**务必调用 `super.update(...)`** 或手动调用 `CodecHelper.updateFields`。
- 仅 `@CodecField` 字段会自动同步；其它字段需要你自行处理。

## 6. @CooAutoRegister 与扫描流程
`@CooAutoRegister` 会被以下管理器扫描并注册：
- `ParticleEmittersManager`（发射器）
- `ParticleCompositionManager`（组合）
- `DisplayEntityManager`（展示实体）
- `ParticleEventHandlerManager`（发射器事件处理器）

### Fabric
扫描由 API 自动触发，无需手动调用 `scan()` 或 `loadScannerPackages()`。Fabric 只需要注册扫描包：
```kotlin
CooAPIScanner.registerPacket("your.mod.package")
```

## 示例：配合 Auto 类使用
```kotlin
@CooAutoRegister
class MyEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    @CodecField var speed = 0.4
    @CodecField var color = Vector3f(1f, 0f, 0f)

    override fun doTick() {}

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        // ...
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {}
}
```

如果不使用自动注册，也可以手动：
```kotlin
ParticleEmittersManager.register(MyEmitter(Vec3.ZERO, null))
```
