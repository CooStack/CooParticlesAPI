# Utils 工具库

`cn.coostack.cooparticlesapi.utils` 提供大量数学、几何、插值、渲染与动画工具。下面按场景整理常用类与使用方式。

## 1. 核心数据结构
- **RelativeLocation**：三维相对坐标/向量，支持 `+/-/*`、`normalize()`、`length()`、`toVector()` 等。
- **CircularQueue**：固定容量循环队列（常用于插值器）。
- **Memo**：惰性缓存容器，用于按需创建/重建数组。

## 2. 数学与几何
- **Math3DUtil**：几何生成与旋转工具集合（直线、圆、弧线、螺旋、闪电、柱体、贝塞尔曲线等）。
- **GraphMathHelper**：插值/平滑/衰减工具（`lerp`、`smoothStep`、`mix`、`distanceFalloff` 等）。
- **RotationMatrix**：轴角旋转矩阵工具。
- **MathDataUtil**：位标记（bitset）辅助工具，适合大规模序列状态存储。
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
- 输出为 `CompositionData` / `ParticleStyle` / `ControlableParticleGroup` 的数据结构

> 注意：`createWithoutClone()` 会返回内部列表引用，后续修改会影响 builder。

## 4. 图像与傅里叶工具
- **ImageUtil**：加载/缩放图片，转换为点集（含 alpha / RGBA）。
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
- **CircleParticleInterpolator**：圆周运动插值（可自定义旋转映射）。
- **InterpolatorDouble/Float/Vec3d/Vector3f/RelativeLocation**：带插值能力的数据包装类（支持 codec，可用于 `@CodecField`）。

示例：
```kotlin
val angle = InterpolatorDouble(0.0)
angle += Math.PI / 8
val current = angle.getWithInterpolator(lerpProgress)
```

## 6. 渲染与摄像机工具
- **MinecraftRendererUtil**：世界坐标变换、旋转、采样（`transformTo` / `applyRotation` / `sampleTriangle`）。
- **ClientCameraUtil / ServerCameraUtil**：客户端镜头抖动与服务端广播。
- **ModelPartPointCollector**：按模型网格采样点（用于模型转点集）。

示例：
```kotlin
ServerCameraUtil.sendShake(world, amplitude = 0.6, tick = 20)
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

- **AlphaHelper / ScaleHelper / BezierValueScaleHelper**：透明度/缩放曲线控制
- **ProgressSequencedHelper**：顺序播放/回收的进度控制
- **SequencedAnimationHelper / SequencedCompositionAnimationHelper**：序列动画条件控制
- **StatusHelper**：显示/关闭状态控制（如 `CompositionStatusHelper`）
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

如果需要更细节的参数与行为，请直接查阅对应类的源码注释。
