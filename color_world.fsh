#version 150

uniform sampler2D InSampler;

// One value per color group, in the order red, orange, yellow, green, cyan, blue, purple
// (the same order and hue slices as ColorGroups.java):
//   value < 0   the group is still grayscale
//   value >= 0  the group has its color back; value is how far its hue is rotated, in turns
//               (0 = its original colors, anything else = a random color)
uniform float CwG0;
uniform float CwG1;
uniform float CwG2;
uniform float CwG3;
uniform float CwG4;
uniform float CwG5;
uniform float CwG6;

in vec2 texCoord;

out vec4 fragColor;

int hueGroup(float h) {
    if (h >= 15.0 && h < 40.0) return 1;
    if (h >= 40.0 && h < 75.0) return 2;
    if (h >= 75.0 && h < 140.0) return 3;
    if (h >= 140.0 && h < 190.0) return 4;
    if (h >= 190.0 && h < 260.0) return 5;
    if (h >= 260.0 && h < 320.0) return 6;
    return 0;
}

float groupValue(int g) {
    if (g == 1) return CwG1;
    if (g == 2) return CwG2;
    if (g == 3) return CwG3;
    if (g == 4) return CwG4;
    if (g == 5) return CwG5;
    if (g == 6) return CwG6;
    return CwG0;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

void main() {
    vec3 rgb = texture(InSampler, texCoord).rgb;

    float mx = max(rgb.r, max(rgb.g, rgb.b));
    float mn = min(rgb.r, min(rgb.g, rgb.b));
    float chroma = mx - mn;

    // Pure grays have no color to take away or change.
    if (chroma <= 0.0) {
        fragColor = vec4(rgb, 1.0);
        return;
    }

    // Hue in degrees, same formula as ColorGroups.hueOf
    float h;
    if (mx == rgb.r) {
        h = mod((rgb.g - rgb.b) / chroma, 6.0);
    } else if (mx == rgb.g) {
        h = (rgb.b - rgb.r) / chroma + 2.0;
    } else {
        h = (rgb.r - rgb.g) / chroma + 4.0;
    }
    h *= 60.0;

    float value = groupValue(hueGroup(h));

    vec3 result;
    if (value < 0.0) {
        // still grayscale
        result = vec3(dot(rgb, vec3(0.299, 0.587, 0.114)));
    } else if (value < 0.0005) {
        // original colors: leave the pixel exactly as it was
        result = rgb;
    } else {
        // random colors: rotate the hue, keep saturation and brightness
        result = hsv2rgb(vec3(fract(h / 360.0 + value), chroma / mx, mx));
    }
    fragColor = vec4(result, 1.0);
}
