package com.example.cameraonedemo.camera.render

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.example.cameraonedemo.R
import com.example.cameraonedemo.utils.ShaderProgram

class CameraShaderProgram(context: Context) :
        ShaderProgram(context, R.raw.vertex_camera, R.raw.fragment_camera) {

    companion object {
        // Uniform constants.
        private const val POSITION_ATTRIBUTE = "a_Position"
        private const val TEXTURE_COORDINATE_ATTRIBUTE = "a_TextureCoordinate"

        // Attribute constants.
        private const val TEXTURE_MATRIX_UNIFORM = "u_TextureMatrix"
        private const val TEXTURE_SAMPLER_UNIFORM = "u_TextureSampler"
    }

    // Uniform locations
    private var uTextureMatrixLocation = -1
    private var uTextureSamplerLocation = -1

    // Attribute locations
    private var aPositionLocation = -1
    private var aTextureCoordinateLocation = -1

    init {
        // Retrieve attribute locations for the shader program.
        aPositionLocation = GLES20.glGetAttribLocation(programId, POSITION_ATTRIBUTE)
        aTextureCoordinateLocation = GLES20.glGetAttribLocation(programId, TEXTURE_COORDINATE_ATTRIBUTE)

        // Retrieve uniform locations for the shader program
        uTextureMatrixLocation = GLES20.glGetUniformLocation(programId, TEXTURE_MATRIX_UNIFORM)
        uTextureSamplerLocation = GLES20.glGetUniformLocation(programId, TEXTURE_SAMPLER_UNIFORM)
    }

    fun setUniform(matrix: FloatArray, textureId: Int) {
        GLES20.glUniformMatrix4fv(uTextureMatrixLocation,
                1, false, matrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureSamplerLocation, 0)
    }

    fun getPositionAttributeLoc(): Int {
        return aPositionLocation
    }

    fun getTextureCoordinateAttributeLoc(): Int {
        return aTextureCoordinateLocation
    }
}