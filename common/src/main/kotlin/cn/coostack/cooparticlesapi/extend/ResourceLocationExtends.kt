package cn.coostack.cooparticlesapi.extend

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.Identifier

/**
 * 缩短指令长度
 */
fun ofID(modID: String, path: String) = Identifier.fromNamespaceAndPath(modID, path)

fun ofID(path: String) = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)

fun ofVanillaID(path: String) = Identifier.fromNamespaceAndPath("minecraft", path)

fun Identifier.asID(modID: String): Identifier = ofID(modID, this.path)