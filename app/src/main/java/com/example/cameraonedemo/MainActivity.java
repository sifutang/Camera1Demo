package com.example.cameraonedemo;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "MainActivity";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private ImageView pictureImageView;

    private Camera camera;
    private Camera.Parameters parameters;
    private CameraInfo currCameraInfo;
    private MyOrientationEventListener orientationEventListener;

    private int previewW;
    private int previewH;
    private int displayOrientation;
    private int currentCameraIdType = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int rotation;

    private Camera.AutoFocusMoveCallback cafCallback = new Camera.AutoFocusMoveCallback() {
        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            Log.e(TAG, "onAutoFocusMoving: start " + start);
            Toast.makeText(MainActivity.this,
                    "focus " + (start ? "start" : "end"), Toast.LENGTH_SHORT).show();
        }
    };

    private Camera.AutoFocusCallback afCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Log.d(TAG, "onAutoFocus: success = " + success);
            Toast.makeText(MainActivity.this,
                    "focus " + (success ? "success" : "fail"), Toast.LENGTH_SHORT).show();
        }
    };


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        pictureImageView = findViewById(R.id.picture_image_view);
        pictureImageView.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 1000);
        }

        orientationEventListener = new MyOrientationEventListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        orientationEventListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
        orientationEventListener.disable();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        openCamera(currentCameraIdType);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        previewW = width;
        previewH = height;
        Log.d(TAG, "surfaceChanged: format = " + format + ",w = " + width + ", h = " + height);

        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                boolean isMirror = currentCameraIdType == Camera.CameraInfo.CAMERA_FACING_FRONT;

                // meter
                int maxNumMeteringAreas = parameters.getMaxNumMeteringAreas();
                if (maxNumMeteringAreas > 0) {
                    Rect tapRect = CameraUtils.calculateTapArea(
                            200, 200, x, y,
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
                                200, 200, x, y,
                                previewW, previewH, displayOrientation,1.5f, isMirror);
                        Camera.Area area = new Camera.Area(tapRect, 1000);
                        areas.add(area);
                        parameters.setFocusAreas(areas);
                    }
                    camera.setParameters(parameters);
                    camera.autoFocus(afCallback);
                    camera.setAutoFocusMoveCallback(null);
                }

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

            closeCamera();
            openCamera(currentCameraIdType);
        } else if (v.getId() == R.id.capture_btn) {
            capture();
        } else if (v.getId() == R.id.picture_image_view) {
            pictureImageView.setVisibility(View.INVISIBLE);
        }
    }

    private void closeCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private void openCamera(int type) {
        currCameraInfo = new CameraInfo(type);
        int cameraId = currCameraInfo.getCameraId();
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
        parameters.setPreviewSize(1920, 1080);
        parameters.setPictureSize(800, 600);

        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setAutoFocusMoveCallback(cafCallback);
        }
        camera.setParameters(parameters);

        displayOrientation = 0;
        try {
            camera.setPreviewDisplay(surfaceHolder);
            displayOrientation = CameraUtils.getCameraDisplayOrientation(this, cameraId);
            camera.setDisplayOrientation(displayOrientation);
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.startPreview();
        Log.d(TAG, "openCamera: " + currCameraInfo);
    }

    private void capture() {
        if (camera != null) {
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

                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bitmap == null) {
                        return;
                    }

                    Log.d(TAG, "onPictureTaken");
                    pictureImageView.setImageBitmap(bitmap);
                    pictureImageView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private class MyOrientationEventListener extends OrientationEventListener {

        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == ORIENTATION_UNKNOWN) return;
            orientation = (orientation + 45) / 90 * 90;
            int degrees;
            if (currCameraInfo.getFacing() == CameraInfo.CAMERA_FACING_FRONT) {
                degrees = (currCameraInfo.getPictureNeedRotateOrientation() - orientation + 360) % 360;
            } else { // back-facing camera
                degrees = (currCameraInfo.getPictureNeedRotateOrientation() + orientation) % 360;
            }

            if (rotation != degrees) {
                rotation = degrees;
                Log.d(TAG, "onOrientationChanged: rotation = " + rotation + ", orientation = " + orientation);
            }
        }
    }
}