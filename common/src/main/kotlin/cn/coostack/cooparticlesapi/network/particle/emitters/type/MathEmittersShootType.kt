package cn.coostack.cooparticlesapi.network.particle.emitters.type

import com.ezylang.evalex.Expression
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/**
 * 基于EvalEx 表达式的运算
 * 支持的变量
 * 对于粒子生成位置
 * t -> tick
 * c -> count
 * i -> 当前索引值
 * 对于粒子初始速度
 * t -> tick
 * x -> posX
 * y -> posY
 * z -> posZ
 * ox -> origin.x
 * oy -> origin.y
 * oz -> origin.z
 */
class MathEmittersShootType : EmittersShootType {
    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, EmittersShootType>(
            { buf, type ->
                type as MathEmittersShootType
                buf.writeUtf(type.x)
                buf.writeUtf(type.y)
                buf.writeUtf(type.z)
                buf.writeUtf(type.dx)
                buf.writeUtf(type.dy)
                buf.writeUtf(type.dz)
            }, {
                MathEmittersShootType().apply {
                    x = it.readUtf()
                    y = it.readUtf()
                    z = it.readUtf()
                    dx = it.readUtf()
                    dy = it.readUtf()
                    dz = it.readUtf()
                    setup()
                }
            }
        )
        const val ID = "math"
    }

    /**
     * 位置运算表达式
     */
    var x = "0"
    var y = "0"
    var z = "0"

    private var xe = Expression(x)
        .with("i", 0)
        .and("c", 0)
        .and("t", 0)
    private var ye = Expression(y)
        .with("i", 0)
        .and("c", 0)
        .and("t", 0)
    private var ze = Expression(z)
        .with("i", 0)
        .and("c", 0)
        .and("t", 0)

    /**
     * 初始方向表达式
     */
    var dx = "0"
    var dy = "0"
    var dz = "0"

    private var dxe = Expression(dx)
        .with("t", 0)

    private var dye = Expression(dy)
        .with("t", 0)

    private var dze = Expression(dz)
        .with("t", 0)

    fun setup() {
        xe = Expression(x)
            .with("i", 0)
            .and("c", 0)
            .and("t", 0)
        ye = Expression(y)
            .with("i", 0)
            .and("c", 0)
            .and("t", 0)
        ze = Expression(z)
            .with("i", 0)
            .and("c", 0)
            .and("t", 0)
        dxe = Expression(dx)
            .with("t", 0)

        dye = Expression(dy)
            .with("t", 0)

        dze = Expression(dz)
            .with("t", 0)
    }

    override fun getID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, EmittersShootType> {
        return CODEC
    }

    override fun getPositions(
        origin: Vec3,
        tick: Int,
        count: Int
    ): List<Vec3> {

        return List(count) {
            val taskX = CompletableFuture.supplyAsync {
                xe.with("t", tick)
                    .with("c", count)
                    .with("i", it)
                    .evaluate().numberValue.toDouble()
            }

            val taskY = CompletableFuture.supplyAsync {
                ye.with("t", tick)
                    .with("c", count)
                    .with("i", it)
                    .evaluate().numberValue.toDouble()
            }

            val taskZ = CompletableFuture.supplyAsync {
                ze.with("t", tick)
                    .and("c", count)
                    .and("i", it)
                    .evaluate().numberValue.toDouble()
            }
            CompletableFuture.allOf(taskX, taskY, taskZ).join()
            origin.add(Vec3(taskX.get(), taskY.get(), taskZ.get()))
        }
    }

    override fun getDefaultDirection(
        enter: Vec3,
        tick: Int,
        pos: Vec3,
        origin: Vec3
    ): Vec3 {
        val x = CompletableFuture.supplyAsync {
            dxe.with("t", tick)
                .and("x", pos.x)
                .and("y", pos.y)
                .and("z", pos.z)
                .and("ox", origin.x)
                .and("oy", origin.y)
                .and("oz", origin.z)
                .evaluate().numberValue.toDouble()
        }
        val y = CompletableFuture.supplyAsync {
            dye.with("t", tick)
                .and("x", pos.x)
                .and("y", pos.y)
                .and("z", pos.z)
                .and("ox", origin.x)
                .and("oy", origin.y)
                .and("oz", origin.z)
                .evaluate().numberValue.toDouble()
        }
        val z = CompletableFuture.supplyAsync {
            dze.with("t", tick)
                .and("x", pos.x)
                .and("y", pos.y)
                .and("z", pos.z)
                .and("ox", origin.x)
                .and("oy", origin.y)
                .and("oz", origin.z)
                .evaluate().numberValue.toDouble()
        }
        CompletableFuture.allOf(x, y, z).join()
        return enter.add(
            x.get(), y.get(), z.get()
        )
    }
}