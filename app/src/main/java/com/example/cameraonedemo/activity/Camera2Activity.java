package com.example.cameraonedemo.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.cameraonedemo.R;
import com.example.cameraonedemo.camera.api2.CameraContext;
import com.example.cameraonedemo.utils.AutoFitSurfaceView;
import com.example.cameraonedemo.utils.Constant;

import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.M)
public class Camera2Activity extends BaseActivity
        implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "Camera2Activity";

    private static final int MSG_UPDATE_IMAGE_VIEW = 1000;

    private CameraContext cameraContext;
    private AutoFitSurfaceView surfaceView;
    private Button flashOptionalBtn;
    private ImageView pictureImageView;
    private MainHandler mainHandler = new MainHandler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);
//        surfaceView.getHolder().setFixedSize(2340, 1080);

        flashOptionalBtn = findViewById(R.id.flash_optional_btn);
        flashOptionalBtn.setOnClickListener(this);

        pictureImageView = findViewById(R.id.picture_image_view);
        pictureImageView.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);
        findViewById(R.id.ec_down_btn).setOnClickListener(this);
        findViewById(R.id.ec_up_btn).setOnClickListener(this);
        findViewById(R.id.ae_lock_btn).setOnClickListener(this);

        cameraContext = new CameraContext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraContext.resume();
        setOnTouchEventListener(new OnTouchEventListener() {
            @Override
            public void onScale(float scaleFactor) {
                Log.d(TAG, "onScale: " + scaleFactor);
                if (cameraContext != null) {
                    cameraContext.zoom(scaleFactor);
                }
            }

            @Override
            public void onSingleTapUp(float x, float y) {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraContext.pause();
        setOnTouchEventListener(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated: ");
        cameraContext.init();
        cameraContext.openCamera(holder);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, final int width, final int height) {
        Log.d(TAG, "surfaceChanged: format = " + format + ", w = " + width + ", h = " + height);

        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (cameraContext != null) {
                        cameraContext.onSingleTap(event.getX(), event.getY(), width, height);
                    }
                }

                return false;
            }
        });
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: ");
        cameraContext.release();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.switch_btn:
                if (cameraContext != null) {
                    cameraContext.switchCamera();
                }
                break;

            case R.id.flash_optional_btn:
                if (cameraContext != null) {
                    String text = flashOptionalBtn.getText().toString();
                    int index = Arrays.asList(FLASH_OPTIONAL_SET).indexOf(text);
                    index = (index + 1) % FLASH_OPTIONAL_SET.length;
                    text = FLASH_OPTIONAL_SET[index];
                    flashOptionalBtn.setText(text);
                    cameraContext.switchFlashMode(text);
                }
                break;

            case R.id.capture_btn:
                if (mModeId == Constant.MODE_ID_CAPTURE) {
                    if (cameraContext != null) {
                        cameraContext.capture(new CameraContext.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, int jpegRotation) {
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                mainHandler.sendMessage(mainHandler.obtainMessage(MSG_UPDATE_IMAGE_VIEW, bitmap));
                            }
                        });
                    }
                } else if (mModeId == Constant.MODE_ID_RECORD) {
                    if (cameraContext != null) {
                        final String text;
                        if (cameraContext.isRecording()) {
                            cameraContext.stopRecord();
                            text = "录像";
                        } else {
                            cameraContext.startRecord();
                            text = "结束";
                        }

                        mCaptureBtn.setText(text);
                    }
                }

                break;

            case R.id.picture_image_view:
                pictureImageView.setVisibility(View.INVISIBLE);
                break;

            case R.id.ec_down_btn:
                if (cameraContext != null) {
                    cameraContext.onExposureChanged(true);
                }
                break;

            case R.id.ec_up_btn:
                if (cameraContext != null) {
                    cameraContext.onExposureChanged(false);
                }
                break;

            case R.id.ae_lock_btn:
                if (cameraContext != null) {
                    cameraContext.setAeLock();
                }
                break;

            default:
                Toast.makeText(this, "not impl", Toast.LENGTH_SHORT).show();
                break;
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
                case MSG_UPDATE_IMAGE_VIEW:
                    pictureImageView.setVisibility(View.VISIBLE);
                    pictureImageView.setImageBitmap((Bitmap) msg.obj);
                    Log.d(TAG, "show picture");
                    break;
            }
        }
    }
}
