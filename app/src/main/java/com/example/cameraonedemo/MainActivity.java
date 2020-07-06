package com.example.cameraonedemo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.cameraonedemo.activity.Camera1Activity;
import com.example.cameraonedemo.activity.Camera2Activity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_camera1).setOnClickListener(this);
        findViewById(R.id.btn_camera2).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_camera1:
                startActivity(new Intent(MainActivity.this, Camera1Activity.class));
                break;
            case R.id.btn_camera2:
                startActivity(new Intent(MainActivity.this, Camera2Activity.class));
                break;
            default:
                break;
        }
    }
}