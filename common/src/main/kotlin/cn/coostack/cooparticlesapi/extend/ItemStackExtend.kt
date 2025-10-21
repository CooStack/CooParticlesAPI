package cn.coostack.cooparticlesapi.extend

import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

fun ItemStack.isOf(item: Item): Boolean = this.`is`(item)
fun ItemStack.isOf(item: TagKey<Item>): Boolean = this.`is`(item)

