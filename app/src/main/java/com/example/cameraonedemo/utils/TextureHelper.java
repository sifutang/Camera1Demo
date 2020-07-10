package com.example.cameraonedemo.utils;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import javax.microedition.khronos.opengles.GL10;

@RequiresApi(api = Build.VERSION_CODES.FROYO)
public class TextureHelper {
    private static final String TAG = "TextureHelper";

    public static int createOesTexture() {
        int[] textureObjects = new int[1];
        GLES20.glGenTextures(1, textureObjects, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureObjects[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        Log.d(TAG, "createOESTextureObject: texture id " + textureObjects[0]);
        return textureObjects[0];
    }

    public static int create2DTexture() {
        int[] textureObjects = new int[1];
        GLES20.glGenTextures(1, textureObjects, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureObjects[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        Log.d(TAG, "createOESTextureObject: texture id " + textureObjects[0]);
        return textureObjects[0];
    }

    public static void deleteTexture(int id) {
        if (id <= 0) {
            return;
        }

        int[] textureObjects = new int[1];
        textureObjects[0] = id;
        GLES20.glDeleteTextures(1, textureObjects, 0);
    }
}
