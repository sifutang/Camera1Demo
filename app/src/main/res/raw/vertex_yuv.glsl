attribute vec4 aPosition;
attribute vec2 aTexCoord;

uniform mat4 matrix;

varying vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = matrix * aPosition;
}
