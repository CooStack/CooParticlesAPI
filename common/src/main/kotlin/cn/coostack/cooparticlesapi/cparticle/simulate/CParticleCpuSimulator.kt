package cn.coostack.cooparticlesapi.cparticle.simulate

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleSelector
import cn.coostack.cooparticlesapi.cparticle.CParticleInstanceFlags
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGrid
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleVoxelCollision
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleMetadataStore
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResourceTable
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_AGE
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_MAX_AGE
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_PREV
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_VEL
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.STRIDE
import org.joml.Matrix4fc
import org.joml.Vector3f
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 显式 CPU 模拟器，仅在调用方设置 CPU 路由时使用。
 *
 * 与 GPU kernel (`cparticle_sim.comp`) 执行**完全相同的打包力场数据与数学**,
 * 并行分块跑在 ForkJoinPool.commonPool 上; 10 万粒子单 tick 约 1~3ms.
 * 结果直接写入 [CParticleStore.data] (交错布局), 之后整段 glBufferSubData 上传.
 */
object CParticleCpuSimulator {

    private const val PARALLEL_THRESHOLD = 8192
    private const val CHUNK = 16384

    /**
     * 推进一个 tick.
     * @param packed 力场打包数据 ([CParticleForce.STRIDE] * count)
     * @param originX/Y/Z 系统原点世界坐标 (噪声/流场需要绝对坐标输入)
     * @param time 系统 tick 计数 (流场时间轴; 原实现使用粒子 age, 此处等价采用粒子 age)
     */
    fun simulate(
        store: CParticleStore,
        packed: FloatArray,
        forceCount: Int,
        originX: Double, originY: Double, originZ: Double,
        speedLimit: Float,
    ) {
        simulate(
            store,
            legacyPackedToCommands(packed, forceCount),
            forceCount,
            CParticleForceResourceTable(),
            originX,
            originY,
            originZ,
            speedLimit,
            null,
        )
    }

    /**
     * 推进一个可选读取共享方块占用网格的 tick。
     *
     * 示例：SIMULATED system 只在存在碰撞实例时传入网格。
     * 禁止：外部 API 调用方不应持有内部网格类型，应使用无网格公开重载。
     *
     * @param store 粒子槽位存储
     * @param packed 力场打包数据
     * @param forceCount 有效力场数量
     * @param originX system 原点世界 X
     * @param originY system 原点世界 Y
     * @param originZ system 原点世界 Z
     * @param speedLimit system 默认速度上限
     * @param collisionGrid 可选共享方块占用网格
     * @param simulationTransform 可选的局部到世界相对坐标矩阵
     * @param inverseSimulationTransform 与 [simulationTransform] 配对的逆矩阵
     */
    internal fun simulate(
        store: CParticleStore,
        packed: FloatArray,
        forceCount: Int,
        originX: Double, originY: Double, originZ: Double,
        speedLimit: Float,
        collisionGrid: CParticleBlockCollisionGrid?,
        simulationTransform: Matrix4fc? = null,
        inverseSimulationTransform: Matrix4fc? = null,
    ) {
        simulateInternal(
            store,
            legacyPackedToCommands(packed, forceCount),
            forceCount,
            CParticleForceResourceTable(),
            originX, originY, originZ, speedLimit,
            collisionGrid, simulationTransform, inverseSimulationTransform,
        )
    }

    /** 使用新 Force Command 与 metadata 的固定 tick 模拟入口。 */
    internal fun simulate(
        store: CParticleStore,
        packed: FloatArray,
        forceCount: Int,
        commandPacked: FloatArray,
        commandCount: Int,
        resourceTable: CParticleForceResourceTable,
        originX: Double, originY: Double, originZ: Double,
        speedLimit: Float,
        collisionGrid: CParticleBlockCollisionGrid?,
        simulationTransform: Matrix4fc? = null,
        inverseSimulationTransform: Matrix4fc? = null,
    ) {
        simulate(
            store,
            commandPacked,
            commandCount,
            resourceTable,
            originX, originY, originZ, speedLimit,
            collisionGrid, simulationTransform, inverseSimulationTransform,
        )
    }

    /** 使用已经统一编码的 Force Command 推进一个 tick。 */
    internal fun simulate(
        store: CParticleStore,
        commandPacked: FloatArray,
        commandCount: Int,
        resourceTable: CParticleForceResourceTable,
        originX: Double,
        originY: Double,
        originZ: Double,
        speedLimit: Float,
        collisionGrid: CParticleBlockCollisionGrid?,
        simulationTransform: Matrix4fc? = null,
        inverseSimulationTransform: Matrix4fc? = null,
    ) {
        simulateInternal(
            store,
            commandPacked,
            commandCount,
            resourceTable,
            originX, originY, originZ, speedLimit,
            collisionGrid, simulationTransform, inverseSimulationTransform,
        )
    }

    private fun simulateInternal(
        store: CParticleStore,
        commandPacked: FloatArray,
        commandCount: Int,
        resourceTable: CParticleForceResourceTable,
        originX: Double, originY: Double, originZ: Double,
        speedLimit: Float,
        collisionGrid: CParticleBlockCollisionGrid?,
        simulationTransform: Matrix4fc?,
        inverseSimulationTransform: Matrix4fc?,
    ) {
        require((simulationTransform == null) == (inverseSimulationTransform == null)) {
            "simulation transform and inverse must be supplied together"
        }
        val first = store.firstAliveSlot
        val high = store.highWater
        if (store.aliveCount <= 0) return
        if (store.aliveCount < PARALLEL_THRESHOLD) {
            simulateRange(
                store, commandPacked, commandCount, resourceTable,
                originX, originY, originZ, speedLimit,
                collisionGrid, simulationTransform, inverseSimulationTransform, first, high,
            )
        } else {
            val tasks = ArrayList<ForkJoinTask<*>>()
            var start = first
            while (start < high) {
                val s = start
                val e = minOf(start + CHUNK, high)
                tasks.add(ForkJoinPool.commonPool().submit {
                    simulateRange(
                        store, commandPacked, commandCount, resourceTable,
                        originX, originY, originZ, speedLimit,
                        collisionGrid, simulationTransform, inverseSimulationTransform, s, e,
                    )
                })
                start = e
            }
            tasks.forEach { it.join() }
        }
        store.markAllAliveDirty()
    }

    private fun simulateRange(
        store: CParticleStore,
        commandPacked: FloatArray,
        commandCount: Int,
        resourceTable: CParticleForceResourceTable,
        originX: Double, originY: Double, originZ: Double,
        speedLimit: Float,
        collisionGrid: CParticleBlockCollisionGrid?,
        simulationTransform: Matrix4fc?,
        inverseSimulationTransform: Matrix4fc?,
        from: Int, to: Int,
    ) {
        val data = store.data
        val bits = store.aliveBits
        val ox = originX.toFloat()
        val oy = originY.toFloat()
        val oz = originZ.toFloat()
        val collisionOffsetX = collisionGrid?.let { (originX - it.minX).toFloat() } ?: 0F
        val collisionOffsetY = collisionGrid?.let { (originY - it.minY).toFloat() } ?: 0F
        val collisionOffsetZ = collisionGrid?.let { (originZ - it.minZ).toFloat() } ?: 0F
        val collisionResult = collisionGrid?.let { FloatArray(CParticleVoxelCollision.RESULT_SIZE) }
        val commandVelocity = FloatArray(3)
        val resourceSample = FloatArray(4)
        val transformedPosition = Vector3f()
        val transformedVelocity = Vector3f()
        for (slot in from until to) {
            if ((bits[slot ushr 6] and (1L shl (slot and 63))) == 0L) continue
            val base = slot * STRIDE
            val flags = data[base + CParticleStore.OFF_FLAGS].toInt()
            if (flags and CParticleStore.FLAG_NEWBORN != 0) {
                data[base + CParticleStore.OFF_FLAGS] =
                    (flags and CParticleStore.FLAG_NEWBORN.inv()).toFloat()
                continue
            }
            var px = data[base]
            var py = data[base + 1]
            var pz = data[base + 2]
            var vx = data[base + OFF_VEL]
            var vy = data[base + OFF_VEL + 1]
            var vz = data[base + OFF_VEL + 2]
            val previousStorageX = px
            val previousStorageY = py
            val previousStorageZ = pz
            if (simulationTransform != null) {
                simulationTransform.transformPosition(px, py, pz, transformedPosition)
                px = transformedPosition.x
                py = transformedPosition.y
                pz = transformedPosition.z
                simulationTransform.transformDirection(vx, vy, vz, transformedVelocity)
                vx = transformedVelocity.x
                vy = transformedVelocity.y
                vz = transformedVelocity.z
            }
            val age = data[base + OFF_AGE]
            val maxAge = data[base + OFF_MAX_AGE]
            val instanceSpeedLimit = data[base + CParticleStore.OFF_SPEED_LIMIT]
            val effectiveSpeedLimit = if (instanceSpeedLimit >= 0F) instanceSpeedLimit else speedLimit

            // prev = cur
            data[base + OFF_PREV] = previousStorageX
            data[base + OFF_PREV + 1] = previousStorageY
            data[base + OFF_PREV + 2] = previousStorageZ

            if (commandCount > 0) {
                val source = store.metadata.sourceId(slot)
                val sign = store.metadata.sign(slot)
                val commandMask = store.metadata.commandMask(slot)
                for (command in 0 until commandCount) {
                    val cb = command * 20
                    val selectorMode = commandPacked[cb + 1].toRawBits()
                    val selectorValue = commandPacked[cb + 2].toRawBits()
                    val selectorMask = commandPacked[cb + 3].toRawBits()
                    val selected = when (selectorMode) {
                        0 -> true
                        1 -> source == selectorValue
                        2 -> source and selectorMask == selectorValue and selectorMask
                        3 -> sign == selectorValue
                        4 -> sign and selectorMask == selectorValue and selectorMask
                        5 -> commandMask and selectorMask != 0
                        else -> false
                    }
                    if (!selected) continue
                    applyCommand(
                        commandPacked,
                        cb,
                        store.metadata,
                        px,
                        py,
                        pz,
                        vx,
                        vy,
                        vz,
                        age,
                        maxAge,
                        originX,
                        originY,
                        originZ,
                        slot,
                        commandVelocity,
                        resourceTable,
                        resourceSample,
                    )
                    vx = commandVelocity[0]
                    vy = commandVelocity[1]
                    vz = commandVelocity[2]
                }
            }

            // 每粒子限速优先；负数哨兵沿用 system 限速
            val sp2 = vx * vx + vy * vy + vz * vz
            if (sp2 > effectiveSpeedLimit * effectiveSpeedLimit && sp2 > 1e-12F) {
                val m = effectiveSpeedLimit / sqrt(sp2)
                vx *= m; vy *= m; vz *= m
            }

            // 积分；碰撞只为当前 worker 分配一个复用结果数组，不产生逐粒子对象。
            val collided = collisionGrid != null && collisionResult != null &&
                    data[base + CParticleStore.OFF_FLAGS].toInt() and
                    CParticleInstanceFlags.BLOCK_COLLISION != 0 &&
                    CParticleVoxelCollision.trace(
                        collisionGrid,
                        px + collisionOffsetX,
                        py + collisionOffsetY,
                        pz + collisionOffsetZ,
                        vx,
                        vy,
                        vz,
                        collisionResult,
                    )
            if (collided) {
                val hitTime = collisionResult[CParticleVoxelCollision.RESULT_TIME]
                val normal = collisionResult[CParticleVoxelCollision.RESULT_NORMAL].toInt()
                px += vx * hitTime
                py += vy * hitTime
                pz += vz * hitTime
                when (normal) {
                    -1 -> { px -= CParticleVoxelCollision.SURFACE_OFFSET; vx = 0F }
                    1 -> { px += CParticleVoxelCollision.SURFACE_OFFSET; vx = 0F }
                    -2 -> { py -= CParticleVoxelCollision.SURFACE_OFFSET; vy = 0F }
                    2 -> { py += CParticleVoxelCollision.SURFACE_OFFSET; vy = 0F }
                    -3 -> { pz -= CParticleVoxelCollision.SURFACE_OFFSET; vz = 0F }
                    3 -> { pz += CParticleVoxelCollision.SURFACE_OFFSET; vz = 0F }
                }
            } else {
                px += vx
                py += vy
                pz += vz
            }
            if (inverseSimulationTransform != null) {
                inverseSimulationTransform.transformPosition(px, py, pz, transformedPosition)
                data[base] = transformedPosition.x
                data[base + 1] = transformedPosition.y
                data[base + 2] = transformedPosition.z
                inverseSimulationTransform.transformDirection(vx, vy, vz, transformedVelocity)
                data[base + OFF_VEL] = transformedVelocity.x
                data[base + OFF_VEL + 1] = transformedVelocity.y
                data[base + OFF_VEL + 2] = transformedVelocity.z
            } else {
                data[base] = px
                data[base + 1] = py
                data[base + 2] = pz
                data[base + OFF_VEL] = vx
                data[base + OFF_VEL + 1] = vy
                data[base + OFF_VEL + 2] = vz
            }
        }
    }

    private fun applyCommand(
        commands: FloatArray,
        base: Int,
        metadata: CParticleMetadataStore,
        px: Float,
        py: Float,
        pz: Float,
        vx0: Float,
        vy0: Float,
        vz0: Float,
        age: Float,
        maxAge: Float,
        originX: Double,
        originY: Double,
        originZ: Double,
        slot: Int,
        outputVelocity: FloatArray,
        resourceTable: CParticleForceResourceTable,
        resourceSample: FloatArray,
    ) {
        var vx = vx0; var vy = vy0; var vz = vz0
        val b = base + 4
        val type = commands[base].toRawBits()
        val dx = px - commands[b + 4]
        val dy = py - commands[b + 5]
        val dz = pz - commands[b + 6]
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val falloff = if (type >= CParticleForce.TYPE_RADIAL) {
            blenderFalloff(commands, b + 11, dx, dy, dz, dist)
        } else {
            1F
        }
        when (type) {
            CParticleForce.TYPE_GRAVITY -> { vx += commands[b + 4]; vy += commands[b + 5]; vz += commands[b + 6] }
            CParticleForce.TYPE_ENV_DRAG -> {
                val speed = sqrt(vx * vx + vy * vy + vz * vz)
                if (speed > 0.01F) { val m = commands[b + 1] * speed; vx -= m * vx; vy -= m * vy; vz -= m * vz }
            }
            CParticleForce.TYPE_EXP_DRAG -> {
                val speed = sqrt(vx * vx + vy * vy + vz * vz)
                if (commands[b + 3] > 0F && speed <= commands[b + 3]) { vx = 0F; vy = 0F; vz = 0F }
                else { val m = commands[b + 1] * commands[b + 2]; vx *= m; vy *= m; vz *= m }
            }
            CParticleForce.TYPE_WIND -> {
                val mode = commands[b + 2].toInt()
                val inRange = when (mode) {
                    1 -> dx * dx + dy * dy + dz * dz <= commands[b + 12] * commands[b + 12]
                    2 -> abs(dx) <= commands[b + 12] && abs(dy) <= commands[b + 13] && abs(dz) <= commands[b + 14]
                    else -> true
                }
                if (inRange) { val rwx = commands[b + 8] - vx; val rwy = commands[b + 9] - vy; val rwz = commands[b + 10] - vz; val len = sqrt(rwx*rwx+rwy*rwy+rwz*rwz); if (len > 1e-6F) { val m=commands[b+1]*len; vx += m*rwx; vy += m*rwy; vz += m*rwz } }
            }
            CParticleForce.TYPE_VORTEX -> {
                val ax = commands[b + 8]
                val ay = commands[b + 9]
                val az = commands[b + 10]
                val dot = dx * ax + dy * ay + dz * az
                val rx = dx - ax * dot
                val ry = dy - ay * dot
                val rz = dz - az * dot
                val radialLength = sqrt(rx * rx + ry * ry + rz * rz)
                val distance = max(radialLength, commands[b + 12])
                val forceFalloff = inversePowerFalloff(distance, commands[b + 7], commands[b + 11])
                val tx = ay * rz - az * ry
                val ty = az * rx - ax * rz
                val tz = ax * ry - ay * rx
                val tangentLength = sqrt(tx * tx + ty * ty + tz * tz)
                val tangentScale = if (tangentLength > 1e-9F) commands[b + 1] * forceFalloff / tangentLength else 0F
                val radialScale = if (radialLength > 1e-9F) -commands[b + 2] * forceFalloff / radialLength else 0F
                val axialScale = commands[b + 3] * forceFalloff
                vx += tx * tangentScale + rx * radialScale + ax * axialScale
                vy += ty * tangentScale + ry * radialScale + ay * axialScale
                vz += tz * tangentScale + rz * radialScale + az * axialScale
            }
            CParticleForce.TYPE_ATTRACT -> {
                val adx = -dx; val ady = -dy; val adz = -dz; val d0=sqrt(adx*adx+ady*ady+adz*adz); if(d0>1e-9F){val f=inversePowerFalloff(max(d0,commands[b+7]),commands[b+2],commands[b+3]); val m=commands[b+1]*f/d0; vx+=adx*m; vy+=ady*m; vz+=adz*m}
            }
            CParticleForce.TYPE_ROTATION -> {
                val ax=commands[b+8]; val ay=commands[b+9]; val az=commands[b+10]; val tx=ay*dz-az*dy; val ty=az*dx-ax*dz; val tz=ax*dy-ay*dx; val tl=sqrt(tx*tx+ty*ty+tz*tz); if(tl>1e-9F){val f=inversePowerFalloff(dist,commands[b+2],commands[b+3]); val m=commands[b+1]*f; vx+=tx/tl*m; vy+=ty/tl*m; vz+=tz/tl*m}
            }
            CParticleForce.TYPE_NOISE -> {
                val time = age * commands[b + 3]
                val seed = slot * 668265263 + commands[b + 13].toInt()
                val wx = (px + originX.toFloat()) * commands[b + 2] + time
                val wy = (py + originY.toFloat()) * commands[b + 2] + time * 0.7F
                val wz = (pz + originZ.toFloat()) * commands[b + 2] + time * 1.3F
                var amplitude = commands[b + 1]
                if (commands[b + 12] > 0.5F) {
                    amplitude *= 1F - (if (maxAge > 0F) age / maxAge else 0F).coerceIn(0F, 1F)
                }
                var nx = valueNoise3(wx, wy, wz, seed + 11) * 2F - 1F
                var ny = valueNoise3(wx, wy, wz, seed + 23) * 2F - 1F
                var nz = valueNoise3(wx, wy, wz, seed + 37) * 2F - 1F
                val noiseLength = sqrt(nx * nx + ny * ny + nz * nz)
                if (noiseLength > 1e-4F) {
                    nx /= noiseLength
                    ny /= noiseLength
                    nz /= noiseLength
                    vx += nx * amplitude
                    vy += ny * commands[b + 11] * amplitude
                    vz += nz * amplitude
                    val clampSpeed = commands[b + 7]
                    val speedSquared = vx * vx + vy * vy + vz * vz
                    if (speedSquared > clampSpeed * clampSpeed && speedSquared > 1e-12F) {
                        val clampScale = clampSpeed / sqrt(speedSquared)
                        vx *= clampScale
                        vy *= clampScale
                        vz *= clampScale
                    }
                }
            }
            CParticleForce.TYPE_FLOW_FIELD -> {
                val wx=px+originX.toFloat()+commands[b+4]; val wy=py+originY.toFloat()+commands[b+5]; val wz=pz+originZ.toFloat()+commands[b+6]; val t=age*commands[b+3]+commands[b+7]; val q=commands[b+2]; val fx=sin((wy+t)*q)+cos((wz-t)*q); val fy=sin((wz+t)*q)+cos((wx+t)*q); val fz=sin((wx-t)*q)+cos((wy-t)*q); val m=0.5F*commands[b+1]; vx+=fx*m;vy+=fy*m;vz+=fz*m
            }
            CParticleForce.TYPE_RADIAL -> {
                if (dist > 1e-6F && falloff > 0F) {
                    var magnitude = commands[b + 1] * falloff
                    if (commands[b + 2] > 0.5F) magnitude /= (dist * dist).coerceAtLeast(1e-6F)
                    vx += dx / dist * magnitude; vy += dy / dist * magnitude; vz += dz / dist * magnitude
                }
            }
            CParticleForce.TYPE_DIRECTIONAL_WIND -> {
                vx += commands[b + 8] * commands[b + 1] * falloff
                vy += commands[b + 9] * commands[b + 1] * falloff
                vz += commands[b + 10] * commands[b + 1] * falloff
            }
            CParticleForce.TYPE_BLENDER_VORTEX -> {
                val ax = commands[b + 8]; val ay = commands[b + 9]; val az = commands[b + 10]
                val dot = dx * ax + dy * ay + dz * az
                val rx = dx - ax * dot; val ry = dy - ay * dot; val rz = dz - az * dot
                val rLen = sqrt(rx * rx + ry * ry + rz * rz)
                if (rLen > 1e-6F) {
                    val tx = ay * rz - az * ry; val ty = az * rx - ax * rz; val tz = ax * ry - ay * rx
                    val tLen = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-6F)
                    val s = falloff
                    vx += tx / tLen * commands[b + 1] * s - rx / rLen * commands[b + 2] * s - vx * commands[b + 3] * s
                    vy += ty / tLen * commands[b + 1] * s - ry / rLen * commands[b + 2] * s - vy * commands[b + 3] * s
                    vz += tz / tLen * commands[b + 1] * s - rz / rLen * commands[b + 2] * s - vz * commands[b + 3] * s
                }
            }
            CParticleForce.TYPE_MAGNETIC -> {
                val fieldMode = commands[b + 2].toInt()
                var fx: Float
                var fy: Float
                var fz: Float
                when (fieldMode) {
                    1 -> {
                        fx = commands[b + 8]
                        fy = commands[b + 9]
                        fz = commands[b + 10]
                    }
                    2 -> {
                        fx = dx
                        fy = dy
                        fz = dz
                    }
                    else -> {
                        fx = commands[b + 9] * dz - commands[b + 10] * dy
                        fy = commands[b + 10] * dx - commands[b + 8] * dz
                        fz = commands[b + 8] * dy - commands[b + 9] * dx
                    }
                }
                val fLen = sqrt(fx * fx + fy * fy + fz * fz)
                if (fLen > 1e-6F) {
                    fx = fx / fLen * commands[b + 1] * falloff; fy = fy / fLen * commands[b + 1] * falloff; fz = fz / fLen * commands[b + 1] * falloff
                    val oldVx = vx
                    val oldVy = vy
                    val oldVz = vz
                    vx += oldVy * fz - oldVz * fy
                    vy += oldVz * fx - oldVx * fz
                    vz += oldVx * fy - oldVy * fx
                }
            }
            CParticleForce.TYPE_HARMONIC -> {
                if (dist > 1e-6F && falloff > 0F) {
                    val displacement = dist - commands[b + 3]
                    val s = -commands[b + 1] * falloff * displacement / dist
                    val damping = commands[b + 2]
                    val oldVx = vx
                    val oldVy = vy
                    val oldVz = vz
                    vx += dx * s - oldVx * damping
                    vy += dy * s - oldVy * damping
                    vz += dz * s - oldVz * damping
                }
            }
            CParticleForce.TYPE_VELOCITY_DRAG -> {
                val speed = sqrt(vx * vx + vy * vy + vz * vz)
                if (speed > 1e-6F) {
                    if (commands[b + 3] > 0.5F) {
                        val factor = (commands[b + 2] + commands[b + 1] * speed) * falloff
                        vx -= vx / speed * speed * factor; vy -= vy / speed * speed * factor; vz -= vz / speed * speed * factor
                    } else {
                        val factor = exp((-commands[b + 2] * falloff).toDouble()).toFloat()
                        vx *= factor; vy *= factor; vz *= factor
                    }
                }
            }
            CParticleForce.TYPE_CHARGE -> {
                if (dist > 1e-6F && falloff > 0F) {
                    val charge = metadata.charge(slot).takeIf { it.isFinite() } ?: commands[b + 2]
                    val s = charge * commands[b + 1] * falloff / dist
                    vx += dx * s; vy += dy * s; vz += dz * s
                }
            }
            CParticleForce.TYPE_LENNARD_JONES -> {
                if (dist > 1e-6F) {
                    val particleRadius = metadata.radius(slot).takeIf(Float::isFinite)?.coerceAtLeast(0F) ?: 0F
                    val ratio = (commands[b + 2] + particleRadius).coerceAtLeast(0F) / dist
                    val sixth = ratio * ratio * ratio * ratio * ratio * ratio
                    val fac = (-sixth * (1F - sixth) / dist).coerceAtMost(2F) * commands[b + 1] * falloff
                    vx += dx / dist * fac; vy += dy / dist * fac; vz += dz / dist * fac
                }
            }
            CParticleForce.TYPE_TURBULENCE -> {
                val time = age * commands[b + 3]
                val seed = commands[b + 7].toInt()
                val scale = commands[b + 2].coerceAtLeast(1e-4F)
                val nx = valueNoise3((px + originX.toFloat()) / scale + time, (py + originY.toFloat()) / scale, (pz + originZ.toFloat()) / scale, seed) * 2F - 1F
                val ny = valueNoise3((py + originY.toFloat()) / scale + time, (pz + originZ.toFloat()) / scale, (px + originX.toFloat()) / scale, seed + 17) * 2F - 1F
                val nz = valueNoise3((pz + originZ.toFloat()) / scale + time, (px + originX.toFloat()) / scale, (py + originY.toFloat()) / scale, seed + 31) * 2F - 1F
                vx += nx * commands[b + 1] * falloff; vy += ny * commands[b + 1] * falloff; vz += nz * commands[b + 1] * falloff
            }
            CParticleForce.TYPE_TEXTURE -> {
                val binding = resourceTable.textureBinding(commands[b + 3].toInt())
                when (commands[b + 2].toInt()) {
                    0 -> {
                        binding.sampleTexture(px.toDouble(), py.toDouble(), pz.toDouble(), resourceSample)
                        val scale = commands[b + 1] * falloff
                        vx += (resourceSample[0] * 2F - 1F) * scale
                        vy += (resourceSample[1] * 2F - 1F) * scale
                        vz += (resourceSample[2] * 2F - 1F) * scale
                    }
                    1 -> {
                        val nabla = commands[b + 7].coerceAtLeast(1e-6F)
                        binding.sampleTexture((px + nabla).toDouble(), py.toDouble(), pz.toDouble(), resourceSample)
                        val positiveX = textureLuminance(resourceSample)
                        binding.sampleTexture((px - nabla).toDouble(), py.toDouble(), pz.toDouble(), resourceSample)
                        val negativeX = textureLuminance(resourceSample)
                        binding.sampleTexture(px.toDouble(), py.toDouble(), (pz + nabla).toDouble(), resourceSample)
                        val positiveZ = textureLuminance(resourceSample)
                        binding.sampleTexture(px.toDouble(), py.toDouble(), (pz - nabla).toDouble(), resourceSample)
                        val negativeZ = textureLuminance(resourceSample)
                        val scale = commands[b + 1] * falloff / (2F * nabla)
                        vx += (positiveX - negativeX) * scale
                        vz += (positiveZ - negativeZ) * scale
                    }
                }
            }
            CParticleForce.TYPE_FLUID_FLOW -> {
                val binding = resourceTable.fluidBinding(commands[b + 2].toInt())
                binding.sampleFluid(px.toDouble(), py.toDouble(), pz.toDouble(), resourceSample)
                val density = if (commands[b + 3] > 0.5F) resourceSample[3] else 1F
                val scale = commands[b + 1] * falloff * density
                val drag = commands[b + 7]
                vx += (resourceSample[0] - vx * drag) * scale
                vy += (resourceSample[1] - vy * drag) * scale
                vz += (resourceSample[2] - vz * drag) * scale
            }
        }
        outputVelocity[0] = vx
        outputVelocity[1] = vy
        outputVelocity[2] = vz
    }

    private fun textureLuminance(sample: FloatArray): Float {
        return (sample[0] + sample[1] + sample[2]) / 3F
    }

    private fun blenderFalloff(
        commands: FloatArray,
        base: Int,
        dx: Float,
        dy: Float,
        dz: Float,
        distance: Float,
    ): Float {
        val min = commands[base]
        val max = commands[base + 1]
        val power = commands[base + 2].coerceAtLeast(0F)
        val shape = commands[base + 3].toInt()
        val zDirection = commands[base + 4].toInt()
        if (!distance.isFinite()) return 0F
        val axisX = commands[base - 3]
        val axisY = commands[base - 2]
        val axisZ = commands[base - 1]
        val axisLength = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        if (axisLength <= 1e-9F && (shape != 0 || zDirection != 0)) return 0F
        val axial = if (axisLength > 1e-9F) {
            (dx * axisX + dy * axisY + dz * axisZ) / axisLength
        } else {
            0F
        }
        if ((zDirection == 1 && axial < 0F) || (zDirection == 2 && axial > 0F)) return 0F
        val factor = when (shape) {
            1 -> sqrt((distance * distance - axial * axial).coerceAtLeast(0F))
            2 -> acos((axial / distance.coerceAtLeast(1e-6F)).coerceIn(-1F, 1F)) * (180F / PI.toFloat())
            else -> distance
        }
        if (max >= 0F && factor > max) return 0F
        if (factor < min) return 1F
        return (1F + factor - min).coerceAtLeast(1e-6F).pow(-power).coerceIn(0F, 1F)
    }

    // ---- 与 ParticleNoiseCommand / GLSL 相同的噪声原语 ----

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6F - 15F) + 10F)

    private fun hash3(ix: Int, iy: Int, iz: Int, seed: Int): Float {
        var n = ix * 374761393 + iy * 668265263 + iz * 2147483647.toInt() + seed * 374761
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff).toFloat() / 2147483647F
    }

    private fun valueNoise3(x: Float, y: Float, z: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val z0 = floor(z).toInt()
        val fx = x - x0
        val fy = y - y0
        val fz = z - z0
        val u = fade(fx)
        val v = fade(fy)
        val w = fade(fz)
        val n000 = hash3(x0, y0, z0, seed)
        val n100 = hash3(x0 + 1, y0, z0, seed)
        val n010 = hash3(x0, y0 + 1, z0, seed)
        val n110 = hash3(x0 + 1, y0 + 1, z0, seed)
        val n001 = hash3(x0, y0, z0 + 1, seed)
        val n101 = hash3(x0 + 1, y0, z0 + 1, seed)
        val n011 = hash3(x0, y0 + 1, z0 + 1, seed)
        val n111 = hash3(x0 + 1, y0 + 1, z0 + 1, seed)
        val nx00 = n000 + (n100 - n000) * u
        val nx10 = n010 + (n110 - n010) * u
        val nx01 = n001 + (n101 - n001) * u
        val nx11 = n011 + (n111 - n011) * u
        val nxy0 = nx00 + (nx10 - nx00) * v
        val nxy1 = nx01 + (nx11 - nx01) * v
        return nxy0 + (nxy1 - nxy0) * w
    }

    /** 1 / (1 + (d/scale)^power) — GraphMathHelper.inversePowerFalloff 的 float 版 (power=2 走快速路径) */
    private fun inversePowerFalloff(distance: Float, scale: Float, power: Float): Float {
        val s = max(scale, 1e-9F)
        val d = max(distance, 0F) / s
        val p = max(power, 1F)
        val powered = if (p == 2F) d * d else d.pow(p)
        return 1F / (1F + powered)
    }

    /** 把旧的 16-float Force ABI 临时包装成统一的 All Command，供兼容重载使用。 */
    private fun legacyPackedToCommands(packed: FloatArray, forceCount: Int): FloatArray {
        require(forceCount >= 0) { "forceCount must be non-negative: $forceCount" }
        require(packed.size >= forceCount * CParticleForce.STRIDE) {
            "legacy Force payload is shorter than forceCount: floats=${packed.size} count=$forceCount"
        }
        val commands = FloatArray(forceCount * 20)
        for (index in 0 until forceCount) {
            val oldBase = index * CParticleForce.STRIDE
            val commandBase = index * 20
            // 旧 ABI 使用可读的数值类型，新 header 使用 int 的原始 bit pattern。
            commands[commandBase] = Float.fromBits(packed[oldBase].toInt())
            commands[commandBase + 1] = Float.fromBits(CParticleSelector.All.mode)
            commands[commandBase + 2] = Float.fromBits(0)
            commands[commandBase + 3] = Float.fromBits(0)
            packed.copyInto(commands, commandBase + 4, oldBase, oldBase + CParticleForce.STRIDE)
        }
        return commands
    }
}
