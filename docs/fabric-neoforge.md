# Fabric / NeoForge 平台差异与注意事项

> 回到索引：[`index.md`](index.md)

这页只讲会踩坑的点。

---

## 1) 类扫描与自动注册

### NeoForge
- 通常可以依赖 NeoForge/Forge 风格的扫描机制（类似 EventBusSubscriber 的思路）。
- 你写好 `@EventListener` / `@CooAutoRegister`，在多数情况下启动时能自动发现并注册。

### Fabric
- **必须手动登记扫描包**：  
  `CooAPIScanner.registerPacket("你的包前缀")`  
  或 `CooAPIScanner.registerPacket(YourModMain::class.java)`  
  原因：Fabric 本体没有类似 NeoForge 的扫描 API，你的实现用 `ClassGraph` 做扫描，而为了避免扫描全 classpath 造成启动变慢，需要你显式指定扫描范围。

见 [`getting-started.md`](getting-started.md) 中的示例。

---

## 2) Dedicated Server（纯服务端）注意事项

- 如果你在 server side 触发粒子，但客户端没有装库/没有对应的 codec/渲染端支持，会出现：
  - 粒子不显示（理所当然）
  - 或客户端收到无法解码的数据（取决于你是否做了版本/存在性保护）

建议：
- 任何会往客户端发“自定义粒子数据包”的逻辑，都要：
  1. 确保客户端装了库
  2. 确保双方版本兼容
  3. 对“不支持”的客户端做降级（例如直接跳过 / 发送 vanilla 粒子）

---

## 3) Kotlin 入口与主类差异

你要求文档不写 `mods.toml`，因此这里只给**概念**：

- Fabric：在 `fabric.mod.json` 里声明 entrypoint；入口通常实现 `ModInitializer` / `ClientModInitializer`。
- NeoForge：一般由 `@Mod("modid")` + 构造器初始化；（`mods.toml` 是必需文件，但你不想在文档里展开，OK。）

写法差异不影响 CooParticlesAPI 的核心用法——**关键是初始化时机**要足够早（建议在 mod 初始化最开始注册扫描包、事件监听、自动注册）。

---

## 4) 推荐的初始化顺序（两端通用）

1. 平台入口（Fabric onInitialize / NeoForge mod ctor）  
2. （Fabric 必需）`CooAPIScanner.registerPacket(...)`  
3. 注册/初始化 CooParticlesAPI（如果库需要）  
4. 依赖 `@CooAutoRegister` 的话，确保扫描发生在 “使用任何效果之前”  
5. 开始使用：事件、发射器、DisplayEntity 等

---

下一篇：
- [事件系统：CooEventBus](event-bus.md)
- [注解：@CodecField / @CooAutoRegister](annotations.md)
