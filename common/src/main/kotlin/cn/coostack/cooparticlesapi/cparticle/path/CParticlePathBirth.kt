package cn.coostack.cooparticlesapi.cparticle.path

import net.minecraft.world.phys.Vec3

/**
 * 一颗粒子的不可变出生参考，独立于当前路径命令和当前模拟位置。
 *
 * @property position 出生位置，坐标系与路径绑定后的输出一致（通常是发射器或 system 相对坐标）
 * @property age 出生时的已有年龄（tick），用于非零出生进度；不是命令第一次执行时的年龄
 */
data class CParticlePathBirth(val position: Vec3, val age: Double = 0.0)
