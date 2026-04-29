# Shader / RenderEntity / Post 开发框架

适用范围：CooParticlesAPI-MultiPlatform 当前 RenderEntity V2、ShaderProgram、Texture、RenderEffectGraph、PostEffectChain 体系。

本文档面向框架接入、效果开发、问题排查。RenderEntity 负责服务端同步和客户端实例生命周期；客户端渲染由 Renderer 完成；帧尾效果由 RenderEffectGraph 收集后交给 PostEffectFrameExecutor；独立后处理由 PostEffectType / PostEffectChain 描述并由 CooPostEffects 调度。

## 1. 框架分层

| 层级 | 主要类型 | 责任 |
| --- | --- | --- |
| 同步实体 | `RenderEntity`、`AutoRenderEntity` | 跨端同步位置、年龄、取消状态、业务字段 |
| 客户端实例 | `RenderEntityInstance` | 持有客户端镜像实体、Renderer、可见性、tick 状态 |
| 客户端 Renderer | `RenderEntityRenderer` 及子接口 | 声明渲染能力、初始化资源、执行世界渲染、提交帧尾效果 |
| 模型 DSL | `RenderEntityModelBuilder`、`RenderEntityModel` | 描述点、三角形、四边形、材质管线和元数据 |
| Shader | `AdvancedShaderProgramBuilder`、`SimpleShaderProgram`、`CooShaderProgram` | 编译、链接、绑定 uniform、执行绘制 |
| 纹理 | `IdentifierTexture`、`SimpleTextures`、`SpriteSheetTexture` | 加载资源纹理、绑定 sampler、精灵图帧选择 |
| 帧尾效果 | `RenderContribution`、`RenderEffectDescriptor`、`RenderEffectGraph` | Renderer 向帧级后处理提交效果描述 |
| Post 链 | `PostEffectType`、`PostEffectChain`、`PostEffectPassRef` | 描述多 pass 后处理、输入输出、pass 拓扑链接 |
| 执行后端 | `OpenGlPostEffectExecutionBackend`、`PostEffectFrameExecutor` | 按拓扑顺序执行 fsh pass，维护 pass 输出纹理 |

## 2. RenderEntity 基础生命周期

`RenderEntity` 是同步对象，不等同于 Minecraft 原生 `Entity`。服务端创建后，通过 `ServerRenderEntityManager` 向可见玩家发送 CREATE / TOGGLE / REMOVE 包；客户端解码后创建 `RenderEntityInstance`，再由注册的 Renderer 处理视觉表现。

基础字段包括：

- `uuid`：跨端唯一标识。
- `pos`：世界位置。
- `canceled`：取消标记。
- `age`：tick 年龄。
- `world`：运行时所在世界引用。
- `durationTicks`：非空时到期自动取消。
- `renderRange`：服务端可见性同步范围。

基础实现方式：

```kotlin
class ArcOrbEntity(
    world: Level?,
    pos: Vec3,
    private var radius: Float,
    private var color: Vector4f
) : RenderEntity(world, pos) {

    override fun getRenderID(): ResourceLocation = ID

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = CODEC

    override fun loadProfileFromEntity(old: RenderEntity) {
        super.loadProfileFromEntity(old)
        if (old is ArcOrbEntity) {
            radius = old.radius
            color = Vector4f(old.color)
        }
    }

    fun radius(): Float = radius
    fun color(): Vector4f = color

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "arc_orb")

        val CODEC: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            factory = { ArcOrbEntity(null, Vec3.ZERO, 1.0f, Vector4f(1f, 1f, 1f, 1f)) },
            encodeExtra = { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.color.x)
                buf.writeFloat(entity.color.y)
                buf.writeFloat(entity.color.z)
                buf.writeFloat(entity.color.w)
            },
            decodeExtra = { buf, entity ->
                entity.radius = buf.readFloat()
                entity.color = Vector4f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )
    }
}
```

`RenderEntity.createCodec` 已包含 `uuid`、`pos`、`canceled`、`age` 的基础同步字段，业务字段通过 `encodeExtra` / `decodeExtra` 追加。字段变化后通过 `tracked(...)` 或显式逻辑标记 dirty，服务端同步包到达客户端后会调用 `loadProfileFromEntity` 将新状态合并到客户端对象。

`AutoRenderEntity` 适合字段较多的同步实体。字段加 `@CodecField` 后，框架负责 codec 和 profile 合并字段：

```kotlin
@CooAutoRegister
class ArcPulseEntity() : AutoRenderEntity(null, Vec3.ZERO) {

    constructor(world: Level?, pos: Vec3) : this() {
        this.world = world
        this.pos = pos
    }

    @CodecField
    var radius: Float = 2.0f

    @CodecField
    var intensity: Float = 1.0f

    override fun getRenderID(): ResourceLocation = ID

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "arc_pulse")
    }
}
```

自动注册只覆盖 RenderEntity codec。客户端 Renderer 仍需要显式注册。

## 3. 注册 RenderEntity 与 Renderer

客户端接收 CREATE 包时，会先通过 `ClientRenderEntityRegistry` 解码实体，再解析 Renderer：

1. 实体自身实现 `RenderEntityRenderer` 时，直接使用实体作为 Renderer。
2. 注册表存在 rendererFactory 时，创建 Renderer。
3. 两者都不存在时抛出 `RenderEntity renderer not registered: <id>`。

常见注册方式：

```kotlin
ClientRenderEntityRegistry.register(
    ArcOrbEntity.ID,
    ArcOrbEntity.CODEC
) { ArcOrbRenderer() }
```

或在 codec 已由自动注册处理时，仅注册 Renderer：

```kotlin
ClientRenderEntityRegistry.registerRenderer(ArcPulseEntity.ID) {
    ArcPulseRenderer()
}
```

服务端创建实体：

```kotlin
val entity = ArcPulseEntity(serverLevel, center).apply {
    radius = 4.0f
    intensity = 0.8f
    durationTicks = 80
    renderRange = 96.0
}
entity.spawn(serverLevel, center)
```

`spawn` 只在 `ServerLevel` 上生效。客户端世界直接调用不会进入同步管理器。

## 4. Renderer 能力声明

`RenderEntityRenderer<T>` 是基础接口。Renderer 通过 `describeFeatures` 声明需要的阶段、场景资源和后处理能力，通过 `initialize` 初始化 GL 资源，通过具体子接口执行渲染。

常用子接口：

| 接口 | 用途 |
| --- | --- |
| `WorldPassRenderEntityRenderer<T>` | 在世界渲染阶段直接绘制实体 |
| `FramePostRenderEntityRenderer<T>` | 在帧尾提交后处理贡献 |
| `RenderEntityModelRenderer<T>` | 使用模型 DSL 构建几何体 |
| `SharedModelMaskBloomRenderEntityRenderer<T>` | 世界模型和 mask bloom 复用同一个模型 |
| `DedicatedGlowMaskRenderEntityRenderer<T>` | 世界绘制和发光 mask 分离 |

能力声明示例：

```kotlin
class ArcOrbRenderer : WorldPassRenderEntityRenderer<ArcOrbEntity>,
    FramePostRenderEntityRenderer<ArcOrbEntity> {

    override fun describeFeatures(entity: ArcOrbEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS, RenderFrameStage.FRAME_POST),
            requestedSceneTargets = setOf(RenderSceneTargets.SCENE_COLOR, RenderSceneTargets.SCENE_DEPTH),
            effectTypes = setOf(BuiltinRenderEffectTypes.MASK_BLOOM),
            localRendererEnabled = true,
            effectGraphEnabled = true
        )
    }

    override fun initialize(instance: RenderEntityInstance<ArcOrbEntity>) {
        ensureResources()
    }

    override fun renderLocal(input: LocalRenderInput<ArcOrbEntity>) {
        renderWorldGeometry(input)
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<ArcOrbEntity>,
        collector: RenderContributionCollector
    ) {
        submitFramePostEffects(input, collector)
    }
}
```

`requestedSceneTargets` 决定后端是否准备 scene color / depth。效果需要采样屏幕或深度时，应在这里声明，否则某些后端不会提供对应纹理。

## 5. 使用 RenderEntityModel 构建基础模型

`RenderEntityModelBuilder` 用于声明几何体、管线参数、post 元数据。适合粒子面片、能量罩、简单网格等结构化模型。

```kotlin
class ArcModelRenderer : RenderEntityModelRenderer<ArcPulseEntity> {

    override fun initialize(instance: RenderEntityInstance<ArcPulseEntity>) = Unit

    override fun buildModel(entity: ArcPulseEntity, tickDelta: Float): RenderEntityModel {
        return RenderEntityModelBuilder().apply {
            val pipe = pipe("arc_shell") {
                param("alpha", 0.55f)
                param("radius", entity.radius)
            }

            val color = Vector4f(0.3f, 0.85f, 1.0f, 0.65f)
            val r = entity.radius

            addQuad(
                pipe,
                RenderEntityModelVertex(Vector3f(-r, 0f, -r), color, Vector2f(0f, 0f)),
                RenderEntityModelVertex(Vector3f( r, 0f, -r), color, Vector2f(1f, 0f)),
                RenderEntityModelVertex(Vector3f( r, 0f,  r), color, Vector2f(1f, 1f)),
                RenderEntityModelVertex(Vector3f(-r, 0f,  r), color, Vector2f(0f, 1f))
            )
        }.build()
    }
}
```

模型 DSL 中的 `pipe.shader(...)` 当前作为管线元数据存在；当前 `OpenGlRenderEntityModelExecutor` 使用内置 `render_entity_model.vsh` / `render_entity_model.fsh` 执行模型绘制，不会按每个 pipe 动态切换自定义 shader。需要完全控制 vertex / fragment shader 时，应实现 `WorldPassRenderEntityRenderer` 并手动绘制。

模型执行器默认处理：

- 使用 `GL_LEQUAL` 深度测试。
- 关闭深度写入。
- 使用 `SRC_ALPHA, ONE` 混合。
- 关闭 cull。
- 绘制点、三角形和四边形拆分出的三角形。

## 6. 给 Entity 绑定自定义顶点着色器和片元着色器

自定义 shader 的核心流程：加载 shader、创建 vertex buffer、设置矩阵和 sampler、绘制。

资源路径约定：

```text
vertex("core/vertex/arc_orb.vsh")
-> assets/cooparticlesapi/shaders/core/vertex/arc_orb.vsh

fragment("core/fragment/arc_orb.fsh")
-> assets/cooparticlesapi/shaders/core/fragment/arc_orb.fsh
```

Renderer 示例：

```kotlin
class ArcOrbRenderer : WorldPassRenderEntityRenderer<ArcOrbEntity> {

    private var program: CooShaderProgram? = null
    private var buffer: DynamicVertexBuffer? = null
    private var textures: SimpleTextures? = null
    private var sprite: SpriteSheetTexture? = null

    override fun initialize(instance: RenderEntityInstance<ArcOrbEntity>) {
        ensureResources()
    }

    override fun renderLocal(input: LocalRenderInput<ArcOrbEntity>) {
        ensureResources()

        val program = program ?: return
        val buffer = buffer ?: return
        val textures = textures ?: return
        val sprite = sprite ?: return
        val entity = input.instance.entity

        val vertices = buildBillboardVertices(entity, input.tickDelta)
        buffer.setVertexes(vertices, CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT)

        program.use()
        program.setMatrix4("projMat", input.projMatrix)
        program.setMatrix4("viewMat", input.viewMatrix)
        program.setMatrix4("transMat", Matrix4f(input.modelMatrix))
        program.setFloat("time", entity.getTime(input.tickDelta))
        program.setFloat("radius", entity.radius())
        program.setFloat4("tint", entity.color())
        program.setInt("spriteTex", 0)

        sprite.uploadSpriteUniforms(program, entity.getTime(input.tickDelta))

        textures.drawWith(Runnable {
            buffer.draw()
        })

        program.reset()
    }

    private fun ensureResources() {
        if (program == null) {
            program = AdvancedShaderProgramBuilder()
                .vertex("core/vertex/arc_orb.vsh")
                .fragment("core/fragment/arc_orb.fsh")
                .managedId("render_entity/arc_orb")
                .build()
                .also { it.init() }
        }

        if (buffer == null) {
            buffer = DynamicVertexBuffer().also { it.init() }
        }

        if (textures == null) {
            val sheet = SpriteSheetTexture(
                IdentifierTexture(ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/arc_orb_sheet.png")),
                columns = 4,
                rows = 4,
                frameCount = 16,
                framesPerSecond = 12.0f
            )
            sprite = sheet
            textures = SimpleTextures().apply {
                addTexture(sheet)
                init()
            }
        }
    }
}
```

`CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT` 的 attribute 约定：

| location | 数据 | GLSL 类型 |
| --- | --- | --- |
| 0 | position | `vec3` |
| 1 | color | `vec4` |
| 2 | uv | `vec2` |

对应 vertex shader：

```glsl
#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV;

uniform mat4 projMat;
uniform mat4 viewMat;
uniform mat4 transMat;

out vec4 vertexColor;
out vec2 texCoord;

void main() {
    vertexColor = Color;
    texCoord = UV;
    gl_Position = projMat * viewMat * transMat * vec4(Position, 1.0);
}
```

对应 fragment shader：

```glsl
#version 330 core

in vec4 vertexColor;
in vec2 texCoord;

uniform sampler2D spriteTex;
uniform bool useSpriteUv;
uniform vec4 spriteUvRect;
uniform vec4 tint;

out vec4 FragColor;

void main() {
    vec2 uv = useSpriteUv ? spriteUvRect.xy + texCoord * spriteUvRect.zw : texCoord;
    vec4 tex = texture(spriteTex, uv);
    FragColor = tex * vertexColor * tint;
}
```

## 7. 绑定纹理

`IdentifierTexture` 按 Minecraft 资源路径加载纹理。传入：

```kotlin
ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/arc_orb_sheet.png")
```

实际资源路径为：

```text
assets/<modid>/textures/effect/arc_orb_sheet.png
```

`SimpleTextures` 会按添加顺序绑定到连续纹理槽：

```kotlin
val textures = SimpleTextures().apply {
    addTexture(IdentifierTexture(ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/base.png")))
    addTexture(IdentifierTexture(ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/noise.png")))
    init()
}

program.setInt("baseTex", 0)
program.setInt("noiseTex", 1)

textures.drawWith(Runnable {
    buffer.draw()
})
```

对应 fsh：

```glsl
uniform sampler2D baseTex;
uniform sampler2D noiseTex;

in vec2 texCoord;
out vec4 FragColor;

void main() {
    vec4 base = texture(baseTex, texCoord);
    float noise = texture(noiseTex, texCoord * 2.0).r;
    FragColor = vec4(base.rgb * (0.7 + noise * 0.3), base.a);
}
```

`SimpleTextures.drawWith` 会保存并恢复当前绑定状态，适合局部绘制。直接使用 OpenGL 绑定纹理时，需要自行恢复 active texture、binding、blend、depth 等状态。

## 8. 精灵图案例

精灵图通过 `SpriteSheetTexture` 包装普通纹理，并在每帧上传 `spriteUvRect`、`spriteFrame`、`useSpriteUv`。

```kotlin
val sprite = SpriteSheetTexture(
    IdentifierTexture(ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/fire_sheet.png")),
    columns = 4,
    rows = 4,
    frameCount = 16,
    framesPerSecond = 10.0f,
    looping = true,
    rowsStartFromTop = true
)

val textures = SimpleTextures().apply {
    addTexture(sprite)
    init()
}

program.setInt("spriteTex", 0)
sprite.uploadSpriteUniforms(program, entity.getTime(tickDelta))
textures.drawWith(Runnable { buffer.draw() })
```

fsh 中将基础 uv 映射到当前帧：

```glsl
uniform sampler2D spriteTex;
uniform bool useSpriteUv;
uniform vec4 spriteUvRect;
uniform int spriteFrame;

in vec2 texCoord;
out vec4 FragColor;

void main() {
    vec2 uv = texCoord;
    if (useSpriteUv) {
        uv = spriteUvRect.xy + texCoord * spriteUvRect.zw;
    }

    vec4 color = texture(spriteTex, uv);
    if (color.a <= 0.001) {
        discard;
    }

    FragColor = color;
}
```

## 9. PostEffectType 基础

独立后处理使用 `PostEffectType` 描述类型，`PostEffectInstance` 表示一次正在运行的效果。

注册方式：

```kotlin
val ARC_FLASH: PostEffectType = CooPostEffectTypes.register(
    ResourceLocation.fromNamespaceAndPath(MOD_ID, "arc_flash")
) {
    screenQuad()
    require(RenderBackendCapability.SCENE_COLOR_COPY)
    require(RenderBackendCapability.FINAL_FRAME_POST)

    pass("compose", ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_flash.fsh")) {
        inputSceneColor("scene", textureSlot = 0)
        inputCustomTexture("noiseTex", textureSlot = 1)
        outputToFinalScreen()
        uniform("intensity") { instance ->
            val base = (instance.params["intensity"] as? PostEffectParamValue.FloatValue)?.value ?: 1.0f
            PostEffectParamValue.FloatValue(base * (1.0f - instance.progress))
        }
    }
}
```

`ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_flash.fsh")` 对应：

```text
assets/<modid>/shaders/post/arc_flash.fsh
```

创建并播放实例：

```kotlin
val instance = ARC_FLASH.create()
    .bindWorld(center.x, center.y, center.z, level.dimension().location())
    .duration(30)
    .params {
        float("intensity", 1.2f)
        resource("noiseTex", ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/noise.png"))
    }

CooPostEffects.client.add(instance)
```

服务端同步：

```kotlin
CooPostEffects.server.spawn(serverLevel, instance)
```

或定向发送：

```kotlin
CooPostEffects.server.send(serverPlayer, instance)
```

常用 binding：

| Binding | 场景 |
| --- | --- |
| `bindScreen()` | 全屏 UI / 纯屏幕效果 |
| `bindScreen(x, y)` | 屏幕坐标效果 |
| `bindWorld(x, y, z, level)` | 世界坐标效果 |
| `bindEntity(entityId)` | 绑定 Minecraft 原生实体 |
| `bindBlock(pos, level)` | 绑定方块位置 |
| `bindCustom(...)` | 自定义绑定数据 |

## 10. 多 fsh 拓扑链接

`PostEffectChain` 支持多个 fsh pass 形成有向无环图。每个 pass 可以输出到临时纹理、mask、bright color 或屏幕输出目标；后续 pass 通过 `asInputTo` / `asInputFrom` 读取前序 pass 的输出。

拓扑链示例：提取高亮、噪声 mask、模糊、合成。

```kotlin
val ARC_BLOOM: PostEffectType = CooPostEffectTypes.register(
    ResourceLocation.fromNamespaceAndPath(MOD_ID, "arc_bloom")
) {
    screenQuad()
    require(RenderBackendCapability.SCENE_COLOR_COPY)
    require(RenderBackendCapability.SCENE_DEPTH_READ)
    require(RenderBackendCapability.FINAL_FRAME_POST)

    val extract = pass("extract", ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_extract.fsh")) {
        inputSceneColor("scene", textureSlot = 0)
        outputToTemporary()
        uniform("threshold") { it.params["threshold"] ?: PostEffectParamValue.FloatValue(0.65f) }
    }

    val noiseMask = pass("noise_mask", ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_noise_mask.fsh")) {
        inputCustomTexture("noiseTex", textureSlot = 0)
        outputToTemporary()
        uniform("noiseScale") { it.params["noiseScale"] ?: PostEffectParamValue.FloatValue(2.5f) }
    }

    val blur = pass("blur", ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_blur.fsh")) {
        outputToTemporary()
        uniform("radius") { instance ->
            val base = (instance.params["blurRadius"] as? PostEffectParamValue.FloatValue)?.value ?: 6.0f
            PostEffectParamValue.FloatValue(base * (1.0f - instance.progress * 0.25f))
        }
    }

    val compose = pass("compose", ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_compose.fsh")) {
        inputSceneColor("scene", textureSlot = 0)
        inputSceneDepth("depthTex", optional = true, textureSlot = 3)
        outputToFinalScreen()
        uniform("intensity") { instance ->
            val base = (instance.params["intensity"] as? PostEffectParamValue.FloatValue)?.value ?: 1.0f
            PostEffectParamValue.FloatValue(base * (1.0f - instance.progress))
        }
    }

    extract.asInputTo(blur, "sourceTex", textureSlot = 0)
    noiseMask.asInputTo(blur, "maskTex", textureSlot = 1)
    blur.asInputTo(compose, "bloomTex", textureSlot = 1)
    noiseMask.asInputTo(compose, "maskTex", textureSlot = 2)
}
```

执行规则：

- pass 名称必须唯一。
- pass 输出依赖自动参与拓扑排序。
- 循环依赖会被拒绝。
- 同一 pass 内显式 `textureSlot` 不能重复。
- 未显式指定的 sampler 会自动分配空闲槽位。
- `inputSceneColor` 依赖 `SCENE_COLOR_COPY`。
- `inputSceneDepth` 依赖 `SCENE_DEPTH_READ`，可使用 `optional = true` 允许缺失。
- `inputCustomTexture("noiseTex")` 从 instance params 中读取同名纹理参数。

合成 pass fsh 示例：

```glsl
#version 330 core

uniform sampler2D scene;
uniform sampler2D bloomTex;
uniform sampler2D maskTex;
uniform sampler2D depthTex;

uniform float intensity;
uniform bool hasDepth;
uniform vec2 screenSize;
uniform vec2 texelSize;
uniform float sourceDepth;

in vec2 texCoord;
out vec4 FragColor;

void main() {
    vec4 sceneColor = texture(scene, texCoord);
    vec4 bloom = texture(bloomTex, texCoord);
    float mask = texture(maskTex, texCoord).r;

    float depthFade = 1.0;
    if (hasDepth) {
        float sceneDepth = texture(depthTex, texCoord).r;
        depthFade = smoothstep(-0.002, 0.004, sceneDepth - sourceDepth);
    }

    vec3 color = sceneColor.rgb + bloom.rgb * mask * intensity * depthFade;
    FragColor = vec4(color, sceneColor.a);
}
```

## 11. 在 post 链上绑定纹理

post 链中的纹理输入使用 `inputCustomTexture` 声明，运行实例通过 `params.resource` 绑定资源纹理。

注册类型：

```kotlin
pass("distort", ResourceLocation.fromNamespaceAndPath(MOD_ID, "post/arc_distort.fsh")) {
    inputSceneColor("scene", textureSlot = 0)
    inputCustomTexture("noiseTex", textureSlot = 1)
    outputToFinalScreen()
}
```

创建实例：

```kotlin
val instance = ARC_DISTORT.create()
    .bindScreen()
    .duration(40)
    .params {
        resource("noiseTex", ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/noise.png"))
        float("strength", 0.025f)
    }
```

对应资源路径：

```text
assets/<modid>/textures/effect/noise.png
```

fsh：

```glsl
uniform sampler2D scene;
uniform sampler2D noiseTex;
uniform float strength;

in vec2 texCoord;
out vec4 FragColor;

void main() {
    vec2 noise = texture(noiseTex, texCoord * 2.0).rg * 2.0 - 1.0;
    vec2 uv = texCoord + noise * strength;
    FragColor = texture(scene, uv);
}
```

`inputCustomTexture` 也接受 raw GL texture id：`IntValue` / `LongValue`。资源纹理通常使用 `ResourceValue`，由后端通过 `IdentifierTexture` 维护生命周期。

## 12. 给 RenderEntity 绑定后处理效果

RenderEntity 的后处理分为两类。

### 12.1 Renderer 提交帧尾效果

实体自身参与世界渲染后，在 `FramePostRenderEntityRenderer.collectRenderContributions` 中提交后处理描述。适合实体发光、屏幕冲击波、mask bloom 等和实体生命周期一致的效果。

```kotlin
class ArcOrbPostRenderer : FramePostRenderEntityRenderer<ArcOrbEntity> {

    private lateinit var arcOrbTextures: SimpleTextures

    override fun initialize(instance: RenderEntityInstance<ArcOrbEntity>) {
        arcOrbTextures = SimpleTextures().apply {
            addTexture(IdentifierTexture(ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/arc_orb_sheet.png")))
            init()
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<ArcOrbEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        val modelMatrix = RenderUtil.buildModelMatrix(entity, input.frameContext)

        collector.submit(
            BuiltinRenderEffectDescriptors.maskBloomTexturedBillboard(
                effectId = "arc_orb_bloom",
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext,
                textures = arcOrbTextures,
                modelMatrix = modelMatrix,
                sourceEntity = entity,
                config = MaskBloomConfig(
                    blurSigma = 12.0f,
                    blurRange = 8.0f,
                    intensity = entity.intensity(),
                    threshold = 0.05f
                ),
                tint = entity.color()
            )
        )
    }
}
```

该路径由 RenderEntity 框架统一收集，并在当前帧的 post 阶段执行。需要 depth occlusion 时，描述符或 shader 必须使用深度资源，并在合成阶段比较 `sourceDepth` 与 scene depth。

### 12.2 独立 PostEffectInstance 绑定世界或原生实体

全屏或世界位置型效果不一定依附 RenderEntity Renderer，可以直接创建 `PostEffectInstance`：

```kotlin
val instance = ARC_FLASH.create()
    .bindWorld(entity.pos.x, entity.pos.y, entity.pos.z, level.dimension().location())
    .duration(24)
    .params {
        float("intensity", entity.intensity())
        resource("noiseTex", ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect/noise.png"))
    }

CooPostEffects.server.spawn(serverLevel, instance)
```

`bindEntity` 绑定的是 Minecraft 原生实体 id，不是 RenderEntity 的 uuid。RenderEntity 专属效果通常放在 Renderer 的 `collectRenderContributions` 中；需要跨网络同步的独立 post 效果，可以用稳定 `instanceId` 创建、更新、移除。

## 13. Post 内置 uniform

OpenGL post 后端会为 pass 注入常用 uniform：

| uniform | 含义 |
| --- | --- |
| `progress` | 实例生命周期进度，通常为 0 到 1 |
| `center` | 绑定点在屏幕空间或语义空间中的位置 |
| `sourceDepth` | 绑定源深度 |
| `hasDepth` | 当前 pass 是否拿到可用深度 |
| `screenSize` | 当前输出尺寸 |
| `texelSize` | `1.0 / screenSize` |

业务参数通过 `uniform("name") { instance -> ... }` 注入。provider 返回 `PostEffectParamValue`，可读取 `instance.params` 和 `instance.progress`。fsh 中声明同名 uniform 即可使用。

## 14. 渲染阶段与后端兼容

客户端管线阶段由 `ClientRenderPipelineManager` 管理：

| 阶段 | 用途 |
| --- | --- |
| `FRAME_BEGIN` | 帧开始，重置上下文 |
| `WORLD_PASS` | RenderEntity 世界渲染 |
| `POST_PROCESS_PREPARE` | 准备 scene color / depth 等资源 |
| `FRAME_POST` | RenderEffectGraph 和 PostEffectChain 执行 |
| `FRAME_END` | 帧结束清理 |

`CooParticlesAPIClient.syncRenderBackend()` 会根据 Iris shader pack 状态选择 `IrisSafeRenderBackend` 或 `VanillaSafeRenderBackend`。两者都通过 `RenderFrameContext`、`RenderSceneTargets` 暴露资源，效果代码不应直接假设 Minecraft 主 FBO 恒定可用。

## 15. 使用注意事项

- RenderEntity codec 和 Renderer 注册是两个独立步骤。
- `AutoRenderEntity` 的自动注册不代表 Renderer 自动注册。
- 自定义字段同步后需要合并到客户端对象；手写 `RenderEntity` 时不要遗漏 `loadProfileFromEntity`。
- `spawn` 必须发生在服务端世界。
- `durationTicks` 到期后实体会被取消。
- `renderRange` 过小会导致服务端不向玩家同步。
- 模型 DSL 当前不负责逐 pipe 切换自定义 shader。
- 自定义 shader 绘制需要保证 vertex format 与 GLSL layout 完全一致。
- `SimpleTextures` 的 sampler 槽位按添加顺序从 0 开始。
- `IdentifierTexture` 参数是 textures 目录下的相对路径，不需要写 `textures/` 前缀。
- post pass 内显式 texture slot 不能重复。
- 多 pass 链需要保持无环依赖。
- 深度相关效果应声明 `SCENE_DEPTH_READ`，shader 内根据 `hasDepth` 做降级。
- 屏幕合成 pass 需要采样 scene 并混合，不应只输出效果纹理。
- 需要方块遮挡时，世界绘制应保持深度测试，后处理应使用 depth-aware 逻辑。
- shader pack 环境中，外部 FBO 和 scene copy 来源可能变化，避免直接绑定固定 framebuffer。
- 手动改 OpenGL 状态后必须恢复，尤其是 depth test、depth mask、blend、cull、active texture、framebuffer。

## 16. 常见错误与原因

| 现象 | 常见原因 | 检查点 |
| --- | --- | --- |
| RenderEntity 不渲染 | codec 未注册 | `ClientRenderEntityRegistry` 或自动注册扫描是否生效 |
| RenderEntity 不渲染 | Renderer 未注册 | 日志或异常是否出现 `RenderEntity renderer not registered` |
| RenderEntity 不渲染 | 在客户端世界 spawn | `spawn` 调用是否传入 `ServerLevel` |
| RenderEntity 不渲染 | `renderRange` 太小 | 玩家到实体距离是否超出同步范围 |
| RenderEntity 不渲染 | `durationTicks` 到期 | 实体是否已被 cancel |
| RenderEntity 不渲染 | Renderer 未声明阶段 | `describeFeatures` 是否包含 `WORLD_PASS` 或 `FRAME_POST` |
| shader 无输出 | shader 编译或链接失败 | 日志中的 shader compile / link 信息 |
| shader 无输出 | attribute 不匹配 | vertex format 与 GLSL `layout(location=...)` 是否一致 |
| shader 无输出 | uniform 名称不匹配 | Kotlin `set*` 名称与 GLSL 声明是否一致 |
| 纹理为空 | 资源路径错误 | `IdentifierTexture` 是否对应 `assets/<modid>/textures/...` |
| 纹理为空 | sampler 槽位错误 | `program.setInt` 和 `SimpleTextures` 添加顺序是否一致 |
| 精灵图不动 | 未上传 sprite uniform | 是否调用 `sprite.uploadSpriteUniforms(program, time)` |
| Post 不执行 | type 未注册 | 客户端日志是否有 synced post effect type not registered 类信息 |
| Post 不执行 | capability 缺失 | `require(...)` 和后端 capability 是否匹配 |
| Post 不执行 | binding 不在当前维度 | binding 维度、实体 id、方块位置是否可解析 |
| 全屏黑屏 | 合成 pass 未采样 scene | final pass 是否把 scene color 混合回输出 |
| 全屏黑屏 | sampler 名称错误 | `inputSceneColor("scene")` 与 `uniform sampler2D scene` 是否一致 |
| 全屏黑屏 | pass 输入依赖断开 | `asInputTo` sampler 名称是否与 fsh uniform 对应 |
| 全屏黑屏 | 显式 texture slot 冲突 | 同一 pass 内是否重复使用 slot |
| 全屏黑屏 | 自定义纹理缺失 | `params.resource("noiseTex", ...)` 名称是否对应 `inputCustomTexture("noiseTex")` |
| 深度错误 | 未声明 depth 输入 | PostEffectType 是否 `require(SCENE_DEPTH_READ)` 并 `inputSceneDepth` |
| 深度错误 | `hasDepth=false` 未降级 | shader 是否在无深度时使用合理 fallback |
| 深度错误 | 屏幕空间合成误当世界遮挡 | 需要遮挡时是否使用 depth-aware 合成或世界 pass 深度测试 |
| 深度错误 | OpenGL 状态泄漏 | 自定义绘制后 depth / blend / framebuffer 是否恢复 |
| 穿墙发光 | 效果允许 throughWalls | 描述符参数和 shader 深度比较逻辑 |
| 光影不兼容 | 直接假设 vanilla main target | 是否改用 `RenderFrameContext` / `RenderSceneTargets` 提供的资源 |
| 光影不兼容 | scene copy 不可用 | 日志是否出现 scene color copy unavailable |
| 光影不兼容 | 外部 FBO 变化 | 日志是否出现 external post framebuffer / fallback target 信息 |
| 光影不兼容 | 手动 FBO 或 GL 状态未恢复 | 是否污染 shader pack 后续 pass |

## 17. 接入顺序建议

1. 先定义 RenderEntity 数据和 codec。
2. 注册 codec 与 Renderer。
3. 使用模型 DSL 或自定义 shader 完成世界渲染。
4. 纹理和精灵图在 `initialize` 中创建并初始化。
5. 后处理需求先判断属于 Renderer 帧尾贡献，还是独立 `PostEffectInstance`。
6. 单 pass post 跑通后，再扩展到多 fsh 拓扑链。
7. 引入深度和 shader pack 场景前，先确认 scene color、depth、final output 均正常。

## 18. 参考源码入口

| 功能 | 路径 |
| --- | --- |
| RenderEntity 基类 | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/RenderEntity.kt` |
| AutoRenderEntity | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/AutoRenderEntity.kt` |
| 客户端实例 | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt` |
| Renderer 接口 | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityRenderer.kt` |
| 客户端注册表 | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/ClientRenderEntityRegistry.kt` |
| 模型 DSL | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/RenderEntityModelBuilder.kt` |
| 模型执行器 | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/model/OpenGlRenderEntityModelExecutor.kt` |
| shader builder | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/AdvancedShaderProgramBuilder.kt` |
| vertex format | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/data/CooVertexFormat.kt` |
| texture | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/texture/` |
| post chain | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectChain.kt` |
| post type | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectType.kt` |
| post executor | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt` |
| OpenGL post backend | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt` |
| 内置效果描述符 | `common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/BuiltinRenderEffectDescriptors.kt` |
| 示例 RenderEntity | `common/src/main/kotlin/cn/coostack/cooparticlesapi/examples/` |
| 示例 shader | `common/src/main/resources/assets/cooparticlesapi/shaders/` |
