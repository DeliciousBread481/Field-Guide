#version 150

#moj_import <minecraft:projection.glsl>

uniform mat4 ModelViewMat;

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out vec4 vertexColor;
out vec2 texCoord0;
out float viewRelY;
out float scanLimitY;
out vec4 scanColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    texCoord0 = UV0;

    viewRelY = Position.y;

    int rawLimit = UV2.x;
    if (rawLimit > 32767) {
        rawLimit -= 65536;
    }
    scanLimitY = float(rawLimit) / 100.0;

    int c = UV2.y;
    float r = float((c >> 12) & 15) / 15.0;
    float g = float((c >> 8) & 15) / 15.0;
    float b = float((c >> 4) & 15) / 15.0;
    float a = float(c & 15) / 15.0;

    scanColor = vec4(r, g, b, a);
}