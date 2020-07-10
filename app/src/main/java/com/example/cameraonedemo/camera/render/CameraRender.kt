package com.example.cameraonedemo.camera.render

import android.content.Context
import android.opengl.GLES20
import com.example.cameraonedemo.utils.Constants
import com.example.cameraonedemo.utils.VertexArray

class CameraRender(context: Context) {

    companion object {
        private val vertexData = floatArrayOf(
                -1f,  1f, 0f, 1f,
                -1f, -1f, 0f, 0f,
                 1f,  1f, 1f, 1f,
                 1f, -1f, 1f, 0f
        )
        const val VERTEX_COMPONENT_COUNT = 2
        const val COORDINATE_COMPONENT_COUNT = 2
        const val STRIDE =
                (VERTEX_COMPONENT_COUNT + COORDINATE_COMPONENT_COUNT) * Constants.BYTES_PRE_FLOAT
    }

    private val vertexArray = VertexArray(vertexData)
    private var cameraShaderProgram = CameraShaderProgram(context)

    fun drawTexture(transformMatrix: FloatArray, oesTextureId: Int) {
        cameraShaderProgram.useProgram()
        cameraShaderProgram.setUniform(transformMatrix, oesTextureId)
        val positionLoc = cameraShaderProgram.getPositionAttributeLoc()
        val coordinateLoc = cameraShaderProgram.getTextureCoordinateAttributeLoc()
        vertexArray.setVertexAttributePointer(
                0, positionLoc, VERTEX_COMPONENT_COUNT, STRIDE
        )
        vertexArray.setVertexAttributePointer(
                2, coordinateLoc, COORDINATE_COMPONENT_COUNT, STRIDE
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        vertexArray.disableVertexAttributeArray(positionLoc)
        vertexArray.disableVertexAttributeArray(coordinateLoc)
    }
}