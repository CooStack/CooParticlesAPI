# Pipeline、RenderEntity 与 ShaderEffect

适用版本：Minecraft 1.21.1，Fabric / NeoForge，Mojmap + Parchment。

公开 Render API 入口：

- `CooPipelines`：实体、方块和通用 Pipeline。
- `CooBlockPipelines`：真实世界方块的 Pipeline 绑定。
- `CooTerrainEffectManager`：按位置批量应用持久或临时的 terrain 效果组。
- `CooShaderEffects`：独立屏幕 ShaderEffect 的注册和播放。

`Pipeline` 是由 shader 节点和 `line` 组成的有向无环图。节点输出是 FBO attachment，节点输入是 sampler 端口。编译器根据连线推导执行顺序、scene color、scene depth、mask、临时 target 和后处理阶段。

## 最小用法

不需要后处理：

```kotlin
override val pipeline = CooPipelines.DEFAULT
```

使用内置 mask bloom：

```kotlin
override val pipeline = CooPipelines.MASK_BLOOM
    .bloomMipLevels(6)
    .bloomThreshold(0F)
    .intensity { entity: MyRenderEntity ->
        2.8F * entity.bright.coerceAtLeast(0F)
    }
```

内置 `MASK_BLOOM` 先从 geometry mask 提取 HDR Bloom，再用 `bloom_bsl_atlas.fsh` 按 BSL 的 6x6 权重构建 7 级多尺度 atlas。atlas 使用 `RGBA16F`，写入时保留 BSL 的四次根 companding，读取各级 tile 后再可逆解码，使低分辨率 tile 的插值不会暴露明显的 mip 块。与 BSL 的 RGB8 路径不同，这里不截顶到 `1.0`，也不在中间纹理加入 Bayer，因此大于 `32` 的亮度仍完整保留。采样核根据画面分辨率自动计算，`bloomMipLevels(...)` 控制参与重建的级数。

BSL 在色调映射前用 `0.2 * BLOOM_STRENGTH` 混合 Bloom。本管线拿到的是最终场景颜色，因此用相同的 `0.2` 基准缩放透射率输入，避免直接 `mix` 把 mask 之外的画面压暗。

合成结果写回 `RGBA8` 前会按最终像素做有序量化，避免高亮缓坡形成条带。同一像素重复经过不同亮度批次时，量化结果不会继续偏移。

`bloomThreshold(0F)` 表示不做亮部过滤，mask 中的全部颜色都会进入 Bloom 通道；传入正数可启用亮部筛选，`bloomSoftKnee(...)` 控制筛选过渡范围。亮度由 `intensity(...)` 在模糊前写入 HDR 中间纹理。

绑定真实世界方块：

```kotlin
CooBlockPipelines.bind(Blocks.STONE, STONE_PIPELINE)

CooBlockPipelines.bind(MyBlocks.ENERGY_BLOCK) { state ->
    if (state.getValue(POWERED)) ENERGY_PIPELINE else CooPipelines.BLOCK_DEFAULT
}

val LIT_LAMP = Blocks.REDSTONE_LAMP.defaultBlockState()
    .setValue(RedstoneLampBlock.LIT, true)
CooBlockPipelines.bind(LIT_LAMP, LIT_LAMP_PIPELINE)

CooBlockPipelines.bindBlocks(listOf(Blocks.STONE, Blocks.DEEPSLATE), STONE_PIPELINE)
CooBlockPipelines.bindStates(poweredStates, ENERGY_PIPELINE)
```

`CooBlockPipelines.bind` 是持久的类型/状态绑定。它只影响命中的 `Block`、精确 `BlockState` 或 `BlockState` predicate，不会替换整张方块图集。精确 `BlockState` 优先于 `Block`，`Block` 优先于 predicate。每个 terrain shader 默认可以读取 `BaseSampler` 和原始 `BaseUV`；`EffectUV` 是另一组坐标，供自定义纹理或 FBO 使用。

terrain shader 的公开输入是 Kotlin Pipeline DSL。用户不需要编写 `ShaderInstance` JSON；客户端仅在调用 Vanilla/Iris terrain hook 时生成内存 descriptor，shader 源文件始终放在 `assets/cooparticlesapi/shaders/core/terrain` 或对应 Pipeline 所属 namespace 下，不创建 `assets/minecraft/shaders/core/cooparticlesapi` 资源目录。Coo shader 的公共 include 语法是 `#coo_import <文件名>`，它读取 `cooparticlesapi:shader/include/文件名`；`#coo_import <modid:path>` 读取 `modid:shader/path`。两种写法都由 Coo 的 source loader 展开，不能使用 `#moj_import`。

terrain shader 会自动获得 `CooAlphaCutoff`。`CUTOUT_MIPPED`、`CUTOUT` 与 tripwire 使用 `0.1F`，solid 和 translucent 使用 `0F`。自定义 fragment shader 采样 `BaseSampler` 后必须先按该 uniform 丢弃透明像素；Iris 下即使随后改用 `SceneColor`，也必须保留这一步。

按位置批量应用持久效果：

```kotlin
val group = CooTerrainEffectGroup(id("charged_area"), ENERGY_PIPELINE) {
    positions(areaBlocks)
    uniform("EffectTint", CooUniformValue.Vec3Value(0.72F, 0.28F, 0.12F))
    uniform("EffectStrength", 0.8F)
}
CooTerrainEffectManager.apply(level, group)
```

临时效果只需增加 `duration(120)`。同一个组继续扩大时调用 `append`，只移除一部分位置时调用 `removePositions`，更新整组共享颜色时调用 `updateUniforms`；这些包都不重复发送 Pipeline 和未变化的位置。持久组会在玩家登录或切换维度后补发，直到显式调用 `remove`。这里的“持久”指服务器本次运行期间持续，不表示写入世界存档。

也可以直接使用批量重载；单个位置仍然走同一套协议：

```kotlin
CooTerrainEffectManager.apply(
    level,
    id("highlighted_blocks"),
    ENERGY_PIPELINE,
    positions
) {
    uniform("EffectStrength", 0.8F)
    duration(40)
}

CooTerrainEffectManager.removePositions(level, id("highlighted_blocks"), positionsToClear)
CooTerrainEffectManager.updateUniforms(
    level,
    id("highlighted_blocks"),
    mapOf("EffectStrength" to CooUniformValue.FloatValue(0.3F))
)
```

需要按每个位置的激活 tick 播放动画时，`CooTerrainEffectGroupBuilder.position/positions` 负责记录延迟，服务端只同步每个位置的激活 tick。terrain vertex shader 会自动计算带 `partialTick` 的经过时间：

```glsl
flat in float effectElapsedTicks;
```

fragment shader 直接用 `effectElapsedTicks` 计算连续变化。整个效果组仍是一个 Pipeline batch，不会按方块创建 Pipeline 或 draw call。激活 tick 以 65536 tick 为周期编码，适合有限时长的局部效果。

传播测试在前 60 tick 每 5 tick 向外发现一层。每个方块从自己的发现 tick 开始独立运行 90 tick：10 tick 淡入白色，10 tick 变为铜红，10 tick 变为铜锈绿，固定 30 tick，再各用 10 tick 经过铜红、白色并淡回原纹理。首次创建组只发一个组包，每 5 tick 只追加新一层的位置；客户端自己推进时间线和 section 刷新，不再每 tick 重发完整位置表。颜色化使用原纹理亮度乘目标色相，并按目标色相的亮度归一化。

```glsl
uniform float CooAlphaCutoff;

vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
if (atlasColor.a < CooAlphaCutoff) {
    discard;
}
```

```kotlin
val ENERGY_PIPELINE = CooPipelines.block(id("energy")) {
    shader(MyShaders.ENERGY)
    inputBlockAtlas("BaseSampler")
    inputSceneColor("SceneColor", optional = true)
}
```

BlockTest 的传播示例在 `BlockAPITestGroupBuilder` 注册：

```kotlin
BlockTexturePropagationTestOption(player)
```

它调用 `BlockUtil.BlockStepSpareData` 每 5 tick 发现一层，并使用 `CooTerrainEffectManager.apply/append/remove` 管理一个效果组。Option 只负责传播策略；位置批量同步、玩家登录补发、生命周期和 section 局部重建都由通用 terrain 效果 API 处理。

注册并播放屏幕效果：

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

`DEFAULT`、`MASK_BLOOM` 和 `BLOCK_DEFAULT` 都是不可变模板。`bloomMipLevels`、`bloomThreshold`、`bloomSoftKnee`、`intensity` 和 `uniform` 返回新 Pipeline，不会改写共享 preset。

## RenderEntity

Renderer 只声明 Pipeline，并在回调中提交几何：

```kotlin
interface RenderEntityRenderer<T : RenderEntity> {
    val pipeline: CooRenderPipeline<T>
    fun render(input: RenderInput<T>)
}
```

示例：

```kotlin
@CooAutoRegisterRenderer
class MyRenderEntityRenderer : RenderEntityRenderer<MyRenderEntity> {
    override val pipeline = CooPipelines.MASK_BLOOM
        .bloomMipLevels(7)
        .intensity { entity: MyRenderEntity -> entity.brightness }

    override fun render(input: RenderInput<MyRenderEntity>) {
        val entity = input.entity
        // 在这里提交实体几何。Pipeline runtime 负责当前节点的 shader、输入和 uniform。
    }
}
```

调用方不再实现额外的阶段描述、mask 配置或专用后处理能力接口。一个含 world 节点和 fullscreen 节点的 Pipeline 会自动产生世界绘制和帧尾阶段。

### 外部模组定义可复用的实体 Pipeline

节点名称不是框架关键字。下面使用 `laser_mask`，也可以换成 `source` 或其他不重复的名称。`world(...)` 表示实体几何阶段，`pass(...)` 表示屏幕四边形阶段。

先定义一个不绑定具体实体类型的模板：

```kotlin
object LaserPipelines {
    val MASK_BLOOM_TEMPLATE: CooRenderPipeline<Nothing> =
        CooPipelines.entity<Nothing>(id("laser_mask_bloom")) {
            val source = world("laser_mask") {
                vertex(id("shader/laser_mask.vsh"))
                fragment(id("shader/laser_mask.fsh"))
                maskOutput()
                uniform("MaskStrength", 1F)
            }
            val blurHorizontal = pass("blur_horizontal") {
                fragment(id("shader/blur_horizontal.fsh"))
                input("Input", textureSlot = 0)
                uniform("Sigma", 15F)
            }
            val blurVertical = pass("blur_vertical") {
                fragment(id("shader/blur_vertical.fsh"))
                input("Input", textureSlot = 0)
                uniform("Sigma", 15F)
            }
            val composite = pass("composite") {
                fragment(id("shader/composite.fsh"))
                input("SceneColor", textureSlot = 0)
                input("Bloom", textureSlot = 1)
            }

            line(source.color(), worldTarget())
            line(source.mask(), blurHorizontal.input("Input"))
            line(blurHorizontal.color(), blurVertical.input("Input"))
            line(sceneColor(), composite.input("SceneColor"))
            line(blurVertical.color(), composite.input("Bloom"))
            line(composite.color(), screenTarget())

            parameter("intensity", source, "MaskStrength")
        }
}
```

`laser_mask.fsh` 至少需要写出与 Pipeline 一致的 attachment。`maskOutput()` 在默认 color attachment 0 后增加 mask attachment 1：

```glsl
#version 330 core

in vec4 fragColor;
layout(location = 0) out vec4 SceneColor;
layout(location = 1) out vec4 MaskColor;

uniform float MaskStrength;

void main() {
    SceneColor = fragColor;
    MaskColor = vec4(fragColor.rgb * max(MaskStrength, 0.0), fragColor.a);
}
```

`parameter("intensity", source, "MaskStrength")` 声明模板参数。它绑定到 world 节点，所以按实体求值，但不会拆分 fullscreen 批次。外部 renderer 再把模板参数绑定到自己的实体字段：

```kotlin
@CooAutoRegister
class LaserRenderEntity() : AutoRenderEntity(null) {
    @CodecField
    var bright: Float = 1F

    override fun getRenderID(): ResourceLocation = ID

    companion object {
        val ID: ResourceLocation = id("laser")
    }
}

@CooAutoRegisterRenderer
class LaserRenderEntityRenderer : RenderEntityRenderer<LaserRenderEntity> {
    override val pipeline = LaserPipelines.MASK_BLOOM_TEMPLATE
        .parameter("intensity") { entity: LaserRenderEntity ->
            2.8F * entity.bright.coerceAtLeast(0F)
        }

    override fun render(input: RenderInput<LaserRenderEntity>) {
        RenderEntityModelExecutors.active().draw(MODEL, input)
    }

    companion object {
        private val MODEL: RenderEntityModel = RenderEntityModelBuilder().run {
            val layer = layer("laser")
            addVertex(layer, Vector3f(0F, 0F, 0F), Vector4f(1F, 0.2F, 0.1F, 1F))
            addVertex(layer, Vector3f(0F, 0F, 4F), Vector4f(1F, 0.8F, 0.2F, 1F))
            build()
        }
    }
}
```

`render(input)` 可能在 world 输出和离屏 attachment 捕获阶段被调用。绘制代码从 `input.entity` 读取本次实体，从 `input.node` 读取当前节点；不要把某个实体的动态值保存在共享 renderer 字段中。

如果 Pipeline 不需要做成模板，可以直接绑定实体类型：

```kotlin
val pipeline = CooPipelines.entity<LaserRenderEntity>(id("laser_bloom")) {
    world("laser_source") {
        vertex(id("shader/laser.vsh"))
        fragment(id("shader/laser.fsh"))
        uniform("MaskStrength") { entity: LaserRenderEntity -> entity.bright }
    }
}
```

## 节点与 line

每个节点自动提供 color attachment 0。`colorAttachments(3)` 会提供 `color(0)`、`color(1)` 和 `color(2)`，分别对应 fragment shader 的 `layout(location = 0..2)`。world 节点调用 `maskOutput()` 后还会增加 `mask()` 输出。

节点输入由 `input("SamplerName", textureSlot = n)` 声明。名称对应 GLSL sampler，`textureSlot` 是纹理单元。节点输出通过 `line` 接到另一个节点输入或 graph target：

```kotlin
val source = world("source") {
    vertex(id("source.vsh"))
    fragment(id("source.fsh"))
    colorAttachments(2)
}
val resolve = pass("resolve") {
    fragment(id("resolve.fsh"))
    input("Base", textureSlot = 0)
    input("Mask", textureSlot = 1)
}

line(source.color(0), resolve.input("Base"))
line(source.color(1), resolve.input("Mask"))
line(resolve.color(), screenTarget())
```

同一组连接也可以按通道写：

```kotlin
line(source, fromChannel = 0, resolve, toChannel = 0)
line(source, fromChannel = 1, resolve, toChannel = 1)
```

`fromChannel` 是源 attachment，`toChannel` 查找目标节点中对应 `textureSlot` 的输入端口。一个输出可以连接多个输入；一个节点也可以声明多个输入和输出。

下面的图有三个卡片。`extract.color()` 是 `extract` 的 color attachment 0；它通过 `line` 直接接到 `blur` 的 `Bright` sampler。

```text
sceneColor -> extract.SceneColor
extract.Color[0] -> blur.Bright
blur.Color[0] -> composite.Blurred
sceneColor -> composite.SceneColor
composite.Color[0] -> final screen
```

对应代码：

```kotlin
val pipeline = CooPipelines.generic<FrameData>(id("soft_bloom")) {
    val extract = pass("extract") {
        fragment(id("post/bright_extract.fsh"))
        input("SceneColor")
    }
    val blur = pass("blur") {
        fragment(id("post/blur.fsh"))
        input("Bright")
    }
    val composite = pass("composite") {
        fragment(id("post/composite.fsh"))
        input("SceneColor")
        input("Blurred")
    }

    line(sceneColor(), extract.input("SceneColor"))
    line(extract.color(), blur.input("Bright"))
    line(sceneColor(), composite.input("SceneColor"))
    line(blur.color(), composite.input("Blurred"))
    line(composite.color(), screenTarget())
}
```

`order(...)` 只处理没有依赖关系时的稳定排序。只要存在 `line(A.color(), B.input(...))`，编译器就会保证 A 在 B 前执行。环、重复输入连线和缺失的必需输入会在构建或编译时失败。

## 多 attachment 与命名 FBO

一个节点可以声明多个 color attachment：

```kotlin
val gbuffer = pass("gbuffer") {
    fragment(id("post/gbuffer.fsh"))
    inputSceneColor("SceneColor")
    colorAttachments(2)
}
val lighting = pass("lighting") {
    fragment(id("post/lighting.fsh"))
    input("Normal")
}

line(gbuffer.color(1), lighting.input("Normal"))
line(lighting.color(), screenTarget())
```

`color(1)` 明确表示 attachment 1。它不会被压缩成一个没有 attachment 身份的“上一个 pass 输出”。后端按这条连线绑定对应纹理。

命名 FBO 通过 `ResourceLocation` 标识：

```kotlin
val target = id("framebuffer/custom_gbuffer")

line(gbuffer.color(1), framebufferTarget(target, attachment = 1))

val resolve = pass("resolve") {
    fragment(id("post/resolve.fsh"))
    inputFramebuffer("Normal", target, attachment = 1)
}
```

可用资源包括：

- `texture(id)`：`ResourceLocation` 纹理。
- `blockAtlas()`：原版方块图集。
- `sceneColor()`、`sceneDepth()`：当前场景颜色和深度。
- `framebuffer(id, attachment)`：命名 FBO 的颜色 attachment。
- `mask()`、`temporary()`、`bloom()`：框架管理的场景资源。
- `node.color(attachment)`、`node.mask()`：某个节点的输出。
- `worldTarget()`、`screenTarget()`、`maskTarget()`、`temporaryTarget()`、`bloomTarget()` 和 `framebufferTarget(...)`：图的输出端。

FBO、窗口 resize 和资源释放继续由 `SimpleFrameBuffer`、`RenderSceneResources` 和 OpenGL 后端处理。业务代码不创建另一套 framebuffer 生命周期。

## 纹理格式和 mip

普通节点默认输出 `CooTextureFormat.RGBA8`，只分配 level 0。这个默认值适合大多数颜色 pass。需要保留大于 1.0 亮度的节点要显式改成 `RGBA16F` 或 `RGBA32F`；OpenGL 没有 `RGBA8F` 这种标准颜色格式。

外部模组可以在 Pipeline 定义中直接声明输入和输出契约：

```kotlin
val blur = pingPong(
    name = "blur",
    iterations = 10,
    inputFormat = CooTextureFormat.RGBA16F
) {
    fragment(id("post/blur.fsh"))
    outputFormat(CooTextureFormat.RGBA16F)
    mipLevels(6)
}

val composite = pass("composite") {
    fragment(id("post/composite.fsh"))
    input(
        sampler = "Bloom",
        format = CooTextureFormat.RGBA16F,
        mipLevels = 6
    )
}
```

输入的 `mipLevels` 是最低要求，输出的 `mipLevels` 是实际分配层数。编译器会拒绝能够静态确认的格式不匹配和 mip 不足；场景颜色、自定义纹理等外部来源会在运行时校验。ping-pong 的两只物理 target 都按声明分配，但只在最后一轮完成后刷新 mip 链，不会在 10 轮循环中重复生成。

命名 FBO 只能保存和采样当前 Pipeline 已生成的 attachment。目前不支持从任意相机离屏重绘完整世界，包括另一视角下的地形、实体和透明层；fragment shader 也不能自行补足这些内容。实现这类摄像机画面需要单独的世界渲染生命周期和渲染状态隔离，不属于现有 FBO API 的能力范围。

## PingPong 卡片

PingPong 是一个节点，不需要手写两组 pass。第一轮从外部 `line` 读取，后续轮交替读取前一轮输出。编译器只分配两组物理 target。

```kotlin
val blur = pingPong(
    name = "blur",
    iterations = 8,
    feedbackSampler = "Input"
) {
    fragment(id("post/blur_ping_pong.fsh"))
    alternate(
        "Axis",
        CooUniformValue.Vec2Value(1F, 0F),
        CooUniformValue.Vec2Value(0F, 1F)
    )
    iterationUniform("Iteration") { iteration ->
        CooUniformValue.IntValue(iteration.index)
    }
}

line(sceneColor(), blur.input("Input"))
line(blur.color(), composite.input("Blurred"))
```

`iterations` 必须大于零。`alternate` 根据 ping/pong 轮次交变参数；`iterationUniform` 可读取当前索引、总轮数和 `isPing`。最后一轮也会使用独立 target，避免同一个 framebuffer 同时作为采样输入和绘制输出。

## 动态 uniform

动态参数的执行方式取决于绑定节点。

绑定到 world 节点时，provider 在绘制每个实体时求值。不同实体可以返回不同值，相同 Pipeline ID 仍然共用 attachment，并且只执行一次 fullscreen graph：

```kotlin
val pipeline = CooPipelines.entity<MyRenderEntity>(id("charged_entity")) {
    world("geometry") {
        shader(id("charged_entity"))
        uniform("Charge") { entity: MyRenderEntity -> entity.charge }
    }
}
```

可复用的 `entity<Nothing>` 模板使用通用参数入口：

```kotlin
val template = CooPipelines.entity<Nothing>(id("charged_template")) {
    val source = world("source") {
        vertex(id("source.vsh"))
        fragment(id("source.fsh"))
        uniform("Charge", 0F)
    }
    parameter("charge", source, "Charge")
}

val pipeline = template.parameter("charge") { entity: MyRenderEntity -> entity.charge }
```

静态值不需要 lambda：

```kotlin
val fixed = template.parameter("charge", 0.8F)
val fixedBloom = CooPipelines.MASK_BLOOM.intensity(2.8F)
```

节点内的静态值使用 `uniform("Charge", 0.8F)`。向量和颜色等类型传入 `CooUniformValue`；动态非 Float 参数使用 `parameterValue(...)`：

```kotlin
val tinted = template.parameterValue("tint") { entity: MyRenderEntity ->
    CooUniformValue.Vec3Value(entity.red, entity.green, entity.blue)
}
```

参数也可以绑定到 fullscreen 节点。此时它控制整次屏幕 pass：解析值相同的实体会合批，值不同的实体会拆批执行。普通 fullscreen uniform 在一次 draw 中只有一个值，不能在已经合并的纹理里继续区分多个实体。

内置 `MASK_BLOOM.intensity { entity -> ... }` 绑定亮部提取 pass，强度会在 BSL 多尺度 atlas 构建前写入 HDR 纹理。解析值相同的实体会合批；值不同时会分批捕获 mask 并分别执行后处理。`bloomMipLevels`、`bloomThreshold` 和 `bloomSoftKnee` 也属于 fullscreen 批次参数。

屏幕效果在每次 `play` 时生成独立参数快照：

```kotlin
HEAT_HAZE.play {
    duration(30)
    uniform("strength", 0.12F)
    texture("NoiseSampler", id("textures/effect/noise.png"))
}
```

一次播放写入的值不会修改注册后的 ShaderEffect 模板，也不会影响其他播放实例。

## 真实 terrain 方块 Pipeline

使用原方块图集的 Pipeline：

```kotlin
val ORIGINAL_TEXTURE_BLOCK = CooPipelines.block(id("original_texture_block")) {
    shader(id("terrain/original_texture"))
    inputBlockAtlas("BaseSampler")
    effectUv(CooEffectUvMode.FACE_LOCAL)
    uniform("TintStrength", 0.35F)
}

CooBlockPipelines.bind(MyBlocks.ENERGY_BLOCK, ORIGINAL_TEXTURE_BLOCK)
```

不采样原纹理的原版方块绑定：

```kotlin
val SOLID_TINT = CooPipelines.block(id("solid_tint")) {
    shader(id("terrain/solid_tint"))
    effectUv(CooEffectUvMode.WORLD_XZ)
    uniform("EffectTint", CooUniformValue.Vec3Value(0.1F, 0.85F, 0.35F))
}

CooBlockPipelines.bind(Blocks.STONE, SOLID_TINT)
```

仓库示例不会在客户端启动时绑定任何原版方块。测试代码需要显式调用 `RenderPipelineExamples.bindVanillaBlockExample(Blocks.STONE)`，避免示例污染整张地图的固定方块类型。

精确 `BlockState` 绑定优先于精确 `Block`，精确 `Block` 优先于全局 `BlockState` predicate；相同级别中后绑定的规则优先。批量绑定只增加一次修订号并触发一次 section rebuild。

绑定按需生效，不要求替换整套方块纹理。未命中的方块会解析为 `CooPipelines.BLOCK_DEFAULT`，继续走原版 terrain batch。

自定义方块仍由区块编译器烘焙。框架按 Pipeline 分 section buffer 和 terrain draw batch，不为每个方块发起 draw call。原本的面剔除、AO、lightmap、normal、破坏覆盖层和透明层顺序仍由 vanilla terrain 路径提供。

### 顶点输入

自定义格式保留两套 UV：

| shader 输入 | 含义 |
| --- | --- |
| `Position` | section-local 位置，可结合 section/camera 数据还原世界位置 |
| `Color` | 原版烘焙颜色和 AO |
| `BaseUV` | 原方块图集 UV |
| `EffectUV` | 独立效果 UV |
| `LightUV` | lightmap UV |
| `Normal` | 烘焙法线 |

`BaseSampler + BaseUV` 读取原方块纹理。`EffectSampler + EffectUV` 读取自定义纹理或 FBO attachment。`EffectUV` 不覆盖 `BaseUV`。

`CooEffectUvMode` 支持：

- `BASE_UV`
- `FACE_LOCAL`
- `WORLD_XZ`
- `WORLD_XY`
- `WORLD_YZ`

三种 `WORLD_*` 模式直接从连续世界位置生成 UV，不会覆盖 `BaseUV`。为避免接近世界边界时丢失亚方块精度，坐标以 1024 方块为周期；相邻方块和负坐标保持连续。它是周期坐标，跨过周期边界时效果相位会重新开始；需要全局无限连续的效果时，shader 应自行提供高低位或分块相位。

shader 没有声明 `BaseSampler` 时可以不采样原图集。

## Iris、Sodium 与 NeoForge

自定义 terrain shader 不复用 `iris:entity` wrapper。

Fabric/Sodium 未启用 shader pack 时，区块编译会把命中绑定的几何从原 batch 移到对应 Pipeline batch。原几何不会重复提交，因此没有两层共面 draw。每个 section 仍按 Pipeline 批量绘制，不会退化为逐方块 draw call。

启用 Iris shader pack 时，绑定方块的原几何先进入 Iris terrain/gbuffer。Iris 完成最终合成后，框架读取最终 scene color 和 terrain depth，再批量覆盖这些方块的可见像素。深度测试直接使用 terrain depth 和 `LEQUAL`，不再使用 polygon offset 或全局 depth range 偏移，避免覆盖层穿过前景。`BaseSampler` 仍是原方块 atlas；声明 `inputSceneColor` 的 shader 收到 Iris 处理后的画面。

这个覆盖 pass 不是 Iris gbuffer program，不写 shader pack 的 PBR、normal、material、shadow MRT。shader pack 若修改 terrain 顶点位置，覆盖几何可能无法完全贴合。透明 Pipeline 会随相机重新排序自己的 quad，但无法与其他 terrain 材质做跨 batch 的逐 quad 交错。这两项是当前限制。

无法取得 Iris terrain depth、创建 shader 或上传 Sodium batch 时，兼容层会记录错误、停用自定义覆盖并触发 section rebuild，恢复原版/Iris terrain。不会静默隐藏绑定方块。

NeoForge 在未安装 Sodium 时使用 vanilla section terrain pass；安装 Sodium 后通过同一套可选的 section overlay 接入 Pipeline batch。Sodium 只作为编译期依赖，模组运行时不会强制要求它。未安装时条件 mixin 不生效，绑定仍走 vanilla fallback。这个路径同样不复用实体 shader wrapper。Pipeline 只有显式声明 `inputSceneColor` 或 `inputSceneDepth` 时才复制场景 attachment，避免在当前 framebuffer 上同时读写。

资源 reload 时 shader 实例会释放并重新创建；窗口大小变化时后处理 target 会按新尺寸重建。客户端 terrain、shader 和 OpenGL 类只从客户端初始化路径安装，不能被 dedicated server 类加载。

## ShaderEffect 多 pass

普通效果使用默认全屏 quad 和通用 vertex shader，只声明 fragment 和输入即可。高级效果直接声明多张卡片：

```kotlin
val BLOOM = CooShaderEffects.register(id("bloom")) {
    val extract = pass("extract") {
        fragment(id("post/bright_extract.fsh"))
        inputSceneColor("SceneColor")
    }
    val blur = pingPong("blur", iterations = 6, feedbackSampler = "Input") {
        fragment(id("post/blur_ping_pong.fsh"))
        alternate("Axis", 0, 1)
    }
    val composite = pass("composite") {
        fragment(id("post/composite.fsh"))
        inputSceneColor("SceneColor")
        input("Bloom")
        outputToScreen()
    }

    line(extract.color(), blur.input("Input"))
    line(blur.color(), composite.input("Bloom"))
}
```

普通调用方不接触 execution plan、backend 或底层后处理模型。这些内容只由 Pipeline compiler 和运行时使用。

## Shader 约定

- 文件路径使用 `assets/<namespace>/shaders/...`。
- terrain core shader 使用当前方块顶点格式声明的 attribute 名称。
- 全屏 fragment sampler 名必须与 Pipeline 输入端口一致。
- 多 attachment fragment 使用 `layout(location = N)`，并与 `colorAttachments(count)` 和 `color(N)` 对应。
- scene depth 可以声明为 optional；shader 必须能处理没有有效深度纹理的回退路径。
- uniform 名区分大小写，必须与 Pipeline 中的声明一致。

## 排查顺序

1. 检查 Pipeline 构建是否因缺失输入、重复 line 或环而失败。
2. 检查 shader 资源 ID、sampler 名、uniform 名和 attachment 下标。
3. 检查节点输出是否真的通过 `line` 接到下游输入或最终 target。
4. terrain 问题先确认绑定命中、section rebuild 和对应 base layer。
5. Iris/Sodium 环境查看兼容日志，确认显式 scene 输入拿到安全副本或进入回退；`BaseSampler` 应始终使用方块 atlas。
6. resize 或 reload 后出现黑屏时，检查 FBO 重建、资源释放和 framebuffer 恢复。

仓库内可运行示例在 `common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/pipeline/RenderPipelineExamples.kt`，APITest 的屏幕效果位于 `PostEffectDemoOptions.kt`。
