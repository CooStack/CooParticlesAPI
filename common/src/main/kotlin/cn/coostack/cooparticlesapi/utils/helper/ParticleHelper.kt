package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.particles.Controlable

interface ParticleHelper {
    fun loadControler(controler: Controlable<*>)
}