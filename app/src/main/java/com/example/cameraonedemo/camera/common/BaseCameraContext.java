package com.example.cameraonedemo.camera.common;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.OrientationEventListener;

public class BaseCameraContext {
    private static final String TAG = "BaseCameraContext";

    public static final String FLASH_MODE_OFF = "Flash Off";
    public static final String FLASH_MODE_AUTO = "Flash Auto";
    public static final String FLASH_MODE_ON = "Flash On";
    public static final String FLASH_MODE_TORCH = "Flash Torch";

    protected int displayOrientation = 0;
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
        orientationEventListener = new MyOrientationEventListener(context);
    }

    public void resume() {
        orientationEventListener.enable();
    }

    public void pause() {
        orientationEventListener.disable();
    }
}
