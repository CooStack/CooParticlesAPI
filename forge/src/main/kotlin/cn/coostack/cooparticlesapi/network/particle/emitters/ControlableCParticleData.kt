package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import org.joml.Vector3f

open class ControlableCParticleData : ControlableParticleData() {

    var textureSource: CParticleTextureSource? = null
    var colorCurve: CParticleColorCurve = CParticleColorCurve.linear(Vector3f(1f, 1f, 1f), Vector3f(1f, 1f, 1f))
    var alphaCurve: CParticleCurve = CParticleCurve.constant(1.0)
    var updateMode: CParticleUpdateMode = CParticleUpdateMode.ALWAYS
    var rotation: Vector3f = Vector3f(0f, 0f, 0f)

    companion object {
        val CODEC: ForgeStreamCodec<PacketByteBuf, ControlableCParticleData> = ForgeStreamCodec.of(
            { buf, data ->
                ControlableParticleData.PACKET_CODEC.encode(buf, data)
                buf.writeBoolean(data.textureSource != null)
                if (data.textureSource != null) {
                    CParticleTextureSource.STREAM_CODEC.encode(buf, data.textureSource!!)
                }
                CParticleColorCurve.STREAM_CODEC.encode(buf, data.colorCurve)
                CParticleCurve.STREAM_CODEC.encode(buf, data.alphaCurve)
                buf.writeEnum(data.updateMode)
                buf.writeVector3f(data.rotation)
            },
            { buf ->
                val data = ControlableParticleData.PACKET_CODEC.decode(buf) as ControlableCParticleData
                if (buf.readBoolean()) {
                    data.textureSource = CParticleTextureSource.STREAM_CODEC.decode(buf)
                }
                data.colorCurve = CParticleColorCurve.STREAM_CODEC.decode(buf)
                data.alphaCurve = CParticleCurve.STREAM_CODEC.decode(buf)
                data.updateMode = buf.readEnum()
                data.rotation = buf.readVector3f()
                data
            }
        )
    }

    override fun getCodec(): ForgeStreamCodec<PacketByteBuf, out SerializableData> {
        return CODEC
    }

    override fun clone(): SerializableData {
        return super.clone().also {
            val data = it as ControlableCParticleData
            data.textureSource = textureSource
            data.colorCurve = colorCurve
            data.alphaCurve = alphaCurve
            data.updateMode = updateMode
            data.rotation = Vector3f(rotation)
        }
    }
}
