attribute vec4 aPosition;
attribute vec4 aTexCoord;

uniform mat4 matrix;

varying vec2 vTexCoord;

void main() {
    vTexCoord = (matrix * aTexCoord).xy;
    gl_Position = aPosition;
}
