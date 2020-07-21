package com.example.cameraonedemo.activity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";
    private MySensorEventListener mySensorEventListener;
    private Sensor accelerometer;
    private SensorManager sensorManager;

    interface OnTouchEventListener {
        void onScale(float scaleFactor);
        void onSingleTapUp(float x, float y);
    }

    private OnTouchEventListener touchEventListener;
    private ScaleGestureDetector scaleGestureDetector;
    private MyScaleGestureDetectorListener myScaleGestureDetectorListener
            = new MyScaleGestureDetectorListener();

    public void setOnTouchEventListener(OnTouchEventListener listener) {
        touchEventListener = listener;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mySensorEventListener = new MySensorEventListener();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        scaleGestureDetector = new ScaleGestureDetector(this, myScaleGestureDetectorListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(mySensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(mySensorEventListener);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private static class MySensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.i(TAG, "onSensorChanged: " + event.values[0] + ", " + event.values[1] + ", " + event.values[2]);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged: ");
        }
    }


    private class MyScaleGestureDetectorListener implements ScaleGestureDetector.OnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (detector != null && touchEventListener != null) {
                touchEventListener.onScale(detector.getScaleFactor());
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }
    }
}
