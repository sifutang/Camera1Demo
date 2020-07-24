package com.example.cameraonedemo.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class FocusMeteringView extends View {

    private Paint mPaint;
    private float mRadius = 100;
    private int mColor = Color.WHITE;
    private float mCenterX = 0f;
    private float mCenterY = 0f;

    public FocusMeteringView(Context context) {
        super(context);
        init();
    }

    public FocusMeteringView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FocusMeteringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);

        mCenterX = getWidth() / 2f;
        mCenterY = getHeight() / 2f;
    }

    /**
     * set focus and metering cycle view radius
     * @param radius cycle radius value
     */
    public void setRadius(float radius) {
        if (radius > 0f) {
            mRadius = radius;
        }

        invalidate();
    }

    /**
     * set focus and metering cycle view color
     * @param color cycle color value, {@link Color}
     */
    public void setColor(int color) {
        mPaint.setColor(color);
        invalidate();
    }

    /**
     * set focus and metering cycle view center point
     * @param x center x
     * @param y center y
     */
    public void setCenter(float x, float y) {
        if (x > 0) {
            mCenterX = clamp(x, mRadius, getWidth() - mRadius);
        }

        if (y > 0) {
            mCenterY = clamp(y, mRadius, getHeight() - mRadius);
        }

        invalidate();
    }

    /**
     * reset view
     */
    public void reset() {
        mCenterX = getWidth() / 2f;
        mCenterY = getHeight() / 2f;
        mPaint.setColor(Color.WHITE);
        invalidate();
    }

    public void hide() {
        setVisibility(INVISIBLE);
    }

    public void show() {
        setVisibility(VISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mPaint);
    }

    private float clamp(float value, float min, float max) {
        value = Math.max(value, min);
        value = Math.min(value, max);
        return value;
    }
}
