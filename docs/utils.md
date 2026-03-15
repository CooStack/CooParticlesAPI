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

### 6.1 Camera operation 协议

现在的 `Shake` 相关代码已经不是“单一 helper + 一个 shake 包”。
完整链路是：

1. `ServerCameraUtil` 在服务端构造相机操作。
2. `PacketCameraShakeS2C` 用 `CameraOperation` 承载实际操作类型和参数。
3. `ClientCameraShakeHandler` 在客户端按操作类型分流。
4. `ClientCameraUtil` 维护持续状态，并在客户端主 tick 中推进。
5. `CooParticleCameraMixin` 在原版 `Camera` 上读取当前状态，并用 partial tick 做插值应用。

这意味着当前相机系统已经是一个完整的 protocol，而不是“收到包立刻算完一次抖动”的一次性逻辑。

### 6.2 `PacketCameraShakeS2C`

当前协议支持 6 类操作：

- `SHAKE`
- `SET_OFFSET`
- `RESET_OFFSET`
- `FORCE_POSITION`
- `RESET_FORCE_POSITION`
- `RESET_ALL`

同一个 payload 会携带：

- shake 参数：`range`、`origin`、`amplitude`、`tick`、`frequency`、`attenuateByDistance`
- offset / force-position 参数：`position`、`yawOffset`、`pitchOffset`、`instant`

因此这份包实际上承载的是“统一 camera operation 协议”，不是只为 shake 服务。

### 6.3 `ServerCameraUtil`

`ServerCameraUtil` 的 `sendShake(...)` 现在按场景提供多组重载：

- 整个世界广播
- 整个世界广播并指定频率
- 指定中心点和作用范围
- 指定中心点、范围、频率，并可选距离衰减
- 单玩家版本的所有重载

除此之外还提供：

- `setCameraOffset(...)` / `resetCameraOffset(...)`
- `forceCameraPosition(...)` / `resetForcedCameraPosition(...)`
- `resetCamera(...)`

当前版本最应该记住的不是某一个具体重载，而是它的能力边界：

- 服务端只负责发“操作”
- 距离衰减发生在客户端
- 平滑过渡还是立即生效由 `instant` 控制

### 6.4 `ClientCameraUtil`

`ClientCameraUtil` 现在是一个三段式状态机：

1. **manual offset**
   - `manualTargetPosOffset`
   - `manualTargetYawOffset`
   - `manualTargetPitchOffset`
2. **procedural shake**
   - `shakeDuration`
   - `shakeFrequency`
   - `shakePhase`
   - `shakeTargetPosOffset / shakeTargetYaw / shakeTargetPitch`
3. **forced camera position**
   - `forcedCameraPositionTarget`
   - `forcedCameraPositionCurrent`
   - `forcedPositionBlendTarget`
   - `forcedPositionBlendCurrent`

`tick()` 每次客户端逻辑更新都会推进这三段状态，然后同步回 legacy-style 的公开偏移字段。

shake 本身也不再是旧版固定 retarget：

- `shakeEnvelope()` 控制包络衰减
- `shakePhaseStep(frequency)` 决定目标更新步长
- `sampleShakeNoise(...)` 生成确定性的噪声采样
- `shakeFollowFactor(frequency)` 让高频 shake 拥有更高的跟随率

所以当前 shake 的正确理解应该是：

- “包络 + 噪声 + 跟随率”的程序化抖动
- 而不是“每 tick 改一次随机 yaw / pitch”

### 6.5 `ClientCameraShakeHandler`

客户端监听器的职责是协议分流，而不是保存长期状态：

- `SHAKE`：先做 range 判断，再根据 `attenuateByDistance` 在客户端衰减振幅和频率，最后调用 `ClientCameraUtil.startShakeCamera(...)`
- `SET_OFFSET / RESET_OFFSET`：切到手动 offset 状态
- `FORCE_POSITION / RESET_FORCE_POSITION`：切到强制相机位置状态
- `RESET_ALL`：统一清空 shake、offset、forced position

### 6.6 `CooParticleCameraMixin`

`CooParticleCameraMixin` 是最终把状态打到原版相机上的桥：

- `tick` 注入：读取 `ClientCameraUtil` 的当前总偏移和强制位置权重
- `setup` 注入：按 partial tick 在“上一帧目标”和“这一帧目标”之间插值

这就是为什么当前相机系统看起来是平滑的，而不是网络包一到就硬切。

### 6.7 常用调用示例

范围抖动并按距离衰减：

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

设置一个持续偏移：

```kotlin
ServerCameraUtil.setCameraOffset(
    target = player,
    positionOffset = Vec3(0.0, 0.8, -1.5),
    yawOffset = 12f,
    pitchOffset = -4f
)
```

强制镜头贴到某个位置：

```kotlin
ServerCameraUtil.forceCameraPosition(
    target = player,
    position = focusPoint,
    instant = false
)
```

本地测试直接触发 shake：

```kotlin
ClientCameraUtil.startShakeCamera(tick = 20, amplitude = 0.6, frequency = 4.0)
```

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
