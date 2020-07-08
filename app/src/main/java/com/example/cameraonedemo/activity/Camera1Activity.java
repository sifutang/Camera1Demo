package com.example.cameraonedemo.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.example.cameraonedemo.camera.api1.CameraContext;
import com.example.cameraonedemo.camera.api1.CameraInfo;
import com.example.cameraonedemo.R;
import com.example.cameraonedemo.utils.AutoFitSurfaceView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera1Activity extends AppCompatActivity
        implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int MSG_CANCEL_AUTO_FOCUS = 1000;
    private static final int MSG_UPDATE_RECORDING_STATUS = 1001;
    private static final int MSG_TOUCH_AF_LOCK_TIME_OUT = 5000;

    private AutoFitSurfaceView surfaceView;
    private ImageView pictureImageView;
    private Button recordBtn;

    private CameraContext cameraContext;
    private int currentCameraIdType = Camera.CameraInfo.CAMERA_FACING_BACK;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private MainHandler mainHandler = new MainHandler(Looper.getMainLooper());

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        pictureImageView = findViewById(R.id.picture_image_view);
        pictureImageView.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);

        recordBtn = findViewById(R.id.record_btn);
        recordBtn.setOnClickListener(this);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }
        cameraContext = new CameraContext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                cameraContext.resume();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                cameraContext.closeCamera();
                cameraContext.pause();
            }
        });
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        final SurfaceHolder surfaceHolder = holder;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                cameraContext.configSurfaceHolder(surfaceHolder);
                cameraContext.openCamera(currentCameraIdType);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        final int previewW = width;
        final int previewH = height;
        Log.d(TAG, "surfaceChanged: format = " + format + ",w = " + width + ", h = " + height);

        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int x = (int) event.getX();
                final int y = (int) event.getY();
                final boolean isMirror = currentCameraIdType == Camera.CameraInfo.CAMERA_FACING_FRONT;

                mainHandler.removeMessages(MSG_CANCEL_AUTO_FOCUS);
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (cameraContext != null) {
                            cameraContext.onTouchAF(x, y, 200, 200, previewW, previewH, isMirror);
                        }
                    }
                });
                mainHandler.sendEmptyMessageDelayed(MSG_CANCEL_AUTO_FOCUS, MSG_TOUCH_AF_LOCK_TIME_OUT);

                return false;
            }
        });
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: ");
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.switch_btn) {
            if (currentCameraIdType == CameraInfo.CAMERA_FACING_BACK) {
                currentCameraIdType = CameraInfo.CAMERA_FACING_FRONT;
            } else {
                currentCameraIdType = CameraInfo.CAMERA_FACING_BACK;
            }

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    if (cameraContext != null) {
                        cameraContext.switchCamera(currentCameraIdType);
                    }
                }
            });
        } else if (v.getId() == R.id.capture_btn) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    if (cameraContext != null) {
                        cameraContext.capture(new CameraContext.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, Camera camera) {
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bitmap == null) {
                                    return;
                                }
                                pictureImageView.setImageBitmap(bitmap);
                                pictureImageView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            });
        } else if (v.getId() == R.id.picture_image_view) {
            pictureImageView.setVisibility(View.INVISIBLE);
        } else if (v.getId() == R.id.record_btn) {
            Log.e(TAG, "onClick: record_btn");
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    if (cameraContext != null) {
                        if (cameraContext.isRecording()) {
                            cameraContext.stopRecord();
                        } else {
                            cameraContext.startRecord();
                        }
                        final boolean isRecording = cameraContext.isRecording();
                        mainHandler.sendMessage(mainHandler.obtainMessage(MSG_UPDATE_RECORDING_STATUS, isRecording));
                    }
                }
            });
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
                    if (cameraContext != null) {
                        cameraContext.cancelAutoFocus();
                        cameraContext.enableCaf();
                    }
                    break;
                case MSG_UPDATE_RECORDING_STATUS:
                    boolean isRecording = (Boolean) msg.obj;
                    Log.e(TAG, "dispatchMessage: MSG_UPDATE_RECORDING_STATUS isRecording = " + isRecording);
                    recordBtn.setText(isRecording ? "结束" : "录像");
                    break;
            }
        }
    }
}