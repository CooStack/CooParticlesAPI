# CooParticlesAPI

一个面向 Fabric / NeoForge 的粒子与渲染框架，提供组合粒子、发射器、事件系统、DisplayEntity / RenderEntity、ShaderPipe 等能力。

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
- RenderEntity：[`docs/render-entity.md`](docs/render-entity.md)（`RenderEntity` 只负责模型与效果输入；屏幕后处理交给 pipeline）
- Shader / RenderEntity / Post 开发框架：[`docs/shader-renderentity-post-framework.md`](docs/shader-renderentity-post-framework.md)
- ShaderPipe：[`docs/shader-pipe.md`](docs/shader-pipe.md)
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
