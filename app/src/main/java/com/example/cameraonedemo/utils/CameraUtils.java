package com.example.cameraonedemo.utils;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.view.Surface;

public class CameraUtils {

    public static int getCameraDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            // compensate the mirror
            result = (360 - result) % 360;
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }

    public static Rect calculateTapArea(int focusW, int focusH,
                                        int x, int y,
                                        int previewW, int previewH,
                                        int deviceDisplayOrientation,
                                        float focusAreaFactor,
                                        boolean isMirror) {
        int left = constrain((int) (x - focusW * focusAreaFactor / 2), 0, previewW - focusW);
        int top = constrain((int) (y - focusH * focusAreaFactor / 2), 0, previewH - focusH);
        RectF rectF = new RectF(left, top, left + focusW, top + focusH);
        Matrix matrixDriverToUi = new Matrix();
        // need mirror for front camera
        matrixDriverToUi.setScale(isMirror ? -1 : 1, 1);

        // need rotate for device display orientation
        matrixDriverToUi.postRotate(deviceDisplayOrientation);

        // camera driver coordinates range from left-top(-1000, -1000) to bottom-right(1000, 1000);
        matrixDriverToUi.postScale(previewW / 2000f, previewH / 2000f);

        // ui coordinates range from left-top(0, 0) to bottom-right(w, h)
        matrixDriverToUi.postTranslate(previewW / 2f, previewH / 2f);

        Matrix matrixUiToDriver = new Matrix();
        matrixDriverToUi.invert(matrixUiToDriver);
        matrixUiToDriver.mapRect(rectF);

        Rect rect = new Rect();
        rect.set(
                Math.round(rectF.left),
                Math.round(rectF.top),
                Math.round(rectF.right),
                Math.round(rectF.bottom)
        );
        return rect;
    }

    public static int constrain(int amount, int low, int high) {
        return amount < low ? low : (amount > high ? high : amount);
    }


}
