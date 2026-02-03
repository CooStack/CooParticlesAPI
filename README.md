# CooParticlesAPI

> 给玩家：这是一个粒子库。**只有当服务端与客户端都需要同步粒子表现时，才需要双方都安装。**  
> 如果只是客户端本地特效（不需要服务器同步），通常只装客户端即可。  

---

## 玩家须知

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

## 开发者：文档导航
> 你最好看看对应的类的定义
> 
> 用AI写的DOC， 全是幻觉 不过有些是对的 文档我有空再去修改一下

- **快速上手**：[`docs/getting-started.md`](docs/getting-started.md)
- **平台差异（Fabric / NeoForge）**：[`docs/fabric-neoforge.md`](docs/fabric-neoforge.md)
- **事件系统（CooEventBus）**：[`docs/event-bus.md`](docs/event-bus.md)
- **注解（@CodecField / @CooAutoRegister）**：[`docs/annotations.md`](docs/annotations.md)
- **实体框架（DisplayEntity / RenderEntity）**：[`docs/entities.md`](docs/entities.md)
- **组合系统（ParticleComposition / SequencedParticleComposition）**：[`docs/compositions.md`](docs/compositions.md)
- **发射器（ParticleEmitters）**：[`docs/emitters.md`](docs/emitters.md)
- **自定义 ShaderPipe**：[`docs/shaderpipe.md`](docs/shaderpipe.md)
- **Utils（不含 buffers）**：[`docs/utils.md`](docs/utils.md)
- **完整示例（从 0 到可跑）**：[`docs/framework-example.md`](docs/framework-example.md)

> 本文档集**不覆盖** `ParticleGroup` / `ParticleStyle`（按你的要求排除）。  
> 其它你点名的模块都在上面的文档里。

---

## 仓库/依赖设置（保留原 README 的“仓库设置”信息）

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

下一步建议直接看：[`docs/getting-started.md`](docs/getting-started.md)
