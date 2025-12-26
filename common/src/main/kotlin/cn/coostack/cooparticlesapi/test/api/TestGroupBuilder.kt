package cn.coostack.cooparticlesapi.test.api

/**
 * 按照自定义的方式去构建 group
 * 在manager里 使用代码注册
 *
 * 主要看代码会不会报错 或者直接崩游戏
 */
interface TestGroupBuilder {


    fun groupID(): String

    fun build(): TestGroup
}