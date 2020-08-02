package com.example.cameraonedemo.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.cameraonedemo.utils.CameraUtils;

public class FaceView extends View {

    private Paint mPaint;
    private int mColor = Color.WHITE;
    private Rect[] faceRectArr;
    private boolean isMirror;
    private int displayOrientation;

    private Matrix mFaceMatrix = new Matrix();
    private RectF mFaceRectF = new RectF();

    public FaceView(Context context) {
        super(context);
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
    }

    public void setFaces(Rect[] rectArr, boolean isMirror, int displayOrientation) {
        this.isMirror = isMirror;
        this.displayOrientation = displayOrientation;

        faceRectArr = rectArr;
        if (faceRectArr == null || faceRectArr.length == 0) {
            setVisibility(INVISIBLE);
        } else {
            setVisibility(VISIBLE);
            invalidate();
        }
    }

    public void clear() {
        setVisibility(INVISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faceRectArr != null) {
            for (Rect rect: faceRectArr) {
                CameraUtils.prepareFaceMatrix(mFaceMatrix,
                        isMirror, displayOrientation, getWidth(), getHeight());
                mFaceRectF.set(rect);
                mFaceMatrix.mapRect(mFaceRectF);
                rect.set(
                        Math.round(mFaceRectF.left),
                        Math.round(mFaceRectF.top),
                        Math.round(mFaceRectF.right),
                        Math.round(mFaceRectF.bottom)
                );
                canvas.drawRect(rect, mPaint);
            }
        }
    }
}
