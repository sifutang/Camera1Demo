package com.example.cameraonedemo.camera.api1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.cameraonedemo.camera.common.BaseCameraContext;
import com.example.cameraonedemo.model.ModeItem;
import com.example.cameraonedemo.utils.CameraUtils;
import com.example.cameraonedemo.utils.Constant;
import com.example.cameraonedemo.utils.PerformanceUtil;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraContext extends BaseCameraContext {

    private static final String TAG = "CameraContext";

    private Context mContext;
    private Camera mCamera;
    private Camera.Parameters mParameters;
    private CameraInfo mCurCameraInfo;
    private int curCameraId = CameraInfo.CAMERA_FACING_BACK;
    private String[] ids = null;

    // for video
    private MediaRecorder mMediaRecorder;
    private volatile boolean mIsRecording = false;

    private boolean isFaceDetectStarted = false;
    private int rotation;

    private PreviewCallback mPreviewCallback;
    private FocusStatusCallback mFocusStatusCallback;
    private FaceDetectionListener mFaceDetectionListener;

    private static final int FRAME_RATE_WINDOW = 30;
    private ArrayDeque<Long> mFrameTimestamps = new ArrayDeque<>(5);

    private Handler mHandler;
    private HandlerThread mHandlerThread;

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
        mContext = context;
    }

    @Override
    public void init() {
        mHandlerThread = new HandlerThread("camera1 thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        int number = Camera.getNumberOfCameras();
        if (number > 0) {
            ids = new String[number];
            for (int i = 0; i < number; i++) {
                ids[i] = String.valueOf(i);
            }
        }
        Log.d(TAG, "init: ");
    }

    @Override
    public void release() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                closeCamera();
                if (mMediaRecorder != null) {
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                }
            }
        });

        if (mHandler != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mHandler = null;
        }
        Log.d(TAG, "release: ");
    }

    @Override
    public void setPreviewCallback(PreviewCallback callback) {
        mPreviewCallback = callback;
    }

    @Override
    public void setFocusStatusCallback(FocusStatusCallback callback) {
        mFocusStatusCallback = callback;
    }

    @Override
    public void setFaceDetectionListener(FaceDetectionListener listener) {
        mFaceDetectionListener = listener;
    }

    @Override
    public int getPreviewHeight() {
        return previewHeight;
    }

    @Override
    public int getPreviewWidth() {
        return previewWidth;
    }

    @Override
    public void openCamera() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                openCameraCore();
            }
        });
    }

    private void openCameraCore() {
        mCurCameraInfo = new CameraInfo();
        mCurCameraInfo.setCameraId(curCameraId);

        mCamera = Camera.open(curCameraId);
        mParameters = mCamera.getParameters();

        // set preview size
        List<Camera.Size> previewSizeList = mParameters.getSupportedPreviewSizes();
        Camera.Size bestPreviewSize = CameraUtils.getBestPreviewCameraSize(mContext, previewSizeList);
        if (bestPreviewSize != null) {
            previewWidth = bestPreviewSize.width;
            previewHeight = bestPreviewSize.height;
            Log.d(TAG, "openCamera: previewWidth = " + previewWidth
                    + ", previewHeight = " + previewHeight);
            mParameters.setPreviewSize(previewWidth, previewHeight);
        } else {
            Log.e(TAG, "openCamera: preview size does't match");
        }

        // set picture size
        List<Camera.Size> pictureSizeList = mParameters.getSupportedPictureSizes();
        Camera.Size bestPictureSize = CameraUtils.getBestPictureSize(pictureSizeList, CameraUtils.RATIO_4_3);
        if (bestPictureSize != null) {
            Log.d(TAG, "openCamera: pictureWidth = " + bestPictureSize.width
                    + ", pictureHeight = " + bestPictureSize.height);
            mParameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);
        } else {
            Log.e(TAG, "openCamera: picture size does't match");
        }

        if (mParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            mCamera.setAutoFocusMoveCallback(cafCallback);
        }
        mCamera.setParameters(mParameters);

        cameraDisplayOrientation = 0;
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            cameraDisplayOrientation = CameraUtils.getCameraDisplayOrientation((Activity) mContext, curCameraId);
            mCamera.setDisplayOrientation(cameraDisplayOrientation);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "openCamera: " + mCurCameraInfo);
        PerformanceUtil.getInstance().logTraceStart("startPreview");
        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                long currentTime = System.currentTimeMillis();
                mFrameTimestamps.push(currentTime);

                while (mFrameTimestamps.size() >= FRAME_RATE_WINDOW) mFrameTimestamps.removeLast();

                Long timestampFirst = mFrameTimestamps.peekFirst();
                timestampFirst = timestampFirst == null ? currentTime : timestampFirst;
                Long timestampLast = mFrameTimestamps.peekLast();
                timestampLast = timestampLast == null ? currentTime : timestampLast;

                int size = Math.max(1, mFrameTimestamps.size());
                double framesPerSecond = 1.0 / ((timestampFirst - timestampLast) / (size * 1.0)) * 1000.0;
                Log.d(TAG, "onPreviewFrame: fps = " + framesPerSecond);
                if (mPreviewCallback != null) {
                    mPreviewCallback.onPreviewFrame(data);
                }
            }
        });

        mCamera.startPreview();
        long consume = PerformanceUtil.getInstance().logTraceEnd("startPreview");
        Log.d(TAG, "openCamera: start preview consume = " + consume);
        int maxNumDetectedFaces = mParameters.getMaxNumDetectedFaces();
        Log.d(TAG, "openCamera: maxNumDetectedFaces = " + maxNumDetectedFaces);
        boolean isSupportFaceDetected = maxNumDetectedFaces > 0;
        if (isSupportFaceDetected) {
            isFaceDetectStarted = true;
            mCamera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
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
            mCamera.startFaceDetection();
        }
    }

    @Override
    public void capture(final PictureCallback callback) {
        if (mCamera != null) {
            Log.e(TAG, "capture: start");
            final long start = System.currentTimeMillis();
            rotation = getCaptureRotation(displayOrientation);
//            parameters.setRotation(rotation);
            mParameters.setExposureCompensation(currentExposureValue);
            mParameters.setAutoExposureLock(true);
            mCamera.setParameters(mParameters);

            final int jpegRotation = rotation;
            mCamera.takePicture(new Camera.ShutterCallback() {
                /**
                 * Called as near as possible to the moment when a photo is captured
                 * from the sensor.  This is a good opportunity to play a shutter sound
                 * or give other feedback of camera operation.  This may be some time
                 * after the photo was triggered, but some time before the actual data
                 * is available.
                 */
                @Override
                public void onShutter() {
                    Log.e(TAG, "onShutter: consume = " + (System.currentTimeMillis() - start));
                }
            }, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    // raw data
                    Log.d(TAG, "capture onPictureTaken: raw");
                }
            }, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    mParameters.setAutoExposureLock(false);
                    camera.setParameters(mParameters);
                    camera.startPreview();

                    Log.e(TAG, "capture onPictureTaken consume = "
                            + (System.currentTimeMillis() - start));
                    if (callback != null) {
                        callback.onPictureTaken(data, jpegRotation);
                    }
                }
            });
        }
    }

    private int getCaptureRotation(int displayOrientation) {
        int degrees;
        if (mCurCameraInfo.getFacing() == CameraInfo.CAMERA_FACING_FRONT) {
            degrees = (mCurCameraInfo.getPictureNeedRotateOrientation() - displayOrientation + 360) % 360;
        } else { // back-facing camera
            degrees = (mCurCameraInfo.getPictureNeedRotateOrientation() + displayOrientation) % 360;
        }

        return degrees;
    }

    @Override
    public void switchCamera() {
        if (ids == null || ids.length == 0) {
            return;
        }

        int index = Arrays.asList(ids).indexOf(String.valueOf(curCameraId));
        if (index >= 0) {
            index = (index + 1) % ids.length;
        }

        curCameraId = Integer.parseInt(ids[index]);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                closeCamera();
                openCamera();
            }
        });
    }

    @Override
    public void cancelAutoFocus() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    mCamera.cancelAutoFocus();
                    Log.d(TAG, "cancelAutoFocus: ");
                }
            }
        });
    }

    @Override
    public void enableCaf() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    mCamera.setAutoFocusMoveCallback(cafCallback);
                    mCamera.setParameters(mParameters);
                    Log.d(TAG, "enableCaf: ");
                }
            }
        });
    }

    @Override
    public void onTouchAF(float x, float y,
                          int focusW, int focusH,
                          int previewW, int previewH,
                          boolean isMirror) {
        // meter
        int maxNumMeteringAreas = mParameters.getMaxNumMeteringAreas();
        if (maxNumMeteringAreas > 0) {
            Rect tapRect = CameraUtils.calculateTapArea(
                    focusW, focusH, x, y,
                    previewW, previewH, cameraDisplayOrientation, 1.0f, isMirror);
            List<Camera.Area> meteringAreas = new ArrayList<>();
            Camera.Area area = new Camera.Area(tapRect, 1000);
            meteringAreas.add(area);
            mParameters.setMeteringAreas(meteringAreas);
            mCamera.setParameters(mParameters);
        }

        // focus
        if (mParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mCamera.cancelAutoFocus();
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            int maxNumFocusAreas = mParameters.getMaxNumFocusAreas();
            if (maxNumFocusAreas > 0) {
                List<Camera.Area> areas = new ArrayList<>();
                Rect tapRect = CameraUtils.calculateTapArea(
                        focusW, focusH, x, y,
                        previewW, previewH, cameraDisplayOrientation,1.5f, isMirror);
                Camera.Area area = new Camera.Area(tapRect, 1000);
                areas.add(area);
                mParameters.setFocusAreas(areas);
            }
            mCamera.setParameters(mParameters);
            mCamera.autoFocus(afCallback);
            mCamera.setAutoFocusMoveCallback(null);
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            if (isFaceDetectStarted) {
                mCamera.setFaceDetectionListener(null);
                mCamera.stopFaceDetection();
            }
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public boolean isRecording() {
        return mIsRecording;
    }

    @Override
    public boolean isFront() {
        return mCurCameraInfo.isFront();
    }

    @Override
    public int getDisplayOrientation() {
        return cameraDisplayOrientation;
    }

    @Override
    public void startRecord() {
        mIsRecording = true;
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.reset();

        // config media recorder start
        mCamera.unlock();
        rotation = getCaptureRotation(displayOrientation);
        mMediaRecorder.setOrientationHint(rotation);
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        CamcorderProfile profile = CamcorderProfile.get(
                mCurCameraInfo.getCameraId(), CamcorderProfile.QUALITY_480P);
        Log.e(TAG, "startRecord: audioCodec = " + profile.audioCodec
                    + ", videoCodec = " + profile.videoCodec
                    + ", videoBitRate = " + profile.videoBitRate
                    + ", videoFrameRate = " + profile.videoFrameRate
                    + ", w = " + profile.videoFrameWidth
                    + ", h = " + profile.videoFrameHeight
                    + ", outputFormat = " + profile.fileFormat);

        mMediaRecorder.setOutputFormat(profile.fileFormat);
        mMediaRecorder.setAudioEncoder(profile.audioCodec);
        mMediaRecorder.setVideoEncoder(profile.videoCodec);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);

        if (mVideoFile.exists()) {
            boolean delete = mVideoFile.delete();
            Log.d(TAG, "startRecord: delete last file: " + delete);
        }

        Log.d(TAG, "startRecord: file = " + mVideoFile);
        mMediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                Log.d(TAG, "onError: what = " + what + ", extra = " + extra);
                mMediaRecorder.stop();
                mMediaRecorder.reset();

                mCamera.startPreview();
            }
        });
        // config media recorder end

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaRecorder.start();

        Log.d(TAG, "startRecord: ");
    }

    @Override
    public void stopRecord() {
        mIsRecording = false;
        Log.d(TAG, "stopRecord: ");
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        mCamera.startPreview();
        enableCaf();
    }

    @Override
    public void switchFlashMode(String flashMode) {
        if (flashMode == null || mParameters == null) {
            return;
        }

        updateFlashMode(mParameters, flashMode);
        updatePreview(mParameters);
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
        if (mCamera != null) {
            mCamera.setParameters(parameters);
        }
    }

    private int currentExposureValue = 0;
    @Override
    public int onExposureChanged(boolean isDown) {
        Log.d(TAG, "onExposureChanged: " + isDown);
        if (mParameters == null) {
            return -1;
        }

        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (max == min && min == 0) {
            Log.e(TAG, "onExposureChanged: not support");
            return -1;
        }

        int ec = mParameters.getExposureCompensation();
        float step = mParameters.getExposureCompensationStep();

        Log.d(TAG, "onExposureChanged: ex = " + ec + ", step = " + step + ", max = " + max + ", min = " + min);

        int diff = (int) (max * step);
        int exposureValue = currentExposureValue;
        if (isDown) {
            exposureValue -= diff;
            exposureValue = Math.max(exposureValue, min);
        } else {
            exposureValue += diff;
            exposureValue = Math.min(exposureValue, max);
        }
        if (exposureValue == currentExposureValue) {
            return exposureValue;
        }

        currentExposureValue = exposureValue;
        mParameters.setExposureCompensation(currentExposureValue);
        mCamera.setParameters(mParameters);
        return exposureValue;
    }

    private boolean isAeLock = false;
    @Override
    public void setAeLock() {
        isAeLock = !isAeLock;
        Log.d(TAG, "setAeLock: " + isAeLock);
    }

    @Override
    public void onCameraModeChanged(ModeItem modeItem) {
        super.onCameraModeChanged(modeItem);
        Log.d(TAG, "onCameraModeChanged: " + modeItem);
        switch (modeItem.getId()) {
            case Constant.MODE_ID_CAPTURE:
                break;
            case Constant.MODE_ID_RECORD:
                break;
            default:
                break;
        }
    }
}
