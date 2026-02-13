package cn.coostack.cooparticlesapi.utils.helper

import cn.coostack.cooparticlesapi.api.controler.Controlable

interface ParticleHelper {
    fun loadControler(controler: Controlable<*>)
}