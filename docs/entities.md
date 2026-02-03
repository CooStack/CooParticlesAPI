# 实体框架：DisplayEntity / RenderEntity

> 回到索引：[`index.md`](index.md)

你想要的是“DisplayEntity 框架完整示例和应用”。这里按你点名的两类进行组织：

- `DisplayEntity`：偏“表现层”，用于挂载粒子/组合，并跟随世界中某个位置或实体
- `RenderEntity`：偏“渲染层”，用于客户端渲染桥接（例如与 shader pipe / 自定义渲染管线交互）

> 由于 GitHub blob/tree 页面在当前环境里无法稳定展开源码，本页以“框架使用方式 + 典型 API 结构”写法为主。  
> 你实际实现的字段/函数名请以 IDE 自动补全为准，但整体模式应该一致。

---

## 1) DisplayEntity：你怎么用它

### 典型用途
- 做一个“持续存在的粒子效果实体”（比如法阵、能量球、环绕特效）
- 由服务器生成 -> 同步到客户端 -> 客户端用组合/发射器持续生成粒子
- 可绑定某个实体（跟随移动）或固定在世界坐标

### 典型生命周期
1. 服务器：spawn DisplayEntity（或你的子类）
2. 服务器：塞入/更新一个 `ParticleComposition`（或 `SequencedParticleComposition`）
3. 客户端：收到同步后，tick 更新 -> 调用 emitters 生成粒子 -> 渲染

---

## 2) RenderEntity：你怎么用它

如果库把“渲染”抽成了 `RenderEntity`（名字来自你的需求）：
- 它更像一个“客户端渲染代理”
- 负责：
  - 读 composition/emitter 的状态
  - 与 shader pipe / render pipeline 对接
  - 做可见性裁剪 / 距离衰减 / 深度测试策略

---

## 3) 一个最小可跑模式（概念 Demo）

下面示例表达的是“怎么把 DisplayEntity + Composition 串起来”，不是 100% 逐字匹配你的类签名：

```kotlin
// 伪代码结构：请根据你的实际包名/类名修正 import

class DemoAuraDisplayEntity(
    level: net.minecraft.world.level.Level
) : cn.coostack.cooparticlesapi.entity.DisplayEntity(level) {

    // 服务器端：给这个 DisplayEntity 配一个组合
    fun setup() {
        val composition = DemoAuraComposition()
        this.setComposition(composition) // 可能叫 setComposition / composition = ...
    }

    override fun tick() {
        super.tick()
        // 如果你的 DisplayEntity 允许：在这里做跟随逻辑 / 位置插值等
    }
}
```

---

## 4) 推荐的“真实做法”：用完整示例对照

请直接看：[`framework-example.md`](framework-example.md)  
里面会把 DisplayEntity、Composition、Emitters、事件系统、自动注册串成一整条链。

---

相关文档：
- [组合系统：ParticleComposition / SequencedParticleComposition](compositions.md)
- [发射器：ParticleEmitters](emitters.md)
- [自定义 ShaderPipe](shaderpipe.md)
