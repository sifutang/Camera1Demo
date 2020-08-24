package com.example.cameraonedemo.camera.common;

import android.view.SurfaceHolder;

import com.example.cameraonedemo.base.OnCameraInfoListener;
import com.example.cameraonedemo.model.ModeItem;

public interface ICameraContext {

    void init();

    void resume();

    void pause();

    void release();

    void onCameraModeChanged(ModeItem modeItem);

    void configSurfaceHolder(SurfaceHolder surfaceHolder);

    void openCamera();

    void switchCamera();

    void switchFlashMode(String flash);

    void onTouchAF(float x, float y,
                   int focusW, int focusH,
                   int viewW, int viewH,
                   boolean isMirror);

    void setFocusStatusCallback(BaseCameraContext.FocusStatusCallback focusStatusCallback);

    void setFaceDetectionListener(BaseCameraContext.FaceDetectionListener faceDetectionListener);

    void setOnCameraInfoListener(OnCameraInfoListener listener);

    boolean isFront();

    int onExposureChanged(boolean isDown);

    void setAeLock();

    void capture(BaseCameraContext.PictureCallback pictureCallback);

    boolean isRecording();

    void startRecord();

    void stopRecord();

    int getPreviewWidth();

    int getPreviewHeight();

    void setPreviewCallback(BaseCameraContext.PreviewCallback previewCallback);

    void cancelAutoFocus();

    void enableCaf();

    int getDisplayOrientation();

    void zoom(float scaleFactor);
}
