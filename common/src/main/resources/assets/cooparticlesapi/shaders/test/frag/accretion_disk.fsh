#version 330 core

in vec2 screen_uv;

layout (location = 0) out vec4 GlowMask;
layout (location = 1) out vec4 DistortionMask;
layout (location = 2) out vec4 SceneLightMask;

uniform mat4 projMat;
uniform mat4 inverseProjMat;
uniform mat3 viewRotationMat;
uniform mat3 inverseViewRotationMat;
uniform sampler2D sceneDepth;

uniform float time;
uniform vec2 screenSize;

uniform vec3 blackHoleWorldPos;
uniform vec3 blackHoleCameraRelativePos;

uniform float effectRadiusWorld;
uniform float lightingInfluenceWorld;
uniform float schwarzschildRadius;
uniform vec3 diskNormal;
uniform float diskInnerRadius;
uniform float diskOuterRadius;
uniform float diskHalfThickness;
uniform float diskTemperatureScale;
uniform float lensingStrength;
uniform float spinSpeed;
uniform int stepCount;
uniform float maxDistance;
uniform float diskDensity;
uniform float diskEmissionStrength;
uniform vec3 diskColor;

const int MAX_TRACE_STEPS = 192;
const int DISK_VOLUME_STEPS = 16;
const float PI = 3.141592653589793;
const float EPSILON = 1.0e-4;
const float DISK_NOISE_SCALE = 1.75;
const float DISK_FILAMENT_SCALE = 6.40;
const float DISK_VOLUME_ABSORPTION = 0.32;
const float DOPPLER_STRENGTH = 0.92;
const float HOTSPOT_STRENGTH = 1.15;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += noise2(p) * amplitude;
        p = p * 2.03 + vec2(13.1, 7.9);
        amplitude *= 0.5;
    }
    return value;
}

void buildDiskBasis(vec3 normal, out vec3 tangent, out vec3 bitangent) {
    vec3 helper = abs(normal.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    tangent = normalize(cross(helper, normal));
    bitangent = normalize(cross(normal, tangent));
}

bool intersectSphere(
    vec3 rayOriginBhSpace,
    vec3 rayDirection,
    float sphereRadius,
    out float tEnter,
    out float tExit
) {
    float projectedCenter = dot(rayOriginBhSpace, rayDirection);
    float radiusTerm = dot(rayOriginBhSpace, rayOriginBhSpace) - sphereRadius * sphereRadius;
    float discriminant = projectedCenter * projectedCenter - radiusTerm;
    if (discriminant < 0.0) {
        tEnter = 0.0;
        tExit = 0.0;
        return false;
    }

    float root = sqrt(discriminant);
    tEnter = -projectedCenter - root;
    tExit = -projectedCenter + root;
    if (tExit <= 0.0) {
        return false;
    }
    tEnter = max(tEnter, 0.0);
    return true;
}

bool isInsideEventHorizon(vec3 positionBhSpace) {
    return length(positionBhSpace) <= schwarzschildRadius;
}

float stepLengthForRadius(float radius) {
    float baseStep = maxDistance / max(float(stepCount), 1.0);
    float minStep = max(max(schwarzschildRadius * 0.04, diskHalfThickness * 0.35), 0.012);
    float diskZone = saturate((radius - schwarzschildRadius) / max(diskOuterRadius - schwarzschildRadius, EPSILON));
    float outerZone = saturate((radius - diskOuterRadius) / max(effectRadiusWorld - diskOuterRadius, EPSILON));
    float nearStep = mix(minStep, baseStep * 0.55, diskZone * diskZone);
    return clamp(mix(nearStep, baseStep * 1.85, outerZone), minStep, baseStep * 2.0);
}

vec3 bendRay(vec3 positionBhSpace, vec3 rayDirection, float stepLength) {
    float radius = max(length(positionBhSpace), schwarzschildRadius + EPSILON);
    vec3 angularMomentum = cross(positionBhSpace, rayDirection);
    float impactSquared = dot(angularMomentum, angularMomentum);
    float invRadius = 1.0 / radius;
    float invRadius5 = invRadius * invRadius * invRadius * invRadius * invRadius;

    vec3 accel = -1.5 * lensingStrength * schwarzschildRadius * impactSquared * positionBhSpace * invRadius5;
    return normalize(rayDirection + accel * stepLength);
}

bool intersectAccretionDisk(
    vec3 segmentStartBhSpace,
    vec3 segmentEndBhSpace,
    out vec3 samplePosBhSpace,
    out float density,
    out float radialDistance,
    out vec3 radialDir
) {
    vec3 normal = normalize(diskNormal);
    float startHeight = dot(segmentStartBhSpace, normal);
    float endHeight = dot(segmentEndBhSpace, normal);
    bool crossesMidPlane = startHeight * endHeight <= 0.0;
    bool touchesSlab = min(abs(startHeight), abs(endHeight)) <= diskHalfThickness;

    samplePosBhSpace = 0.5 * (segmentStartBhSpace + segmentEndBhSpace);
    density = 0.0;
    radialDistance = 0.0;
    radialDir = vec3(1.0, 0.0, 0.0);

    if (!crossesMidPlane && !touchesSlab) {
        return false;
    }

    float denom = endHeight - startHeight;
    float planeT = abs(denom) > EPSILON ? clamp(-startHeight / denom, 0.0, 1.0) : 0.5;
    vec3 planePoint = mix(segmentStartBhSpace, segmentEndBhSpace, planeT);
    vec3 slabSample = touchesSlab ? samplePosBhSpace : planePoint;
    float sampleHeight = dot(slabSample, normal);
    vec3 radialVector = slabSample - normal * sampleHeight;
    radialDistance = length(radialVector);

    if (radialDistance < diskInnerRadius || radialDistance > diskOuterRadius) {
        return false;
    }

    samplePosBhSpace = slabSample;
    radialDir = radialDistance > EPSILON ? radialVector / radialDistance : vec3(1.0, 0.0, 0.0);

    float verticalProfile = exp(-pow(abs(sampleHeight) / max(diskHalfThickness, EPSILON), 2.0) * 2.1);
    float innerFade = smoothstep(diskInnerRadius, diskInnerRadius + diskHalfThickness * 3.0, radialDistance);
    float outerFade = 1.0 - smoothstep(diskOuterRadius - diskHalfThickness * 4.0, diskOuterRadius, radialDistance);
    density = verticalProfile * innerFade * outerFade;
    return density > 1.0e-3;
}

vec3 blackbodyColor(float kelvin) {
    float temperature = clamp(kelvin, 1000.0, 40000.0) / 100.0;
    float red;
    float green;
    float blue;

    if (temperature <= 66.0) {
        red = 1.0;
        green = clamp((99.4708025861 * log(max(temperature, 1.0)) - 161.1195681661) / 255.0, 0.0, 1.0);
        if (temperature <= 19.0) {
            blue = 0.0;
        } else {
            blue = clamp((138.5177312231 * log(max(temperature - 10.0, 1.0)) - 305.0447927307) / 255.0, 0.0, 1.0);
        }
    } else {
        float hot = max(temperature - 60.0, 0.01);
        red = clamp((329.698727446 * pow(hot, -0.1332047592)) / 255.0, 0.0, 1.0);
        green = clamp((288.1221695283 * pow(hot, -0.0755148492)) / 255.0, 0.0, 1.0);
        blue = 1.0;
    }

    return vec3(red, green, blue);
}

void sampleDiskVolumeProperties(
    vec3 samplePosBhSpace,
    vec3 rayDirection,
    float densityHint,
    out float volumeDensity,
    out vec3 emissionColor
) {
    vec3 normal = normalize(diskNormal);
    vec3 tangent;
    vec3 bitangent;
    buildDiskBasis(normal, tangent, bitangent);

    float planeHeight = dot(samplePosBhSpace, normal);
    vec3 radialVector = samplePosBhSpace - normal * planeHeight;
    float radialDistance = length(radialVector);

    volumeDensity = 0.0;
    emissionColor = vec3(0.0);
    if (radialDistance < diskInnerRadius || radialDistance > diskOuterRadius) {
        return;
    }

    vec3 radialDir = radialDistance > EPSILON ? radialVector / radialDistance : tangent;
    float radiusRatio = max(radialDistance / max(diskInnerRadius, EPSILON), 1.0);
    float orbitRadius = max(length(samplePosBhSpace), diskInnerRadius + EPSILON);
    float equatorialRatio = radialDistance / orbitRadius;

    float localThickness = diskHalfThickness * mix(0.82, 1.12, smoothstep(1.0, 4.8, radiusRatio));
    float verticalWeight = exp(-pow(abs(planeHeight) / max(localThickness, EPSILON), 1.60) * 2.65);
    float innerWeight = smoothstep(diskInnerRadius, diskInnerRadius + diskHalfThickness * 4.5, radialDistance);
    float outerWeight = 1.0 - smoothstep(diskOuterRadius - diskHalfThickness * 8.0, diskOuterRadius, radialDistance);
    float radialWeight = innerWeight * outerWeight;
    float equatorialWeight = pow(saturate((equatorialRatio - 0.58) / 0.42), 1.85);
    if (radialWeight <= 1.0e-4 || verticalWeight <= 1.0e-4 || equatorialWeight <= 1.0e-4) {
        return;
    }

    float phi = atan(dot(radialDir, bitangent), dot(radialDir, tangent));
    vec2 localDiskPos = vec2(dot(samplePosBhSpace, tangent), dot(samplePosBhSpace, bitangent));
    float objectSeed = fract(dot(blackHoleWorldPos, vec3(0.013, 0.021, 0.034)));
    float spinMagnitude = 0.55 + abs(spinSpeed) * 1.15;
    float angularFlow = phi - time * (0.45 + spinMagnitude * 1.10) / max(radiusRatio, 0.92);
    float spiralWarp = log(max(radiusRatio, 1.0)) * (6.5 + spinMagnitude * 1.8);

    float differentialPhase = time * (0.22 + spinMagnitude * 0.42) / max(radiusRatio, 0.88);
    float flowCos = cos(differentialPhase);
    float flowSin = sin(differentialPhase);
    mat2 flowRot = mat2(flowCos, -flowSin, flowSin, flowCos);
    vec2 flowPos = flowRot * localDiskPos;
    vec2 shearedPos = vec2(
        flowPos.x + spiralWarp * 0.22 - flowPos.y * 0.08,
        flowPos.y * 0.84 + angularFlow * radialDistance * 0.15
    );

    vec2 macroUv = shearedPos * 0.22 + vec2(objectSeed * 19.0, -time * (0.08 + spinMagnitude * 0.12));
    float macroNoise = fbm2(macroUv * DISK_NOISE_SCALE);

    vec2 domainWarpUvA = shearedPos * 0.36 + vec2(angularFlow * 1.4, objectSeed * 13.0);
    vec2 domainWarpUvB = vec2(-shearedPos.y, shearedPos.x) * 0.30 + vec2(-time * (0.11 + spinMagnitude * 0.08), objectSeed * 29.0);
    vec2 domainWarp = vec2(fbm2(domainWarpUvA), fbm2(domainWarpUvB)) - 0.5;

    vec2 cloudUvLarge = shearedPos * 0.28 + domainWarp * 1.45;
    vec2 cloudUvMedium = shearedPos * 0.54 + domainWarp * 2.10 + vec2(7.3, -11.1);
    vec2 cloudUvFine = shearedPos * (DISK_FILAMENT_SCALE * 0.12) + domainWarp * 3.40 + vec2(-19.0, 13.0);
    float cloudLarge = fbm2(cloudUvLarge);
    float cloudMedium = fbm2(cloudUvMedium);
    float cloudFine = fbm2(cloudUvFine);
    float wispyNoise = 1.0 - abs(fbm2(cloudUvMedium * 1.32 + vec2(4.1, 9.7)) * 2.0 - 1.0);
    float breakup = smoothstep(0.18, 0.82, noise2(cloudUvLarge * 0.92 - domainWarp * 1.25 + vec2(5.2, -7.1)));
    float voidNoise = smoothstep(0.20, 0.86, noise2(cloudUvFine * 0.65 + domainWarp * 0.75 + vec2(-3.4, 8.8)));
    float cloudMass = smoothstep(0.22, 0.82, cloudLarge * 0.78 + cloudMedium * 0.52 + macroNoise * 0.28);
    float clumpWeight = mix(0.40, 1.55, cloudMass);
    float mistWeight = mix(0.68, 1.24, smoothstep(0.18, 0.88, wispyNoise * 0.72 + cloudFine * 0.38));
    float voidWeight = mix(1.25, 0.58, voidNoise);
    float hotspot = smoothstep(0.88, 0.995, noise2(vec2(
        cloudUvFine.x * 1.45 + time * (0.42 + spinMagnitude * 0.35),
        cloudUvFine.y * 1.45 + objectSeed * 37.0
    )));
    hotspot *= exp(-pow((radiusRatio - 1.35) * 1.1, 2.0)) * HOTSPOT_STRENGTH;

    float turbulence = mix(0.78, 1.30, macroNoise);
    float cloudy = mix(0.82, 1.28, cloudMedium);
    float densityBase = radialWeight * verticalWeight * equatorialWeight * turbulence * clumpWeight * mistWeight * voidWeight * cloudy * mix(0.80, 1.15, breakup);
    volumeDensity = densityHint * densityBase;
    volumeDensity *= 0.86 + hotspot * 0.42;

    float thinDiskCut = max(1.0 - sqrt(diskInnerRadius / max(radialDistance, diskInnerRadius + EPSILON)), 0.0);
    float fluxFalloff = pow(radiusRatio, -2.1) * max(thinDiskCut, 0.10);
    float innerHeat = exp(-pow((radiusRatio - 1.0) * 1.15, 2.0));
    float temperature = diskTemperatureScale * pow(radiusRatio, -0.82);
    temperature *= 1.15 + innerHeat * 0.85 + hotspot * 0.40;

    float beta = sqrt(clamp(schwarzschildRadius / max(2.0 * orbitRadius, EPSILON), 0.0, 0.78));
    beta *= clamp(abs(spinSpeed), 0.40, 1.35);

    float spinSign = spinSpeed >= 0.0 ? 1.0 : -1.0;
    vec3 orbitalDir = normalize(cross(normal, radialDir)) * spinSign;
    vec3 velocity = orbitalDir * beta;
    float gamma = inversesqrt(max(1.0 - dot(velocity, velocity), 0.08));
    vec3 toObserver = normalize(-rayDirection);
    float doppler = 1.0 / max(gamma * (1.0 - dot(velocity, toObserver)), 0.15);
    float gravRedshift = sqrt(clamp(1.0 - schwarzschildRadius / orbitRadius, 0.03, 1.0));
    float beaming = clamp(gravRedshift * mix(1.0, doppler, DOPPLER_STRENGTH), 0.24, 3.2);
    float forwardBias = saturate(0.5 + 0.5 * dot(velocity, toObserver));

    vec3 thermalColor = blackbodyColor(max(temperature * beaming, 1000.0));
    float midBlend = smoothstep(1.1, 2.4, radiusRatio);
    float outerBlend = smoothstep(2.4, 5.8, radiusRatio);
    vec3 innerTint = vec3(1.30, 1.24, 1.08);
    vec3 midTint = vec3(1.38, 1.11, 0.45);
    vec3 outerTint = vec3(1.18, 0.46, 0.17);
    vec3 tint = mix(innerTint, midTint, midBlend);
    tint = mix(tint, outerTint, outerBlend);
    tint = mix(tint, vec3(1.55, 1.42, 1.16), hotspot * 0.55 + forwardBias * 0.15);
    vec3 tintedColor = thermalColor * tint * mix(vec3(1.0), diskColor, 0.38);

    float anisotropy = mix(0.70, 1.35, forwardBias);
    float grazingView = 1.0 - abs(dot(toObserver, normal));
    float farSideDepth = max(-planeHeight * sign(dot(toObserver, normal)), 0.0);
    float selfShadow = mix(
        1.0,
        0.42 + 0.58 * exp(-farSideDepth / max(localThickness, EPSILON) * 2.2),
        grazingView * 0.85
    );
    float faceOnFactor = abs(dot(toObserver, normal));
    float intensity = fluxFalloff * diskEmissionStrength * (0.82 + volumeDensity * 1.18) * (1.0 + innerHeat * 3.2);
    intensity *= pow(beaming, 2.85) * anisotropy;
    intensity *= 1.06 + cloudLarge * 0.56 + cloudMedium * 0.32;
    intensity *= 1.08 + hotspot * 1.85;
    intensity *= mix(1.10, 1.46, faceOnFactor);
    intensity *= mix(0.96, 1.14, grazingView) * selfShadow;

    emissionColor = tintedColor * intensity;
}

vec3 sampleDiskEmission(
    vec3 samplePosBhSpace,
    vec3 radialDir,
    float radialDistance,
    vec3 rayDirection,
    float localDensity
) {
    float volumeDensity;
    vec3 emissionColor;
    sampleDiskVolumeProperties(samplePosBhSpace, rayDirection, localDensity, volumeDensity, emissionColor);
    return emissionColor;
}

void integrateDiskVolumeSegment(
    vec3 segmentStartBhSpace,
    vec3 segmentEndBhSpace,
    vec3 rayDirection,
    float densityHint,
    inout vec3 diskRadiance,
    inout float transmittance,
    inout float accumulatedOpticalDepth
) {
    vec3 segment = segmentEndBhSpace - segmentStartBhSpace;
    float segmentLength = length(segment);
    if (segmentLength <= EPSILON) {
        return;
    }

    vec3 stepVec = segment / float(DISK_VOLUME_STEPS);
    float localStepLength = segmentLength / float(DISK_VOLUME_STEPS);
    vec3 samplePos = segmentStartBhSpace + stepVec * 0.5;

    for (int stepIndex = 0; stepIndex < DISK_VOLUME_STEPS; stepIndex++) {
        float volumeDensity;
        vec3 emissionColor;
        sampleDiskVolumeProperties(samplePos, rayDirection, densityHint, volumeDensity, emissionColor);

        if (volumeDensity > 1.0e-4) {
            float opticalDepth = volumeDensity * localStepLength * DISK_VOLUME_ABSORPTION;
            diskRadiance += emissionColor * transmittance * localStepLength;
            transmittance *= exp(-opticalDepth);
            accumulatedOpticalDepth += opticalDepth;

            if (transmittance <= 0.01) {
                break;
            }
        }

        samplePos += stepVec;
    }
}

vec3 reconstructWorldRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 viewFar = inverseProjMat * vec4(ndc, 1.0, 1.0);
    vec3 viewDir = normalize(viewFar.xyz / max(viewFar.w, EPSILON));
    return normalize(inverseViewRotationMat * viewDir);
}

vec3 reconstructCameraRelativePosFromDepth(vec2 uv, float depth01) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth01 * 2.0 - 1.0, 1.0);
    vec4 viewPos = inverseProjMat * clipPos;
    vec3 cameraViewPos = viewPos.xyz / max(viewPos.w, EPSILON);
    return inverseViewRotationMat * cameraViewPos;
}

vec3 reconstructSceneNormal(vec2 uv, vec3 centerCameraRelativePos) {
    vec2 texel = 1.0 / max(screenSize, vec2(1.0));
    vec2 rightUv = clamp(uv + vec2(texel.x, 0.0), vec2(0.001), vec2(0.999));
    vec2 upUv = clamp(uv + vec2(0.0, texel.y), vec2(0.001), vec2(0.999));

    float depthRight = texture(sceneDepth, rightUv).r;
    float depthUp = texture(sceneDepth, upUv).r;

    vec3 rightPos = depthRight < 0.999999
        ? reconstructCameraRelativePosFromDepth(rightUv, depthRight)
        : centerCameraRelativePos + vec3(texel.x, 0.0, 0.0);
    vec3 upPos = depthUp < 0.999999
        ? reconstructCameraRelativePosFromDepth(upUv, depthUp)
        : centerCameraRelativePos + vec3(0.0, texel.y, 0.0);

    vec3 normal = cross(rightPos - centerCameraRelativePos, upPos - centerCameraRelativePos);
    float normalLength = length(normal);
    if (normalLength <= EPSILON) {
        return normalize(-centerCameraRelativePos);
    }
    normal /= normalLength;
    if (dot(normal, -centerCameraRelativePos) < 0.0) {
        normal = -normal;
    }
    return normal;
}

float projectDepth01FromCameraRelative(vec3 cameraRelativePos) {
    vec3 viewPos = viewRotationMat * cameraRelativePos;
    vec4 clipPos = projMat * vec4(viewPos, 1.0);
    if (clipPos.w <= EPSILON) {
        return 1.0;
    }
    return clipPos.z / clipPos.w * 0.5 + 0.5;
}

bool isOccludedByScene(vec2 uv, vec3 cameraRelativePos) {
    float sceneDepthSample = texture(sceneDepth, uv).r;
    if (sceneDepthSample >= 0.999999) {
        return false;
    }
    float projectedDepth = projectDepth01FromCameraRelative(cameraRelativePos);
    return sceneDepthSample + 1.0e-4 < projectedDepth;
}

vec2 sampleLensedBackground(vec3 escapedRayDir, vec2 currentUv) {
    vec3 farPointCameraRelative = normalize(escapedRayDir) * max(maxDistance, effectRadiusWorld * 2.0);
    vec3 farPointViewSpace = viewRotationMat * farPointCameraRelative;
    vec4 clipPos = projMat * vec4(farPointViewSpace, 1.0);
    if (clipPos.w <= EPSILON) {
        return currentUv;
    }

    vec2 ndc = clipPos.xy / clipPos.w;
    vec2 uv = ndc * 0.5 + 0.5;
    return clamp(uv, vec2(0.001), vec2(0.999));
}

vec3 sampleDiskSceneLighting(vec2 uv, float sceneDepthSample) {
    if (sceneDepthSample >= 0.999999) {
        return vec3(0.0);
    }

    vec3 surfaceCameraRelativePos = reconstructCameraRelativePosFromDepth(uv, sceneDepthSample);
    vec3 surfaceBhSpace = surfaceCameraRelativePos - blackHoleCameraRelativePos;
    float centerDistance = length(surfaceBhSpace);
    if (centerDistance > lightingInfluenceWorld) {
        return vec3(0.0);
    }

    vec3 surfaceNormal = reconstructSceneNormal(uv, surfaceCameraRelativePos);
    vec3 diskNormalN = normalize(diskNormal);
    vec3 tangent;
    vec3 bitangent;
    buildDiskBasis(diskNormalN, tangent, bitangent);

    float planeHeight = dot(surfaceBhSpace, diskNormalN);
    vec3 planeProjection = surfaceBhSpace - diskNormalN * planeHeight;
    float planeRadius = length(planeProjection);
    vec3 radialDir = planeRadius > EPSILON ? planeProjection / planeRadius : tangent;
    float emitterRadius = clamp(
        planeRadius,
        diskInnerRadius + diskHalfThickness * 1.25,
        diskOuterRadius - diskHalfThickness * 0.75
    );
    float emitterHeight = clamp(-planeHeight * 0.18, -diskHalfThickness * 1.05, diskHalfThickness * 1.05);
    vec3 emitterBhSpace = radialDir * emitterRadius + diskNormalN * emitterHeight;

    vec3 toEmitter = emitterBhSpace - surfaceBhSpace;
    float emitterDistance = length(toEmitter);
    if (emitterDistance <= EPSILON) {
        return vec3(0.0);
    }

    vec3 lightDir = toEmitter / emitterDistance;
    float proxyDensity;
    vec3 proxyEmission;
    sampleDiskVolumeProperties(emitterBhSpace, lightDir, max(diskDensity, 0.01), proxyDensity, proxyEmission);
    float emissionLum = luminance(proxyEmission);
    if (proxyDensity <= 1.0e-4 || emissionLum <= 1.0e-4) {
        return vec3(0.0);
    }

    float diffuse = max(dot(surfaceNormal, lightDir), 0.0);
    float wrappedDiffuse = saturate(diffuse * 0.72 + 0.28);
    float attenuation = saturate(1.0 - emitterDistance / max(lightingInfluenceWorld, EPSILON));
    attenuation *= attenuation * (0.42 + attenuation * 0.58);

    float centerClearance = smoothstep(schwarzschildRadius * 1.25, schwarzschildRadius * 3.7, centerDistance);
    float viewFacing = saturate(dot(surfaceNormal, normalize(-surfaceCameraRelativePos)) * 0.40 + 0.60);
    float diskFacing = mix(0.72, 1.12, saturate(1.0 - abs(dot(lightDir, diskNormalN)) * 0.65));
    float luminousFlux = (0.03 + diskEmissionStrength * 0.028) * (0.40 + diskDensity * 0.045);
    luminousFlux *= 0.85 + sqrt(proxyDensity) * 0.35 + sqrt(emissionLum) * 0.08;

    vec3 lightColor = normalize(proxyEmission + vec3(EPSILON));
    vec3 scatterTint = mix(lightColor, lightColor * vec3(1.06, 0.98, 0.90), 0.22);
    return scatterTint * emissionLum * luminousFlux * wrappedDiffuse * attenuation * centerClearance * viewFacing * diskFacing;
}

void main() {
    vec2 currentUv = screen_uv;
    vec3 cameraBhSpace = -blackHoleCameraRelativePos;
    vec3 rayDir = reconstructWorldRay(currentUv);
    float sceneDepthSample = texture(sceneDepth, currentUv).r;

    float sphereEntry;
    float sphereExit;
    bool traceHitsEffect = intersectSphere(cameraBhSpace, rayDir, effectRadiusWorld, sphereEntry, sphereExit);

    float lightEntry;
    float lightExit;
    bool traceHitsLighting = intersectSphere(cameraBhSpace, rayDir, lightingInfluenceWorld, lightEntry, lightExit);

    if (!traceHitsEffect && !traceHitsLighting) {
        discard;
    }

    vec3 diskRadiance = vec3(0.0);
    vec3 sceneLight = traceHitsLighting ? sampleDiskSceneLighting(currentUv, sceneDepthSample) : vec3(0.0);
    float sceneLightWeight = saturate(luminance(sceneLight) * 0.10);

    float transmittance = 1.0;
    float traveled = 0.0;
    float minRadius = effectRadiusWorld;
    float accumulatedOpticalDepth = 0.0;
    bool captured = false;
    float horizonMask = 0.0;
    vec2 uvOffset = vec2(0.0);
    float diskOpacity = 0.0;
    float emissionWeight = 0.0;
    float coverage = 0.0;

    if (traceHitsEffect) {
        vec3 tracePos = cameraBhSpace + rayDir * sphereEntry;
        vec3 entryCameraRelativePos = tracePos + blackHoleCameraRelativePos;
        bool traceVisible = !(sphereEntry > EPSILON && isOccludedByScene(currentUv, entryCameraRelativePos));

        if (traceVisible) {
            float entryRadius = length(tracePos);
            float startStep = stepLengthForRadius(entryRadius);
            float jitter = hash12(gl_FragCoord.xy + blackHoleWorldPos.xy * 19.73);
            float initialAdvance = min(startStep * jitter * 0.45, max(effectRadiusWorld * 0.08, 0.0));
            tracePos += rayDir * initialAdvance;
            traveled = initialAdvance;
            minRadius = length(tracePos);

            for (int i = 0; i < MAX_TRACE_STEPS; i++) {
                if (i >= stepCount) {
                    break;
                }

                float radius = length(tracePos);
                minRadius = min(minRadius, radius);

                if (isInsideEventHorizon(tracePos)) {
                    captured = true;
                    break;
                }

                if (traveled >= maxDistance || transmittance <= 0.015) {
                    break;
                }

                float stepLength = min(stepLengthForRadius(radius), maxDistance - traveled);
                vec3 bentDir = bendRay(tracePos, rayDir, stepLength);
                vec3 nextPos = tracePos + 0.5 * (rayDir + bentDir) * stepLength;

                vec3 samplePosBhSpace;
                float localDensity;
                float radialDistance;
                vec3 radialDir;
                if (intersectAccretionDisk(tracePos, nextPos, samplePosBhSpace, localDensity, radialDistance, radialDir)) {
                    integrateDiskVolumeSegment(
                        tracePos,
                        nextPos,
                        bentDir,
                        localDensity,
                        diskRadiance,
                        transmittance,
                        accumulatedOpticalDepth
                    );
                }

                traveled += length(nextPos - tracePos);
                tracePos = nextPos;
                rayDir = bentDir;

                bool exitedInfluenceSphere = length(tracePos) > effectRadiusWorld && dot(tracePos, rayDir) > 0.0;
                if (exitedInfluenceSphere) {
                    break;
                }
            }

            float photonSphereRadius = 1.5 * schwarzschildRadius;
            float shadowCore = 1.0 - smoothstep(
                photonSphereRadius * 0.97,
                photonSphereRadius + schwarzschildRadius * 1.55,
                minRadius
            );
            horizonMask = max(captured ? 1.0 : 0.0, shadowCore);

            vec2 lensedUv = sampleLensedBackground(rayDir, currentUv);
            uvOffset = lensedUv - currentUv;
            float deflection = length(uvOffset);

            diskOpacity = saturate(1.0 - exp(-accumulatedOpticalDepth * 2.15));
            diskOpacity = max(diskOpacity, saturate(luminance(diskRadiance) * 0.22));
            emissionWeight = saturate(luminance(diskRadiance) * 0.16);
            float gravityField = 1.0 - smoothstep(
                diskOuterRadius + schwarzschildRadius * 1.3,
                effectRadiusWorld,
                minRadius
            );
            coverage = clamp(
                deflection * 28.0
                    + gravityField * 0.74
                    + emissionWeight * 0.58
                    + diskOpacity * 0.76
                    + horizonMask * 0.95,
                0.0,
                1.0
            );
        }
    }

    if (
        coverage < 1.0e-3 &&
        emissionWeight < 1.0e-3 &&
        horizonMask < 1.0e-3 &&
        diskOpacity < 1.0e-3 &&
        sceneLightWeight < 1.0e-3
    ) {
        discard;
    }

    GlowMask = vec4(diskRadiance, diskOpacity);
    DistortionMask = vec4(uvOffset, horizonMask, coverage);
    SceneLightMask = vec4(sceneLight, sceneLightWeight);
}
