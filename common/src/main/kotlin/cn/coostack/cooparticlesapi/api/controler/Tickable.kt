package cn.coostack.cooparticlesapi.api.controler

interface Tickable<T> {
    fun addPreTickAction(action: T.() -> Unit): Tickable<T>
}