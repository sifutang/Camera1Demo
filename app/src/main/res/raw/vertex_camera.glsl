attribute vec4 a_Position;
attribute vec4 a_TextureCoordinate;

uniform mat4 u_TextureMatrix;

varying vec2 vTextureCoord;

void main() {
    vTextureCoord = (u_TextureMatrix * a_TextureCoordinate).xy;
    gl_Position = a_Position;
}
