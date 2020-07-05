package com.example.cameraonedemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraContext {

    private static final String TAG = "CameraContext";

    private Context context;
    private Camera camera;
    private Camera.Parameters parameters;
    private CameraInfo currCameraInfo;
    private MyOrientationEventListener orientationEventListener;
    private SurfaceHolder surfaceHolder;

    // for video
    private MediaRecorder mediaRecorder;
    private volatile boolean isRecording;
    private File videoFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4");


    private int displayOrientation;
    private int rotation;

    private Camera.AutoFocusMoveCallback cafCallback = new Camera.AutoFocusMoveCallback() {
        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            Toast.makeText(context,
                    "focus " + (start ? "start" : "end"), Toast.LENGTH_SHORT).show();
        }
    };

    private Camera.AutoFocusCallback afCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Toast.makeText(context,
                    "focus " + (success ? "success" : "fail"), Toast.LENGTH_SHORT).show();
        }
    };

    public CameraContext(Context context) {
        this.context = context;
        orientationEventListener = new MyOrientationEventListener(context);
    }

    public void configSurfaceHolder(SurfaceHolder holder) {
        surfaceHolder = holder;
    }

    public void resume() {
        orientationEventListener.enable();
    }

    public void pause() {
        orientationEventListener.disable();
    }


    public void openCamera(int type) {
        currCameraInfo = new CameraInfo(type);
        int cameraId = currCameraInfo.getCameraId();
        camera = Camera.open(cameraId);
        parameters = camera.getParameters();
        List<Camera.Size> previewSizeList = parameters.getSupportedPreviewSizes();
        for (Camera.Size size: previewSizeList) {
            Log.d(TAG, "preview size w " + size.width + ", h = " + size.height);
        }

        List<Camera.Size> pictureSizeList = parameters.getSupportedPictureSizes();
        for (Camera.Size size: pictureSizeList) {
            Log.d(TAG, "picture size w " + size.width + ", h = " + size.height);
        }

        List<Camera.Size> videoSizeList = parameters.getSupportedVideoSizes();
        for (Camera.Size size: pictureSizeList) {
            Log.d(TAG, "video size w " + size.width + ", h = " + size.height);
        }
        parameters.setPreviewSize(1920, 1080);
        parameters.setPictureSize(800, 600);

        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setAutoFocusMoveCallback(cafCallback);
        }
        camera.setParameters(parameters);

        displayOrientation = 0;
        try {
            camera.setPreviewDisplay(surfaceHolder);
            displayOrientation = CameraUtils.getCameraDisplayOrientation((Activity) context, cameraId);
            camera.setDisplayOrientation(displayOrientation);
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.startPreview();
        Log.d(TAG, "openCamera: " + currCameraInfo);
    }

    public void capture(final PictureCallback callback) {
        if (camera != null) {
            parameters.setRotation(rotation);
            camera.setParameters(parameters);
            camera.takePicture(new Camera.ShutterCallback() {
                /**
                 * Called as near as possible to the moment when a photo is captured
                 * from the sensor.  This is a good opportunity to play a shutter sound
                 * or give other feedback of camera operation.  This may be some time
                 * after the photo was triggered, but some time before the actual data
                 * is available.
                 */
                @Override
                public void onShutter() {
                    Log.d(TAG, "onShutter: ");
                }
            }, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    // raw data
                    Log.d(TAG, "onPictureTaken: raw");
                }
            }, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    camera.startPreview();

                    Log.d(TAG, "onPictureTaken");
                    if (callback != null) {
                        callback.onPictureTaken(data, camera);
                    }
                }
            });
        }
    }

    public void switchCamera(int type) {
        closeCamera();
        openCamera(type);
    }

    public void cancelAutoFocus() {
        if (camera != null) {
            camera.cancelAutoFocus();
            Log.d(TAG, "cancelAutoFocus: ");
        }
    }

    public void enableCaf() {
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setAutoFocusMoveCallback(cafCallback);
            camera.setParameters(parameters);
            Log.d(TAG, "enableCaf: ");
        }
    }

    public void onTouchAF(int x, int y,
                          int focusW, int focusH,
                          int previewW, int previewH,
                          boolean isMirror) {
        // meter
        int maxNumMeteringAreas = parameters.getMaxNumMeteringAreas();
        if (maxNumMeteringAreas > 0) {
            Rect tapRect = CameraUtils.calculateTapArea(
                    focusW, focusH, x, y,
                    previewW, previewH, displayOrientation, 1.0f, isMirror);
            List<Camera.Area> meteringAreas = new ArrayList<>();
            Camera.Area area = new Camera.Area(tapRect, 1000);
            meteringAreas.add(area);
            parameters.setMeteringAreas(meteringAreas);
            camera.setParameters(parameters);
        }

        // focus
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            camera.cancelAutoFocus();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            int maxNumFocusAreas = parameters.getMaxNumFocusAreas();
            if (maxNumFocusAreas > 0) {
                List<Camera.Area> areas = new ArrayList<>();
                Rect tapRect = CameraUtils.calculateTapArea(
                        focusW, focusH, x, y,
                        previewW, previewH, displayOrientation,1.5f, isMirror);
                Camera.Area area = new Camera.Area(tapRect, 1000);
                areas.add(area);
                parameters.setFocusAreas(areas);
            }
            camera.setParameters(parameters);
            camera.autoFocus(afCallback);
            camera.setAutoFocusMoveCallback(null);
        }
    }

    public void closeCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void startRecord() {
        isRecording = true;
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.reset();

        // config media recorder start
        camera.unlock();
        mediaRecorder.setOrientationHint(rotation);
        mediaRecorder.setCamera(camera);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        CamcorderProfile profile = CamcorderProfile.get(
                currCameraInfo.getCameraId(), CamcorderProfile.QUALITY_480P);
        Log.e(TAG, "startRecord: audioCodec = " + profile.audioCodec
                    + ", videoCodec = " + profile.videoCodec
                    + ", videoBitRate = " + profile.videoBitRate
                    + ", videoFrameRate = " + profile.videoFrameRate
                    + ", w = " + profile.videoFrameWidth
                    + ", h = " + profile.videoFrameHeight
                    + ", outputFormat = " + profile.fileFormat);

        mediaRecorder.setOutputFormat(profile.fileFormat);
        mediaRecorder.setAudioEncoder(profile.audioCodec);
        mediaRecorder.setVideoEncoder(profile.videoCodec);
        mediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mediaRecorder.setVideoFrameRate(profile.videoFrameRate);

        if (videoFile.exists()) {
            boolean delete = videoFile.delete();
            Log.d(TAG, "startRecord: delete last file: " + delete);
        }

        Log.d(TAG, "startRecord: file = " + videoFile);
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
        mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                Log.d(TAG, "onError: what = " + what + ", extra = " + extra);
                mediaRecorder.stop();
                mediaRecorder.reset();

                camera.startPreview();
            }
        });
        // config media recorder end

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaRecorder.start();

        Log.d(TAG, "startRecord: ");
    }

    public void stopRecord() {
        isRecording = false;
        Log.d(TAG, "stopRecord: ");
        mediaRecorder.stop();
        mediaRecorder.reset();

        camera.startPreview();
        enableCaf();
        Toast.makeText(context, "video save path = " + videoFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private class MyOrientationEventListener extends OrientationEventListener {

        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == ORIENTATION_UNKNOWN) return;
            orientation = (orientation + 45) / 90 * 90;
            int degrees;
            if (currCameraInfo.getFacing() == CameraInfo.CAMERA_FACING_FRONT) {
                degrees = (currCameraInfo.getPictureNeedRotateOrientation() - orientation + 360) % 360;
            } else { // back-facing camera
                degrees = (currCameraInfo.getPictureNeedRotateOrientation() + orientation) % 360;
            }

            if (rotation != degrees) {
                rotation = degrees;
                Log.d(TAG, "onOrientationChanged: rotation = " + rotation + ", orientation = " + orientation);
            }
        }
    }

    public interface PictureCallback {
        void onPictureTaken(byte[] data, Camera camera);
    }
}
