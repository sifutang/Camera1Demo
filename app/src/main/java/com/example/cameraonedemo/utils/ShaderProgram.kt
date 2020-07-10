package com.example.cameraonedemo.utils

import android.content.Context
import android.opengl.GLES20

open class ShaderProgram(context: Context,
                         vertexShaderResId: Int,
                         fragmentShaderResId: Int) {

    var programId = -1
    init {
        // Compile the shader and link the program
        programId = ShaderHelper.buildProgram(
                TextResourceReader.readTextFileFromResource(context, vertexShaderResId),
                TextResourceReader.readTextFileFromResource(context, fragmentShaderResId)
        )
    }

    fun useProgram() {
        // Set the current OpenGL shader program to this program
        GLES20.glUseProgram(programId)
    }
}