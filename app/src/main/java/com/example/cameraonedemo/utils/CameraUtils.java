package com.example.cameraonedemo.utils;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

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
                                        float x, float y,
                                        int previewW, int previewH,
                                        int deviceDisplayOrientation,
                                        float focusAreaFactor,
                                        boolean isMirror) {
        int left = constrain((int) (x - focusW * focusAreaFactor / 2), 0, previewW - focusW);
        int top = constrain((int) (y - focusH * focusAreaFactor / 2), 0, previewH - focusH);
        RectF rectF = new RectF(left, top, left + focusW, top + focusH);
        Matrix driverToUiMatrix = new Matrix();
        // need mirror for front camera
        driverToUiMatrix.setScale(isMirror ? -1 : 1, 1);

        // need rotate for device display orientation
        driverToUiMatrix.postRotate(deviceDisplayOrientation);

        // camera driver coordinates range from left-top(-1000, -1000) to bottom-right(1000, 1000);
        driverToUiMatrix.postScale(previewW / 2000f, previewH / 2000f);

        // ui coordinates range from left-top(0, 0) to bottom-right(w, h)
        driverToUiMatrix.postTranslate(previewW / 2f, previewH / 2f);

        Matrix uiToDriverMatrix = new Matrix();
        driverToUiMatrix.invert(uiToDriverMatrix);
        uiToDriverMatrix.mapRect(rectF);

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

    public static void prepareFaceMatrix(Matrix matrix,
                                         boolean isMirror,
                                         int displayOrientation,
                                         int viewWidth,
                                         int viewHeight) {
        if (matrix == null) {
            throw new NullPointerException("matrix is null");
        }
        // Need mirror for front camera
        matrix.setScale(isMirror ? -1 : 1, 1);
        // Need rotate for device display orientation
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from left-top(-1000, -1000) to bottom-right(1000, 1000);
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

    public static Rect convertToUiFaceRect(Rect sensorFaceRect,
                                         boolean isMirror,
                                         int displayOrientation,
                                         int viewWidth,
                                         int viewHeight) {
        Matrix matrix = new Matrix();
        // Need mirror for front camera
        matrix.setScale(isMirror ? -1 : 1, 1);
        // Need rotate for device display orientation
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from left-top(-1000, -1000) to bottom-right(1000, 1000);
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);

        // rect apply matrix transform
        RectF rectF = new RectF(sensorFaceRect);
        matrix.mapRect(rectF);

        Rect rect = new Rect();
        rect.set(
                Math.round(rectF.left),
                Math.round(rectF.top),
                Math.round(rectF.right),
                Math.round(rectF.bottom)
        );

        return rect;
    }

    /**
     * convert nv21 to nv12
     * nv21: YYYY YYYY vu vu
     * nv12: YYYY YYYY uv uv
     * @param nv21
     * @param width
     * @param height
     */
    public static void nv21ToNv12(byte[] nv21, int width, int height) {
        byte temp;
        for (int i = width * height; i < nv21.length; i++) {
            if ((i + 1) % 2 == 0) {
                temp = nv21[i - 1];
                nv21[i - 1] = nv21[i];
                nv21[i] = temp;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean isSupportAutoFocus(CameraCharacteristics characteristics) {
        if (characteristics == null) {
            return false;
        }

        int[] availableAfModes = characteristics.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
        );
        if (availableAfModes == null) {
            return false;
        }

        boolean result = false;
        loop:
        for (int mode: availableAfModes) {
            switch (mode) {
                case CameraMetadata.CONTROL_AF_MODE_AUTO:
                case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                    result = true;
                    break loop;
                default:
                    break;

            }
        }

        return result;
    }
}
