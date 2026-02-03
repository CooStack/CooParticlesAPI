# 自定义 ShaderPipe

> 回到索引：[`index.md`](index.md)

你要“自定义 ShaderPipe 的完整示例和应用”。  
因为我们无法稳定展开源码，这里按典型渲染管线设计给出可落地写法，并在文末告诉你怎么对照你的实际接口。

---

## 1) ShaderPipe 是什么（按框架抽象）

ShaderPipe 通常是一个“渲染管线插槽”：
- 负责把一组“要渲染的数据”喂给 shader
- 定义生命周期：
  - `setup()`：创建/绑定 shader、初始化 uniform
  - `beginFrame()`：每帧开始
  - `render(...)`：渲染调用（可能多次）
  - `endFrame()`：收尾
  - `close()`：释放资源

它常和 `RenderEntity` 配合（见 [`entities.md`](entities.md)）。

---

## 2) 典型接口（伪代码）

```kotlin
interface ShaderPipe {
    fun setup()
    fun beginFrame(partialTicks: Float)
    fun render(renderEntity: Any, partialTicks: Float)
    fun endFrame()
    fun close()
}
```

> 你实际项目里的方法名/参数类型可能不同，但“阶段”基本跑不掉。

---

## 3) 一个可复用的实现骨架

```kotlin
class DemoGlowShaderPipe : ShaderPipe {

    override fun setup() {
        // 1) 取 shader（资源 id / json / glsl）
        // 2) 编译/链接
        // 3) uniform location 缓存
    }

    override fun beginFrame(partialTicks: Float) {
        // 设置全局 uniform（时间、相机矩阵等）
    }

    override fun render(renderEntity: Any, partialTicks: Float) {
        // 1) 根据 renderEntity 的数据写入 uniform/SSBO/UBO
        // 2) 提交 draw call
    }

    override fun endFrame() {
        // 清理状态
    }

    override fun close() {
        // 释放 shader/缓冲
    }
}
```

---

## 4) 怎么把 ShaderPipe 挂到框架上

通常有三种方式（任选一种对照你的实现）：

1. `RenderEntity` 内部持有一个 `ShaderPipe` 字段  
2. `ParticleComposition` 提供 “render pipeline” 插槽  
3. 全局注册表：`ShaderPipeRegistry.register("id", factory)`

你在 IDE 搜索关键词：
- `ShaderPipe`
- `Registry`
- `register`
- `pipeline`
就能找到你实际使用点。

---

## 5) 与粒子系统联动（建议）

- “粒子数据”里只做逻辑（位置、速度、寿命）
- “渲染管线”负责：
  - 发光、扭曲、噪声、屏幕空间效果
  - 深度测试/遮挡策略
  - 合批（减少 draw call）

---

下一篇：
- [实体框架：DisplayEntity / RenderEntity](entities.md)
- [完整示例：框架应用 Demo](framework-example.md)
