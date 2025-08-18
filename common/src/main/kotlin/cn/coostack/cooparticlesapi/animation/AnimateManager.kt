package cn.coostack.cooparticlesapi.animation



object AnimateManager {
    val serverAnimates: MutableList<StreamAnimation> = ArrayList()
    val clientAnimates: MutableList<StreamAnimation> = ArrayList()

    fun addClient(animation: StreamAnimation) {
        clientAnimates.add(animation)
    }

    fun addServer(animation: StreamAnimation) {
        serverAnimates.add(animation)
    }

    fun tickServer() {
        val iterator = serverAnimates.iterator()
        tick(iterator)
    }

    fun tickClient() {
        val iterator = clientAnimates.iterator()
        tick(iterator)
    }

    private fun tick(iterator: MutableIterator<StreamAnimation>) {
        while (iterator.hasNext()) {
            val anim = iterator.next()
            anim.tick()
            if (anim.canceled) {
                iterator.remove()
                continue
            }
        }
    }

}