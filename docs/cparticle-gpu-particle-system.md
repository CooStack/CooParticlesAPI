# cparticle — GPU 粒子系统

`cn.coostack.cooparticlesapi.cparticle` 是基于 OpenGL 的 GPU 粒子系统:
**渲染** = 每系统一次 instanced draw (GL 3.1 + core/ARB divisor, `glDrawArraysInstanced`);
**模拟** = GL 4.3 或 ARB compute+SSBO；能力不足或 shader 初始化失败时，自动回退多线程 CPU SoA 模拟，数学保持一致。
设计目标: 10 万粒子稳定 60FPS+。

## 架构

```
CParticle              粒子基类(生成描述符) — 只在生成时存在, 属性打包进 SoA 后由 GPU 驱动
CParticleSystem        粒子池: SoA 存储(36 float/粒子, 与 GPU 布局 1:1) + GL 缓冲 + 力场 + 曲线
CParticleSystemManager 客户端总管: tick / 渲染 / 世界切换清理 / 空闲池回收
CParticleRenderLayer   渲染层 (与 TextureSheetsEnum 混合语义一一对应, 5 种)
force/CParticleForce   力场 (Gravity/EnvDrag/ExpDrag/Wind/Vortex/Attraction/Rotation/Noise/FlowField)
                       — 与内置 ParticleCommand 数学一致, CPU/GPU 共用同一份打包数据
compat/                emitter & composition 兼容层
```

- 模拟按客户端 tick 推进 (支持 tickrate 补偿), 渲染逐帧在 shader 内做 prev→cur 插值
- 粒子位置以"系统原点"存 float (双精度原点, 相机相对渲染), 远坐标无精度抖动
- 死槽位输出退化三角形, 不做压缩; 槽位复用有世代计数保护句柄
- 渲染阶段: 接入 `ParticleEngine.render`; Iris `MIXED` 模式按 opaque/translucent 分两次绘制
- GPU draw 前重新应用 Iris 的 `PARTICLES` / `PARTICLES_TRANS` shader，由 Iris 绑定对应的 gbuffer FBO

## 两种系统模式

| 模式 | 语义 | 位置权威 | 适用 |
|---|---|---|---|
| `SIMULATED` | 发射器: 力场驱动, fire-and-forget | GPU (或 CPU 模拟器) | emitter / 大规模散粒子 |
| `SCRIPTED` | composition: 句柄 teleport / 整组变换 | CPU | composition / 形状展示 |

## 与 Emitter 兼容 (emitter 是 Data)

`genParticles()` 返回的数据类型决定粒子走哪条路径。`ControlableCParticleData` 进入
SIMULATED CParticle system，普通 `ControlableParticleData` 保留原有 controller 和事件逻辑。
同一次生成可以混用两种 data。

```kotlin
val gpuTemplate = ControlableCParticleData().apply {
    updateMode = CParticleUpdateMode.STATIC
    blockCollision = true
    alphaCurve = CParticleCurve.fadeInOut()
}

override fun genParticles(lerpProgress: Float) = listOf(
    gpuTemplate.clone().apply {
        velocity = Vec3(0.0, -0.1, 0.0)
    } to RelativeLocation(),
    ControlableParticleData().apply {
        sign = 1
    } to RelativeLocation(1.0, 0.0, 0.0),
)
```

`textureSource = null` 表示继续使用当前 data 的 `effect`，不会请求 missing texture。显式
`textureSource` 是叠在原有 `sprite/effect` 轮廓上的额外纹理蒙版：基础纹理与蒙版各自解析、着色和
分批，片元阶段再相乘。旧 `sprite`、`effect` API 及其优先级保持不变。

```kotlin
val blockData = gpuTemplate.clone().apply {
    textureSource = textureOfBlock(
        state,
        randomCrop = true,
        applyTint = true,
        applyBrightness = true,
    )
}
val itemData = gpuTemplate.clone().apply {
    textureSource = textureOfItem(stack, modelSeed = 42, tintIndex = 0)
}
val customData = gpuTemplate.clone().apply {
    textureSource = textureOf(textureLocation, CParticleUv(0f, 0f, 0.5f, 0.5f))
}
val atlasData = gpuTemplate.clone().apply {
    textureSource = textureOfAtlas(atlasLocation, spriteLocation)
}
val animatedData = gpuTemplate.clone().apply {
    textureSource = textureAnimationOfAtlas(atlasLocation, frameLocations)
}
```

`textureOfEffect(effect)` 使用粒子图集和该 effect 的 `SpriteSet`。Block 使用
`BlockModelShaper.getParticleIcon`；Item 通过当前世界和 `modelSeed` 调用 `ItemRenderer.getModel`，只取
解析后模型的代表性 particle icon，默认 `tintIndex = 0`。ItemStack 在来源创建时复制。资源重载会
重新解析图集 UV、模型 override 和 custom model data；独立纹理缺失时回退 missing texture，并只警告一次。

`blockCollision` 是逐粒子 flag，不参与 system key。同一纹理 binding 下，开启和关闭碰撞的
粒子仍可合批。SIMULATED system 以原点附近的 64³ 方块占用网格做碰撞，每 5 tick 从已加载区块
刷新一次；system 原点到任一边缘至少有 24 格。因纹理拆开的 system 只要网格坐标相同，就会复用这份数据。粒子命中后停在表面前
0.07 格，并清除速度的法线分量。

这不是精确 `VoxelShape` 碰撞。任何非空碰撞形状都按完整方块处理，网格外不做碰撞，也不处理
实体和流体。需要精确台阶、栅栏、实体事件、`singleParticleAction` 或死亡回调的粒子，应返回普通
`ControlableParticleData`。GPU 容量不足时，CParticle 条目会被丢弃，不会自动改走 CPU。

测试 emitter 默认使用标准 `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` 透明混合，以便直接观察生命周期
alpha。`ADDITION_BLEND_TRANSLUCENT` 使用 `SRC_ALPHA / ONE`，大量粒子重叠会累加到饱和，看起来像
出生即完全不透明，但不会绕过 shader 的 alpha 曲线。

## 与 Composition 兼容 (composition 是 display)

`CParticleDisplayer` 实现现有 `ParticleDisplayer` 接口, 任何 composition 换个 displayer 即上 GPU;
teleport / rotate / scale / remove / 嵌套语义完整保留:

```kotlin
override fun getParticles(): Map<CompositionData, RelativeLocation> {
    val res = LinkedHashMap<CompositionData, RelativeLocation>()
    PointsBuilder().addBall(2.0, 40).create().forEach { rel ->
        // 一行: displayer 已接好 GPU 粒子
        res[CParticleCompositions.data(CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT) {
            size = 0.15f
            color = Vector3f(0.4f, 0.8f, 1f)
        }] = rel
    }
    return res
}
// 或手动: CompositionData().setDisplayerSupplier { uuid -> CParticleDisplayer.of(uuid) { ... } }
```

composition 每 tick 的 `toggleRelative()` teleport 会写入 SoA 并整段增量上传;
静止后自动收敛停止上传。GPU 粒子句柄不进 `controlerTicks`, 无逐粒子 tick 开销。
`CParticleDisplayer.of` 创建的粒子默认随 composition 存活；只有显式设置有限 `maxAge` 时才会自行过期。

需要逐粒子 action、精确 `VoxelShape` 或实体碰撞时，可以保留 GPU instanced 渲染，只把对应句柄放回 CPU tick：

```kotlin
CompositionData()
    .setDisplayerSupplier { uuid -> ParticleDisplayer.withCParticle(uuid) }
    .addCParticleInstanceInit {
        velocity = Vec3(0.0, -0.08, 0.0)
    }
    .addCParticleControlerInstanceInit {
        addPreTickAction {
            moveWithPhysics()
        }
    }
```

这条路径每 tick 都会查询 Minecraft 世界碰撞，适合少量特殊粒子。大量普通粒子不要注册 action，仍走无逐粒子 CPU tick 的路径。

## 直接使用 (脱离 emitter/composition)

```kotlin
// 散粒子
CParticleSystemManager.spawn(CParticle().apply {
    pos = target; velocity = Vec3(0.0, 0.1, 0.0); size = 0.2f; maxAge = 80
    sprite("minecraft", "glitter_0")
})

// 自建系统 + 力场 + 生命周期曲线
val sys = CParticleSystemManager.getOrCreateSystem(
    "my/effect", 131072, CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE,
    CParticleSystemMode.SIMULATED
)
sys.forces += CParticleForce.Gravity(0.04)
sys.forces += CParticleForce.Noise(strength = 0.05)
sys.alphaCurve = CParticleCurve.fadeInOut()
sys.scaleCurve = CParticleCurve.linear(1f, 0.3f)
repeat(100_000) { sys.spawn(CParticle().apply { pos = ...; velocity = ... }) }
```

## 生命周期曲线

`CParticleCurve` 的 key 是归一化生命周期位置，value 是该位置的倍率。`0f to 0f` 表示粒子出生时
倍率为 0，`1f to 1f` 表示生命周期结束时倍率为 1。比如：

```kotlin
CParticleCurve.fadeInOut(fadeIn = 0.12f, fadeOut = 0.82f)
```

它等价于 `of(0f to 0f, 0.12f to 1f, 0.82f to 1f, 1f to 0f)`：前 12% 生命周期从 0
线性升到 1，中间保持 1，82% 之后再降到 0。`fadeIn` 和 `fadeOut` 是时间点，不是淡入、淡出的时长。

标量曲线可以直接保留三次贝塞尔控制柄：

```kotlin
val smoothFade = CParticleCurve.bezier(
    BezierFloatKeyframe(time = 0.0, value = 0.0, outX = 8.0, outY = 0.0),
    BezierFloatKeyframe(time = 0.12, value = 1.0, inX = -4.0, inY = 0.0),
    BezierFloatKeyframe(time = 0.82, value = 1.0, outX = 6.0, outY = 0.0),
    BezierFloatKeyframe(time = 1.0, value = 0.0, inX = -6.0, inY = 0.0),
)

CParticle().apply {
    alphaCurve = smoothFade
    scaleCurve = smoothFade
    scaleXCurve = smoothFade
    scaleYCurve = smoothFade
}
```

`outX`、`inX` 是相对当前 key 的时间偏移，单位是百分比点，`8.0` 表示 `+0.08`；`outY`、`inY`
是相对当前 value 的偏移。每一段的 X 控制点必须留在两个 key 之间并保持单调，这样 GPU 才能稳定地反求
贝塞尔参数。标量曲线可用于逐粒子的 `alpha/scale/scaleX/scaleY`、系统的 `alpha/scale`，也可传给
`playVisualTransition` 和 `playAlphaTransition`。

颜色曲线共享一条时间轴，但 R、G、B 各有自己的值控制柄：

```kotlin
val colorShift = CParticleColorCurve.bezier(
    CParticleBezierColorKeyframe(
        time = 0.0,
        value = Vector3f(1f, 0.2f, 0.1f),
        outX = 25.0,
        outValueOffset = Vector3f(0f, 0.5f, 0.7f),
    ),
    CParticleBezierColorKeyframe(
        time = 1.0,
        value = Vector3f(0.1f, 0.3f, 1f),
        inX = -25.0,
        inValueOffset = Vector3f(0.4f, 0f, 0f),
    ),
)

particle.colorCurve = colorShift
system.colorCurve = colorShift
```

这些曲线在顶点 shader 中求值。线性曲线仍走 `mix`，只有贝塞尔曲线执行固定 10 次二分反求；
控制柄存入共享 appearance descriptor，不增加每粒子的 36-float 实例大小。

一条 GPU 曲线最多有 8 个 key。每个 appearance descriptor 占 73 个 `vec4` texel，并由内容去重。
不要给 DYNAMIC 粒子每帧创建一组从未出现过的控制柄；这会持续注册新 descriptor，并让 lookup table
反复整表上传。重复使用稳定的曲线对象或少量预先构造的曲线即可。

线性曲线继续使用原有网络格式，新版本可以读取旧版发送的线性数据。贝塞尔曲线使用扩展格式，发送端和
接收端都必须升级；旧客户端收到贝塞尔 payload 会拒绝解码，不能用于新旧版本混合联机。

## 性能要点

- 帧成本: 每系统 1 次 instanced draw + ~15 个 uniform; 400k 顶点对 GPU 可忽略
- tick 成本: GPU 模式 1 次 compute dispatch (100k 线程) + 仅新生成粒子的增量上传;
  CPU 回退 = ForkJoin 分块 ~1‑3ms + 整段 `glBufferSubData` (~11MB/tick, 224MB/s)
- 热路径零分配: 力场打包数组 / 上传 scratch / SoA 全部复用
- 方块碰撞: 每份共享网格使用 32KB CPU 位图、32KB 可复用上传缓冲和 32KB SSBO; 网格刷新成本与 64³ 单元有关, 与粒子数无关
- `ADDITION_BLEND*` 层无排序需求; `TRANSLUCENT` 层不做逐粒子深度排序 (与原版同级限制)

## 实例布局与批次

每个实例是 9 个 `vec4`，共 36 float / 144 bytes：

| vec4 | 内容 |
|---|---|
| 0 | position.xyz, age |
| 1 | previousPosition.xyz, maxAge |
| 2 | velocity.xyz, packedFlags |
| 3 | width, height, yaw, pitch |
| 4 | axis.xyz, roll |
| 5 | textureDescriptorId, visualAgeBase, randomSeed low/high |
| 6 | color.rgb, alpha |
| 7 | angularVelocity.xyz, epochTick |
| 8 | appearanceDescriptorId, speedLimit, maskDescriptorId, packedMaskTint |

`blockCollision` 使用 flags bit 15，不增加实例大小。相比旧 28-float 布局，每粒子增加 32 bytes；
30 万粒子的实例缓冲由约 32.0 MiB 增至约 41.2 MiB。启用碰撞的活动区域另有一份共享 32KB SSBO，
以及 32KB CPU 位图和 32KB 可复用上传缓冲。

系统查找键至少包含逻辑 system、`CParticleSystemMode`、`CParticleRenderLayer`、基础 binding 和可选蒙版
binding。同一 binding 内不同 sprite、BlockState、ItemStack 和 descriptor 仍在同一次实例化 draw 中；
不同 atlas 或独立纹理拆分 system。renderer 先按 layer，再按基础/蒙版 binding 排序，只在 binding
变化时绑定纹理，不会逐粒子 bind。不同逻辑 system 即使 binding 相同仍各自 draw。Emitter 根据当次生成的
CParticle 数量选择 segment 容量，下限为 16384，单段上限为 32767；写满后才会增加 system 和 draw。

## 生命周期挂载点

| 时机 | 位置 |
|---|---|
| tick | `CooParticlesAPIClient.tickClient` → `CParticleSystemManager.tick()` |
| 渲染 | 平台 mixin 在 `ParticleEngine.render` 关闭 lightmap 前调用 `renderParticlePass()`；draw 前重新应用当前粒子 shader |
| 断线/换世界 | `clearTransientClientState` → `clear()` |
| 资源重载 | shader 经 `ShaderProgramRegistry` 自动重建; 图集 UV 缓存清空 |

shader 资源: `assets/cooparticlesapi/shaders/core/{vertex,fragment,compute}/cparticle*`,
支持 F3+T 热重载 (managedId: `cparticle/render`, `cparticle/simulate`)。

## 可运行示例

两个用例已注册进 `BlockAPITestGroupBuilder`(测试方块的 API 测试组), 参数均可在测试界面实时编辑:

| 文件 | 演示内容 |
|---|---|
| `test/options/particle/emitter/TestCParticleEmitter.kt` | emitter 侧: `ControlableCParticleData` + `cparticleForces()` 龙卷风和方块占用网格碰撞; 默认稳态 `600 × 170 ≈ 10.2 万` 粒子 |
| `test/options/particle/composition/TestCParticleComposition.kt` | composition 侧: 槽位 displayer 换成 `CParticleCompositions.data`, 多层旋转法阵 (默认 4×320 槽位) |

调 `每tick生成数` / `粒子存活tick` 即可线性调节负载做压测。
emitter 的 system 分段写满后会继续创建分段，达到全局数量上限的生成请求会被丢弃；composition 的共享池也会自动增加分段。
直接调用 `CParticleSystem.spawn` 时，池满仍返回 `-1`，调用方可以自行决定丢弃或回退。

> ⚠️ 顶层**同步** composition (`AutoParticleComposition`) 里挂自转要加 `if (client)` 判断:
> `onDisplay()` 两端都执行, 服务端 `rotateAsAxis` 每 tick 发一个 rotate 包,
> 客户端会既跑本地 tick 旋转、又应用收到的包 → 实际转速翻倍。
> 嵌套的 `ParticleShapeComposition` 是纯客户端, 不受此影响。
