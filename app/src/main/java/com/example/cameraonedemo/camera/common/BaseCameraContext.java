package com.example.cameraonedemo.camera.common;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;

import com.example.cameraonedemo.base.OnCameraInfoListener;
import com.example.cameraonedemo.camera.api1.CameraInfo;
import com.example.cameraonedemo.model.ModeItem;

import java.io.File;

public class BaseCameraContext implements ICameraContext {
    private static final String TAG = "BaseCameraContext";

    public static final String FLASH_MODE_OFF = "Flash Off";
    public static final String FLASH_MODE_AUTO = "Flash Auto";
    public static final String FLASH_MODE_ON = "Flash On";
    public static final String FLASH_MODE_TORCH = "Flash Torch";

    protected OnCameraInfoListener mOnCameraInfoListener;

    protected int displayOrientation = 0;
    protected int cameraDisplayOrientation = 90;
    protected File mVideoFile;
    protected SurfaceHolder mSurfaceHolder;

    protected int previewWidth = -1;
    protected int previewHeight = -1;

    private MyOrientationEventListener orientationEventListener;
    private class MyOrientationEventListener extends OrientationEventListener {

        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == ORIENTATION_UNKNOWN) return;
            int temp = (orientation + 45) / 90 * 90;
            if (temp != displayOrientation) {
                displayOrientation = temp;
                Log.d(TAG, "onOrientationChanged: displayOrientation = " + displayOrientation);
            }
        }
    }

    public interface PictureCallback {
        void onPictureTaken(byte[] data, int jpegRotation);
    }

    public interface PreviewCallback {
        void onPreviewFrame(byte[] data);
    }

    public interface FocusStatusCallback {
        void onAutoFocus(boolean success);
        void onAutoFocusMoving(boolean start);
    }

    public interface FaceDetectionListener {
        void onFaceDetection(Rect[] faces);
    }

    public BaseCameraContext(Context context) {
        mVideoFile = new File(context.getFilesDir().getAbsoluteFile() + "/test.mp4");
        orientationEventListener = new MyOrientationEventListener(context);
    }

    @Override
    public void setOnCameraInfoListener(OnCameraInfoListener onCameraInfoListener) {
        mOnCameraInfoListener = onCameraInfoListener;
    }

    @Override
    public void init() {

    }

    @Override
    public void resume() {
        orientationEventListener.enable();
    }

    @Override
    public void pause() {
        orientationEventListener.disable();
    }

    @Override
    public void release() {

    }

    @Override
    public void onCameraModeChanged(ModeItem modeItem) {

    }

    @Override
    public void configSurfaceHolder(SurfaceHolder surfaceHolder) {
        mSurfaceHolder = surfaceHolder;
    }

    @Override
    public void openCamera() {

    }

    @Override
    public void switchCamera() {

    }

    @Override
    public void switchFlashMode(String flash) {

    }

    @Override
    public void onTouchAF(float x, float y, int focusW, int focusH, int viewW, int viewH, boolean isMirror) {

    }

    @Override
    public void setFocusStatusCallback(FocusStatusCallback focusStatusCallback) {

    }

    @Override
    public void setFaceDetectionListener(FaceDetectionListener faceDetectionListener) {

    }

    @Override
    public boolean isFront() {
        return false;
    }

    @Override
    public int onExposureChanged(boolean isDown) {
        return 0;
    }

    @Override
    public void setAeLock() {

    }

    @Override
    public void capture(PictureCallback pictureCallback) {

    }

    @Override
    public boolean isRecording() {
        return false;
    }

    @Override
    public void startRecord() {

    }

    @Override
    public void stopRecord() {

    }

    @Override
    public int getPreviewWidth() {
        return previewWidth;
    }

    @Override
    public int getPreviewHeight() {
        return previewHeight;
    }

    @Override
    public void setPreviewCallback(PreviewCallback previewCallback) {

    }

    @Override
    public void cancelAutoFocus() {

    }

    @Override
    public void enableCaf() {

    }

    @Override
    public int getDisplayOrientation() {
        return cameraDisplayOrientation;
    }

    @Override
    public void zoom(float scaleFactor) {

    }
}
