package cn.coostack.cooparticlesapi.animation

object AnimateManager {
    val displayedServerAnimates = ArrayList<Animate>()
    val displayedClientAnimates = ArrayList<Animate>()

    fun displayAnimateServer(animate: Animate) {
        displayedServerAnimates.add(animate)
        animate.start()
    }

    fun displayAnimateClient(animate: Animate) {
        displayedClientAnimates.add(animate)
        animate.start()
    }


    fun tickServer() {
        val iterator = displayedServerAnimates.iterator()
        while (iterator.hasNext()) {
            val animate = iterator.next()
            animate.tick()
            if (animate.done) {
                iterator.remove()
            }
        }
    }

    fun tickClient() {
        val iterator = displayedClientAnimates.iterator()
        while (iterator.hasNext()) {
            val animate = iterator.next()
            animate.tick()
            if (animate.done) {
                iterator.remove()
            }
        }
    }
}