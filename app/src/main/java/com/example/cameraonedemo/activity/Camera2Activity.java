package com.example.cameraonedemo.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.cameraonedemo.R;
import com.example.cameraonedemo.base.OnCameraInfoListener;
import com.example.cameraonedemo.camera.api2.CameraContext;
import com.example.cameraonedemo.utils.Constant;

import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.M)
public class Camera2Activity extends BaseActivity
        implements View.OnClickListener, OnCameraInfoListener {

    private static final String TAG = "Camera2Activity";

    private static final int MSG_UPDATE_IMAGE_VIEW = 1000;

    private Button flashOptionalBtn;
    private ImageView pictureImageView;
    private MainHandler mainHandler = new MainHandler(Looper.getMainLooper());

    private OnTouchEventListener mOnTouchEventListener = new OnTouchEventListener() {
        @Override
        public void onScale(float scaleFactor) {
            Log.d(TAG, "onScale: " + scaleFactor);
            if (mCameraContext != null) {
                mCameraContext.zoom(scaleFactor);
            }
        }

        @Override
        public void onSingleTapUp(float x, float y) {
            if (mCameraContext != null) {
                mCameraContext.onTouchAF(x, y,
                        200, 200, mPreviewWidth, mPreviewHeight, mCameraContext.isFront());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        flashOptionalBtn = findViewById(R.id.flash_optional_btn);
        flashOptionalBtn.setOnClickListener(this);

        pictureImageView = findViewById(R.id.picture_image_view);
        pictureImageView.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);
        findViewById(R.id.ec_down_btn).setOnClickListener(this);
        findViewById(R.id.ec_up_btn).setOnClickListener(this);
        findViewById(R.id.ae_lock_btn).setOnClickListener(this);

        mCameraContext = new CameraContext(this);
        mCameraContext.setOnCameraInfoListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraContext.resume();
        setOnTouchEventListener(mOnTouchEventListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraContext.pause();
        setOnTouchEventListener(null);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.switch_btn:
                if (mCameraContext != null) {
                    mCameraContext.switchCamera();
                }
                break;

            case R.id.flash_optional_btn:
                if (mCameraContext != null) {
                    String text = flashOptionalBtn.getText().toString();
                    int index = Arrays.asList(FLASH_OPTIONAL_SET).indexOf(text);
                    index = (index + 1) % FLASH_OPTIONAL_SET.length;
                    text = FLASH_OPTIONAL_SET[index];
                    flashOptionalBtn.setText(text);
                    mCameraContext.switchFlashMode(text);
                }
                break;

            case R.id.capture_btn:
                if (mModeId == Constant.MODE_ID_CAPTURE) {
                    if (mCameraContext != null) {
                        mCameraContext.capture(new CameraContext.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, int jpegRotation) {
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (jpegRotation != 0) {
                                    Matrix matrix = new Matrix();
                                    matrix.postRotate(jpegRotation);
                                    bitmap = Bitmap.createBitmap(bitmap,
                                            0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                }

                                mainHandler.sendMessage(mainHandler.obtainMessage(MSG_UPDATE_IMAGE_VIEW, bitmap));
                            }
                        });
                    }
                } else if (mModeId == Constant.MODE_ID_RECORD) {
                    if (mCameraContext != null) {
                        final String text;
                        if (mCameraContext.isRecording()) {
                            mCameraContext.stopRecord();
                            text = "录像";
                        } else {
                            mCameraContext.startRecord();
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
                if (mCameraContext != null) {
                    mCameraContext.onExposureChanged(true);
                }
                break;

            case R.id.ec_up_btn:
                if (mCameraContext != null) {
                    mCameraContext.onExposureChanged(false);
                }
                break;

            case R.id.ae_lock_btn:
                if (mCameraContext != null) {
                    mCameraContext.setAeLock();
                }
                break;

            default:
                Toast.makeText(this, "not impl", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onCameraPreviewSizeChanged(final int w, final int h) {
        Log.d(TAG, "onCameraPreviewSizeChanged: w = " + w + ", h = " + h);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                mSurfaceView.setAspectRatio(w, h);
            }
        });
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
