package com.example.cameraonedemo.camera.api2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
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
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.cameraonedemo.camera.common.BaseCameraContext;
import com.example.cameraonedemo.utils.CameraUtils;
import com.example.cameraonedemo.utils.ImageReaderManager;
import com.example.cameraonedemo.utils.PerformanceUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.M)
public class CameraContext extends BaseCameraContext {

    public static final String FLASH_MODE_OFF = "Flash Off";
    public static final String FLASH_MODE_AUTO = "Flash Auto";
    public static final String FLASH_MODE_ON = "Flash On";
    public static final String FLASH_MODE_TORCH = "Flash Torch";

    private static final String TAG = "CameraContext";
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)
    };

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_WAITING_FOR_SHOT = 1;
    private static final int STATUS_WAITING_AE_PRE_CAPTURE_TRIGGER = 2;
    private static final int STATUS_WAITING_AE_PRE_CAPTURE_DONE = 3;

    private HandlerThread handlerThread;
    private Handler handler;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private SurfaceHolder surfaceHolder;
    private Surface codecSurface;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private ImageReader jpegImageReader;
    private Size pictureSize = new Size(800, 600);
    private CameraCharacteristics mCameraCharacteristics;

    // for video
    private MediaRecorder mediaRecorder;
    private volatile boolean isRecording;
    private File videoFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4");

    private int curCameraId = -1;
    private String[] ids;
    private String currentFlashMode = CameraContext.FLASH_MODE_OFF;

    private boolean isAutoFocusCanDo = false;
    private boolean sendAePreCaptureRequest = false;
    private int status = STATUS_IDLE;
    private int aePreCaptureRequestStatus = STATUS_IDLE;
    private int aePreCaptureRequestCode;
    private PictureCallback pictureCallback;

    // for zoom
    private Rect cropRect = null;
    private float curZoomRatio = 1f;

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
                    pictureCallback.onPictureTaken(jpeg);
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

        private static final int AE_PRE_CAPTURE_REQUEST_WAIT_MAX_COUNT = 60;
        private int aePreCaptureRequestWaitCount = 0;

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     long timestamp,
                                     long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
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
                Log.i(TAG, "[\nafMode=" + afMode + ", afState=" + afState
                        + ",\naeMode=" + aeMode + ", aeState=" + aeState
                        + ",\nflashMode=" + flashMode + ", flashState=" + flashState
                        + ",\nawbMode = " + awbMode + ", awbState= " + awbState
                        + "\n]");
            }

            mAfMode = afMode;
            mAfState = afState;
            mAeMode = aeMode;
            mAeState = aeState;
            mFlashMode = flashMode;
            mFlashState = flashState;
            mAwbMode = awbMode;
            mAwbState = awbState;

            if (status == STATUS_WAITING_FOR_SHOT) {
                if (aePreCaptureRequestCode == request.hashCode()) {
                    Log.d(TAG, "onCaptureCompleted: ae pre capture done");
                    aePreCaptureRequestStatus = STATUS_WAITING_AE_PRE_CAPTURE_DONE;
                }

                boolean isAfStateOk = afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                        || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
                if (afState == -1) {
                    Log.e(TAG, "onCaptureCompleted: afState is -1");
                    isAfStateOk = true;
                }

                if (isAfStateOk) {
                    boolean isAeStateOk = aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                            || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED;
                    if (aeState == -1) {
                        Log.e(TAG, "onCaptureCompleted: aeState is -1");
                        isAeStateOk = sendAePreCaptureRequest;
                    }

                    boolean isNeedPreCapture = isNeedAePreCapture();
                    Log.w(TAG, "onCaptureCompleted: af state ok, isAeStateOk = " + isAeStateOk
                            + ", isNeedPreCapture = " + isNeedPreCapture);

                    if (aePreCaptureRequestStatus == STATUS_WAITING_AE_PRE_CAPTURE_TRIGGER && isAeStateOk) {
                        if (aePreCaptureRequestWaitCount < AE_PRE_CAPTURE_REQUEST_WAIT_MAX_COUNT) {
                            aePreCaptureRequestWaitCount++;
                        } else {
                            Log.e(TAG, "onCaptureCompleted: ae pre capture done, maybe time out...");
                            aePreCaptureRequestStatus = STATUS_WAITING_AE_PRE_CAPTURE_DONE;
                            aePreCaptureRequestWaitCount = 0;
                        }
                    }

                    if (!sendAePreCaptureRequest && (!isAeStateOk || isNeedPreCapture)) {
                        sendAePreCaptureRequest();
                    } else if (aePreCaptureRequestStatus == STATUS_WAITING_AE_PRE_CAPTURE_DONE && isAeStateOk){
                        // do capture
                        // ...
                        Log.e(TAG, "onCaptureCompleted: send capture request, do capture");
                        doCapture();
                        status = STATUS_IDLE;
                        sendAePreCaptureRequest = false;
                        aePreCaptureRequestStatus = STATUS_IDLE;
                        aePreCaptureRequestCode = 0;
                        aePreCaptureRequestWaitCount = 0;
                    } else {
//                        Log.d(TAG, "onCaptureCompleted: other status");
                    }
                }
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (aePreCaptureRequestCode == request.hashCode()) {
                Log.e(TAG, "onCaptureFailed: ae pre capture done");
                aePreCaptureRequestStatus = STATUS_WAITING_AE_PRE_CAPTURE_DONE;
            }
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session,
                                               int sequenceId,
                                               long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session,
                                             int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull Surface target,
                                        long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
            if (aePreCaptureRequestCode == request.hashCode()) {
                Log.e(TAG, "onCaptureBufferLost: ae pre capture done");
                aePreCaptureRequestStatus = STATUS_WAITING_AE_PRE_CAPTURE_DONE;
            }
        }
    };

    private void resetNormalPreview() {
        // af
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

        // ae
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

        // effect preview
        updatePreview(previewCaptureRequestBuilder);
    }

    private void sendAePreCaptureRequest() {
        if (sendAePreCaptureRequest) {
            Log.w(TAG, "sendAePreCaptureRequest: processing, filter it...");
            return;
        }

        sendAePreCaptureRequest = true;
        aePreCaptureRequestStatus = STATUS_WAITING_AE_PRE_CAPTURE_TRIGGER;
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
        CaptureRequest request = previewCaptureRequestBuilder.build();
        aePreCaptureRequestCode = request.hashCode();
        capture(request);
        Log.e(TAG, "sendAePreCaptureRequest: ");
    }

    public CameraContext(Context context) {
        super(context);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
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
                    session.setRepeatingRequest(builder.build(), previewCallback, null);
                    previewCaptureRequestBuilder = builder;
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
                if (videoFile.exists()) {
                    boolean delete = videoFile.delete();
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
                Log.d(TAG, "stopRecord: " + videoFile.getAbsolutePath());
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
        Log.d(TAG, "startRecord: file = " + videoFile);
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
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

    public void onSingleTap(float x, float y) {
        if (isAutoFocusCanDo) {
            onTouchAF(x, y);
        }
        Log.d(TAG, "onSingleTap: x = " + x + ", y = " + y);
    }

    private void onTouchAF(float x, float y) {
        if (previewCaptureRequestBuilder != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (previewCaptureRequestBuilder != null) {
                        // maybe trigger cancel if previous trigger not done.
                        // ..

                        // trigger start
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                        capture(previewCaptureRequestBuilder);

                        // trigger idle
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);

//                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON); // look flash mode
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

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
               try {
                   cameraCaptureSession.setRepeatingRequest(builder.build(), previewCallback, null);
               } catch (CameraAccessException e) {
                   e.printStackTrace();
               }
           }
       });
    }

    public void capture(PictureCallback callback) {
        pictureCallback = callback;
        Log.e(TAG, "capture: start");
        if (isAutoFocusCanDo && isNeedAePreCapture()) {
            status = STATUS_WAITING_FOR_SHOT;
            onTouchAF(0, 0);
        } else {
            doCapture();
        }
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
                    captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                    Integer afMode = previewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
                    Log.d(TAG, "doCapture af mode = " + afMode);
                    if (afMode != null) {
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
                    }

                    updateFlashMode(captureBuilder, currentFlashMode);
                    captureBuilder.addTarget(jpegImageReader.getSurface());
                    cameraCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Log.d(TAG, "onCaptureCompleted: doCapture");
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
}
