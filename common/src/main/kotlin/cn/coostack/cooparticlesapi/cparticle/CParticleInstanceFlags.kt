package cn.coostack.cooparticlesapi.cparticle

/**
 * CParticle 实例 flags 的唯一位分配表。
 *
 * 所有位都低于 float 的 24 位精确整数范围，CPU/SSBO/vertex attribute 转换不会丢位。
 * Example: [pack] 后可用 [cameraMode]、[blockLight] 和 [skyLight] 解包。
 * Forbidden: shader 新功能不能自行占用未登记的位。
 */
object CParticleInstanceFlags {
    /** 活跃槽位标记，bit 0。Example: dead slot 会清除此位。Forbidden: 不要用于相机模式。 */
    const val ALIVE = 1 shl 0

    /** 相机模式起始位，bit 1..2。Example: ROTATION 写入值 `2`。Forbidden: 值宽不能超过 2 位。 */
    const val CAMERA_SHIFT = 1

    /** 方块光起始位，bit 3..6。Example: `15` 表示满方块光。Forbidden: 不要写入负值。 */
    const val BLOCK_LIGHT_SHIFT = 3

    /** 天空光起始位，bit 7..10。Example: `15` 表示满天空光。Forbidden: 不要与方块光共用位。 */
    const val SKY_LIGHT_SHIFT = 7

    /** 每 tick 随机动画帧，bit 11。Example: randomAgePreTick 开启时置位。Forbidden: 不要改变真实 age。 */
    const val RANDOM_AGE = 1 shl 11

    /** ROTATION 使用方向向量，bit 12。Example: rotationDirection 非空时置位。Forbidden: billboard 不读取此位。 */
    const val ROTATION_DIRECTION = 1 shl 12

    /** 使用稳定随机 1/4 UV 裁剪，bit 13。Example: Block randomCrop 开启时置位。Forbidden: 不要为每粒子注册裁剪 UV。 */
    const val RANDOM_QUARTER_UV = 1 shl 13

    /** 蒙版使用稳定随机 1/4 UV 裁剪，bit 14。Example: 方块蒙版开启 randomCrop 时置位。Forbidden: 不要裁剪基础纹理两次。 */
    const val MASK_RANDOM_QUARTER_UV = 1 shl 14

    /** 方块占用网格碰撞，bit 15。Example: emitter data 开启 blockCollision 时置位。Forbidden: SCRIPTED 句柄不读取此位。 */
    const val BLOCK_COLLISION = 1 shl 15

    /** 当前已分配位形成的最大值。Example: 可用于 float 精确性测试。Forbidden: 不要把它当成 descriptor 上限。 */
    const val MAX_PACKED_VALUE = (1 shl 16) - 1

    /** float 能精确表示的整数边界。Example: flags 必须小于此值。Forbidden: 不要分配 bit 24。 */
    const val FLOAT_EXACT_INTEGER_LIMIT = 1 shl 24

    /**
     * 打包实例 flags。
     *
     * Example: `pack(true, 2, 5, 12, true, true, false)`。
     * Forbidden: 超出范围的模式和光照会被掩码截断，不应依赖该行为做校验。
     *
     * @param alive 槽位是否参与绘制
     * @param cameraMode 相机模式的低 2 位
     * @param blockLight 方块光的低 4 位
     * @param skyLight 天空光的低 4 位
     * @param randomAge 是否每 tick 随机选动画帧
     * @param rotationDirection 是否使用方向向量
     * @param randomQuarterUv 是否随机裁剪 1/4 UV
     * @param blockCollision 是否使用方块占用网格碰撞
     * @return 可无损存进 float 的整数 flags
     */
    @JvmStatic
    @JvmOverloads
    fun pack(
        alive: Boolean,
        cameraMode: Int,
        blockLight: Int,
        skyLight: Int,
        randomAge: Boolean = false,
        rotationDirection: Boolean = false,
        randomQuarterUv: Boolean = false,
        blockCollision: Boolean = false,
    ): Int = packWithMask(
        alive,
        cameraMode,
        blockLight,
        skyLight,
        randomAge,
        rotationDirection,
        randomQuarterUv,
        false,
        blockCollision,
    )

    /**
     * 打包包含蒙版随机裁剪和方块碰撞位的内部实例 flags。
     *
     * Example: store 生成实例时一次写入所有功能位。
     * Forbidden: 新功能不能在调用方绕开本入口自行分配位。
     */
    internal fun packWithMask(
        alive: Boolean,
        cameraMode: Int,
        blockLight: Int,
        skyLight: Int,
        randomAge: Boolean,
        rotationDirection: Boolean,
        randomQuarterUv: Boolean,
        randomMaskQuarterUv: Boolean,
        blockCollision: Boolean = false,
    ): Int {
        var flags = if (alive) ALIVE else 0
        flags = flags or ((cameraMode and 3) shl CAMERA_SHIFT)
        flags = flags or ((blockLight and 15) shl BLOCK_LIGHT_SHIFT)
        flags = flags or ((skyLight and 15) shl SKY_LIGHT_SHIFT)
        if (randomAge) flags = flags or RANDOM_AGE
        if (rotationDirection) flags = flags or ROTATION_DIRECTION
        if (randomQuarterUv) flags = flags or RANDOM_QUARTER_UV
        if (randomMaskQuarterUv) flags = flags or MASK_RANDOM_QUARTER_UV
        if (blockCollision) flags = flags or BLOCK_COLLISION
        return flags
    }

    /**
     * 解包相机模式。
     *
     * Example: `cameraMode(pack(...))` 返回输入模式的低 2 位。
     * Forbidden: 不要用它读取 lighting bits。
     *
     * @param flags 已打包实例 flags
     * @return `0..3` 相机模式
     */
    @JvmStatic
    fun cameraMode(flags: Int): Int = (flags ushr CAMERA_SHIFT) and 3

    /**
     * 解包方块光。
     *
     * Example: 满亮度返回 `15`。
     * Forbidden: 返回值不是 Minecraft packed light 整数。
     *
     * @param flags 已打包实例 flags
     * @return `0..15` 方块光
     */
    @JvmStatic
    fun blockLight(flags: Int): Int = (flags ushr BLOCK_LIGHT_SHIFT) and 15

    /**
     * 解包天空光。
     *
     * Example: 满亮度返回 `15`。
     * Forbidden: 不要把它当作方块光。
     *
     * @param flags 已打包实例 flags
     * @return `0..15` 天空光
     */
    @JvmStatic
    fun skyLight(flags: Int): Int = (flags ushr SKY_LIGHT_SHIFT) and 15
}
