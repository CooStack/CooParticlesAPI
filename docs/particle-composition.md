# ParticleComposition 使用文档

`ParticleComposition` 用于“组合式粒子”的同步与渲染：服务端负责同步参数，客户端负责真正生成粒子对象。适合做复杂的几何图形、阵列、动画序列等。

## 1. 生命周期与职责
- **服务端**：维护位置、可见范围、状态、序列进度等；通过 `ParticleCompositionManager` 同步到客户端。
- **客户端**：接收参数后调用 `display()` 生成粒子，并在 `tick()` 中更新。

典型流程：
1. 服务端创建并 `spawn(world, pos)`
2. `ParticleCompositionManager` 发送同步包
3. 客户端 `display()`，生成粒子
4. `tick()` 按状态更新、旋转、缩放等

## 2. 最小实现（AutoParticleComposition）
推荐使用 `AutoParticleComposition`：自动生成 codec，并配合 `@CodecField` 同步自定义字段。

```kotlin
@CooAutoRegister
class MyComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    @CodecField var radius = 6.0

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return PointsBuilder()
            .addCircle(radius, 200)
            .createWithCompositionData {
                CompositionData().addParticleInstanceInit {
                    colorOfRGBA(255, 200, 80, 1f)
                }
            }
    }

    override fun onDisplay() {
        addPreTickAction {
            rotateAsAxis(Math.PI / 64)
        }
    }
}
```

关键点：
- 构造函数必须提供 `(Vec3, Level?)` 或无参构造。
- `@CodecField` 字段会自动同步。
- `@CooAutoRegister` 可被扫描自动注册。

## 3. CompositionData 与粒子初始化
`getParticles()` 需要返回 **相对坐标** 的集合：
- key：`CompositionData`
- value：`RelativeLocation`

`CompositionData` 提供了常用初始化入口：
- `setDisplayerSupplier { uuid -> ParticleDisplayer }`
- `addParticleInstanceInit { ... }`（初始化单个粒子）
- `addParticleControlerInstanceInit { ... }`（初始化控制器）

示例：
```kotlin
CompositionData()
    .setDisplayerSupplier { ParticleDisplayer.withSingle(ControlableEndRodEffect(it)) }
    .addParticleInstanceInit { colorOfRGBA(120, 220, 255, 1f) }
```

## 4. 变换与缩放
`ParticleComposition` 自带常见变换：
- 平移：`teleportTo(...)`
- 旋转：`rotateToPoint(...)` / `rotateAsAxis(...)` / `rotateToWithAngle(...)`
- 缩放：`scale(newScale)`

注意：
- `scale` 影响的是 **相对坐标** 的距离比例。
- 如果组合已显示，内部会自动重新计算相对位置。

## 5. 同步与可见范围
- `visibleRange` 控制客户端可见范围。
- 客户端若距离过远，会在 `tick()` 中自动取消显示。
- 服务端会按 `visibleRange` 决定是否向某玩家发送更新。

## 6. 序列式组合（SequencedParticleComposition）
当你需要“逐步生成/消失”的组合时，使用 `SequencedParticleComposition`。

特点：
- 只同步“序列状态”，客户端按差异创建/移除粒子。
- 提供 `addSingle/addMultiple/removeSingle/removeMultiple/resetAll` 等方法。

示例：
```kotlin
@CooAutoRegister
class MySeqComposition(pos: Vec3, world: Level? = null) : AutoSequencedParticleComposition(pos, world) {
    override fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation> {
        var order = 0
        return PointsBuilder()
            .addSpiral(0.0, 8.0, 6.0, 1024, Math.PI / 32)
            .createWithCompositionDataSorted {
                CompositionData().apply { this.order = order++ }
            }
    }

    override fun onDisplay() {
        addPreTickAction {
            if (!client) {
                addMultiple(30)
            }
        }
    }
}
```

注意：
- `addSingle/addMultiple/removeSingle/removeMultiple` 通常 **在服务端调用**。
- 客户端会根据 bitset diff 自动创建/销毁粒子。

## 7. 常见坑
- `getParticles()` 应保持 **稳定与可重复**，因为服务端也会调用以计算数量与同步状态。
- 自定义字段必须 `@CodecField` 且已注册 codec。
- 重写 `update(...)` 时务必调用 `super.update(...)`，否则字段不会更新。
