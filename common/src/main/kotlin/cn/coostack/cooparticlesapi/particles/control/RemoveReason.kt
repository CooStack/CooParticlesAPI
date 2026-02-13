package cn.coostack.cooparticlesapi.particles.control

enum class RemoveReason {
    /**
     * 因为驱逐队列或者手动调用了clearParticles而被清理的粒子
     */
    QUEUE,

    /**
     * 因为 age >= maAge 而被清理的粒子
     */
    LIFECYCLE,

    /**
     * 因为模组手动调用remove而清理的粒子
     */
    CALL

}