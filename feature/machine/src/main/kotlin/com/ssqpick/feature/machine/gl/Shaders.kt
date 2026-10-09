package com.ssqpick.feature.machine.gl

// GLSL ES 3.0 着色器源码。

/**
 * 球的顶点着色器。
 *
 * 使用实例化渲染：一个球网格画 49 个球，每实例带自己的模型矩阵、着色与图集 UV 矩形。
 * 49 个球各带不同号码贴图，因此贴图打进一张 8×8 图集，用 per-instance UV 矩形取样。
 */
const val BALL_VERT = """
#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;

layout(location = 3) in mat4 iModel;
layout(location = 7) in vec3 iTint;
layout(location = 8) in vec4 iAtlasUv;

uniform mat4 uViewProj;

out vec3 vNormalW;
out vec3 vPosW;
out vec2 vUv;

void main() {
    vec4 wp = iModel * vec4(aPosition, 1.0);
    vPosW = wp.xyz;
    vNormalW = mat3(iModel) * aNormal;
    vUv = iAtlasUv.xy + aUv * iAtlasUv.zw;
    gl_Position = uViewProj * wp;
}
"""

const val BALL_FRAG = """
#version 300 es
precision mediump float;

in vec3 vNormalW;
in vec3 vPosW;
in vec2 vUv;

uniform sampler2D uAtlas;
uniform vec3 uCameraPos;

out vec4 fragColor;

void main() {
    vec3 albedo = texture(uAtlas, vUv).rgb;
    vec3 n = normalize(vNormalW);
    vec3 v = normalize(uCameraPos - vPosW);

    // 三点布光：主光 + 补光 + 环境，保证号码牌上的数字任何角度都读得清
    vec3 keyDir = normalize(vec3(0.45, 0.85, 0.35));
    vec3 fillDir = normalize(vec3(-0.6, 0.15, 0.45));

    float key = max(dot(n, keyDir), 0.0);
    float fill = max(dot(n, fillDir), 0.0) * 0.4;

    vec3 color = albedo * (0.32 + key * 0.9 + fill);

    // Blinn-Phong 高光：橡胶球的亮边
    vec3 h = normalize(keyDir + v);
    float spec = pow(max(dot(n, h), 0.0), 42.0) * 0.4;
    color += vec3(spec);

    // 边缘提亮，橡胶质感
    float rim = pow(1.0 - max(dot(n, v), 0.0), 2.5) * 0.16;
    color += vec3(rim);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

/** 鼓壳：半透明、菲涅尔边缘增亮，并叠一层细网格让人看出鼓在转。 */
const val SHELL_VERT = """
#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;

uniform mat4 uModel;
uniform mat4 uViewProj;

out vec3 vNormalW;
out vec3 vPosW;
out vec2 vUv;

void main() {
    vec4 wp = uModel * vec4(aPosition, 1.0);
    vPosW = wp.xyz;
    vNormalW = mat3(uModel) * aNormal;
    vUv = aUv;
    gl_Position = uViewProj * wp;
}
"""

const val SHELL_FRAG = """
#version 300 es
precision mediump float;

in vec3 vNormalW;
in vec3 vPosW;
in vec2 vUv;

uniform vec3 uCameraPos;
uniform vec4 uColor;
uniform float uFresnelPower;
uniform float uGridAmount;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormalW);
    vec3 v = normalize(uCameraPos - vPosW);
    float f = pow(1.0 - abs(dot(n, v)), uFresnelPower);
    vec3 color = uColor.rgb + f * 0.6;
    float grid = step(0.965, fract(vUv.x * 36.0)) + step(0.965, fract(vUv.y * 20.0));
    color += grid * uGridAmount;
    fragColor = vec4(color, clamp(uColor.a + f * 0.3, 0.0, 1.0));
}
"""
