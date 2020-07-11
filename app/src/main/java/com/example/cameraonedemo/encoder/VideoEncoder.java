package com.example.cameraonedemo.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.cameraonedemo.utils.CameraUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VideoEncoder {
    private static final String TAG = "VideoEncoder";

    private static final long TIME_OUT = 10 * 1000L;

    private LinkedBlockingQueue<byte[]> videoDataQueue = new LinkedBlockingQueue<>();
    private MediaCodec videoCodec;
    private volatile boolean isExit = false;

    private MediaMuxer mediaMuxer;
    private int videoTrack = -1;
    private MediaCodec.BufferInfo bufferInfo;

    private volatile boolean isStart = false;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private int width;
    private int height;

    public interface VideoEncodeListener {
        void onVideoEncodeStart();
        void onVideoEncodeEnd();
    }
    private VideoEncodeListener encodeListener;

    public void setVideoEncodeListener(VideoEncodeListener listener) {
        encodeListener = listener;
    }

    public void addVideoData(byte[] data) {
        if (data == null) {
            Log.e(TAG, "addVideoData: data is null");
        } else {
            videoDataQueue.offer(data);
        }
    }

    public VideoEncoder(int w, int h) {
        this.width = w;
        this.height = h;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                init(width, height);
            }
        });
    }

    private void init(int width, int height) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        try {
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (videoCodec != null) {
            videoCodec.configure(
                    mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        File videoFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/mux.mp4");
        try {
            mediaMuxer = new MediaMuxer(videoFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.setOrientationHint(90);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "VideoEncoder: path = " + videoFile.getAbsolutePath());
    }

    public void start() {
        isStart = true;
        Log.d(TAG, "encode start...");
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (videoCodec == null) {
                    Log.d(TAG, "start: codec is null");
                    return;
                }

                videoCodec.start();
                if (encodeListener != null) {
                    encodeListener.onVideoEncodeStart();
                }

                while (!isExit) {
                    byte[] data = videoDataQueue.poll();
                    if (data != null) {
                        CameraUtils.nv21ToNv12(data, width, height);
                        encodeVideo(data, false);
                    }
                }

                // send EOF flag
                Log.d(TAG, "send eof flag");
                encodeVideo(null, true);

                // release
                videoCodec.release();
                mediaMuxer.stop();
                mediaMuxer.release();

                if (encodeListener != null) {
                    encodeListener.onVideoEncodeEnd();
                }
                Log.d(TAG, "encode end...");
            }
        });
    }

    public boolean isStart() {
        Log.d(TAG, "isStart: " + isStart);
        return isStart;
    }

    public void stop() {
        isExit = true;
        isStart = false;
    }

    public void release() {
        executor.shutdown();
    }

    private void encodeVideo(byte[] data, boolean isEnd) {
        ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = videoCodec.getOutputBuffers();
        int index = videoCodec.dequeueInputBuffer(TIME_OUT);
        if (index >= 0) {
            ByteBuffer byteBuffer = inputBuffers[index];
            byteBuffer.clear();
            if (!isEnd) {
                byteBuffer.put(data);
                videoCodec.queueInputBuffer(index, 0, data.length,
                        System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_KEY_FRAME);
            } else {
                videoCodec.queueInputBuffer(index, 0, 0,
                        System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            if (bufferInfo == null) {
                bufferInfo = new MediaCodec.BufferInfo();
            }
            int dequeueOutputBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, TIME_OUT);
            if (dequeueOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                videoTrack = mediaMuxer.addTrack(videoCodec.getOutputFormat());
                mediaMuxer.start();
                Log.d(TAG, "encodeVideo: mux start");
            } else if (dequeueOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "encodeVideo: INFO_OUTPUT_BUFFERS_CHANGED");
                outputBuffers = videoCodec.getOutputBuffers();
            }

            while (dequeueOutputBufferIndex >= 0) {
                ByteBuffer buffer = outputBuffers[dequeueOutputBufferIndex];
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "encodeVideo: BUFFER_FLAG_CODEC_CONFIG ");
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    mediaMuxer.writeSampleData(videoTrack, buffer, bufferInfo);
                }
                videoCodec.releaseOutputBuffer(dequeueOutputBufferIndex, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "encodeVideo: BUFFER_FLAG_END_OF_STREAM");
                    break;
                } else {
                    dequeueOutputBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 10 * 1000);
                }
            }
        }
    }
}
