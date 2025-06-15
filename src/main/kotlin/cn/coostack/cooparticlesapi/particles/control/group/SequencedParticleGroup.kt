package cn.coostack.cooparticlesapi.particles.control.group

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MathDataUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Vec3d
import java.util.SortedMap
import java.util.UUID

/**
 * 为了实现粒子一个一个出现的效果
 * 专门写的这个类
 * 对应的服务器端请搭配 SequencedServerParticleGroup 使用
 * 或者 在ServerParticleGroup搭配toggle 参数使用
 */
abstract class SequencedParticleGroup(uuid: UUID) : ControlableParticleGroup(uuid) {
    class SequencedParticleRelativeData(
        effect: (UUID) -> ParticleDisplayer,
        invoker: ControlableParticle.() -> Unit,
        val order: Int
    ) :
        ParticleRelativeData(effect, invoker), Comparable<SequencedParticleRelativeData> {
        override fun compareTo(other: SequencedParticleRelativeData): Int {
            return order - other.order
        }

        override fun equals(other: Any?): Boolean {
            return other === this
        }

        override fun hashCode(): Int {
            return order
        }

    }

    val displayedStatus: LongArray by lazy {
        LongArray(sequencedParticles.size)
    }

    /**
     * 统计显示的粒子个数
     */
    var particleDisplayedCount = 0
        private set

    /**
     * 此参数会输入args
     */
    var particleLinkageDisplayCurrentIndex = 0
        private set

    final override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        return mapOf()
    }

    final override fun beforeDisplay(locations: Map<ParticleRelativeData, RelativeLocation>) {
    }


    open fun beforeDisplay(locations: SortedMap<SequencedParticleRelativeData, RelativeLocation>) {}
    abstract fun loadParticleLocationsWithIndex(): SortedMap<SequencedParticleRelativeData, RelativeLocation>

    /**
     * 统计粒子顺序
     */
    protected val sequencedParticles = ArrayList<Pair<SequencedParticleRelativeData, RelativeLocation>>()

    override fun flush() {
        if (canceled || !valid || !displayed) return
        remove()
        valid = true
        axis = RelativeLocation(0.0, 1.0, 0.0)
        particleDisplayedCount = 0
        displayParticles()
    }

    override fun display(pos: Vec3d, world: ClientWorld) {
        if (displayed) {
            return
        }
        displayed = true
        this.origin = pos
        this.world = world
        displayParticles()
        onGroupDisplay()
    }

    private fun displayParticles() {
        val locations = loadParticleLocationsWithIndex()
        sequencedParticles.addAll(locations.map { it.key to it.value })
        beforeDisplay(locations)
        toggleScale(locations)
    }

    fun withEffect(
        effect: (UUID) -> ParticleDisplayer,
        invoker: ControlableParticle.() -> Unit,
        order: Int
    ): SequencedParticleRelativeData {
        return SequencedParticleRelativeData(effect, invoker, order)
    }


    fun setSingleStatus(index: Int, status: Boolean) {
        val page = MathDataUtil.getStoragePageLong(index)
        val bit = MathDataUtil.getStorageWithBitLong(index)
        val container = displayedStatus[page]
        val currentStatus = MathDataUtil.getStatusLong(
            container, bit
        ) == 1
        if (currentStatus == status) {
            return
        }

        toggleFromStatus(index, status)
        displayedStatus[page] = MathDataUtil.setStatusLong(container, bit, status)
    }

    fun addSingle(): Boolean {
        if (particleLinkageDisplayCurrentIndex >= sequencedParticles.size || sequencedParticles.isEmpty()) {
            return false
        }
        particleDisplayedCount++
        return createWithIndex(particleLinkageDisplayCurrentIndex++)
    }

    fun addMultiple(count: Int): Boolean {
        if (particleLinkageDisplayCurrentIndex >= sequencedParticles.size || sequencedParticles.isEmpty()) {
            return false
        }
        for (i in 0..<count) {
            if (!addSingle()) {
                break
            }
        }
        return true
    }

    fun addAll(): Boolean {
        return addMultiple(sequencedParticles.size - particleLinkageDisplayCurrentIndex)
    }

    fun removeSingle(): Boolean {
        if (particleLinkageDisplayCurrentIndex < 0 || sequencedParticles.isEmpty()) {
            return false
        }
        particleDisplayedCount--
        val currentPair = sequencedParticles[particleLinkageDisplayCurrentIndex-- - 1]
        val currentUUID = currentPair.first.uuid
        val particle = particles[currentUUID] ?: return true
        particlesLocations.remove(particle)
        particle.remove()
        return true
    }

    fun removeMultiple(count: Int): Boolean {
        if (particleLinkageDisplayCurrentIndex < 0 || sequencedParticles.isEmpty()) {
            return false
        }

        for (i in 0..<count) {
            if (!removeSingle()) {
                break
            }
        }
        return true
    }

    fun removeAll(): Boolean {
        return removeMultiple(particleDisplayedCount)
    }

    fun toggle(count: Int) {
        if (particleLinkageDisplayCurrentIndex == count) return
        if (particleLinkageDisplayCurrentIndex == 0) {
            addMultiple(count)
            return
        }

        if (count > particleLinkageDisplayCurrentIndex) {
            removeMultiple(count - particleLinkageDisplayCurrentIndex)
            return
        }

        removeMultiple(particleLinkageDisplayCurrentIndex - count)
    }

    fun toggleStatus(statusArray: LongArray) {
        if (statusArray.isEmpty()) return
        if (displayedStatus.isEmpty()) return
        if (!displayed) return
        statusArray.forEachIndexed { page, container ->
            displayedStatus[page] = container
            for (bit in 1..64) {
                val index = page * 64 + bit - 1
                if (index >= sequencedParticles.size) {
                    break
                }
                val status = MathDataUtil.getStatusLong(container, bit)
                toggleFromStatus(index, status == 1)
            }
        }
    }

    override fun remove() {
        super.remove()
        sequencedParticles.clear()
    }

    override fun scale(new: Double) {
        if (new < 0.0) {
            CooParticleAPI.logger.error("scale can not be less than zero")
            return
        }
        scale = new
        if (displayed) {
            toggleScaleDisplayed()
        }
    }

    final override fun toggleScaleDisplayed() {
        if (scale == 1.0) {
            return
        }
        sequencedParticles.forEach {
            val uuid = it.first.uuid
            val len = particlesDefaultScaleLengths[uuid]!!
            val value = it.second
            value.multiply(len * scale / value.length())
        }
    }


    override fun rotateParticlesToPoint(to: RelativeLocation) {
        if (!displayed) {
            return
        }
        Math3DUtil.rotatePointsToPoint(
            sequencedParticles.map { it.second }.toList(), to, axis
        )
        // 把粒子丢到对应的位置
        particlesLocations.forEach { (t, u) ->
            t.teleportTo(u.x + origin.x, u.y + origin.y, u.z + origin.z)
        }

        axis = to.normalize()
    }

    override fun rotateParticlesAsAxis(angle: Double) {
        if (!displayed) {
            return
        }
        Math3DUtil.rotateAsAxis(
            sequencedParticles.map { it.second }.toList(), axis, angle
        )
        // 把粒子丢到对应的位置
        particlesLocations.forEach { (t, u) ->
            t.teleportTo(u.x + origin.x, u.y + origin.y, u.z + origin.z)
        }
    }

    override fun rotateToWithAngle(to: RelativeLocation, angle: Double) {
        if (!displayed) {
            return
        }
        Math3DUtil.rotatePointsToPoint(
            sequencedParticles.map { it.second }.toList(), to, axis
        )
        Math3DUtil.rotateAsAxis(
            sequencedParticles.map { it.second }.toList(), to.normalize(), angle
        )
        particlesLocations.forEach { (t, u) ->
            t.teleportTo(u.x + origin.x, u.y + origin.y, u.z + origin.z)
        }
        axis = to.normalize()
    }

    private fun createWithIndex(index: Int): Boolean {
        if (index >= sequencedParticles.size || sequencedParticles.isEmpty()) {
            return false
        }
        val pair = sequencedParticles[index]
        val data = pair.first
        val rl = pair.second
        val uuid = data.uuid
        val particleDisplayer = data.effect(uuid)
        if (particleDisplayer is ParticleDisplayer.SingleParticleDisplayer) {
            val controler = ControlParticleManager.createControl(uuid)
            controler.initInvoker = data.invoker
        }
        val pos = origin
        val toPos = Vec3d(pos.x + rl.x, pos.y + rl.y, pos.z + rl.z)
        val controler = particleDisplayer.display(toPos, world!!) ?: return true
        if (controler is ParticleControler) {
            data.controlerAction(controler)
        }
        particles[uuid] = controler
        particlesLocations[controler] = rl

        return true
    }

    private fun toggleScale(locations: SortedMap<SequencedParticleRelativeData, RelativeLocation>) {
        if (particlesDefaultScaleLengths.isEmpty()) {
            locations.forEach {
                val uuid = it.key.uuid
                particlesDefaultScaleLengths[uuid] = it.value.length()
            }
        }
        if (scale == 1.0) {
            return
        }

        locations.forEach {
            val uuid = it.key.uuid
            val len = particlesDefaultScaleLengths[uuid]!!
            val value = it.value
            value.multiply(len * scale / value.length())
        }
    }

    private fun toggleFromStatus(index: Int, status: Boolean) {
        if (index >= sequencedParticles.size) return
        if (status) {
            createWithIndex(index)
        } else {
            val uuid = sequencedParticles[index].first.uuid
            val particle = particles[uuid] ?: return
            particle.remove()
            particles.remove(uuid)
            particlesLocations.remove(particle)
        }
        val page = MathDataUtil.getStoragePageLong(index)
        val container = displayedStatus[page]
        val bit = MathDataUtil.getStorageWithBitLong(index)
        MathDataUtil.setStatusLong(container, bit, status)
    }

}