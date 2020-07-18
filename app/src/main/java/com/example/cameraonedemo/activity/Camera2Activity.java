package com.example.cameraonedemo.activity;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.cameraonedemo.R;
import com.example.cameraonedemo.camera.api2.CameraContext;
import com.example.cameraonedemo.utils.AutoFitSurfaceView;

import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.M)
public class Camera2Activity extends AppCompatActivity
        implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "Camera2Activity";
    private static final String[] FLASH_OPTIONAL_SET = {
            CameraContext.FLASH_MODE_OFF,
            CameraContext.FLASH_MODE_AUTO,
            CameraContext.FLASH_MODE_ON,
            CameraContext.FLASH_MODE_TORCH
    };

    private CameraContext cameraContext;
    private AutoFitSurfaceView surfaceView;
    private Button recordBtn;
    private Button flashOptionalBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        recordBtn = findViewById(R.id.record_btn);
        recordBtn.setOnClickListener(this);

        flashOptionalBtn = findViewById(R.id.flash_optional_btn);
        flashOptionalBtn.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);

        cameraContext = new CameraContext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged: format = " + format + ", w = " + width + ", h = " + height);

        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (cameraContext != null) {
                        cameraContext.onSingleTap(event.getX(), event.getY());
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
            case R.id.record_btn:
                if (cameraContext != null) {
                    final String text;
                    if (cameraContext.isRecording()) {
                        cameraContext.stopRecord();
                        text = "录像";
                    } else {
                        cameraContext.startRecord();
                        text = "结束";
                    }

                    recordBtn.setText(text);
                }
                break;

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
                if (cameraContext != null) {
                    cameraContext.capture();
                }
                break;
            default:
                Toast.makeText(this, "not impl", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}