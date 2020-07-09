package com.example.cameraonedemo.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {

    private static final String TAG = "AutoFitSurfaceView";

    private float aspectRatio = 0f;

    public AutoFitTextureView(Context context) {
        super(context);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAspectRatio(int width, int height) {
        if (width > 0 && height > 0) {
            aspectRatio = 1f * width / height;
            getSurfaceTexture().setDefaultBufferSize(width, height);
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (aspectRatio == 0f) {
            setMeasuredDimension(w, h);
        } else {
            int newW, newH;
            float actualRatio = w > h ? aspectRatio : 1f / aspectRatio;
            if (w < h * actualRatio) {
                newH = h;
                newW = Math.round(h * actualRatio);
            } else {
                newW = w;
                newH = Math.round(w / actualRatio);
            }
            Log.d(TAG, "onMeasure: set w = " + newW + ", h = " + newH);
            setMeasuredDimension(newW, newH);
        }
    }
}
