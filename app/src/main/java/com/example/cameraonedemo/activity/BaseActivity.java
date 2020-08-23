package com.example.cameraonedemo.activity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cameraonedemo.R;
import com.example.cameraonedemo.camera.api2.CameraContext;
import com.example.cameraonedemo.model.ModeItem;
import com.example.cameraonedemo.utils.Constant;

import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

    protected static final String[] FLASH_OPTIONAL_SET = {
            CameraContext.FLASH_MODE_OFF,
            CameraContext.FLASH_MODE_AUTO,
            CameraContext.FLASH_MODE_ON,
            CameraContext.FLASH_MODE_TORCH
    };

    protected int mModeId = Constant.MODE_ID_CAPTURE;

    protected Button mCaptureBtn;

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

    private GestureDetector mGestureDetector;
    private MyOnGestureListener mMyOnGestureListener
            = new MyOnGestureListener();

    public void setOnTouchEventListener(OnTouchEventListener listener) {
        touchEventListener = listener;
    }

    public void onCameraModeChanged(ModeItem modeItem) {
        Log.d(TAG, "onCameraModeChanged: "+ modeItem);
        mModeId = modeItem.getId();
        mCaptureBtn.setText(modeItem.getName());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);

//        WindowManager.LayoutParams lp = getWindow().getAttributes();
//        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
//        getWindow().setAttributes(lp);

        mySensorEventListener = new MySensorEventListener();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        scaleGestureDetector = new ScaleGestureDetector(this, myScaleGestureDetectorListener);
        mGestureDetector = new GestureDetector(this, mMyOnGestureListener);

        initRecycleView();
        mCaptureBtn = findViewById(R.id.capture_btn);
    }

    private void initRecycleView() {
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(layoutManager);
        List<ModeItem> modes = new ArrayList<>();
        modes.add(new ModeItem("录像", Constant.MODE_ID_RECORD));
        modes.add(new ModeItem("拍照", Constant.MODE_ID_CAPTURE));
        modes.add(new ModeItem("硬编", Constant.MODE_ID_HARDWARE_RECORD));
        Adapter adapter = new Adapter(modes);
        adapter.setOnItemClickListener(new Adapter.OnItemClickListener() {
            @Override
            public void onItemClick(ModeItem modeItem) {
                onCameraModeChanged(modeItem);
            }
        });
        recyclerView.setAdapter(adapter);
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
        mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private static class MySensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
//            Log.i(TAG, "onSensorChanged: " + event.values[0] + ", " + event.values[1] + ", " + event.values[2]);
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

    private class MyOnGestureListener implements GestureDetector.OnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (e != null && touchEventListener != null) {
                touchEventListener.onSingleTapUp(e.getX(), e.getY());
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        interface OnItemClickListener {
            void onItemClick(ModeItem modeItem);
        }

        private List<ModeItem> mLists;
        private OnItemClickListener mListener;

        public Adapter(List<ModeItem> modes) {
            mLists = modes;
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            mListener = listener;
        }

        @NonNull
        @Override
        public Adapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.mode_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Adapter.ViewHolder holder, final int position) {
            ModeItem modeItem = mLists.get(position);
            holder.mTextView.setText(modeItem.getName());
            holder.mTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        mListener.onItemClick(mLists.get(position));
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mLists.size();
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {

            private TextView mTextView;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                mTextView = itemView.findViewById(R.id.text_view);
            }
        }
    }
}
