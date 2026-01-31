#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texSample = texture(Sampler0, texCoord0);

    if (texSample.a < 0.1) {
        discard;
    }

    vec4 outColor = vec4(vertexColor.rgb, vertexColor.a * texSample.a);

    fragColor = outColor * ColorModulator;
}