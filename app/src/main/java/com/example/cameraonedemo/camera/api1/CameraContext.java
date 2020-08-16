package com.example.cameraonedemo.camera.api1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;

import com.example.cameraonedemo.camera.common.BaseCameraContext;
import com.example.cameraonedemo.utils.CameraUtils;
import com.example.cameraonedemo.utils.PerformanceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraContext extends BaseCameraContext {

    private static final String TAG = "CameraContext";

    private Context context;
    private Camera camera;
    private Camera.Parameters parameters;
    private CameraInfo currCameraInfo;
    private SurfaceHolder surfaceHolder;

    // for video
    private MediaRecorder mediaRecorder;
    private volatile boolean isRecording = false;
    private File videoFile =
            new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4");


    private boolean isFaceDetectStarted = false;
    private int displayOrientation;
    private int rotation;
    private int previewWidth = 1920;
    private int previewHeight = 1080;

    private String currentFlashMode = FLASH_MODE_OFF;

    private PreviewCallback callback;
    private FocusStatusCallback mFocusStatusCallback;
    private FaceDetectionListener mFaceDetectionListener;

    private Camera.AutoFocusMoveCallback cafCallback = new Camera.AutoFocusMoveCallback() {
        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            if (mFocusStatusCallback != null) {
                mFocusStatusCallback.onAutoFocusMoving(start);
            }
        }
    };

    private Camera.AutoFocusCallback afCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (mFocusStatusCallback != null) {
                mFocusStatusCallback.onAutoFocus(success);
            }
        }
    };

    public CameraContext(Context context) {
        super(context);
        this.context = context;
    }

    public void configSurfaceHolder(SurfaceHolder holder) {
        surfaceHolder = holder;
    }

    public void setPreviewCallback(PreviewCallback callback) {
        this.callback = callback;
    }

    public void setFocusStatusCallback(FocusStatusCallback callback) {
        mFocusStatusCallback = callback;
    }

    public void setFaceDetectionListener(FaceDetectionListener listener) {
        mFaceDetectionListener = listener;
    }

    public void resume() {
        super.resume();
    }

    public void pause() {
        super.pause();
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public void openCamera(int type) {
        currCameraInfo = new CameraInfo(type);
        final int cameraId = currCameraInfo.getCameraId();
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
        parameters.setPreviewSize(previewWidth, previewHeight);
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

        Log.d(TAG, "openCamera: " + currCameraInfo);
        PerformanceUtil.getInstance().logTraceStart("startPreview");
        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (callback != null) {
                    callback.onPreviewFrame(data);
                }
            }
        });

        camera.startPreview();
        long consume = PerformanceUtil.getInstance().logTraceEnd("startPreview");
        Log.d(TAG, "openCamera: start preview consume = " + consume);
        int maxNumDetectedFaces = parameters.getMaxNumDetectedFaces();
        Log.d(TAG, "openCamera: maxNumDetectedFaces = " + maxNumDetectedFaces);
        boolean isSupportFaceDetected = maxNumDetectedFaces > 0;
        if (isSupportFaceDetected) {
            isFaceDetectStarted = true;
            camera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
                @Override
                public void onFaceDetection(Camera.Face[] faces, Camera camera) {
                    if (faces == null || faces.length == 0) {
                        Log.i(TAG, "onFaceDetection: no face ");
                        if (mFaceDetectionListener != null) {
                            mFaceDetectionListener.onFaceDetection(null);
                        }
                        return;
                    }

                    if (mFaceDetectionListener != null) {
                        Rect[] rectArr = new Rect[faces.length];
                        for (int i = 0; i < faces.length; i++) {
                            Rect rect = faces[i].rect;
                            Log.d(TAG, "onFaceDetection: " + rect);
                            rectArr[i] = rect;
                        }
                        mFaceDetectionListener.onFaceDetection(rectArr);
                    }
                }
            });
            camera.startFaceDetection();
        }
    }

    public void capture(final PictureCallback callback) {
        if (camera != null) {
            rotation = getCaptureRotation(displayOrientation);
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
                        callback.onPictureTaken(data);
                    }
                }
            });
        }
    }

    private int getCaptureRotation(int displayOrientation) {
        int degrees;
        if (currCameraInfo.getFacing() == CameraInfo.CAMERA_FACING_FRONT) {
            degrees = (currCameraInfo.getPictureNeedRotateOrientation() - displayOrientation + 360) % 360;
        } else { // back-facing camera
            degrees = (currCameraInfo.getPictureNeedRotateOrientation() + displayOrientation) % 360;
        }

        return degrees;
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

    public void onTouchAF(float x, float y,
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
            if (isFaceDetectStarted) {
                camera.setFaceDetectionListener(null);
                camera.stopFaceDetection();
            }
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isFront() {
        return currCameraInfo.isFront();
    }

    public int getDisplayOrientation() {
        return displayOrientation;
    }

    public void startRecord() {
        isRecording = true;
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.reset();

        // config media recorder start
        camera.unlock();
        rotation = getCaptureRotation(displayOrientation);
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
    }

    public void switchFlashMode(String flashMode) {
        if (flashMode == null || parameters == null) {
            return;
        }

        currentFlashMode = flashMode;
        updateFlashMode(parameters, flashMode);
        updatePreview(parameters);
    }

    private void updateFlashMode(Camera.Parameters parameters, String flashMode) {
        if (parameters != null && flashMode != null) {
            List<String> supportFlashMode = parameters.getSupportedFlashModes();
            Log.d(TAG, "updateFlashMode: supportFlashMode = " + supportFlashMode);
            switch (flashMode) {
                case FLASH_MODE_AUTO:
                    if (supportFlashMode.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                    }
                    break;
                case FLASH_MODE_OFF:
                    if (supportFlashMode.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    }
                    break;
                case FLASH_MODE_ON:
                    if (supportFlashMode.contains(Camera.Parameters.FLASH_MODE_ON)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    }
                    break;
                case FLASH_MODE_TORCH:
                    if (supportFlashMode.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void updatePreview(Camera.Parameters parameters) {
        if (camera != null) {
            camera.setParameters(parameters);
        }
    }
}
