package com.example.cameraonedemo;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class VideoPlayActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener,
        SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private static final String TAG = "VideoPlayActivity";
    private MediaPlayer mMediaPlayer;

    private SeekBar mSeekBar;
    private TextView mDurationTextView;
    private TextureView mTextureView;
    private int mLastMediaPosition = -1;
    private boolean mIsPlaying = false;
    private boolean mIsStopped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_play);

        mTextureView = findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(this);

        mSeekBar = findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(this);

        mDurationTextView = findViewById(R.id.duration_text);

        findViewById(R.id.start_btn).setOnClickListener(this);
        findViewById(R.id.stop_btn).setOnClickListener(this);
        findViewById(R.id.pause_btn).setOnClickListener(this);
        findViewById(R.id.resume_btn).setOnClickListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: ");

        File videoFile = new File(getFilesDir().getAbsoluteFile() + "/test.mp4");
        mMediaPlayer = MediaPlayer.create(this, Uri.fromFile(videoFile));
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(new Surface(surface));
            int videoDuration = mMediaPlayer.getDuration();
            mSeekBar.setMax(videoDuration);
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mIsPlaying = false;
                    Log.d(TAG, "onCompletion: done");
                }
            });
            Log.d(TAG, "onSurfaceTextureAvailable: duration = " + videoDuration);
        } else {
            Log.e(TAG, "onSurfaceTextureAvailable: create media player failed, file exit = "
                    + videoFile.exists());
            Toast.makeText(this, "Please record first", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged: w = " + width + ", h = " + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mMediaPlayer != null) {
            if (mIsPlaying) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        int position =  mMediaPlayer.getCurrentPosition();
        if (position < mLastMediaPosition) {
            return;
        }

        mLastMediaPosition = position;
        mSeekBar.setProgress(position);

        // MS -> S
        position = position / 1000;
        int hour = position / 3600;
        int minus = (position - hour * 3600) / 60;
        int second = position - hour * 3600 - minus * 60;
        String time;
        if (hour > 9) {
            time = String.valueOf(hour);
        } else if (hour > 0){
            time = "0" + hour;
        } else {
            time = "00:";
        }

        if (minus > 9) {
            time += String.valueOf(minus);
        } else if (minus > 0) {
            time += "0" + minus;
        } else {
            time += "00:";
        }

        if (second > 9) {
            time += String.valueOf(second);
        } else if (second > 0) {
            time += "0" + second;
        } else {
            time += "00";
        }
        mDurationTextView.setText(time);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        Log.d(TAG, "onProgressChanged: " + progress + ", fromUser = " + fromUser);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStartTrackingTouch: ");
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStopTrackingTouch: ");
        mMediaPlayer.seekTo(seekBar.getProgress());
    }

    @Override
    public void onClick(View v) {
        if (mMediaPlayer == null) {
            return;
        }

        switch (v.getId()) {
            case R.id.start_btn:
                resetUI();
            case R.id.resume_btn:
                if (mIsStopped) {
                    resetUI();
                    try {
                        mMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mIsStopped = false;
                }
                if (!mIsPlaying) {
                    mMediaPlayer.start();
                    mIsPlaying = true;
                }
                break;
            case R.id.stop_btn:
                if (mIsPlaying) {
                    mMediaPlayer.stop();
                    mIsPlaying = false;
                    mIsStopped = true;
                }
                break;
            case R.id.pause_btn:
                if (mIsPlaying) {
                    mMediaPlayer.pause();
                    mIsPlaying = false;
                }
                break;
            default:
                break;
        }
    }

    private void resetUI() {
        mLastMediaPosition = -1;
        mDurationTextView.setText("00:00:00");
        mSeekBar.setProgress(0);
    }
}