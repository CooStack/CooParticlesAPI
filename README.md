# CooParticlesAPI

一个面向 Fabric / NeoForge 的粒子与渲染框架，提供组合粒子、发射器、事件系统、DisplayEntity、RenderEntity 和 Pipeline 渲染图。

## 本模组目前提供了3套粒子系统

1. 基于原版MC系统修改的 ControlableParticle -> CPU粒子 (此系统有我全权实现 AI帮我修复了一点BUG)
2. 基于原版TextureSheet逻辑的 CParticle -> GPU粒子（此系统由AI完全实现）
3. 基于Blender的 CooFx Particle系统 用于兼容Blender的全套逻辑（此系统由AI完全实现）（测试中） 

## 提供了一个Forge类似的Listener事件系统

`CooEvent` 提供相同的扫描方案，但是限制会比较少, 比较自由 (此系统由我全权实现， AI只是修复了一些我没考虑到的BUG)

## 提供了一个模型渲染方案

`RenderEntity` 与 `PipeLine` 提供自定义模型渲染和后处理 (此系统由我实现，但是经过AI 两次重构，应该已经变成AI的形状了)

## 提供Blender模型与摄像机动画方案

`CooFx (测试中）` 基于上述的Shader框架 实现的额外一套 Blender Bridge（此系统由AI完全实现）

其中`CooFxParticleSystem`不会主动兼容先前针对1,2的 `Composition`， `Emitter`等 2，3 完全由GPT完成，我没有参与任何的审查（个人能力有限，按照自己的理解写的提示词，所以质量应该一般
测试面也不会那么全 对于3的粒子系统不建议自己写， 有BUG可以修复并且提交PR 我会大致审查然后合并的）

按照我的提示词和Skill要求的代码风格，这些由GPT完成的API大多数都有较为详细的注释，
所以代码理解应该不会那么复杂

本项目开发AI部分的Skill: [Minecraft-Modding-Skill](https://github.com/CooStack/minecraft-modding-skill)

# 开发现状
由于各种原因， 懒，没方向等 我对高等数学，线性代数上面的了解可以说是0，因此我并不会计算机图形学的数学能力，总之我的数学能力烂完了

所有着色相关的都是交由AI完成，我正在学习Blender动画，所以基于3很有可能会大改（在我对动画，特效组成了解更深之后）

如果你是开发，不建议你使用这个API，
因为他完全不符合一个API应该有的特质，它很臃肿，正是因为我把太多的功能强行塞入里面，
这里有我几年前的插件框架计算部分的思路，总之是把很多个功能缝合在了一起，缺什么就让AI补什么
不过我的要求是兼容IRIS，所以你可以发现这里的绝大多数自定义着色都是支持IRIS的光影处理的

并且对于框架我还会进行多次破坏兼容性更新，这导致了我的模组大多数都是不兼容旧版，或者不兼容新版的

如果你想学习，你可以用AI或者自己去看对应的实现思路，可能会对你有所帮助

## 为什么还要维护这个项目？

- 这个项目是我自媒体模组开发的必要工具， 他可以节约我很多很多的时间
- 对于粒子组合制作，从0写相同的框架时间开销是很大的，并且这是完全重复的内容
- 我可以选择这里已经实现的内容进行特效制作更新，这很有用，我可以用更少的成本呈现出更好看的内容
- 所以所有的性能优化都是基于我目前的配置来的，对于低端机用户并不友好（我也没有办法测试）

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
- Pipeline / RenderEntity / ShaderEffect：[
  `docs/shader-renderentity-post-framework.md`](docs/shader-renderentity-post-framework.md)
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
