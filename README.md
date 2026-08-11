# CooParticlesAPI

一个面向 Fabric / NeoForge 的粒子与渲染框架，提供组合粒子、发射器、事件系统、DisplayEntity、RenderEntity 和 Pipeline 渲染图。

## Render API 快速开始

不需要后处理：

```kotlin
override val pipeline = CooPipelines.DEFAULT
```

使用内置 mask bloom，并按实体状态提供动态 uniform：

```kotlin
override val pipeline = CooPipelines.MASK_BLOOM
    .blurSigma(15F)
    .blurRange(10F)
    .bloomThreshold(0F)
    .intensity { entity: MyRenderEntity ->
        2.8F * entity.bright.coerceAtLeast(0F)
    }
```

`MASK_BLOOM` 使用 `bloom_gaussian_blur.fsh` 做可分离高斯模糊，横向和纵向通过布尔 uniform `Horizontal` 在同一个 shader 中交替执行 10 次 ping-pong。默认 `bloomThreshold(0F)` 不做亮部过滤，mask 中的全部颜色都会进入 Bloom 通道；传入正数则启用亮部筛选，`bloomSoftKnee(...)` 控制过渡范围。

把真实世界方块绑定到 terrain Pipeline：

```kotlin
CooBlockPipelines.bind(Blocks.STONE, STONE_PIPELINE)

CooBlockPipelines.bind(MyBlocks.ENERGY_BLOCK) { state ->
    if (state.getValue(POWERED)) ENERGY_PIPELINE else CooPipelines.BLOCK_DEFAULT
}
```

只有命中绑定的方块会进入自定义 Pipeline，其余方块继续使用原版 terrain 渲染。

注册并播放独立屏幕效果：

```kotlin
val HEAT_HAZE = CooShaderEffects.register(id("heat_haze")) {
    fragment(id("post/heat_haze.fsh"))
    inputSceneColor("SceneColor")
    inputSceneDepth("SceneDepth", optional = true)
    outputToScreen()
}

HEAT_HAZE.play {
    duration(30)
    uniform("strength", 0.12F)
    uniform("radius", 0.35F)
}
```

按位置批量设置持久或临时的方块效果。整组只发送一次 Pipeline、uniform 和位置表；扩大、删位置或改共享参数时只发送对应的增量包：

```kotlin
val group = CooTerrainEffectGroup(id("charged_area"), ENERGY_PIPELINE) {
    positions(areaBlocks)
    uniform("EffectStrength", 0.8F)
    duration(120) // 不设置则一直持续到 remove
}
CooTerrainEffectManager.apply(level, group)
CooTerrainEffectManager.append(level, group.id, newBlocks)
CooTerrainEffectManager.removePositions(level, group.id, oldBlocks)
```

公开渲染入口是 `CooPipelines`、`CooBlockPipelines` 和 `CooShaderEffects`；位置效果使用 `CooTerrainEffectGroup` 与 `CooTerrainEffectManager`。方块 terrain 的 GLSL 由 `shader(...)` 指定。JSON 不是用户输入，客户端只在 Vanilla/Iris 桥接层按 Pipeline 临时生成内存 descriptor，仓库不提供 `minecraft` 命名空间下的 Coo JSON。

BlockTest 中的传播示例注册在 `BlockAPITestGroupBuilder`，Option ID 为 `block-texture-propagation`。它从 `BlockTestPlayer` 所在位置开始，每 5 tick 推进一层，前 60 tick 传播，再用 60 tick 按 10 tick 一个阶段恢复原纹理：

```kotlin
BlockTexturePropagationTestOption(player)
```

该 Option 使用 `BlockUtil.BlockStepSpareData`，只负责发现位置并调用通用效果组 API。地形编译器只为命中的 BlockPos 创建批量管线，同一阶段共享 RenderType，不会为每个方块单独绘制。

多 pass、FBO attachment、PingPong 卡片和 EffectUV 见 [Shader / Render 文档](docs/shader-renderentity-post-framework.md)。

> 给玩家：这是一个粒子库。只有当服务端与客户端都需要同步粒子表现时，才需要双方都安装。  
> 如果只是客户端本地特效（不需要服务端同步），通常只装客户端即可。

---

## 玩家告知

### 安装

#### Fabric
需求：
1. Fabric API  
2. Fabric Language Kotlin  
3. Minecraft 1.21.1  

把模组 jar 放进 `mods/` 即可。

#### NeoForge
需求：
1. Kotlin for Forge  
2. Minecraft 1.21.1  

把模组 jar 放进 `mods/` 即可。

---

## 开发者快速开始
1. 在你的 mod 中引入依赖（见下方“仓库/依赖设置”）。
2. 直接使用 `@CooAutoRegister` + `@CodecField` 的 Auto 类（如 `AutoParticleEmitters` / `AutoParticleComposition`）。
3. 自动注册无需手动扫描；Fabric 只需注册包：

### Fabric 扫描
```kotlin
CooAPIScanner.registerPacket("your.mod.package")
```

### NeoForge
无需额外调用。


---

## 文档导航
- ParticleComposition：[`docs/particle-composition.md`](docs/particle-composition.md)
- AutoParticleEmitters：[`docs/auto-particle-emitters.md`](docs/auto-particle-emitters.md)
- CodecField 自动注册：[`docs/codecfield-auto-register.md`](docs/codecfield-auto-register.md)
- CooEventBus：[`docs/event-bus.md`](docs/event-bus.md)
- DisplayEntity：[`docs/display-entity.md`](docs/display-entity.md)
- Pipeline / RenderEntity / ShaderEffect：[`docs/shader-renderentity-post-framework.md`](docs/shader-renderentity-post-framework.md)
- Utils：[`docs/utils.md`](docs/utils.md)

---

## 仓库/依赖设置

### 仓库（Gradle）
```gradle
repositories {
    maven {
        name = "jsdu"
        url = "https://nexus.jsdu.cn/repository/maven-public/"
    }
}
```

### 依赖（Gradle）
版本号请以最新 Release 为准。
```gradle
dependencies {
    // neoforge
    implementation("cn.coostack:cooparticlesapi-neoforge:<version>")
    // fabric
    implementation("cn.coostack:cooparticlesapi-fabric:<version>")
}
```
