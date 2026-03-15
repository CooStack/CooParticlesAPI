# Utils 工具库

`cn.coostack.cooparticlesapi.utils` 提供大量数学、几何、插值、渲染与动画工具。下面按场景整理常用类与当前版本的使用方式。

## 1. 核心数据结构

- **RelativeLocation**：三维相对坐标/向量，支持 `+/-/*`、`normalize()`、`length()`、`toVector()` 等。
- **CircularQueue**：固定容量循环队列，常用于插值或短期历史缓存。
- **Memo**：惰性缓存容器，用于按需创建和重建数组或中间结果。

## 2. 数学与几何

- **Math3DUtil**：几何生成与旋转工具集合，覆盖直线、圆、弧线、螺旋、闪电、柱体、贝塞尔曲线等。
- **GraphMathHelper**：插值、平滑、混合、衰减工具，例如 `lerp`、`smoothStep`、`mix`、`distanceFalloff`。
- **RotationMatrix**：轴角旋转矩阵工具。
- **MathDataUtil**：位标记辅助工具，适合大规模序列状态存储。
- **MathPresets**：罗马数字等预设图形点集。

示例：

```kotlin
val points = Math3DUtil.getCircleXZ(3.0, 128)
val rotated = points.onEach { it.y += 1.5 }
```

## 3. 点集构建（PointsBuilder）

`PointsBuilder` 是点集 DSL，可组合图形、旋转、噪声、缩放并导出：

```kotlin
val points = PointsBuilder()
    .addCircle(6.0, 200)
    .applyNoiseOffset(0.1, mode = NoiseMode.SPHERE_UNIFORM)
    .rotateAsAxis(Math.PI / 8)
    .create()
```

常用能力：

- 组合多个形状：`addCircle` / `addLine` / `addSpiral` / `addPolygonInCircle` ...
- 批量变换：`pointsOnEach` / `rotateAsAxis` / `rotateTo` / `scale`
- 输出为 `CompositionData` / `ParticleStyle` / `ControlableParticleGroup` 等结构

> 注意：`createWithoutClone()` 会返回内部列表引用，后续修改会影响 builder 本体。

## 4. 图像与傅里叶工具

- **ImageUtil**：加载、缩放图片并转换为点集（含 alpha / RGBA）。
- **ImagePointBuilder / RGBImagePointBuilder**：从纹理生成点集。
- **FourierSeriesBuilder**：通过傅里叶级数生成曲线。
- **FourierPhotoUtil**：从图片轮廓生成傅里叶曲线，支持多连通块与 offset。
- **FourierPresets**：常用傅里叶图形预设。

示例：

```kotlin
val builder = FourierPhotoUtil.toFourierBuilder(image, sampleCount = 1024, harmonics = 120)
val points = builder.build()
```

## 5. 插值器（Interpolator）

用于在高速度移动时补点，避免粒子轨迹断裂。

- **LineEmitterInterpolator**：线性插值，适合发射器移动。
- **DirectParticleInterpolator**：粒子速度插值。
- **CircleParticleInterpolator**：圆周运动插值，可自定义旋转映射。
- **InterpolatorDouble/Float/Vec3d/Vector3f/RelativeLocation**：带插值能力的数据包装类，支持 codec，可用于 `@CodecField`。

示例：

```kotlin
val angle = InterpolatorDouble(0.0)
angle += Math.PI / 8
val current = angle.getWithInterpolator(lerpProgress)
```

## 6. 渲染与摄像机工具

- **MinecraftRendererUtil**：世界坐标变换、旋转、采样（`transformTo` / `applyRotation` / `sampleTriangle`）。
- **ModelPartPointCollector**：按模型网格采样点，用于模型转点集。
- **ClientCameraUtil / ServerCameraUtil**：当前版本的客户端相机状态机与服务端控制入口。

### 6.1 先记一条链路

```text
ServerCameraUtil
  -> PacketCameraShakeS2C
  -> ClientCameraShakeHandler
  -> ClientCameraUtil
  -> CooParticleCameraMixin
```

可以把它理解成：

- 服务端只负责发“相机操作”
- 客户端把操作存成持续状态
- 原版 `Camera` 每帧再把这些状态真正应用上去

所以现在的 shake 不是“收到包就抖一下”，而是一个小型状态机。

### 6.2 服务端最常用的 4 个调用

爆炸冲击：

```kotlin
ServerCameraUtil.sendShake(
    world = serverLevel,
    origin = explosionCenter,
    range = 32.0,
    amplitude = 0.8,
    tick = 24,
    frequency = 6.0,
    attenuateByDistance = true
)
```

只对一个玩家抖：

```kotlin
ServerCameraUtil.sendShake(
    target = player,
    amplitude = 0.5,
    tick = 16,
    frequency = 4.0
)
```

把镜头往右后方拉一点：

```kotlin
ServerCameraUtil.setCameraOffset(
    target = player,
    positionOffset = Vec3(0.0, 0.8, -1.5),
    yawOffset = 12f,
    pitchOffset = -4f,
    instant = false
)
```

强制镜头看某个点：

```kotlin
ServerCameraUtil.forceCameraPosition(
    target = player,
    position = focusPoint,
    instant = false
)
```

清空全部相机效果：

```kotlin
ServerCameraUtil.resetCamera(player, instant = false)
```

### 6.3 客户端实际会发生什么

`PacketCameraShakeS2C` 现在不只是 shake 包，它有 6 种操作：

- `SHAKE`
- `SET_OFFSET`
- `RESET_OFFSET`
- `FORCE_POSITION`
- `RESET_FORCE_POSITION`
- `RESET_ALL`

`ClientCameraShakeHandler` 负责分流：

- `SHAKE`：先做 range 判断，再按 `attenuateByDistance` 衰减，最后调用 `ClientCameraUtil.startShakeCamera(...)`
- `SET_OFFSET` / `RESET_OFFSET`：更新手动偏移
- `FORCE_POSITION` / `RESET_FORCE_POSITION`：更新强制相机位置
- `RESET_ALL`：一次清空 shake、offset、forced position

`CooParticleCameraMixin` 再在原版相机上做 partial tick 插值，所以你看到的是平滑过渡，不是硬切。

### 6.4 3 个直接能抄的场景

场景 1：爆炸中心越远，抖得越轻。

```kotlin
ServerCameraUtil.sendShake(
    world = serverLevel,
    origin = boss.position(),
    range = 48.0,
    amplitude = 1.0,
    tick = 30,
    frequency = 5.5,
    attenuateByDistance = true
)
```

场景 2：技能读条时，让镜头持续偏一点。

```kotlin
ServerCameraUtil.setCameraOffset(
    target = player,
    positionOffset = Vec3(0.0, 0.2, -0.6),
    yawOffset = 6f,
    pitchOffset = -2f,
    instant = false
)
```

读条结束后恢复：

```kotlin
ServerCameraUtil.resetCameraOffset(player, instant = false)
```

场景 3：过场镜头暂时锁到指定位置。

```kotlin
ServerCameraUtil.forceCameraPosition(
    target = player,
    position = Vec3(100.0, 72.0, 100.0),
    instant = false
)
```

过场结束后恢复：

```kotlin
ServerCameraUtil.resetForcedCameraPosition(player, instant = false)
```

### 6.5 本地调试怎么测

不想先走网络时，客户端可以直接测 shake：

```kotlin
ClientCameraUtil.startShakeCamera(
    tick = 20,
    amplitude = 0.6,
    frequency = 4.0
)
```

停掉：

```kotlin
ClientCameraUtil.stopShakeCameraNow()
```

### 6.6 常见坑

- `range` 只在带 `origin + range` 的重载里有意义。玩家超出范围，客户端会直接忽略。
- `attenuateByDistance = true` 时，边缘玩家的效果会非常弱，这通常不是 bug。
- `instant = false` 表示平滑过渡，不是“没生效”。
- `resetCamera(...)` 会把 shake、offset、forced position 一起清掉。
- `setCameraOffset(...)` 是“相机偏移”，`forceCameraPosition(...)` 是“相机位置被接管”，两者不是一回事。

## 7. 物理与碰撞

- **PhysicsUtil**：射线碰撞、吸引力轨迹、速度修正等。
- **LinearResistanceHelper**：线性阻尼。

示例：

```kotlin
val nextVel = PhysicsUtil.nextAttractVelocity(pos, vel, target)
```

## 8. Helper / 动画辅助

这些 helper 用于在 `Style/Group/Composition` 内实现渐变缩放、透明度、序列动画等。

- **AlphaHelper / ScaleHelper / BezierValueScaleHelper**：透明度 / 缩放曲线控制
- **ProgressSequencedHelper**：顺序播放 / 回收的进度控制
- **SequencedAnimationHelper / SequencedCompositionAnimationHelper**：序列动画条件控制
- **StatusHelper**：显示 / 关闭状态控制（如 `CompositionStatusHelper`）
- **HelperUtil**：快速构建 helper 的工厂方法

提示：大多数 helper 需要在构造时调用 `loadControler(...)` 绑定对象，否则不会生效。

## 9. Buffer 注解工具

- **@ControlableBuffer**：标注 `Controlable` 中需要同步的字段
- **ControlableBufferHelper**：快速将字段转成 `ParticleControlerDataBuffer` 或反向写回

```kotlin
@ControlableBuffer("size")
var size = 1.0f
```

## 10. 反射与调试

- **ReflectUtil**：计时工具与常用 class 引用（`infoTimeWith` / `infoTimeCallable`）。

---

如果需要更细节的参数和行为，优先直接回看对应工具类、payload 和 mixin 的源码，因为相机控制这部分现在已经跨越了 util、network、client listener 和 mixin 多个层次。
