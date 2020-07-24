package com.example.cameraonedemo;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cameraonedemo.utils.CameraUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    private static final String TAG = "ExampleInstrumentedTest";

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.example.cameraonedemo", appContext.getPackageName());
    }

    @Test
    public void touchAfMap() {
        int previewW = 1000;
        int previewH = 1000;
        int focusW = 0;
        int focusH = 0;

        int x = 0;
        int y = 0;

        // left-top
        Rect acl = CameraUtils.calculateTapArea(
                focusW, focusH, x, y, previewW, previewH, 0, 1f, false
        );
        Rect exp = new Rect(-1000, -1000, -1000, -1000);
        assertEquals(exp.toString(), acl.toString());

        // center
        acl = CameraUtils.calculateTapArea(
                focusW, focusH, previewW / 2f, previewH / 2f, previewW, previewH, 0, 1f, false
        );
        exp = new Rect(0, 0, 0, 0);
        assertEquals(exp.toString(), acl.toString());
    }
}