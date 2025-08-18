package cn.coostack.cooparticlesapi.barrages

class BarrageOption {
    companion object {
        /**
         * 重力加速度
         * 单位 m/ tick^2
         * 即 9.8 / (20 * 20)
         */
        const val G = 0.0245
    }

    /**
     * 是否可以穿过方块
     * 如果为true则 在result的block一定为null
     */
    var acrossBlock = false

    /**
     * 是否可以穿过类似 草 这种没有方块碰撞箱的方块
     */
    var acrossEmptyCollectionShape = true

    /**
     * 是否忽略其他的弹幕 (shooter不相同的弹幕)
     */
    var barrageIgnored = true

    /**
     * 是否可以穿过液体
     * 如果为false则遇到液体会触发onHit方法
     */
    var acrossLiquid = true

    /**
     * 设置为true时,即使击中了(触发了onHit方法)
     * 也不会销毁弹幕
     */
    var acrossable = false

    /**
     * 如果 acrossable为true 则此值代表onHit方法被触发的次数
     * -1 代表不做限制
     */
    var maxAcrossCount = -1

    /**
     * 一个弹幕能存在的最大时间 单位 tick
     * 达到最大时间时会触发onHit方法
     * 当设置成-1时 不启用
     */
    var maxLivingTick = -1

    /**
     * 在此设定值内的时间不会因为击中实体而触发onHit方法
     * 防止一放出就触发
     * 设置<=0 取消此功能
     */
    var noneHitBoxTick = 3

    /**
     * 如果设置为false则取消速度设置 (则速度取决于设置的direction的长度)
     * @see speed
     */
    var enableSpeed = false

    /**
     * 弹幕的速度
     * @see enableSpeed 为true时生效
     */
    var speed = -1.0

    /**
     * 加速度
     * 计算方法为 speed += acceleration / 每tick执行一次
     */
    var acceleration = 0.0

    /**
     * 在加速度不为0的情况下 是否启用加速度速度上限
     * 判定方法 max(accelerationMaxSpeed, abs(speed))
     * @see accelerationMaxSpeed
     */
    var accelerationMaxSpeedEnabled = false

    /**
     * 在加速度不为0的情况下，自然添加速度的上限
     * 如果你设定的速度大于这个值 则也会受到这个值的影响
     * @see accelerationMaxSpeedEnabled
     */
    var accelerationMaxSpeed = 1.0
}