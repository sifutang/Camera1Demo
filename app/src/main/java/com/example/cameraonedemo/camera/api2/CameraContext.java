package com.example.cameraonedemo.camera.api2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.cameraonedemo.camera.common.BaseCameraContext;
import com.example.cameraonedemo.model.ModeItem;
import com.example.cameraonedemo.utils.CameraUtils;
import com.example.cameraonedemo.utils.Constant;
import com.example.cameraonedemo.utils.CoordinateTransformer;
import com.example.cameraonedemo.utils.ImageReaderManager;
import com.example.cameraonedemo.utils.PerformanceUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.M)
public class CameraContext extends BaseCameraContext {

    private static final String TAG = "CameraContext";
    private static final String CAPTURE_REQUEST_TAG_FOR_SHOT = "CAPTURE_REQUEST_TAG_FOR_SHOT";

    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)
    };

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_WAITING_AE_AF_CONVERGED_FOR_SHOT = 1;

    private HandlerThread handlerThread;
    private Handler handler;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private SurfaceHolder surfaceHolder;
    private Surface codecSurface;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private ImageReader jpegImageReader;
    private Size pictureSize = new Size(1440, 720);
    private CameraCharacteristics mCameraCharacteristics;

    // for video
    private MediaRecorder mediaRecorder;
    private volatile boolean isRecording;

    private int curCameraId = -1;
    private String[] ids;
    private String currentFlashMode = CameraContext.FLASH_MODE_OFF;

    private boolean isAfStateOk = false;
    private boolean isAutoFocusCanDo = false;
    private int status = STATUS_IDLE;

    private PictureCallback pictureCallback;

    // for zoom
    private Rect cropRect = null;
    private float curZoomRatio = 1f;

    private boolean mIsUseTorchModeWhenFlashOnOptional = true;

    private ImageReader.OnImageAvailableListener jpegImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            Log.d(TAG, "onImageAvailable: image = " + image);
            if (image != null) {
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] jpeg = new byte[byteBuffer.capacity()];
                byteBuffer.get(jpeg);
                if (pictureCallback != null) {
                    pictureCallback.onPictureTaken(jpeg, 0);
                    pictureCallback = null;
                }
                image.close();
            }
            resetNormalPreview();
        }
    };

    private CameraCaptureSession.CaptureCallback previewCallback = new CameraCaptureSession.CaptureCallback() {

        private Integer mAfMode = -1;
        private Integer mAfState = -1;
        private Integer mAeMode = -1;
        private Integer mAeState = -1;
        private Integer mFlashMode = -1;
        private Integer mFlashState = -1;
        private Integer mAwbMode = -1;
        private Integer mAwbState = -1;
        private boolean isShotCanDo = false;

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            cropRect = result.get(CaptureResult.SCALER_CROP_REGION);

            Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            Integer aeMode = result.get(CaptureResult.CONTROL_AE_MODE);
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            Integer flashMode = result.get(CaptureResult.FLASH_MODE);
            Integer flashState = result.get(CaptureResult.FLASH_STATE);
            Integer awbMode = result.get(CaptureResult.CONTROL_AWB_MODE);
            Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);

            afMode = afMode != null ? afMode : -1;
            afState = afState != null ? afState : -1;
            aeMode = aeMode != null ? aeMode : -1;
            aeState = aeState != null ? aeState : -1;
            flashMode = flashMode != null ? flashMode : -1;
            flashState = flashState != null ? flashState : -1;
            awbMode = awbMode != null ? awbMode : -1;
            awbState = awbState != null ? awbState : -1;

            if (!mAfMode.equals(afMode) || !mAfState.equals(afState) || !mAeMode.equals(aeMode)
                    || !mAeState.equals(aeState) || !mFlashMode.equals(flashMode) || !mFlashState.equals(flashState)
                    || !mAwbMode.equals(awbMode) || !mAwbState.equals(awbState)) {
                Log.i(TAG, "[afMode=" + afMode + ", afState=" + afState
                        + ",aeMode=" + aeMode + ", aeState=" + aeState
                        + ",flashMode=" + flashMode + ", flashState=" + flashState
                        + ",awbMode = " + awbMode + ", awbState= " + awbState
                        + "]");
            }

            mAfMode = afMode;
            mAfState = afState;
            mAeMode = aeMode;
            mAeState = aeState;
            mFlashMode = flashMode;
            mFlashState = flashState;
            mAwbMode = awbMode;
            mAwbState = awbState;

            boolean afStateOk = afState == -1 ||
                    afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                    afState == CaptureRequest.CONTROL_AF_STATE_PASSIVE_FOCUSED;

            if (afStateOk != isAfStateOk) {
                isAfStateOk = afStateOk;
                Log.d(TAG, "onCaptureCompleted: af state = " + isAfStateOk);
            }

            if (status == STATUS_WAITING_AE_AF_CONVERGED_FOR_SHOT) {
                if (CAPTURE_REQUEST_TAG_FOR_SHOT.equals(request.getTag())) {
                    isShotCanDo = true;
                    Log.e(TAG, "onCaptureCompleted: is shot can do"  + ", request tag = " + request.getTag());
                }

                if (!isShotCanDo) {
                    Log.i(TAG, "onCaptureCompleted: discard previous callback");
                    return;
                }

                if (isAfStateOk) {
                    boolean isAeStateOk = aeState == -1 ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED;

                    if (isAeStateOk){
                        // do capture
                        long consume = PerformanceUtil.getInstance().logTraceEnd("send-capture-command");
                        Log.e(TAG, "run: send-capture-command consume = " + consume);
                        doCapture();
                        status = STATUS_IDLE;
                        isShotCanDo = false;
                    }
                }
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure failure) {
            if (status == STATUS_WAITING_AE_AF_CONVERGED_FOR_SHOT) {
                if (CAPTURE_REQUEST_TAG_FOR_SHOT.equals(request.getTag())) {
                    isShotCanDo = true;
                    Log.d(TAG, "onCaptureFailed: ");
                }
            }
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull Surface target,
                                        long frameNumber) {
            if (status == STATUS_WAITING_AE_AF_CONVERGED_FOR_SHOT) {
                if (CAPTURE_REQUEST_TAG_FOR_SHOT.equals(request.getTag())) {
                    isShotCanDo = true;
                    Log.d(TAG, "onCaptureBufferLost: ");
                }
            }
        }
    };

    private void resetNormalPreview() {
        // cancel af trigger
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        capture(previewCaptureRequestBuilder);

        // af
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

        // ae
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureValue);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);

        if (isSimulateFlashOnCanDo()) {
            switchFlashMode(FLASH_MODE_ON);
        }

        // effect preview
        updatePreview(previewCaptureRequestBuilder);
    }

    private void sendAeAfTriggerCaptureRequest() {
        // af trigger start
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        // ae trigger start
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        previewCaptureRequestBuilder.setTag(CAPTURE_REQUEST_TAG_FOR_SHOT);
        CaptureRequest request = previewCaptureRequestBuilder.build();
        capture(request);
        previewCaptureRequestBuilder.setTag(null);

        // trigger idle
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);

        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureValue);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);

        // effect preview
        updatePreview(previewCaptureRequestBuilder);
        Log.d(TAG, "sendAeAfTriggerCaptureRequest");
    }

    private Context mContext;
    public CameraContext(Context context) {
        super(context);
        mContext = context;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "CameraContext: platform = " + CameraUtils.getCpuName());
        }
    }

    public void init() {
        handlerThread = new HandlerThread("camera2 thread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void openCamera(SurfaceHolder holder) {
        surfaceHolder = holder;

        handler.post(new Runnable() {
            @Override
            public void run() {

                if (curCameraId == -1) {
                    try {
                        ids = cameraManager.getCameraIdList();
                        Log.d(TAG, "openCamera: ids = " + Arrays.toString(ids));
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                    if (ids == null || ids.length == 0) {
                        Log.e(TAG, "openCamera: no camera id get!");
                        return;
                    }

                    String backId = null;
                    for (String id : ids) {
                        try {
                            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                            if (facing == null) {
                                return;
                            }

                            if (facing == CameraMetadata.LENS_FACING_BACK) {
                                backId = id;
                                Log.d(TAG, "openCamera: id = " + id + " is back id");
                            } else if (facing == CameraMetadata.LENS_FACING_FRONT) {
                                Log.d(TAG, "openCamera: id = " + id + " is front id");
                            }
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                    if (backId != null) {
                        curCameraId = Integer.parseInt(backId);
                    }
                }

                openCamera();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (curCameraId != -1) {
            try {
                PerformanceUtil.getInstance().logTraceStart("openCamera");
                cameraManager.openCamera(String.valueOf(curCameraId), new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        PerformanceUtil.getInstance().logTraceEnd("openCamera");
                        cameraDevice = camera;
                        createSession(camera);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.d(TAG, "onDisconnected: ");
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.d(TAG, "onError: " + error);
                    }
                }, null);

                mCameraCharacteristics =
                        cameraManager.getCameraCharacteristics(String.valueOf(curCameraId));
                isAutoFocusCanDo = CameraUtils.isSupportAutoFocus(mCameraCharacteristics);
                currentExposureValue = 0;
            } catch (CameraAccessException e) {
                e.printStackTrace();;
            }
            Log.e(TAG, "open camera id " + curCameraId + ", isAutoFocusCanDo = " + isAutoFocusCanDo);
        }
    }

    public void closeCamera() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (cameraDevice != null) {
                    PerformanceUtil.getInstance().logTraceStart("closeCamera");
                    try {
                        if (cameraCaptureSession != null) {
                            cameraCaptureSession.abortCaptures();
                            cameraCaptureSession.close();
                        }
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                    cameraDevice.close();
                    cameraDevice = null;
                    ImageReaderManager.getInstance().release();
                    PerformanceUtil.getInstance().logTraceEnd("closeCamera");

                    curZoomRatio = 1f;
                    cropRect = null;
                }
            }
        });
    }

    public void release() {
        closeCamera();

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder != null) {
                    mediaRecorder.release();
                    mediaRecorder = null;
                }

                if (codecSurface != null) {
                    codecSurface.release();
                    codecSurface = null;
                }
                Log.d(TAG, "release: ");
            }
        });

        if (handler != null) {
            handlerThread.quitSafely();
            handlerThread = null;
            handler = null;
        }
    }

    private void createSession(final CameraDevice device) {
        configMediaRecorder();

        StreamConfigurationMap streamConfigurationMap = mCameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportPreviewSize = streamConfigurationMap.getOutputSizes(SurfaceHolder.class);
        Size size = CameraUtils.getBestPreviewSize(mContext, supportPreviewSize);
        Log.d(TAG, "createSession: size = " + size);
        final List<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(surfaceHolder.getSurface());
        surfaceList.add(codecSurface);
        jpegImageReader = ImageReaderManager
                .getInstance().getJpegImageReader(pictureSize.getWidth(), pictureSize.getHeight());
        jpegImageReader.setOnImageAvailableListener(jpegImageAvailableListener, null);
        surfaceList.add(jpegImageReader.getSurface());

        PerformanceUtil.getInstance().logTraceStart("createCaptureSession");
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    device.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            PerformanceUtil.getInstance().logTraceEnd("createCaptureSession");
                            cameraCaptureSession = session;
                            startPreview(session);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: ");
                        }
                    }, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void startPreview(final CameraCaptureSession session) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // photo mode
                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                    // video mode
//                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    builder.addTarget(surfaceHolder.getSurface());
                    builder.addTarget(codecSurface);
                    CaptureRequest request = builder.build();
                    session.setRepeatingRequest(request, previewCallback, null);
                    previewCaptureRequestBuilder = builder;

//                    if (Logger.DEBUG) {
//                        List<CaptureRequest.Key<?>> keys = request.getKeys();
//                        CaptureRequest.Key<?> meteringAvailableModeKey = null;
//                        CaptureRequest.Key<?> meteringExposureMeteringModeKey = null;
//                        for (CaptureRequest.Key<?> key: keys) {
//                            String name = key.getName();
//                            Log.d(TAG, "request key = " + name);
//                            if ("org.codeaurora.qcamera3.exposure_metering.available_modes".equals(name)) {
//                                meteringAvailableModeKey = key;
//                            } else if ("org.codeaurora.qcamera3.exposure_metering.exposure_metering_mode".equals(name)) {
//                                meteringExposureMeteringModeKey = key;
//                            }
//                        }
//
//                        if (meteringAvailableModeKey != null) {
//                            Log.e(TAG, "meteringAvailableMode = " + request.get(meteringAvailableModeKey));
//                            Log.e(TAG, "meteringExposureMeteringMode = " + request.get(meteringExposureMeteringModeKey));
//                        } else {
//                            Log.e(TAG, "meteringAvailableMode is null");
//                        }
//
//                        Log.e(TAG, "run: ----------------------------------------segment-----------------------------");
//                        List<CaptureRequest.Key<?>> requestKes = mCameraCharacteristics.getAvailableCaptureRequestKeys();
//                        for (CaptureRequest.Key<?> key: requestKes) {
//                            Log.d(TAG, "request key = " + key.getName());
//                        }
//
//                        Log.e(TAG, "run: ----------------------------------------segment-----------------------------");
//                        List<CaptureResult.Key<?>> resultKeys = mCameraCharacteristics.getAvailableCaptureResultKeys();
//                        for (CaptureResult.Key<?> key: resultKeys) {
//                            Log.d(TAG, "result key = " + key.getName());
//                        }
//
//                        testCall();
//                    }
                    switchFlashMode(FLASH_MODE_OFF);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void startRecord() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                isRecording = true;
                if (mVideoFile.exists()) {
                    boolean delete = mVideoFile.delete();
                    Log.d(TAG, "startRecord: delete last file: " + delete);
                }
                if (mediaRecorder != null) {
                    mediaRecorder.reset();
                }
                configMediaRecorder();
                mediaRecorder.start();

                Log.d(TAG, "startRecord: ");
            }
        });
    }


    public void stopRecord() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                isRecording = false;
                mediaRecorder.stop();
                mediaRecorder.reset();
                Log.d(TAG, "stopRecord: " + mVideoFile.getAbsolutePath());
            }
        });
    }

    private void configMediaRecorder() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        } else {
            mediaRecorder.reset();
        }

        mediaRecorder.setOrientationHint(90);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        if (codecSurface == null) {
            codecSurface = MediaCodec.createPersistentInputSurface();
        }
        mediaRecorder.setInputSurface(codecSurface);

        CamcorderProfile profile = CamcorderProfile.get(curCameraId, CamcorderProfile.QUALITY_720P);
        Log.i(TAG, "configMediaRecorder: audioCodec = " + profile.audioCodec
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
        Log.d(TAG, "startRecord: file = " + mVideoFile);
        mediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());
        mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                Log.d(TAG, "onError: what = " + what + ", extra = " + extra);
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
        });

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void switchCamera() {
        if (ids == null || ids.length == 0) {
            return;
        }

        int index = Arrays.asList(ids).indexOf(String.valueOf(curCameraId));
        if (index >= 0) {
            index = (index + 1) % ids.length;
        }

        curCameraId = Integer.parseInt(ids[index]);
        handler.post(new Runnable() {
            @Override
            public void run() {
                closeCamera();
                openCamera(surfaceHolder);
            }
        });
    }

    public void switchFlashMode(String flashMode) {
        if (flashMode == null || previewCaptureRequestBuilder == null) {
            return;
        }

        currentFlashMode = flashMode;
        updateFlashMode(previewCaptureRequestBuilder, flashMode);
        updatePreview(previewCaptureRequestBuilder);
        Log.e(TAG, "switchFlashMode: " + flashMode);
    }

    private void updateFlashMode(CaptureRequest.Builder builder, String flashMode) {
        Log.d(TAG, "updateFlashMode: " + flashMode);
        switch (flashMode) {
            case FLASH_MODE_OFF:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_MODE_AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_MODE_ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_MODE_TORCH:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            default:
                break;
        }
    }

    public void onSingleTap(float x, float y, int width, int height) {
        if (mPreviewRect == null) {
            mPreviewRect = new Rect(0, 0, height, width);
        } else {
            mPreviewRect.set(0, 0, height, width);
        }

        if (mCoordinateTransformer == null) {
            mCoordinateTransformer = new CoordinateTransformer(mCameraCharacteristics, new RectF(mPreviewRect));
        }

        if (isAutoFocusCanDo) {
            onTouchAF(x, y);
        }
        Log.d(TAG, "onSingleTap: x = " + x + ", y = " + y);
    }

    private void onTouchAF(final float x, final float y) {
        if (previewCaptureRequestBuilder != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (previewCaptureRequestBuilder != null) {
                        MeteringRectangle rect = calcTapAreaForCamera2(200, 1000, x, y);

                        AFRegions = new MeteringRectangle[]{rect};
                        AERegions = new MeteringRectangle[]{rect};
                        Log.d(TAG, "run: AERegions = " + Arrays.toString(AERegions) + ", AFRegions = " + Arrays.toString(AFRegions));

                        // maybe trigger cancel if previous trigger not done.
                        // ..
                        // trigger start
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                        capture(previewCaptureRequestBuilder);

                        // trigger idle
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, AFRegions);

                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureValue);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, AERegions);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);

                        // effect preview
                        updatePreview(previewCaptureRequestBuilder);
                    }
                }
            });
        }
    }

    private void capture(final CaptureRequest.Builder builder) {
       capture(builder.build());
    }

    private void capture(final CaptureRequest request) {
        try {
            cameraCaptureSession.capture(request, previewCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview(final CaptureRequest.Builder builder) {
       handler.post(new Runnable() {
           @Override
           public void run() {
               updatePreview(builder.build());
           }
       });
    }

    private void updatePreview(CaptureRequest request) {
        try {
            cameraCaptureSession.setRepeatingRequest(request, previewCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void capture(PictureCallback callback) {
        pictureCallback = callback;
        PerformanceUtil.getInstance().logTraceStart("send-capture-command");
        Log.e(TAG, "capture: start isAfStateOk = " + isAfStateOk);
        if (isSimulateFlashOnCanDo()) {
            status = STATUS_WAITING_AE_AF_CONVERGED_FOR_SHOT;

            // torch
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            previewCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

           // af trigger
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

            previewCaptureRequestBuilder.setTag(CAPTURE_REQUEST_TAG_FOR_SHOT);
            CaptureRequest request = previewCaptureRequestBuilder.build();
            capture(request);
            previewCaptureRequestBuilder.setTag(null);

            // trigger idle
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);

            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureValue);
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            // effect preview
            updatePreview(previewCaptureRequestBuilder);
            return;
        }

        if (isAutoFocusCanDo && isNeedAePreCapture()) {
            status = STATUS_WAITING_AE_AF_CONVERGED_FOR_SHOT;
            sendAeAfTriggerCaptureRequest();
        } else {
            doCapture();
        }
    }

    private boolean isSimulateFlashOnCanDo() {
        return mIsUseTorchModeWhenFlashOnOptional && FLASH_MODE_ON.equals(currentFlashMode);
    }

    private void doCapture() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getCaptureOrientation());
                    captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureValue);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                    Integer afMode = previewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
                    if (afMode != null) {
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
                    }

                    if (isSimulateFlashOnCanDo()) {
                        updateFlashMode(captureBuilder, FLASH_MODE_TORCH);
                    } else {
                        updateFlashMode(captureBuilder, currentFlashMode);
                    }
                    captureBuilder.addTarget(jpegImageReader.getSurface());
                    cameraCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Log.d(TAG, "onCaptureCompleted: doCapture");
                            resetNormalPreview();
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                    @NonNull CaptureRequest request,
                                                    @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.d(TAG, "onCaptureFailed: doCapture");
                            resetNormalPreview();
                        }
                    }, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private int getCaptureOrientation() {
        int sensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int deviceOrientation = displayOrientation;
        boolean facingFront = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;
        int captureOrientation = (sensorOrientation + deviceOrientation + 360) % 360;
        Log.d(TAG, "getCaptureOrientation: captureOrientation = " + captureOrientation);
        return captureOrientation;
    }

    private boolean isNeedAePreCapture() {
        return FLASH_MODE_AUTO.equals(currentFlashMode) || FLASH_MODE_ON.equals(currentFlashMode);
    }

    public void zoom(final float scaleFactor) {
        if (cropRect != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Rect sensorRect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    Float maxZoomRatio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    if (sensorRect != null && maxZoomRatio != null) {
                        curZoomRatio *= scaleFactor;
                        curZoomRatio = Math.min(curZoomRatio, maxZoomRatio);
                        curZoomRatio = Math.max(1f, curZoomRatio);
                        int centerX = sensorRect.width() / 2;
                        int centerY = sensorRect.height() / 2;
                        int xDel = (int) (0.5f * sensorRect.width() / curZoomRatio);
                        int yDel = (int) (0.5f * sensorRect.height() / curZoomRatio);
                        Rect zoomRect = new Rect(centerX - xDel, centerY - yDel,
                                centerX + xDel, centerY + yDel);
                        previewCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
                        updatePreview(previewCaptureRequestBuilder);
                    }
                }
            });
        }
    }

    private int currentExposureValue = 0;
    public int onExposureChanged(boolean isDown) {
        Range<Integer> exposureRange = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        Rational exposureStep = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (exposureRange == null) {
            return -1;
        }

        if (exposureStep == null) {
            return -1;
        }

        if ((exposureRange.getUpper().equals(exposureRange.getLower()))
                && (exposureRange.getLower() == 0)) {
            return -1;
        }

        Log.d(TAG, "onExposureChanged: exposureRange range " + exposureRange + ", exposureStep = " + exposureStep);
        int step = (int) (exposureRange.getUpper() * exposureStep.doubleValue());
        int exposureValue = currentExposureValue;
        if (isDown) {
            exposureValue -= step;
            exposureValue = Math.max(exposureValue, exposureRange.getLower());
        } else {
            exposureValue += step;
            exposureValue = Math.min(exposureValue, exposureRange.getUpper());
        }
        if (exposureValue == currentExposureValue) {
            return -1;
        }

        currentExposureValue = exposureValue;
        Log.d(TAG, "onExposureChanged: currentExposureValue = " + exposureValue);

        if (previewCaptureRequestBuilder == null) {
            return -1;
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureValue);
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, isAeLock);
                updatePreview(previewCaptureRequestBuilder);
            }
        });
        return exposureValue;
    }

    private boolean isAeLock = false;
    public void setAeLock() {
        isAeLock = !isAeLock;
        Log.d(TAG, "setAeLock: " + isAeLock);
        handler.post(new Runnable() {
            @Override
            public void run() {
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, isAeLock);
                updatePreview(previewCaptureRequestBuilder);
            }
        });
    }

    public void test() {
        Log.d(TAG, "test: ");
    }

    private CoordinateTransformer mCoordinateTransformer;
    private Rect mPreviewRect;
    private MeteringRectangle[] AFRegions;
    private MeteringRectangle[] AERegions;

    private MeteringRectangle calcTapAreaForCamera2(int areaSize, int weight, float x, float y) {
        int left = CameraUtils.constrain((int) x - areaSize / 2,
                mPreviewRect.left, mPreviewRect.right - areaSize);
        int top = CameraUtils.constrain((int) y - areaSize / 2,
                mPreviewRect.top, mPreviewRect.bottom - areaSize);
        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        Log.d(TAG, "[calcTapAreaForCamera2: areaSize = " + areaSize + ", weight = " + weight
                + ",\nx = " + x + ", y = " + y
                + ",\nleft = " + left + ", top = " + top
                + "]"
        );

        rectF = mCoordinateTransformer.toCameraSpace(rectF);
        Rect focusRect = new Rect();
        focusRect.left = Math.round(rectF.left);
        focusRect.top = Math.round(rectF.top);
        focusRect.right = Math.round(rectF.right);
        focusRect.bottom = Math.round(rectF.bottom);

        return new MeteringRectangle(focusRect, weight);
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
