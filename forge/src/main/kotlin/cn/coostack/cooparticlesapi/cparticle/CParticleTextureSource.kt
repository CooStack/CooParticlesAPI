@file:JvmName("CParticleTextures")

package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block as MinecraftBlock
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f

sealed interface CParticleTextureSource {
    companion object {
        const val MAX_ATLAS_ANIMATION_FRAMES: Int = 4096

        val STREAM_CODEC: ForgeStreamCodec<CParticleTextureSource> = ForgeStreamCodec.of(::encodeSource, ::decodeSource)

        private fun encodeSource(
            buf: PacketByteBuf,
            source: CParticleTextureSource,
        ) {
            when (source) {
                is ParticleEffect -> {
                    buf.writeByte(0)
                    ForgeCodecHelper.particleCodecOf(source.effect).encode(buf, source.effect)
                    buf.writeBoolean(source.animateByAge)
                }
                is AtlasSprite -> {
                    buf.writeByte(1)
                    buf.writeResourceLocation(source.atlasLocation)
                    buf.writeResourceLocation(source.spriteLocation)
                }
                is AtlasAnimation -> {
                    require(source.spriteLocations.size <= MAX_ATLAS_ANIMATION_FRAMES) {
                        "CParticle atlas animation exceeds $MAX_ATLAS_ANIMATION_FRAMES frames"
                    }
                    buf.writeByte(2)
                    buf.writeResourceLocation(source.atlasLocation)
                    buf.writeVarInt(source.spriteLocations.size)
                    source.spriteLocations.forEach(buf::writeResourceLocation)
                }
                is Block -> {
                    buf.writeByte(3)
                    val id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_STATE_REGISTRY.getId(source.state)
                    buf.writeVarInt(id)
                    buf.writeBoolean(source.randomCrop)
                    buf.writeBoolean(source.applyTint)
                    buf.writeBoolean(source.applyBrightness)
                }
                is Item -> {
                    buf.writeByte(4)
                    buf.writeItem(source.resolveStack())
                    buf.writeInt(source.modelSeed)
                    buf.writeBoolean(source.applyTint)
                    buf.writeInt(source.tintIndex)
                }
                is Custom -> {
                    buf.writeByte(5)
                    buf.writeResourceLocation(source.textureLocation)
                    writeUv(buf, source.uv)
                }
            }
        }

        private fun decodeSource(buf: PacketByteBuf): CParticleTextureSource {
            return when (val type = buf.readUnsignedByte().toInt()) {
            0 -> ParticleEffect(
                ForgeCodecHelper.particleCodecOf(ParticleTypes.END_ROD).decode(buf),
                animateByAge = buf.readBoolean(),
            )
                1 -> AtlasSprite(
                    buf.readResourceLocation(),
                    buf.readResourceLocation(),
                )
                2 -> {
                    val atlas = buf.readResourceLocation()
                    val frameCount = buf.readVarInt()
                    require(frameCount in 1..MAX_ATLAS_ANIMATION_FRAMES) {
                        "CParticle atlas animation frame count is invalid: $frameCount"
                    }
                    AtlasAnimation(
                        atlas,
                        List(frameCount) { buf.readResourceLocation() },
                    )
                }
                3 -> {
                    val id = buf.readVarInt()
                    val state = net.minecraft.core.registries.BuiltInRegistries.BLOCK_STATE_REGISTRY.byId(id)
                        ?: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    Block(
                        state,
                        randomCrop = buf.readBoolean(),
                        applyTint = buf.readBoolean(),
                        applyBrightness = buf.readBoolean(),
                    )
                }
                4 -> Item(
                    buf.readItem(),
                    modelSeed = buf.readInt(),
                    applyTint = buf.readBoolean(),
                    tintIndex = buf.readInt(),
                )
                5 -> Custom(
                    buf.readResourceLocation(),
                    readUv(buf),
                )
                else -> throw IllegalArgumentException("Unknown CParticle texture source type: $type")
            }
        }

        private fun writeUv(buf: PacketByteBuf, uv: CParticleUv) {
            buf.writeFloat(uv.u0)
            buf.writeFloat(uv.v0)
            buf.writeFloat(uv.u1)
            buf.writeFloat(uv.v1)
        }

        private fun readUv(buf: PacketByteBuf): CParticleUv = CParticleUv(
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
        )
    }

    data class ParticleEffect(
        val effect: ParticleOptions,
        val animateByAge: Boolean = true,
    ) : CParticleTextureSource

    data class AtlasSprite(
        val atlasLocation: ResourceLocation,
        val spriteLocation: ResourceLocation,
    ) : CParticleTextureSource

    class AtlasAnimation(
        val atlasLocation: ResourceLocation,
        spriteLocations: List<ResourceLocation>,
    ) : CParticleTextureSource {
        val spriteLocations: List<ResourceLocation> = spriteLocations.toList()

        init {
            require(this.spriteLocations.size in 1..MAX_ATLAS_ANIMATION_FRAMES) {
                "Atlas animation frame count must be in 1..$MAX_ATLAS_ANIMATION_FRAMES"
            }
        }
    }

    data class Block(
        val state: BlockState,
        val randomCrop: Boolean = true,
        val applyTint: Boolean = true,
        val applyBrightness: Boolean = true,
    ) : CParticleTextureSource

    class Item internal constructor(
        stack: ItemStack,
        val modelSeed: Int = 0,
        val applyTint: Boolean = true,
        val tintIndex: Int = 0,
    ) : CParticleTextureSource {
        private val snapshot = stack.copy()

        fun stackCopy(): ItemStack = snapshot.copy()
        internal fun resolveStack(): ItemStack = snapshot
    }

    data class Custom(
        val textureLocation: ResourceLocation,
        val uv: CParticleUv = CParticleUv.FULL,
    ) : CParticleTextureSource
}

fun interface CParticleTextureSourceProvider {
    fun cparticleTextureSource(): CParticleTextureSource
}

data class CParticleResolvedTexture(
    val bindingKey: CParticleTextureBindingKey,
    val descriptorId: Int,
    val uv: CParticleUv,
    val animationId: Int?,
    val colorMultiplier: Vector3f,
) {
    val isValid: Boolean
        get() = bindingKey != CParticleTextureBindingKey.MISSING
}

@JvmOverloads
fun textureOfBlock(
    state: BlockState,
    randomCrop: Boolean = true,
    applyTint: Boolean = true,
    applyBrightness: Boolean = true,
): CParticleTextureSource = CParticleTextureSource.Block(state, randomCrop, applyTint, applyBrightness)

@JvmOverloads
fun textureOfItem(
    stack: ItemStack,
    modelSeed: Int = 0,
    applyTint: Boolean = true,
    tintIndex: Int = 0,
): CParticleTextureSource = CParticleTextureSource.Item(stack, modelSeed, tintIndex)

@JvmOverloads
fun textureOf(
    textureLocation: ResourceLocation,
    uv: CParticleUv = CParticleUv.FULL,
): CParticleTextureSource = CParticleTextureSource.Custom(textureLocation, uv)

fun textureOfAtlas(
    atlasLocation: ResourceLocation,
    spriteLocation: ResourceLocation,
): CParticleTextureSource = CParticleTextureSource.AtlasSprite(atlasLocation, spriteLocation)

fun textureAnimationOfAtlas(
    atlasLocation: ResourceLocation,
    spriteLocations: List<ResourceLocation>,
): CParticleTextureSource = CParticleTextureSource.AtlasAnimation(atlasLocation, spriteLocations.toList())

fun textureOfEffect(effect: ParticleOptions): CParticleTextureSource =
    (effect as? CParticleTextureSourceProvider)?.cparticleTextureSource()
        ?: CParticleTextureSource.ParticleEffect(effect)

fun textureOfParticleSprite(spriteLocation: ResourceLocation): CParticleTextureSource =
    if (spriteLocation == CParticleSprites.DEFAULT) {
        CParticleTextureSource.ParticleEffect(ParticleTypes.END_ROD, animateByAge = false)
    } else {
        CParticleTextureSource.AtlasSprite(TextureAtlas.LOCATION_PARTICLES, spriteLocation)
    }
