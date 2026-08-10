package cn.coostack.cooparticlesapi.test.api

import net.minecraft.resources.ResourceLocation

/**
 * 按照自定义的方式去构建 group
 * 在manager里 使用代码注册
 *
 * 主要看代码会不会报错 或者直接崩游戏
 */
interface TestGroupBuilder {

    /** @return 待构建测试组的资源 ID */
    fun groupID(): ResourceLocation

    fun build(): TestGroup
}
