package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/**
 * 缩短指令长度
 */
fun of(modID: String, path: String) = ResourceLocation.fromNamespaceAndPath(modID, path)

fun of(path: String) = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)

fun ofVanilla(path: String) = ResourceLocation.fromNamespaceAndPath("minecraft", path)