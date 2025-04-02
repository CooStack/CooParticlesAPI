
     * API创建粒子组合使用流程
     * 1. 创建自定义粒子: 自定义粒子需要继承 ControlableParticle
     * 2. 自行创建ParticleEffect 要求ParticleEffect必须包含UUID 这个UUID会在构造group时输入 并且使用Fabric API正常注册
     * 3. 继承 ControlableParticleGroup 并且实现粒子组合的位置关系 其中
     * loadParticleLocations 的ParticleRelativeData 的第一个参数要求输入 ParticleEffect构造器
     * 改构造器只会提供一个参数即粒子UUID 你必须使用这个参数作为该Effect的UUID否则会产生NullPointerException (获取控制器失败)
     * 4. 在进行ControlableParticle的Factory构建时, 要求输入的uuid来源一定是effect的uuid 并且effect遵从上面的要求
     * 5. 在ClientParticleGroupManager注册该ControlableParticleGroup 并且提供ControlableParticleGroup的构建器
     * @see ClientParticleGroupManager.register(你的客户端粒子组class, 构建器构造器)
     * 构建器构造器lambda要求返回 ControlableParticleGroupProvider 类型 这个类型的createGroup方法的第一个参数是该group的UUID
     * 在new 你的Group时 一定要将该uuid作为参数输入0
     * 第二个参数是ServerParticleGroup 传输给客户端的参数 用于同步服务器信息
     * 6. 创建一个对标的ServerParticleGroup 并且重写tick / otherPacketArgs 方法
     * otherPacketArgs方法的返回结果会作为参数传输到客户端ControlableParticleGroup类中
     * 创建后使用 ServerParticleGroupManager 添加该粒子组合
     * 此时客户端就可以同步收到该粒子组合并且按照ControlableParticleGroup设定的tick方法变化
     *
     * 需要继承的类
     * @see ControlableParticleGroup 客户端渲染粒子行为
     * @see ControlableParticleGroupProvider 服务端发送数据包后 解析成ControlableParticleGroup的构造器
     * @see ServerParticleGroup 用于同步给其他客户端的服务器粒子组控制器对象
     * @see ControlableParticle 能够被控制的粒子 (原版addParticle是不提供粒子对象的)
     * @see ParticleEffect 对应所需要的粒子效果
     *
