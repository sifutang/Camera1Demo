package com.example.cameraonedemo.camera.api1;

import android.hardware.Camera;

public class CameraInfo {

    /**
     * The facing of the camera is opposite to that of the screen.
     */
    public static final int CAMERA_FACING_BACK = 0;

    /**
     * The facing of the camera is the same as that of the screen.
     */
    public static final int CAMERA_FACING_FRONT = 1;

    private Camera.CameraInfo cameraInfo;
    private int cameraId;

    public CameraInfo(int facing) {
        int cameraNum = Camera.getNumberOfCameras();
        int id = -1;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraNum; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) {
                id = i;
                break;
            }
        }

        if (id == -1) {
            throw new IllegalStateException("Can't find camera id, facing = " + facing);
        }

        cameraId = id;
        cameraInfo = info;
    }

    public int getCameraId() {
        return cameraId;
    }

    public int getFacing() {
        return cameraInfo.facing;
    }

    public int getPictureNeedRotateOrientation() {
        return cameraInfo.orientation;
    }

    public boolean canDisableShutterSound() {
        return cameraInfo.canDisableShutterSound;
    }

    @Override
    public String toString() {
        return "CameraInfo{" +
                "cameraId=" + cameraId +
                ", facing=" + cameraInfo.facing +
                ", orientation=" + cameraInfo.orientation +
                ", canDisableShutterSound=" + cameraInfo.canDisableShutterSound +
                '}';
    }
}
