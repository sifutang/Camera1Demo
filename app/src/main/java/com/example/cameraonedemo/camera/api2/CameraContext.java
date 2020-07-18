package com.example.cameraonedemo.camera.api2;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.M)
public class CameraContext {

    public static final String FLASH_MODE_OFF = "Flash Off";
    public static final String FLASH_MODE_AUTO = "Flash Auto";
    public static final String FLASH_MODE_ON = "Flash On";
    public static final String FLASH_MODE_TORCH = "Flash Torch";

    private static final String TAG = "CameraContext";
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)
    };

    private HandlerThread handlerThread;
    private Handler handler;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private SurfaceHolder surfaceHolder;
    private Surface codecSurface;
    private CaptureRequest.Builder previewCaptureRequestBuilder;

    // for video
    private MediaRecorder mediaRecorder;
    private volatile boolean isRecording;
    private File videoFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4");

    private int curCameraId = -1;
    private String[] ids;

    private CameraCaptureSession.CaptureCallback previewCallback = new CameraCaptureSession.CaptureCallback() {

        private Integer mAfMode = -1;
        private Integer mAfState = -1;
        private Integer mAeMode = -1;
        private Integer mAeState = -1;
        private Integer mFlashMode = -1;
        private Integer mFlashState = -1;

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
            Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            Integer aeMode = result.get(CaptureResult.CONTROL_AE_MODE);
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            Integer flashMode = result.get(CaptureResult.FLASH_MODE);
            Integer flashState = result.get(CaptureResult.FLASH_STATE);
            if (!mAfMode.equals(afMode) || !mAfState.equals(afState) || !mAeMode.equals(aeMode)
                    || !mAeState.equals(aeState) || !mFlashMode.equals(flashMode) || !mFlashState.equals(flashState)) {
                Log.i(TAG, "[onCaptureCompleted] afMode=" + afMode + ", afState=" + afState
                        + ", aeMode=" + aeMode + ", aeState=" + aeState
                        + ", flashMode=" + flashMode + ", flashState=" + flashState);
            }

            mAfMode = afMode;
            mAfState = afState;
            mAeMode = aeMode;
            mAeState = aeState;
            mFlashMode = flashMode;
            mFlashState = flashState;
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
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
        }
    };

    public CameraContext(Context context) {
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
            Log.d(TAG, "open camera id " + curCameraId);
            try {
                cameraManager.openCamera(String.valueOf(curCameraId), new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.d(TAG, "onOpened: ");
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
            } catch (CameraAccessException e) {
                e.printStackTrace();;
            }
        }
    }

    public void closeCamera() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (cameraDevice != null) {
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
                    Log.d(TAG, "closeCamera: ");
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

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    device.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "onConfigured: ");
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
                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    builder.addTarget(surfaceHolder.getSurface());
                    builder.addTarget(codecSurface);
                    session.setRepeatingRequest(builder.build(), previewCallback, null);
                    previewCaptureRequestBuilder = builder;
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

        switch (flashMode) {
            case FLASH_MODE_OFF:
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                previewCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_MODE_AUTO:
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                previewCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_MODE_ON:
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                previewCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case FLASH_MODE_TORCH:
                previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                previewCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            default:
                break;
        }

        updatePreview(previewCaptureRequestBuilder);
    }

    public void onSingleTap(float x, float y) {
        Log.d(TAG, "onSingleTap: x = " + x + ", y = " + y);
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
                        try {
                            cameraCaptureSession.capture(previewCaptureRequestBuilder.build(), previewCallback, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                        // trigger idle
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);

                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        // effect preview
                        updatePreview(previewCaptureRequestBuilder);
                    }
                }
            });
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
}
