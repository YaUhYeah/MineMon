#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform float u_time;
varying vec2 v_texCoord;

void main() {
    vec2 coord = v_texCoord;
    coord.x += sin(coord.y * 4.0 + u_time) * 0.01;
    vec4 color = texture2D(u_texture, coord);
    gl_FragColor = color;
}