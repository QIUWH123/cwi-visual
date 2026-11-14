#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseViewProjection;
uniform vec3 CameraOffset;
uniform float Time;
uniform float Strength;
uniform vec3 AshColor;
uniform float MaxDistance;

in vec2 texCoord;

out vec4 fragColor;

// A periodic lattice keeps drifting patches continuous when camera coordinates wrap.
float lattice(vec2 cell) {
    vec2 wrapped = mod(cell, 64.0);
    vec3 seed = fract(vec3(wrapped, wrapped.x + wrapped.y) * vec3(0.127, 0.173, 0.193));
    return fract(dot(seed, seed.yzx + vec3(1.7, 2.3, 3.1)) * 13.7);
}

float patchNoise(vec2 position) {
    vec2 cell = floor(position);
    vec2 part = fract(position);
    vec2 blend = part * part * (3.0 - 2.0 * part);
    float lower = mix(lattice(cell), lattice(cell + vec2(1.0, 0.0)), blend.x);
    float upper = mix(lattice(cell + vec2(0.0, 1.0)), lattice(cell + vec2(1.0)), blend.x);
    return mix(lower, upper, blend.y);
}

void main() {
    float strength = clamp(Strength, 0.0, 1.0);
    if (strength <= 0.0 || MaxDistance <= 2.0) {
        fragColor = vec4(0.0);
        return;
    }

    float depth = texture(DepthSampler, texCoord).r;
    vec4 projected = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 endpoint = abs(projected.w) > 0.000001
            ? projected.xyz / projected.w : normalize(projected.xyz) * MaxDistance;
    float sceneDistance = length(endpoint);
    float distance = min(sceneDistance, MaxDistance);
    if (distance <= 2.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 direction = endpoint / max(sceneDistance, 0.000001);
    float stepLength = (distance - 2.0) / 6.0;
    float opticalDepth = 0.0;
    float heightSum = 0.0;
    // Fixed work per pixel; depth bounds the veil instead of changing terrain visibility.
    for (int sampleIndex = 0; sampleIndex < 6; sampleIndex++) {
        float along = 2.0 + (float(sampleIndex) + 0.5) * stepLength;
        vec3 point = CameraOffset + direction * along;
        float nearPatch = patchNoise((point.xz - vec2(0.08, -0.03) * Time) / 24.0);
        float farPatch = patchNoise((point.xz - vec2(-0.03, 0.05) * Time) / 96.0);
        float patches = 0.35 + 1.15 * smoothstep(0.2, 0.8, nearPatch * 0.68 + farPatch * 0.32);
        float height = max(point.y, 0.0);
        float groundDensity = exp(-height / 10.0);
        float nearbyClearance = smoothstep(2.0, 6.0, along);
        opticalDepth += (0.035 * groundDensity * patches + 0.0012)
                * nearbyClearance * stepLength;
        heightSum += height;
    }

    // Keep some scene contrast for the drifting dust already rendered into the scene.
    float opacity = min(1.0 - exp(-opticalDepth * strength), 0.72 * strength);
    float shade = mix(0.9, 1.0, smoothstep(0.0, 18.0, heightSum / 6.0));
    fragColor = vec4(AshColor * shade, opacity);
}
