# ShaderPipe 使用文档

`ShaderPipe` 是后处理渲染管线框架。核心由三部分组成：
- **ShaderPipe**：单个渲染管道（一次 pass）
- **ShaderPipeManager**：管理多个 pipe，并通过有向图连接输入/输出
- **PipeLinker / PipeLinkDsl**：描述 pipe 之间的通道连接关系

## 1. 基础流程
1. 创建 `ShaderPipeManager`（指定 `pipeID`）
2. 设置 `valueInput` / `valueOutput`
3. 使用 `setLinkerFunc { linker -> ... }` 描述连接关系
4. 注册到 `ClientRenderPipelineManager`
5. 每帧执行 `writeFrame { ... }` 与 `render()`

## 2. 最小示例
```kotlin
val manager = ShaderPipeManager(ResourceLocation.fromNamespaceAndPath("yourmod", "my_pipe"))
    .addTransformUniformMatrix()
    .setLinkerFunc { linker ->
        val input = valueInput(
            SimpleShaderPipe(
                IdentifierShader(ResourceLocation.fromNamespaceAndPath("yourmod", "pipe/frag/basic.fsh"), GlShaderType.FRAGMENT),
                { minecraft.mainRenderTarget.depthTextureId }
            )
        )
        val output = valueOutput(
            SimpleShaderPipe(
                IdentifierShader(ResourceLocation.fromNamespaceAndPath("yourmod", "pipe/frag/output.fsh"), GlShaderType.FRAGMENT),
                { minecraft.mainRenderTarget.depthTextureId }
            )
        )
        linker.from(input, 0).to(output, 0)
    }

ClientRenderPipelineManager.register(manager)
```

渲染阶段：
```kotlin
manager.writeFrame {
    // 把世界/实体渲染内容写入 input pipe
}
manager.render()
```

## 3. PipeLink DSL
为了更可读：
```kotlin
linker.from(outputPipe, 0).to(inputPipe, 1)
```
含义：`outputPipe` 的第 0 通道 -> `inputPipe` 的第 1 通道。

## 4. 常用内置 Pipe
- **SimpleShaderPipe**：单次 pass 的基础 pipe
- **PingPongShaderPipe**：双 FBO 往复，多次模糊/迭代
- **MCHookedShaderPipe**：挂接 MC 的 RenderTarget
- **OutputDepthPipe**：只输出深度通道
- **TextureShaderPipe**：用指定纹理作为输出通道

## 5. 全局 Uniform
`ShaderPipeManager` 可在每个 pipe 执行前注入全局 uniform：
```kotlin
manager.addTransformUniformMatrix() // transMat/viewMat/projMat
manager.updateGlobalUniform("viewMat", viewMatrix)
```

## 6. 管线初始化
- `setLinkerFunc { ... }` 是必填，否则会抛出 `RenderPipeLinkerNotSetException`。
- `valueOutput(...)` 必须设置，否则 `render()` 时会抛出 `RenderPipeOutputNotSetException`。
- `ClientRenderPipelineManager.init()` 会为所有注册管线初始化 depth 纹理与 FBO。

## 7. 与 RenderEntity 结合
`ClientRenderEntityManager.bindEntityRenderPipe(...)` 可将某类 `RenderEntity` 绑定到指定管线：
```kotlin
ClientRenderEntityManager.bindEntityRenderPipe(MyRenderEntity.ID, ShaderPipeManagers.simpleBloom.pipeID)
```

## 8. 参考实现
- `ShaderPipeManagers.default`：最简默认输出
- `ShaderPipeManagers.simpleBloom`：多 pass 的泛光示例
