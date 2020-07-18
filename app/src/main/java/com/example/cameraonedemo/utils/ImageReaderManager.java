package com.example.cameraonedemo.utils;

import android.graphics.ImageFormat;
import android.media.ImageReader;

public class ImageReaderManager {

    private ImageReader jpegImageReader;
    private volatile boolean isRelease = false;

    private static class H {
        private static final ImageReaderManager INSTANCE = new ImageReaderManager();
    }

    private ImageReaderManager() { }

    public static ImageReaderManager getInstance() {
        return H.INSTANCE;
    }

    public ImageReader getJpegImageReader(int w, int h) {
        if (jpegImageReader == null ||
                w != jpegImageReader.getWidth() ||
                h != jpegImageReader.getHeight()) {
            if (jpegImageReader != null) {
                jpegImageReader.close();
            }

            jpegImageReader = ImageReader.newInstance(w, h, ImageFormat.JPEG, 2);
        }

        return jpegImageReader;
    }

    public void release() {
        if (jpegImageReader != null) {
            jpegImageReader.close();
            jpegImageReader = null;
        }
        isRelease = true;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!isRelease) {
            release();
        }
    }
}
