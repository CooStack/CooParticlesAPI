# 基本用法

创建一个粒子类并且继承 ControlableParticle

### Example Particle

```kotlin 
    class TestEndRodParticle(
    // Particle粒子需要的参数
    world: ClientWorld,
    pos: Vec3d,
    velocity: Vec3d,
    // 用于获取ParticleControler的粒子唯一标识符
    controlUUID: UUID,
    val provider: SpriteProvider
) :
// 必须继承 ControlableParticle类
    ControlableParticle(world, pos, velocity, controlUUID) {
    override fun getType(): ParticleTextureSheet {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE
    }

    init {
        setSprite(provider.getSprite(0, 120))
        // 由于ControlableParticle 禁止重写 tick方法
        // 使用此方法代替
        controler.addPreTickAction {
            setSpriteForAge(provider)
        }
    }

    // 基本粒子注册
    class Factory(val provider: SpriteProvider) : ParticleFactory<TestEndRodEffect> {
        override fun createParticle(
            parameters: TestEndRodEffect,
            world: ClientWorld,
            x: Double,
            y: Double,
            z: Double,
            velocityX: Double,
            velocityY: Double,
            velocityZ: Double
        ): Particle {
            return TestEndRodParticle(
                world,
                Vec3d(x, y, z),
                Vec3d(velocityX, velocityY, velocityZ),
                parameters.controlUUID,
                provider
            )
        }
    }
}
```

为了能够获取到对应的 UUID 所以你的ParticleEffect也要有uuid

```kotlin
// 作为构造参数
class TestEndRodEffect(controlUUID: UUID) : ControlableParticleEffect(controlUUID) {
    companion object {
        @JvmStatic
        val codec: MapCodec<TestEndRodEffect> = RecordCodecBuilder.mapCodec {
            return@mapCodec it.group(
                Codec.BYTE_BUFFER.fieldOf("uuid").forGetter { effect ->
                    val toString = effect.controlUUID.toString()
                    val buffer = Unpooled.buffer()
                    buffer.writeBytes(toString.toByteArray())
                    buffer.nioBuffer()
                }
            ).apply(it) { buf ->
                TestEndRodEffect(
                    UUID.fromString(
                        String(buf.array())
                    )
                )
            }
        }

        @JvmStatic
        val packetCode: PacketCodec<RegistryByteBuf, TestEndRodEffect> = PacketCodec.of(
            { effect, buf ->
                buf.writeUuid(effect.controlUUID)
            }, {
                TestEndRodEffect(it.readUuid())
            }
        )

    }

    override fun getType(): ParticleType<*> {
        return ModParticles.testEndRod
    }
}
```

使用Fabric API 在客户端处注册此粒子后
接下来进行粒子组合 (ControlableParticleGroup) 的构建

### 构建 ControlableParticleGroup

ControlableParticleGroup的作用是在玩家客户端处渲染粒子组合

构建一个基本的ControlableParticleGroup代码示例:
一个在玩家视野正中心 每tick旋转10度的魔法阵

```kotlin
class TestGroupClient(uuid: UUID, val bindPlayer: UUID) : ControlableParticleGroup(uuid) {

    // 为了让服务器能够正常的将ParticleGroup数据转发给每一个玩家
    // 服务器会发 PacketParticleGroupS2C 数据包
    // 这里是解码
    class Provider : ControlableParticleGroupProvider {
        override fun createGroup(
            uuid: UUID,
            // 这里的 args是 服务器同步给客户端用的参数
            // 可以查看 cn.coostack.network.packet.PacketParticleGroupS2C 类注释的字段不建议覆盖也无需处理(已经处理好了)
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ControlableParticleGroup {
            // 绑定到的玩家
            val bindUUID = args["bindUUID"]!!.loadedValue as UUID
            return TestGroupClient(uuid, bindUUID)
        }
    }

    // 魔法阵粒子组合
    override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        // 在XZ平面的魔法阵
        val list = Math3DUtil.getCycloidGraphic(3.0, 5.0, 2, -3, 360, 0.2).onEach { it.y += 6 }
        return list.associateBy {
            withEffect({
                // 提供ParticleEffect (在display方法中 world.addParticle)使用
                // it类型为UUID
                // 如果需要在这个位置设置一个ParticleGroup则使用
                // ParticleDisplayer.withGroup(你的particleGroup)
                ParticleDisplayer.withSingle(TestEndRodEffect(it))
            }) {
                // kt: this is ControlableParticle
                // java: this instanceof ControlableParticle
                // 用于初始化粒子信息
                // 如果参数是withGroup 则不需要实现该方法
                color = Vector3f(230 / 255f, 130 / 255f, 60 / 255f)
                this.maxAliveTick = this.maxAliveTick
            }
        }
    }


    /**
     * 当粒子第一次渲染在玩家视角的时候
     * 玩家超出渲染范围后又回归渲染范围任然会调用一次
     * 可以理解为粒子组初始化
     */
    override fun onGroupDisplay() {
        MinecraftClient.getInstance().player?.sendMessage(Text.of("发送粒子: ${this::class.java.name} 成功"))
        addPreTickAction {
            // 当玩家能够看到粒子的时候 (这个类会被构造)
            val bindPlayerEntity = world!!.getPlayerByUuid(bindPlayer) ?: let {
                return@addPreTickAction
            }
            teleportGroupTo(bindPlayerEntity.eyePos)
            rotateToWithAngle(
                RelativeLocation.of(bindPlayerEntity.rotationVector),
                Math.toRadians(10.0)
            )
        }
    }
}
```

创建好ControlableParticleGroup后, 需要在客户端进行注册

```kotlin
ClientParticleGroupManager.register(
    // 如果这个particleGroup的 loadParticleLocations方法中输入了一个子ParticleGroup 这个子Group就无需在这注册
    // 除非你需要ClientParticleGroupManager.addVisibleGroup(子Group)
    TestGroupClient::class.java, TestGroupClient.Provider()
)
```

当你完成上述操作后, 为了让其他玩家也能同步操作, 需要设置一个服务器向的ControlableParticleGroup
示例:

```kotlin
/**
 * 构造参数无要求
 */
class TestParticleGroup(private val bindPlayer: ServerPlayerEntity) :
// 第一个参数是 ParticleGroup的唯一标识符
// 这个内容会同步到客户端
// 第二个参数是粒子的可见范围
// 当玩家超出这个范围时会发送删除粒子组包(对该玩家不可见)
    ServerParticleGroup(UUID.randomUUID(), 16.0) {
    override fun tick() {
        withPlayerStats(bindPlayer)
        setPosOnServer(bindPlayer.eyePos)
    }

    /**
     * 这个是你想发送给客户端用于构建ControlableParticleGroup的参数
     * 最终会传入 ControlableParticleGroupProvider.createGroup()
     */
    override fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>> {
        return mapOf(
            "bindUUID" to ParticleControlerDataBuffers.uuid(bindPlayer.uuid)
        )
    }

    override fun getClientType(): Class<out ControlableParticleGroup> {
        return TestGroupClient::class.java
    }

}
```

完成上述构建后,只需要在服务器中添加粒子

```kotlin
val serverGroup = TestParticleGroup(user as ServerPlayerEntity)
ServerParticleGroupManager.addParticleGroup(
    //                      world必须是ServerWorld
    serverGroup, user.pos, world as ServerWorld
)
```

其余特殊用法可以查看
cn.coostack.particles.control.group.ControlableParticleGroup 与

cn.coostack.network.particle.ServerParticleGroup

#### ParticleGroup嵌套示例

- 主ParticleGroup:

```kotlin
class TestGroupClient(uuid: UUID, val bindPlayer: UUID) : ControlableParticleGroup(uuid) {

    class Provider : ControlableParticleGroupProvider {
        override fun createGroup(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ControlableParticleGroup {
            val bindUUID = args["bindUUID"]!!.loadedValue as UUID
            return TestGroupClient(uuid, bindUUID)
        }

        /**
         * 当ServerParticleGroup被调用change方法时， 在这里对group进行应用
         * 位于PacketParticleGroupS2C.PacketArgsType为key的所有参数 无需在这处理
         * 但是也会作为args参数输入
         */
        override fun changeGroup(group: ControlableParticleGroup, args: Map<String, ParticleControlerDataBuffer<*>>) {
        }
    }

    override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        val r1 = 3.0
        val r2 = 5.0
        val w1 = -2
        val w2 = 3
        val scale = 1.0
        val count = 360
        val list = Math3DUtil.getCycloidGraphic(r1, r2, w1, w2, count, scale).onEach { it.y += 6 }
        val map = list.associateBy {
            withEffect({ ParticleDisplayer.withSingle(TestEndRodEffect(it)) }) {
                color = Vector3f(230 / 255f, 130 / 255f, 60 / 255f)
                this.maxAliveTick = this.maxAliveTick
            }
        }
        val mutable = map.toMutableMap()
        // 获取此参数下生成图像的顶点
        for (rel in Math3DUtil.computeCycloidVertices(r1, r2, w1, w2, count, scale)) {
            // 在这些顶点上设置一个SubParticleGroup
            mutable[withEffect({ u -> ParticleDisplayer.withGroup(TestSubGroupClient(u, bindPlayer)) }) {}] =
                rel.clone()
        }
        return mutable
    }


    override fun onGroupDisplay() {
        MinecraftClient.getInstance().player?.sendMessage(Text.of("发送粒子: ${this::class.java.name} 成功"))
        addPreTickAction {
            // 这种方法就是其他人看到的话粒子会显示在他们的头上而不是某个玩家的头上....
            val bindPlayerEntity = world!!.getPlayerByUuid(bindPlayer) ?: let {
                return@addPreTickAction
            }
            teleportTo(bindPlayerEntity.eyePos)
            rotateToWithAngle(
                RelativeLocation.of(bindPlayerEntity.rotationVector),
                Math.toRadians(10.0)
            )
        }
    }
} 
```

子ParticleGroup实例

```kotlin
class TestSubGroupClient(uuid: UUID, val bindPlayer: UUID) : ControlableParticleGroup(uuid) {

    override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        val list = Math3DUtil.getCycloidGraphic(2.0, 2.0, -1, 2, 360, 1.0).onEach { it.y += 6 }
        return list.associateBy {
            withEffect({ ParticleDisplayer.withSingle(TestEndRodEffect(it)) }) {
                color = Vector3f(100 / 255f, 100 / 255f, 255 / 255f)
                this.maxAliveTick = this.maxAliveTick
            }
        }

    }


    override fun onGroupDisplay() {
        addPreTickAction {
            val bindPlayerEntity = world!!.getPlayerByUuid(bindPlayer) ?: let {
                return@addPreTickAction
            }
            rotateToWithAngle(
                RelativeLocation.of(bindPlayerEntity.rotationVector),
                Math.toRadians(-10.0)
            )
        }
    }
}
```

# 其他用法

## SequencedParticleGroup 用法

此类解决规定粒子生成的顺序和速度的需求
此方法修改了ControlableParticleGroup的某些基本方法
使用此类时 在服务器层使用 SequencedServerParticleGroup
示例:

```kotlin
class SequencedMagicCircleClient(uuid: UUID, val bindPlayer: UUID) : SequencedParticleGroup(uuid) {
    // 测试缩放
    var maxScaleTick = 36
    var current = 0

    // provider和正常一样
    class Provider : ControlableParticleGroupProvider {
        override fun createGroup(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ControlableParticleGroup {
            val bindUUID = args["bind_player"]!!.loadedValue as UUID
            return SequencedMagicCircleClient(uuid, bindUUID)
        }

        override fun changeGroup(
            group: ControlableParticleGroup,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ) {
        }
    }

    // 由于要记录粒子的顺序, 所以在这里使用顺序
    override fun loadParticleLocationsWithIndex(): SortedMap<SequencedParticleRelativeData, RelativeLocation> {
        val res = TreeMap<SequencedParticleRelativeData, RelativeLocation>()
        val points = Math3DUtil.getCycloidGraphic(3.0, 5.0, -2, 3, 360, .5)
//        val points = Math3DUtil.getCycloidGraphic(1.0,1.0,1,1,360,6.0)
        points.forEachIndexed { index, it ->
            res[withEffect(
                { id -> ParticleDisplayer.withSingle(TestEndRodEffect(id)) }, {
                    color = Vector3f(100 / 255f, 100 / 255f, 255 / 255f)
                }, index // 粒子的顺序 升序
            )] = it.also { it.y += 15.0 }
        }
        return res
    }

    override fun beforeDisplay(locations: SortedMap<SequencedParticleRelativeData, RelativeLocation>) {
        super.beforeDisplay(locations)
        // 设置缩放
        scale = 1.0 / maxScaleTick
    }

    var toggle = false
    override fun onGroupDisplay() {
        addPreTickAction {
            // 设置缩放 大小循环
            if (current < maxScaleTick && !toggle) {
                current++
                scale(scale + 1.0 / maxScaleTick)
            } else if (current < maxScaleTick) {
                current++
                scale(scale - 1.0 / maxScaleTick)
            } else {
                toggle = !toggle
                current = 0
            }
            // 设置旋转
            rotateParticlesAsAxis(Math.toRadians(10.0))
            val player = world!!.getPlayerByUuid(bindPlayer) ?: return@addPreTickAction
            val dir = player.rotationVector
            rotateParticlesToPoint(RelativeLocation.of(dir))
            teleportTo(player.eyePos)
        }
    }
}
```

上述粒子和ControlableParticleGroup的区别如下

1. 生成时默认粒子数量为0
2. 使用addSingle addMultiple addAll removeSingle removeAll removeMultiple 控制粒子队列生成顺序
3. 使用setSingleStatus 控制某个索引下的粒子的顺序
4. 建议使用SequencedServerParticleGroup控制粒子生成顺序

对应的 Server层

```kotlin

class SequencedMagicCircleServer(val bindPlayer: UUID) : SequencedServerParticleGroup(16.0) {
    val maxCount = maxCount()

    // 控制粒子逐个出现又消失
    var add = false

    // 控制单个粒子控制器
    var st = 0
    val maxSt = 72
    var stToggle = false
    override fun tick() {
        val player = world!!.getPlayerByUuid(bindPlayer) ?: return
        setPosOnServer(player.pos)
        if (st++ > maxSt) {
            if (!stToggle) {
                stToggle = true
                // 服务器上设置某个粒子的显示状态
                for (i in 0 until maxCount()) {
                    if (i <= 30) {
                        setDisplayed(i, true)
                    } else {
                        setDisplayed(i, false)
                    }
                }
                // 同步到客户端 粒子个数和粒子状态
                toggleCurrentCount()
            }
            return
        }
        if (add && serverSequencedParticleCount >= maxCount) {
            add = false
            serverSequencedParticleCount = maxCount
        } else if (!add && serverSequencedParticleCount <= 0) {
            add = true
            serverSequencedParticleCount = 0
        }
        // 服务器控制子粒子生成
        if (add) {
            addMultiple(10)
        } else {
            removeMultiple(10)
        }
    }

    override fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>> {
        return mapOf(
            "bind_player" to ParticleControlerDataBuffers.uuid(bindPlayer),
            toggleArgLeastIndex(),// 同步粒子数, 会生成 从第1个粒子生成到第serverSequencedParticleCount个粒子
            toggleArgStatus() // 在生成serverSequencedParticleCount粒子后, 再对clientIndexStatus内存储的状态进行同步
        )
    }

    override fun getClientType(): Class<out ControlableParticleGroup>? {
        return SequencedMagicCircleClient::class.java
    }

    /**
     * 切记一定要和 SequencedParticleGroup.loadParticleLocationsWithIndex().size 相同
     * 如果你的group的粒子数量是可变的(使用了flush方法刷新了粒子样式 其中长度发生变化)
     * 那么请在服务器层做好数据同步 ( size同步 )
     * 如果此处的 maxCount > SequencedParticleGroup.loadParticleLocationsWithIndex().size 则会导致数组越界异常
     * 如果此处的 maxCount < SequencedParticleGroup.loadParticleLocationsWithIndex().size 则会导致粒子控制不完全(部分粒子无法从服务器生成)
     */
    override fun maxCount(): Int {
        return 360
    }
}
```

# 使用 ParticleGroupStyle
## 使用此类的原因
在进行客户端和服务器的数据渲染同步时发现, 每次进行一个新的操作都要在服务器类上复制一样的代码 创建一样的变量, 相当的麻烦
于是基于 ControlableParticleGroup 和 ServerParticleGroup 构造了此类
## 使用方法
```kotlin
class ExampleStyle(val bindPlayer: UUID, uuid: UUID = UUID.randomUUID()) :
    /**
     * 第一个参数代表玩家可视范围 默认32.0
     * 第二个参数代表这个粒子样式的唯一标识符
     * 在这里直接使用默认值(randomUUID)即可
      */
    ParticleGroupStyle(16.0, uuid) {
    /**
     *  和 ControlableParticleGroup一样 为了在服务器构建这个类 同时也需要自己制作构建器
     */
    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            val player = args["bind_player"]!!.loadedValue as UUID
            return ExampleStyle(player, uuid)
        }
    }

    //  自定义参数
    val maxScaleTick = 60
    var scaleTick = 0
    val maxTick = 240
    var current = 0
    var angleSpeed = PI / 72
    
    init {
        // 如果你想要修改基类 (ParticleGroupStyle)
        // 不要在beforeDisplay修改 在构造方法内修改
        // 否则会出现联机客户端不同步的问题 (或者使用change?)
        scale = 1.0 / maxScaleTick
    }

    /**
     * 对应 ControlableParticleGroup的loadParticleLocations方法
     */
    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        // 这里采用了自制的点图形制作器 查阅 cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
        val res = mutableMapOf<StyleData, RelativeLocation>().apply {
            putAll(
                PointsBuilder()
                    .addDiscreteCircleXZ(8.0, 720, 10.0)
                    .createWithStyleData {
                        // 支持单个粒子
                        StyleData { ParticleDisplayer.withSingle(ControlableCloudEffect(it)) }
                            .withParticleHandler {
                                colorOfRGB(127, 139, 175)
                                this.scale(1.5f)
                                textureSheet = ParticleTextureSheet.PARTICLE_SHEET_LIT
                            }
                    })
            putAll(
                PointsBuilder()
                    .addCircle(6.0, 4)
                    .pointsOnEach { it.y -= 12.0 }
                    .addCircle(6.0, 4)
                    .pointsOnEach { it.y += 6.0 }
                    // 这里要你的Data构建器
                    .createWithStyleData {
                        // 相当于ControlableParticleGroup的 withEffect
                        StyleData {
                            // 也支持粒子组合
                            // 如果有其他style也可以改成 ParticleDisplayer.withStyle(xxxStyle(it,...))
                            ParticleDisplayer.withGroup(
                                MagicSubGroup(it, bindPlayer)
                            )
                        }
                    }
            )
        }
        return res
    }

    
    override fun onDisplay() {
        // 开启参数自动同步
        autoToggle = true

        /**
         * 对于区分客户端环境和服务器环境
         * 此类提供了 client 属性
         * 或者使用 world!!.isClient 也可以查询是否为客户端
         */
        addPreTickAction {
            if (scaleTick++ >= maxScaleTick) {
                return@addPreTickAction
            }
            scale(scale + 1.0 / maxScaleTick)
        }
        addPreTickAction {
            current++
            if (current >= maxTick) {
                remove()
            }
            val player = world!!.getPlayerByUuid(bindPlayer) ?: return@addPreTickAction
            teleportTo(player.pos)
            rotateParticlesAsAxis(angleSpeed)
        }
    }
    
    // 参数自动同步时, 服务器的这些参数会自动同步到每一个客户端上
    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf(
            "current" to ParticleControlerDataBuffers.int(current),
            "angle_speed" to ParticleControlerDataBuffers.double(angleSpeed),
            "bind_player" to ParticleControlerDataBuffers.uuid(bindPlayer),
            "scaleTick" to ParticleControlerDataBuffers.int(scaleTick),
        )
    }
    // 获取来自服务器的同步数据时, 执行此方法
    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        if (args.containsKey("current")) {
            current = args["current"]!!.loadedValue as Int
        }
        if (args.containsKey("angle_speed")) {
            angleSpeed = args["angle_speed"]!!.loadedValue as Double
        }
        if (args.containsKey("scaleTick")) {
            scaleTick = args["scaleTick"]!!.loadedValue as Int
        }
    }
}
```

完成类的构建时 需要在ClientModInitializer进行注册
```kotlin
    ParticleStyleManager.register(ExampleStyle::class.java, ExampleStyle.Provider())
```
如何在服务器生成此粒子样式?
这里以Item为例
```kotlin
    class TestStyleItem : Item(Settings()) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack?>? {
        val res = super.use(world, user, hand)
        // 如果你在world.isClient 为true环境下生成粒子
        // 则该生成只会针对这一个客户端
        // 否则就是在服务器生成- 所有符合条件的玩家都能看到
        if (world.isClient) {
            return res
        }
        val style = ExampleStyle(user.uuid)
        // server world
        ParticleStyleManager.spawnStyle(world, user.pos, style)
        // 测试自动同步用的延时
        CooParticleAPI.scheduler.runTask(30) {
            style.angleSpeed += PI / 72
        }
        return res
    }
}
```

# 粒子样式Helper 使用规范
- 所有的Helper必须在构造函数中执行loadControler方法
- 否则会出现应用失败的BUG (原因未知)

# 粒子发射器
## 前言
- 由于前面的所有示例均指向一个有限粒子个数的样式
为了更好的实现 例如爆炸, 冲击波, 火焰等粒子效果
特地抽象出此类
###  此粒子发射器主要有3个类用于实现功能
- SimpleParticleEmitters
- PhysicsParticleEmitters
- ClassParticleEmitters
#### 这三个类对应的3个不同的实现方法
首先讲下前两个类
#### SimpleParticleEmitters
```kotlin
/**
 * 粒子发射器位置偏移量表达式 提供t作为参数 t 生成时间 int类型
 */
    var evalEmittersXWithT = "0"
    var evalEmittersYWithT = "0"
    var evalEmittersZWithT = "0"

    /**
     * 提供了多种预设
     * box 箱子发射器
     * point 点发射器
     * math 数学轨迹发射器
     */
    var shootType = EmittersShootTypes.point()
    private var bufferX = Expression(evalEmittersXWithT)
    private var bufferY = Expression(evalEmittersYWithT)
    private var bufferZ = Expression(evalEmittersZWithT)
    var offset = Vec3d(0.0, 0.0, 0.0)
    /**
     * 每tick生成粒子个数
     */
    var count = 1
    /**
     * 每tick实际生成的粒子个数会受此影响 随机范围(0 .. countRandom)
     */
    var countRandom = 0
```
#### 构建方法
```kotlin
val emitters = SimpleParticleEmitters(位置,服务器世界,粒子信息)
val emitters = PhysicsParticleEmitters(位置,服务器世界,粒子信息)
```
#### 粒子信息同步
- 为了能够更方便的在服务器之间同步粒子属性
- 构建了ControlableParticleData类
- 如果你的ParticleEffect有其他的属性
- 可以继承此类并且重写PacketCodec解析器
```kotlin
/**
 * 此类可以修改的属性
 */
// UUID不建议修改
var uuid = UUID.randomUUID()
var velocity: Vec3d = Vec3d.ZERO
var size = 0.2f
var color = Vector3f(1f, 1f, 1f)
var alpha = 1f
var age = 0
var maxAge = 120
// 粒子可见范围(未测试)
var visibleRange = 128f
// 当你想要修改成其他Effect时(只支持ControlableParticleEffect 实现一个可控制粒子详细见 TestEndRodEffect的实现方式)
var effect: ControlableParticleEffect = TestEndRodEffect(uuid)
var textureSheet = ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
/**
 * 粒子移动速度 在SimpleParticleEmitter和PhysicsParticleEmitter中应用
 */
var speed = 1.0
```
#### PhysicsParticleEmitters
- 此类提供了一些基本物理参数 重力, 空气密度, 质量, 风向

```kotlin
/**
 * 重力加速度 时间单位是tick
 */
var gravity = 0.0
/**
 * 空气密度
 */
var airDensity = 0.0
/**
 * 风力方向
 */
var wind: WindDirection = GlobalWindDirection(Vec3d.ZERO)
/**
 * 质量
 * 单位 g
 */
var mass = 1.0
```
在世界中启用emitter的发射
```kotlin
ParticleEmittersManager.spawnEmitters(emitter)
```
修改emitter的属性
```kotlin
var pos: Vec3d // 发射器的位置
var world: World? // 发射器所处的世界 (在构建生成时不能传入null 可null是因为在序列化的时候不用传入世界信息)
var tick: Int // 生成时间 tick
/**
 * 当maxTick == -1时
 * 代表此粒子不会由生命周期控制
 * 粒子生命周期
 */
var maxTick: Int
// 发射延时 (每一次发射后都会延迟delay)
var delay: Int
// 发射器唯一标识符 不建议修改
var uuid: UUID
// 如果设置为true则会导致发射器失效
var cancelled: Boolean
// 是否已经在世界中生成
var playing: Boolean
```
#### 注意
- 修改发射器属性只在服务器环境修改
- 每一个tick都会自动同步发射器属性给所有可视客户端

#### ClassParticleEmitters
- 这个类是一个抽象类 其实就是给开发者(我)偷懒写表达式用的
- 可以按照自定义的规则生成粒子
```kotlin
abstract class ClassParticleEmitters(
    override var pos: Vec3d,
    override var world: World?,
) : ParticleEmitters {
    override var tick: Int = 0
    override var maxTick: Int = 120
    override var delay: Int = 0
    override var uuid: UUID = UUID.randomUUID()
    override var cancelled: Boolean = false
    override var playing: Boolean = false
    var airDensity = 0.0
    var gravity: Double = 0.0

    companion object {
        fun encodeBase(data: ClassParticleEmitters, buf: RegistryByteBuf) {
            buf.writeVec3d(data.pos)
            buf.writeInt(data.tick)
            buf.writeInt(data.maxTick)
            buf.writeInt(data.delay)
            buf.writeUuid(data.uuid)
            buf.writeBoolean(data.cancelled)
            buf.writeBoolean(data.playing)
            buf.writeDouble(data.gravity)
            buf.writeDouble(data.airDensity)
            buf.writeDouble(data.mass)
            buf.writeString(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        /**
         * 写法
         * 先在codec的 decode方法中 创建此对象
         * 然后将buf和container 传入此方法
         * 然后继续decode自己的参数
         */
        fun decodeBase(container: ClassParticleEmitters, buf: RegistryByteBuf) {
            val pos = buf.readVec3d()
            val tick = buf.readInt()
            val maxTick = buf.readInt()
            val delay = buf.readInt()
            val uuid = buf.readUuid()
            val canceled = buf.readBoolean()
            val playing = buf.readBoolean()
            val gravity = buf.readDouble()
            val airDensity = buf.readDouble()
            val mass = buf.readDouble()
            val id = buf.readString()
            val wind = WindDirections.getCodecFromID(id)
                .decode(buf)
            container.apply {
                this.pos = pos
                this.tick = tick
                this.maxTick = maxTick
                this.delay = delay
                this.uuid = uuid
                this.cancelled = canceled
                this.airDensity = airDensity
                this.gravity = gravity
                this.mass = mass
                this.playing = playing
                this.airDensity = airDensity
                this.wind = wind
            }

        }

    }

    /**
     * 风力方向
     */
    var wind: WindDirection = GlobalWindDirection(Vec3d.ZERO).also {
        it.loadEmitters(this)
    }

    /**
     * 质量
     * 单位 g
     */
    var mass: Double = 1.0
    override fun start() {
        if (playing) return
        playing = true
        if (world?.isClient == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun stop() {
        cancelled = true
        if (world?.isClient == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun tick() {
        if (cancelled || !playing) {
            return
        }
        if (tick++ >= maxTick && maxTick != -1) {
            stop()
        }

        world ?: return
        doTick()
        if (!world!!.isClient) {
            return
        }

        if (tick % max(1, delay) == 0) {
            // 执行粒子变更操作
            // 生成新粒子
            spawnParticle()
        }
    }

    override fun spawnParticle() {
        if (!world!!.isClient) {
            return
        }
        val world = world as ClientWorld
        // 生成粒子样式
        genParticles().forEach {
            spawnParticle(world, pos.add(it.value.toVector()), it.key)
        }
    }

    /**
     * 服务器和客户端都会执行此方法
     * 判断服务器清使用 if(!world!!.isClient)
     */
    abstract fun doTick()

    /**
     * 粒子样式生成器
     */
    abstract fun genParticles(): Map<ControlableParticleData, RelativeLocation>

    /**
     * 如若要修改粒子的位置, 速度 属性
     * 请直接修改 ControlableParticleData
     * @param data 用于操作单个粒子属性的类
     * 执行tick方法请使用
     * controler.addPreTickAction
     */
    abstract fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    )

    private fun spawnParticle(world: ClientWorld, pos: Vec3d, data: ControlableParticleData) {
        val effect = data.effect
        effect.controlUUID = data.uuid
        val displayer = ParticleDisplayer.withSingle(effect)
        val control = ControlParticleManager.createControl(effect.controlUUID)
        control.initInvoker = {
            this.size = data.size
            this.color = data.color
            this.currentAge = data.age
            this.maxAge = data.maxAge
            this.textureSheet = data.textureSheet
            this.particleAlpha = data.alpha
        }
        singleParticleAction(control, data, pos, world)
        control.addPreTickAction {
            // 模拟粒子运动 速度
            teleportTo(
                this.pos.add(data.velocity)
            )
            if (currentAge++ >= maxAge) {
                markDead()
            }
        }
        displayer.display(pos, world)
    }

    protected fun updatePhysics(pos: Vec3d, data: ControlableParticleData) {
        val m = mass / 1000
        val v = data.velocity
        val speed = v.length()
        val gravityForce = Vec3d(0.0, -m * gravity, 0.0)
        val airResistanceForce = if (speed > 0.01) {
            val dragMagnitude = 0.5 * airDensity * DRAG_COEFFICIENT *
                    CROSS_SECTIONAL_AREA * speed.pow(2) * 0.05
            v.normalize().multiply(-dragMagnitude)
        } else {
            Vec3d.ZERO
        }
        val windForce = WindDirections.handleWindForce(
            wind, pos,
            airDensity, DRAG_COEFFICIENT, CROSS_SECTIONAL_AREA, v
        )

        val a = gravityForce
            .add(airResistanceForce)
            .add(windForce)
            .multiply(1.0 / m)

        data.velocity = v.add(a)
    }


    /**
     * 可选重写
     * 但是重写请注意一定要记得调用super.update() 也就是这里的方法
     * 或者你愿意复制一段一模一样的
     * 要不然会出现更新失败的问题
     * 
     * 实现注意事项
     * 如果你写的ClassParticleEmitters 存在一些新的参数
     * 并且在使用的过程中可能会在外部发生改变
     * 那么就必须在update里实现赋值
     * 输入参数 emitters 接受到更新后生成的emitters (输入的参数只起到参数传输的作用)
     */
    override fun update(emitters: ParticleEmitters) {
        if (emitters !is ClassParticleEmitters) return
        this.pos = emitters.pos
        this.world = emitters.world
        this.tick = emitters.tick
        this.maxTick = emitters.maxTick
        this.delay = emitters.delay
        this.uuid = emitters.uuid
        this.cancelled = emitters.cancelled
        this.playing = emitters.playing
    }
}
```

#### 实现示例
```kotlin
class ExampleClassParticleEmitters(pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var moveDirection = Vec3d.ZERO
    var templateData = ControlableParticleData()

    companion object {
        // 必须提供发射器ID 用于序列化使用
        const val ID = "example-class-particle-emitters"

        @JvmStatic
        // 构建自己的CODEC 用于同步自己写的数据
        val CODEC = PacketCodec.ofStatic<RegistryByteBuf, ParticleEmitters>(
            { buf, data ->
                data as ExampleClassParticleEmitters
                // 请务必调用此方法(来源ClassParticleEmitters) 用于同步父类的参数
                encodeBase(data, buf)
                buf.writeVec3d(data.moveDirection)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val instance = ExampleClassParticleEmitters(Vec3d.ZERO, null)
                // 请务必调用此方法(来源ClassParticleEmitters) 反序列化父类的参数
                decodeBase(instance, it)
                instance.moveDirection = it.readVec3d()
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }
    
    // 自己 发射器tick
    override fun doTick() {
        pos = pos.add(moveDirection)
    }

    /**
     * 每delay tick后会调用此方法
     * delay是ParticleEmitters提供的参数 和上面的意义相同
     * 获取粒子生成的位置
     */
    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        return PointsBuilder()
            .addBall(2.0, 20)
            .create().associateBy {
                // 复制输入的粒子数据
                templateData.clone()
                    .apply {
                        // 对粒子初速度进行修改 (类似球的收缩)
                        this.velocity = it.normalize().multiplyClone(-0.1).toVector()
                    }
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    ) {
        // 每生成一个粒子就会执行此方法
        // 如果要给粒子单体设置某些运动方式
        // 或者不透明度, 颜色的变化
        // 请使用 controler.addPreTickAction 设置每tick的粒子变化方法
        // 对data的修改也会同步应用到粒子上
        // spawnPos是首次生成的位置
        // spawnWorld是首次生成的世界(不会改变)
    }

    override fun update(emitters: ParticleEmitters) {
        super.update(emitters)
        if (emitters !is ExampleClassParticleEmitters) {
            return
        }
        this.templateData = emitters.templateData
        this.moveDirection = emitters.moveDirection
    }

    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<RegistryByteBuf, ParticleEmitters> {
        return CODEC
    }
}
```
实现完成后 不要忘记注册
```kotlin
ParticleEmittersManager.register(ExampleClassParticleEmitters.ID,ExampleClassParticleEmitters.CODEC)
```