package cn.coostack.cooparticlesapi.utils

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

object ClientCameraUtil {
    private const val MANUAL_FOLLOW = 0.35
    private const val SHAKE_FOLLOW = 0.45
    private const val FORCE_POSITION_FOLLOW = 0.45
    private const val FORCE_BLEND_FOLLOW = 0.4

    private const val LEGACY_SHAKE_RETARGET_TICKS = 2.0
    private const val DEFAULT_SHAKE_FREQUENCY = 1.0
    private const val HIGH_FREQUENCY_FOLLOW_START = 2.0
    private const val HIGH_FREQUENCY_FOLLOW_RANGE = 8.0
    private const val MAX_SHAKE_FOLLOW = 0.92
    private const val SHAKE_POS_SCALE = 0.45
    private const val SHAKE_ROT_SCALE = 2.0

    private const val POS_EPSILON = 1.0E-4
    private const val ROT_EPSILON = 1.0E-3f

    var shakeYawOffset = 0f
    var shakePitchOffset = 0f
    var shakeXOffset = 0.0
    var shakeYOffset = 0.0
    var shakeZOffset = 0.0

    var currentYawOffset = 0f
    var currentPitchOffset = 0f

    var currentXOffset = 0.0
    var currentYOffset = 0.0
    var currentZOffset = 0.0

    var tick = 0
    var ampStep = 0.0
    var amp = 0.0

    private var manualTargetPosOffset = Vec3.ZERO
    private var manualTargetYawOffset = 0f
    private var manualTargetPitchOffset = 0f

    private var manualPosOffset = Vec3.ZERO
    private var manualYawOffset = 0f
    private var manualPitchOffset = 0f

    private var shakeTargetPosOffset = Vec3.ZERO
    private var shakeTargetYaw = 0f
    private var shakeTargetPitch = 0f

    private var shakePosOffset = Vec3.ZERO
    private var shakeYaw = 0f
    private var shakePitch = 0f

    private var shakeDuration = 0
    private var shakeFrequency = DEFAULT_SHAKE_FREQUENCY
    private var shakePhase = 0.0

    private var forcedCameraPositionTarget = Vec3.ZERO
    private var forcedCameraPositionCurrent = Vec3.ZERO
    private var forcedPositionBlendTarget = 0f
    private var forcedPositionBlendCurrent = 0f

    fun setOffset(position: Vec3, yawOffset: Float = 0f, pitchOffset: Float = 0f) {
        manualTargetPosOffset = position
        manualTargetYawOffset = yawOffset
        manualTargetPitchOffset = pitchOffset
    }

    fun setOffsetNow(position: Vec3, yawOffset: Float = 0f, pitchOffset: Float = 0f) {
        setOffset(position, yawOffset, pitchOffset)
        manualPosOffset = position
        manualYawOffset = yawOffset
        manualPitchOffset = pitchOffset
        syncLegacyState()
    }

    fun setOffsetPosition(offset: Vec3) {
        manualTargetPosOffset = offset
        manualPosOffset = offset
        syncLegacyState()
    }

    fun addOffsetPosition(delta: Vec3) {
        manualTargetPosOffset = manualTargetPosOffset.add(delta)
    }

    fun setOffsetAngle(yawOffset: Float, pitchOffset: Float) {
        manualTargetYawOffset = yawOffset
        manualTargetPitchOffset = pitchOffset
    }

    fun addOffsetAngle(yawOffset: Float, pitchOffset: Float) {
        manualTargetYawOffset += yawOffset
        manualTargetPitchOffset += pitchOffset
    }

    fun resetPosOffset() {
        manualTargetPosOffset = Vec3.ZERO
        manualPosOffset = Vec3.ZERO
        syncLegacyState()
    }

    fun resetPosOffsetNow() {
        resetPosOffset()
        manualPosOffset = Vec3.ZERO
        syncLegacyState()
    }

    fun resetAngleOffset() {
        manualTargetYawOffset = 0f
        manualTargetPitchOffset = 0f
        manualYawOffset = 0f
        manualPitchOffset = 0f
        syncLegacyState()
    }

    fun resetAngleOffsetNow() {
        resetAngleOffset()
        manualYawOffset = 0f
        manualPitchOffset = 0f
        syncLegacyState()
    }

    fun resetOffset() {
        resetAngleOffset()
        resetPosOffset()
    }

    fun resetOffsetNow() {
        resetAngleOffsetNow()
        resetPosOffsetNow()
    }

    fun setForcedCameraPosition(position: Vec3) {
        forcedCameraPositionTarget = position
        if (forcedPositionBlendTarget <= 0f && forcedPositionBlendCurrent <= 0f) {
            forcedCameraPositionCurrent = position
        }
        forcedPositionBlendTarget = 1f
    }

    fun setForcedCameraPositionNow(position: Vec3) {
        forcedCameraPositionTarget = position
        forcedCameraPositionCurrent = position
        forcedPositionBlendTarget = 1f
        forcedPositionBlendCurrent = 1f
    }

    fun resetForcedCameraPosition() {
        forcedPositionBlendTarget = 0f
    }

    fun resetForcedCameraPositionNow() {
        forcedPositionBlendTarget = 0f
        forcedPositionBlendCurrent = 0f
    }

    fun stopShakeCamera() {
        tick = 0
    }

    fun stopShakeCameraNow() {
        tick = 0
        shakeDuration = 0
        amp = 0.0
        ampStep = 0.0
        shakeFrequency = DEFAULT_SHAKE_FREQUENCY
        shakePhase = 0.0
        shakeTargetPosOffset = Vec3.ZERO
        shakeTargetYaw = 0f
        shakeTargetPitch = 0f
        shakePosOffset = Vec3.ZERO
        shakeYaw = 0f
        shakePitch = 0f
        syncLegacyState()
    }

    fun resetAll() {
        stopShakeCamera()
        resetOffset()
        resetForcedCameraPosition()
    }

    fun resetAllNow() {
        stopShakeCameraNow()
        resetOffsetNow()
        resetForcedCameraPositionNow()
    }

    fun getTotalYawOffset(): Float {
        return shakeYawOffset + currentYawOffset
    }

    fun getTotalPitchOffset(): Float {
        return shakePitchOffset + currentPitchOffset
    }

    fun getTotalPositionOffset(): Vec3 {
        return Vec3(
            shakeXOffset + currentXOffset,
            shakeYOffset + currentYOffset,
            shakeZOffset + currentZOffset
        )
    }

    fun getForcedCameraPosition(): Vec3 {
        return forcedCameraPositionCurrent
    }

    fun getForcedCameraBlend(): Float {
        return forcedPositionBlendCurrent
    }

    fun startShakeCamera(tick: Int, amplitude: Double) {
        startShakeCamera(tick, amplitude, DEFAULT_SHAKE_FREQUENCY)
    }

    fun startShakeCamera(tick: Int, amplitude: Double, frequency: Double) {
        val state = ClientCameraShakeMath.startShake(tick, amplitude, frequency) ?: return
        this.tick = state.tick
        shakeDuration = state.duration
        amp = state.amplitude
        shakeFrequency = state.frequency
        ampStep = state.amplitudeStep
    }

    fun tick() {
        tickManualOffset()
        tickShake()
        tickForcedPosition()
        syncLegacyState()
    }

    private fun tickManualOffset() {
        manualPosOffset = GraphMathHelper.lerp(MANUAL_FOLLOW, manualPosOffset, manualTargetPosOffset)
        manualYawOffset = GraphMathHelper.lerp(MANUAL_FOLLOW, manualYawOffset, manualTargetYawOffset)
        manualPitchOffset = GraphMathHelper.lerp(MANUAL_FOLLOW, manualPitchOffset, manualTargetPitchOffset)

        manualPosOffset = snapVec(manualPosOffset, manualTargetPosOffset)
        manualYawOffset = snapFloat(manualYawOffset, manualTargetYawOffset)
        manualPitchOffset = snapFloat(manualPitchOffset, manualTargetPitchOffset)
    }

    private fun tickShake() {
        if (tick > 0) {
            val envelope = shakeEnvelope()
            shakePhase += shakePhaseStep(shakeFrequency)
            shakeTargetPosOffset = sampleShakePos(shakePhase, envelope)
            shakeTargetYaw = sampleShakeAngle(shakePhase, 3.11, envelope)
            shakeTargetPitch = sampleShakeAngle(shakePhase, 5.47, envelope)
            tick--
        } else {
            shakeTargetPosOffset = Vec3.ZERO
            shakeTargetYaw = 0f
            shakeTargetPitch = 0f
            if (isZeroShake()) {
                shakeDuration = 0
                amp = 0.0
                ampStep = 0.0
                shakeFrequency = DEFAULT_SHAKE_FREQUENCY
                shakePhase = 0.0
            }
        }

        val shakeFollow = shakeFollowFactor(shakeFrequency)
        shakePosOffset = GraphMathHelper.lerp(shakeFollow, shakePosOffset, shakeTargetPosOffset)
        shakeYaw = GraphMathHelper.lerp(shakeFollow, shakeYaw, shakeTargetYaw)
        shakePitch = GraphMathHelper.lerp(shakeFollow, shakePitch, shakeTargetPitch)

        if (tick <= 0) {
            shakePosOffset = snapVec(shakePosOffset, Vec3.ZERO)
            shakeYaw = snapFloat(shakeYaw, 0f)
            shakePitch = snapFloat(shakePitch, 0f)
        }
    }

    private fun tickForcedPosition() {
        forcedCameraPositionCurrent =
            GraphMathHelper.lerp(FORCE_POSITION_FOLLOW, forcedCameraPositionCurrent, forcedCameraPositionTarget)
        forcedPositionBlendCurrent =
            GraphMathHelper.lerp(FORCE_BLEND_FOLLOW, forcedPositionBlendCurrent, forcedPositionBlendTarget)

        forcedCameraPositionCurrent = snapVec(forcedCameraPositionCurrent, forcedCameraPositionTarget)
        forcedPositionBlendCurrent = snapFloat(forcedPositionBlendCurrent, forcedPositionBlendTarget)
    }

    private fun shakeEnvelope(): Double {
        if (shakeDuration <= 0) {
            return 0.0
        }
        val progress = 1.0 - tick.toDouble() / shakeDuration.toDouble()
        val decay = (1.0 - progress).coerceIn(0.0, 1.0)
        return amp * decay * decay
    }

    internal fun shakePhaseStep(frequency: Double): Double {
        return ClientCameraShakeMath.shakePhaseStep(frequency)
    }

    internal fun shakeFollowFactor(frequency: Double): Double {
        return ClientCameraShakeMath.shakeFollowFactor(frequency)
    }

    internal fun sampleShakeNoise(phase: Double, seed: Double): Double {
        return ClientCameraShakeMath.sampleShakeNoise(phase, seed)
    }

    private fun sampleShakePos(phase: Double, envelope: Double): Vec3 {
        val range = envelope * SHAKE_POS_SCALE
        return Vec3(
            sampleShakeNoise(phase, 0.13) * range,
            sampleShakeNoise(phase, 1.37) * range,
            sampleShakeNoise(phase, 2.73) * range
        )
    }

    private fun sampleShakeAngle(phase: Double, seed: Double, envelope: Double): Float {
        val range = envelope * SHAKE_ROT_SCALE
        return (sampleShakeNoise(phase, seed) * range).toFloat()
    }

    private fun smoothstep(progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        return clamped * clamped * (3.0 - 2.0 * clamped)
    }

    private fun hashNoise(index: Double, seed: Double): Double {
        val raw = sin(index * 12.9898 + seed * 78.233) * 43758.5453123
        val fract = raw - floor(raw)
        return fract * 2.0 - 1.0
    }

    private fun lerpDouble(progress: Double, from: Double, to: Double): Double {
        return from + (to - from) * progress
    }

    private fun isZeroShake(): Boolean {
        if (abs(shakeYaw) > ROT_EPSILON || abs(shakePitch) > ROT_EPSILON) {
            return false
        }
        return shakePosOffset.distanceTo(Vec3.ZERO) <= POS_EPSILON
    }

    private fun snapVec(value: Vec3, target: Vec3): Vec3 {
        return if (value.distanceTo(target) <= POS_EPSILON) {
            target
        } else {
            value
        }
    }

    private fun snapFloat(value: Float, target: Float): Float {
        return if (abs(value - target) <= ROT_EPSILON) {
            target
        } else {
            value
        }
    }

    private fun syncLegacyState() {
        shakeYawOffset = shakeYaw
        shakePitchOffset = shakePitch
        shakeXOffset = shakePosOffset.x
        shakeYOffset = shakePosOffset.y
        shakeZOffset = shakePosOffset.z

        currentYawOffset = manualYawOffset
        currentPitchOffset = manualPitchOffset
        currentXOffset = manualPosOffset.x
        currentYOffset = manualPosOffset.y
        currentZOffset = manualPosOffset.z
    }
}
