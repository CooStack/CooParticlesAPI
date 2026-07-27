package cn.coostack.cooparticlesapi.cparticle.simulate

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_AGE
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_MAX_AGE
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_PREV
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.OFF_VEL
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore.Companion.STRIDE
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU 回退模拟器 (无 GL43 compute 能力时使用).
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
        val high = store.highWater
        if (high <= 0) return
        if (store.aliveCount < PARALLEL_THRESHOLD) {
            simulateRange(store, packed, forceCount, originX, originY, originZ, speedLimit, 0, high)
        } else {
            val tasks = ArrayList<ForkJoinTask<*>>()
            var start = 0
            while (start < high) {
                val s = start
                val e = minOf(start + CHUNK, high)
                tasks.add(ForkJoinPool.commonPool().submit {
                    simulateRange(store, packed, forceCount, originX, originY, originZ, speedLimit, s, e)
                })
                start = e
            }
            tasks.forEach { it.join() }
        }
        store.markAllAliveDirty()
    }

    private fun simulateRange(
        store: CParticleStore,
        packed: FloatArray,
        forceCount: Int,
        originX: Double, originY: Double, originZ: Double,
        speedLimit: Float,
        from: Int, to: Int,
    ) {
        val data = store.data
        val bits = store.aliveBits
        val ox = originX.toFloat()
        val oy = originY.toFloat()
        val oz = originZ.toFloat()
        for (slot in from until to) {
            if ((bits[slot ushr 6] and (1L shl (slot and 63))) == 0L) continue
            val base = slot * STRIDE
            var px = data[base]
            var py = data[base + 1]
            var pz = data[base + 2]
            var vx = data[base + OFF_VEL]
            var vy = data[base + OFF_VEL + 1]
            var vz = data[base + OFF_VEL + 2]
            val age = data[base + OFF_AGE]
            val maxAge = data[base + OFF_MAX_AGE]
            val instanceSpeedLimit = data[base + CParticleStore.OFF_SPEED_LIMIT]
            val effectiveSpeedLimit = if (instanceSpeedLimit >= 0f) instanceSpeedLimit else speedLimit

            // prev = cur
            data[base + OFF_PREV] = px
            data[base + OFF_PREV + 1] = py
            data[base + OFF_PREV + 2] = pz

            // ---- 力场 (与 GLSL 相同的数学) ----
            for (f in 0 until forceCount) {
                val b = f * CParticleForce.STRIDE
                when (packed[b].toInt()) {
                    CParticleForce.TYPE_GRAVITY -> {
                        vx += packed[b + 4]; vy += packed[b + 5]; vz += packed[b + 6]
                    }

                    CParticleForce.TYPE_ENV_DRAG -> {
                        val k = packed[b + 1]
                        val speed = sqrt(vx * vx + vy * vy + vz * vz)
                        if (speed > 0.01f) {
                            val m = k * speed
                            vx -= m * vx; vy -= m * vy; vz -= m * vz
                        }
                    }

                    CParticleForce.TYPE_EXP_DRAG -> {
                        val factor = packed[b + 1]
                        val linearShrink = packed[b + 2]
                        val minSpeed = packed[b + 3]
                        val speed = sqrt(vx * vx + vy * vy + vz * vz)
                        if (minSpeed > 0f && speed <= minSpeed) {
                            vx = 0f; vy = 0f; vz = 0f
                        } else {
                            val m = factor * linearShrink
                            vx *= m; vy *= m; vz *= m
                        }
                    }

                    CParticleForce.TYPE_WIND -> {
                        val mode = packed[b + 2].toInt()
                        var inRange = true
                        if (mode != 0) {
                            val dx = px - packed[b + 4]
                            val dy = py - packed[b + 5]
                            val dz = pz - packed[b + 6]
                            inRange = if (mode == 1) {
                                dx * dx + dy * dy + dz * dz <= packed[b + 12] * packed[b + 12]
                            } else {
                                abs(dx) <= packed[b + 12] && abs(dy) <= packed[b + 13] && abs(dz) <= packed[b + 14]
                            }
                        }
                        if (inRange) {
                            val rwx = packed[b + 8] - vx
                            val rwy = packed[b + 9] - vy
                            val rwz = packed[b + 10] - vz
                            val len = sqrt(rwx * rwx + rwy * rwy + rwz * rwz)
                            if (len > 1e-6f) {
                                val m = packed[b + 1] * len
                                vx += m * rwx; vy += m * rwy; vz += m * rwz
                            }
                        }
                    }

                    CParticleForce.TYPE_VORTEX -> {
                        val rx = px - packed[b + 4]
                        val ry = py - packed[b + 5]
                        val rz = pz - packed[b + 6]
                        val axx = packed[b + 8]; val axy = packed[b + 9]; val axz = packed[b + 10]
                        val dot = rx * axx + ry * axy + rz * axz
                        val radX = rx - axx * dot
                        val radY = ry - axy * dot
                        val radZ = rz - axz * dot
                        val dist = sqrt(radX * radX + radY * radY + radZ * radZ)
                        val d = max(dist, packed[b + 12])
                        val falloff = inversePowerFalloff(d, packed[b + 7], packed[b + 11])
                        // tangential = axis x radial
                        var tx = axy * radZ - axz * radY
                        var ty = axz * radX - axx * radZ
                        var tz = axx * radY - axy * radX
                        val tLen = sqrt(tx * tx + ty * ty + tz * tz)
                        if (tLen > 1e-9f) {
                            tx /= tLen; ty /= tLen; tz /= tLen
                        } else {
                            tx = 0f; ty = 0f; tz = 0f
                        }
                        var inX = 0f; var inY = 0f; var inZ = 0f
                        if (dist > 1e-9f) {
                            inX = -radX / dist; inY = -radY / dist; inZ = -radZ / dist
                        }
                        val swirl = packed[b + 1] * falloff
                        val pull = packed[b + 2] * falloff
                        val lift = packed[b + 3] * falloff
                        vx += tx * swirl + inX * pull + axx * lift
                        vy += ty * swirl + inY * pull + axy * lift
                        vz += tz * swirl + inZ * pull + axz * lift
                    }

                    CParticleForce.TYPE_ATTRACT -> {
                        val dx = packed[b + 4] - px
                        val dy = packed[b + 5] - py
                        val dz = packed[b + 6] - pz
                        val dist = sqrt(dx * dx + dy * dy + dz * dz)
                        if (dist >= 1e-9f) {
                            val d = max(dist, packed[b + 7])
                            val falloff = inversePowerFalloff(d, packed[b + 2], packed[b + 3])
                            val m = packed[b + 1] * falloff / dist
                            vx += dx * m; vy += dy * m; vz += dz * m
                        }
                    }

                    CParticleForce.TYPE_ROTATION -> {
                        val rx = px - packed[b + 4]
                        val ry = py - packed[b + 5]
                        val rz = pz - packed[b + 6]
                        val dist = sqrt(rx * rx + ry * ry + rz * rz)
                        if (dist >= 1e-9f) {
                            val axx = packed[b + 8]; val axy = packed[b + 9]; val axz = packed[b + 10]
                            var tx = axy * rz - axz * ry
                            var ty = axz * rx - axx * rz
                            var tz = axx * ry - axy * rx
                            val tLen = sqrt(tx * tx + ty * ty + tz * tz)
                            if (tLen >= 1e-9f) {
                                tx /= tLen; ty /= tLen; tz /= tLen
                                val falloff = inversePowerFalloff(dist, packed[b + 2], packed[b + 3])
                                val m = packed[b + 1] * falloff
                                vx += tx * m; vy += ty * m; vz += tz * m
                            }
                        }
                    }

                    CParticleForce.TYPE_NOISE -> {
                        val strength = packed[b + 1]
                        val frequency = packed[b + 2]
                        val speed = packed[b + 3]
                        val clampSpeed = packed[b + 7]
                        val affectY = packed[b + 11]
                        val useLife = packed[b + 12] > 0.5f
                        val seed = slot * 668265263 + packed[b + 13].toInt()
                        val t = if (maxAge > 0f) (age / maxAge).coerceIn(0f, 1f) else 0f
                        val time = age * speed
                        val wx = (px + ox) * frequency + time
                        val wy = (py + oy) * frequency + time * 0.7f
                        val wz = (pz + oz) * frequency + time * 1.3f
                        var amp = strength
                        if (useLife) amp *= (1f - t)
                        var nx = valueNoise3(wx, wy, wz, seed + 11) * 2f - 1f
                        var ny = valueNoise3(wx, wy, wz, seed + 23) * 2f - 1f
                        var nz = valueNoise3(wx, wy, wz, seed + 37) * 2f - 1f
                        val nLen = sqrt(nx * nx + ny * ny + nz * nz)
                        if (nLen > 1e-4f) {
                            nx /= nLen; ny /= nLen; nz /= nLen
                            vx += nx * amp
                            vy += ny * affectY * amp
                            vz += nz * amp
                            val sp2 = vx * vx + vy * vy + vz * vz
                            if (sp2 > clampSpeed * clampSpeed && sp2 > 1e-12f) {
                                val m = clampSpeed / sqrt(sp2)
                                vx *= m; vy *= m; vz *= m
                            }
                        }
                    }

                    CParticleForce.TYPE_FLOW_FIELD -> {
                        val amplitude = packed[b + 1]
                        val frequency = packed[b + 2]
                        val timeScale = packed[b + 3]
                        val phase = packed[b + 7]
                        val wx = px + ox + packed[b + 4]
                        val wy = py + oy + packed[b + 5]
                        val wz = pz + oz + packed[b + 6]
                        val t = age * timeScale + phase
                        val fx = sin((wy + t) * frequency) + cos((wz - t) * frequency)
                        val fy = sin((wz + t) * frequency) + cos((wx + t) * frequency)
                        val fz = sin((wx - t) * frequency) + cos((wy - t) * frequency)
                        val m = 0.5f * amplitude
                        vx += fx * m; vy += fy * m; vz += fz * m
                    }
                }
            }

            // 每粒子限速优先；负数哨兵沿用 system 限速
            val sp2 = vx * vx + vy * vy + vz * vz
            if (sp2 > effectiveSpeedLimit * effectiveSpeedLimit && sp2 > 1e-12f) {
                val m = effectiveSpeedLimit / sqrt(sp2)
                vx *= m; vy *= m; vz *= m
            }

            // 积分
            data[base] = px + vx
            data[base + 1] = py + vy
            data[base + 2] = pz + vz
            data[base + OFF_VEL] = vx
            data[base + OFF_VEL + 1] = vy
            data[base + OFF_VEL + 2] = vz
        }
    }

    // ---- 与 ParticleNoiseCommand / GLSL 相同的噪声原语 ----

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun hash3(ix: Int, iy: Int, iz: Int, seed: Int): Float {
        var n = ix * 374761393 + iy * 668265263 + iz * 2147483647.toInt() + seed * 374761
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff).toFloat() / 2147483647f
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
        val s = max(scale, 1e-9f)
        val d = max(distance, 0f) / s
        val p = max(power, 1f)
        val powered = if (p == 2f) d * d else Math.pow(d.toDouble(), p.toDouble()).toFloat()
        return 1f / (1f + powered)
    }
}
