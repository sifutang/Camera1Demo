package com.example.cameraonedemo;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;

public class VideoPlayActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener {

    private static final String TAG = "VideoPlayActivity";
    private MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_play);

        TextureView textureView = findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: ");
        File videoFile = new File(getFilesDir().getAbsoluteFile() + "/mux.mp4");
        mMediaPlayer = MediaPlayer.create(this, Uri.fromFile(videoFile));
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(new Surface(surface));
            mMediaPlayer.start();
        } else {
            Log.e(TAG, "onSurfaceTextureAvailable: create media player failed, file exit = "
                    + videoFile.exists());
            Toast.makeText(this, "please check file exists?", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged: w = " + width + ", h = " + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}