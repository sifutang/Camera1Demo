package com.example.cameraonedemo.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.annotation.SuppressLint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.cameraonedemo.camera.api1.CameraContext;
import com.example.cameraonedemo.camera.api1.CameraInfo;
import com.example.cameraonedemo.R;
import com.example.cameraonedemo.camera.common.BaseCameraContext;
import com.example.cameraonedemo.encoder.VideoEncoder;
import com.example.cameraonedemo.utils.AutoFitSurfaceView;
import com.example.cameraonedemo.view.FaceView;
import com.example.cameraonedemo.view.FocusMeteringView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera1Activity extends BaseActivity
        implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int MSG_CANCEL_AUTO_FOCUS = 1000;
    private static final int MSG_UPDATE_RECORDING_STATUS = 1001;
    private static final int MSG_UPDATE_CODEC_STATUS = 1002;
    private static final int MSG_TOUCH_AF_LOCK_TIME_OUT = 5000;

    private AutoFitSurfaceView mSurfaceView;
    private ImageView mPictureImageView;
    private Button mRecordBtn;
    private Button mCodecBtn;
    private Button flashOptionalBtn;
    private FocusMeteringView mFocusMeteringView;
    private FaceView mFaceView;
    private VideoEncoder mVideoEncoder;

    private CameraContext mCameraContext;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int mCurrentCameraIdType = Camera.CameraInfo.CAMERA_FACING_BACK;
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private MainHandler mMainHandler = new MainHandler(Looper.getMainLooper());

    private OnTouchEventListener mOnTouchEventListener = new OnTouchEventListener() {
        @Override
        public void onScale(float scaleFactor) {

        }

        @Override
        public void onSingleTapUp(final float x, final float y) {
            int focusW = mFocusMeteringView.getWidth();
            int focusH = mFocusMeteringView.getHeight();
            Log.d(TAG, "onSingleTapUp: x = " + x + ", y = " + y
                    + ", focusW = " + focusW + ", focusH = " + focusH);
            mFocusMeteringView.show();
            mFocusMeteringView.setCenter(x, y);
            mFocusMeteringView.setColor(Color.WHITE);

            mMainHandler.removeMessages(MSG_CANCEL_AUTO_FOCUS);
            mMainHandler.sendEmptyMessageDelayed(MSG_CANCEL_AUTO_FOCUS, MSG_TOUCH_AF_LOCK_TIME_OUT);
            final boolean isMirror = mCurrentCameraIdType == Camera.CameraInfo.CAMERA_FACING_FRONT;
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        mCameraContext.onTouchAF(x, y, 200, 200, mPreviewWidth, mPreviewHeight, isMirror);
                    }
                }
            });
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(this);

        mPictureImageView = findViewById(R.id.picture_image_view);
        mPictureImageView.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);

        flashOptionalBtn = findViewById(R.id.flash_optional_btn);
        flashOptionalBtn.setOnClickListener(this);

        mCodecBtn = findViewById(R.id.codec_btn);
        mCodecBtn.setOnClickListener(this);

        mRecordBtn = findViewById(R.id.record_btn);
        mRecordBtn.setOnClickListener(this);

        mFocusMeteringView = findViewById(R.id.focus_metering_view);
        mFaceView = findViewById(R.id.face_view);

        findViewById(R.id.ec_down_btn).setOnClickListener(this);
        findViewById(R.id.ec_up_btn).setOnClickListener(this);
        findViewById(R.id.ae_lock_btn).setOnClickListener(this);


        mCameraContext = new CameraContext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mCameraContext.resume();
                mCameraContext.setFocusStatusCallback(new BaseCameraContext.FocusStatusCallback() {
                    @Override
                    public void onAutoFocus(boolean success) {
                        mFocusMeteringView.setColor(success ? Color.GREEN : Color.RED);
                    }

                    @Override
                    public void onAutoFocusMoving(boolean start) {
                        Log.d(TAG, "onAutoFocusMoving: " + start);
//                        mFocusMeteringView.reset();
//                        mFocusMeteringView.setColor(start ? Color.WHITE : Color.GREEN);
                    }
                });
            }
        });
        mCameraContext.setFaceDetectionListener(new BaseCameraContext.FaceDetectionListener() {
            @Override
            public void onFaceDetection(Rect[] faces) {
                mFaceView.setFaces(faces, mCameraContext.isFront(), mCameraContext.getDisplayOrientation());
            }
        });
        setOnTouchEventListener(mOnTouchEventListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mCameraContext.closeCamera();
                mCameraContext.pause();
                mCameraContext.setFocusStatusCallback(null);
            }
        });
        mMainHandler.removeCallbacksAndMessages(null);
        mCameraContext.setFaceDetectionListener(null);
        setOnTouchEventListener(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        final SurfaceHolder surfaceHolder = holder;
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mCameraContext.configSurfaceHolder(surfaceHolder);
                mCameraContext.openCamera(mCurrentCameraIdType);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mPreviewWidth = width;
        mPreviewHeight = height;
        Log.d(TAG, "surfaceChanged: w = " + width + ", h = " + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: ");
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.switch_btn) {
            if (mCurrentCameraIdType == CameraInfo.CAMERA_FACING_BACK) {
                mCurrentCameraIdType = CameraInfo.CAMERA_FACING_FRONT;
            } else {
                mCurrentCameraIdType = CameraInfo.CAMERA_FACING_BACK;
            }

            mFocusMeteringView.reset();
            mFocusMeteringView.hide();
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        mCameraContext.switchCamera(mCurrentCameraIdType);
                    }
                }
            });
            mFaceView.clear();
        } else if (v.getId() == R.id.capture_btn) {
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        mCameraContext.capture(new CameraContext.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, int jpegRotation) {
                                Log.d(TAG, "onPictureTaken: rotation = " + jpegRotation);

                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bitmap == null) {
                                    return;
                                }

                                if (jpegRotation != 0) {
                                    Matrix matrix = new Matrix();
                                    matrix.postRotate(jpegRotation);
                                    bitmap = Bitmap.createBitmap(bitmap,
                                            0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                }

                                mPictureImageView.setImageBitmap(bitmap);
                                mPictureImageView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            });
        } else if (v.getId() == R.id.picture_image_view) {
            mPictureImageView.setVisibility(View.INVISIBLE);
        } else if (v.getId() == R.id.record_btn) {
            Log.e(TAG, "onClick: record_btn");
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        if (mCameraContext.isRecording()) {
                            mCameraContext.stopRecord();
                        } else {
                            mCameraContext.startRecord();
                        }
                        final boolean isRecording = mCameraContext.isRecording();
                        mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_UPDATE_RECORDING_STATUS, isRecording));
                    }
                }
            });
        } else if (v.getId() == R.id.codec_btn) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                if (mVideoEncoder == null) {
                    mVideoEncoder = new VideoEncoder(
                            getApplicationContext(),
                            mCameraContext.getPreviewWidth(),
                            mCameraContext.getPreviewHeight()
                    );
                }

                if (!mVideoEncoder.isStart()) {
                    mCameraContext.setPreviewCallback(new CameraContext.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data) {
                            mVideoEncoder.addVideoData(data);
                        }
                    });
                    mVideoEncoder.setVideoEncodeListener(new VideoEncoder.VideoEncodeListener() {
                        @Override
                        public void onVideoEncodeStart() {
                            Log.d(TAG, "onVideoEncodeStart: ");
                            mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_UPDATE_CODEC_STATUS, true));
                        }

                        @Override
                        public void onVideoEncodeEnd() {
                            mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_UPDATE_CODEC_STATUS, false));
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mVideoEncoder.release();
                                    mVideoEncoder = null;
                                }
                            });
                            Log.d(TAG, "onVideoEncodeEnd: ");
                        }
                    });
                    mVideoEncoder.start();
                } else {
                    mCameraContext.setPreviewCallback(null);
                    mVideoEncoder.stop();
                }
            }
        } else if (v.getId() == R.id.flash_optional_btn) {
            if (mCameraContext != null) {
                String text = flashOptionalBtn.getText().toString();
                int index = Arrays.asList(FLASH_OPTIONAL_SET).indexOf(text);
                index = (index + 1) % FLASH_OPTIONAL_SET.length;
                text = FLASH_OPTIONAL_SET[index];
                flashOptionalBtn.setText(text);
                mCameraContext.switchFlashMode(text);
            }
        } else if (v.getId() == R.id.ec_down_btn) {
            if (mCameraContext != null) {
                int value = mCameraContext.onExposureChanged(true);
                Toast.makeText(this, "ec = " + value, Toast.LENGTH_SHORT).show();
            }
        } else if (v.getId() == R.id.ec_up_btn) {
            if (mCameraContext != null) {
                int value = mCameraContext.onExposureChanged(false);
                Toast.makeText(this, "ec = " + value, Toast.LENGTH_SHORT).show();
            }
        } else if (v.getId() == R.id.ae_lock_btn) {
            if (mCameraContext != null) {
                mCameraContext.setAeLock();
            }
        }
    }

    private class MainHandler extends Handler {

        MainHandler(Looper looper) {
            super((looper));
        }

        @Override
        public void dispatchMessage(@NonNull Message msg) {
            super.dispatchMessage(msg);
            switch (msg.what) {
                case MSG_CANCEL_AUTO_FOCUS:
                    if (mCameraContext != null) {
                        mCameraContext.cancelAutoFocus();
                        mCameraContext.enableCaf();
                        mFocusMeteringView.hide();
                    }
                    break;
                case MSG_UPDATE_RECORDING_STATUS:
                    boolean isRecording = (Boolean) msg.obj;
                    Log.e(TAG, "dispatchMessage: MSG_UPDATE_RECORDING_STATUS isRecording = " + isRecording);
                    mRecordBtn.setText(isRecording ? "结束" : "录像");
                    break;
                case MSG_UPDATE_CODEC_STATUS:
                    boolean isCodec = (Boolean) msg.obj;
                    Log.e(TAG, "dispatchMessage: MSG_UPDATE_CODEC_STATUS isCodec = " + isCodec);
                    mCodecBtn.setText(isCodec ? "结束" : "硬编");
                    break;
            }
        }
    }
}