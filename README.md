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
