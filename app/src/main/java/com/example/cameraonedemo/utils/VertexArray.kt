package com.example.cameraonedemo.utils

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VertexArray(vertexData: FloatArray) {

    private var floatBuffer: FloatBuffer =
            ByteBuffer.allocateDirect(vertexData.size * Constants.BYTES_PRE_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    init {
        floatBuffer.put(vertexData)
    }

    fun setVertexAttributePointer(dataOffset: Int,
                                  attributeLocation: Int,
                               componentCount: Int,
                                  stride: Int) {
        floatBuffer.position(dataOffset)
        GLES20.glVertexAttribPointer(attributeLocation,
                componentCount, GLES20.GL_FLOAT, false, stride, floatBuffer)
        GLES20.glEnableVertexAttribArray(attributeLocation)
        floatBuffer.position(0)
    }

    fun updateBuffer(vertexData: FloatArray?, start: Int, count: Int) {
        floatBuffer.position(start)
        floatBuffer.put(vertexData, start, count)
        floatBuffer.position(0)
    }

    fun disableVertexAttributeArray(attributeLoc: Int) {
        GLES20.glDisableVertexAttribArray(attributeLoc)
    }
}