package com.example.cameraonedemo.encoder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.cameraonedemo.utils.CameraUtils;
import com.example.cameraonedemo.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VideoEncoder {
    private static final String TAG = "VideoEncoder";

    private static final long TIME_OUT = 10 * 1000L;
    private static final int SAMPLE_RATE_IN_HZ_44100 = 44100;
    private LinkedBlockingQueue<byte[]> videoDataQueue = new LinkedBlockingQueue<>();
    private MediaCodec videoCodec;
    private volatile boolean isExit = false;

    private MediaMuxer mediaMuxer;
    private int videoTrack = -1;
    private MediaCodec.BufferInfo bufferInfo;

    // audio
    private MediaCodec audioCodec;
    private int audioTrack = -1;
    private AudioRecord audioRecord;
    private int audioBufferSize = -1;
    private Thread audioThread = null;
    private AudioEncoder audioEncoder = null;

    private volatile boolean muxerStarted = false;
    private final Object mLock = new Object();
    private volatile boolean isStart = false;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private int width;
    private int height;
    private Context context;

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

    public VideoEncoder(Context context, int w, int h) {
        this.width = w;
        this.height = h;
        this.context = context;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                init(width, height);
            }
        });
    }

    private void init(int width, int height) {
        // video
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

        // audio
        MediaFormat audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE_IN_HZ_44100, 2
        );
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
        try {
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (audioCodec != null) {
            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        // audio record
        audioBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_IN_HZ_44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN_HZ_44100,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize
        );

        // muxer
//        File videoFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/mux.mp4");
        File  videoFile = new File(context.getFilesDir().getAbsoluteFile() + "/mux.mp4");
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
                if (audioCodec == null) {
                    Log.e(TAG, "start: audio codec is null");
                    return;
                }

                audioCodec.start();
                audioRecord.startRecording();

                audioEncoder = new AudioEncoder();
                audioThread = new Thread(audioEncoder);
                audioThread.start();

                audioEncoder.startRecord();
                Log.d(TAG, "audio start....");
            }
        });

        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (videoCodec == null) {
                    Log.e(TAG, "start: video codec is null");
                    return;
                }

                videoCodec.start();
                videoBaseTimestamp = System.nanoTime();
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
        executor.submit(new Runnable() {
            @Override
            public void run() {
                audioEncoder.stopRecord();
                if (audioThread != null) {
                    try {
                        audioThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    audioThread = null;
                }
                if (audioCodec != null) {
                    audioCodec.stop();
                    audioCodec.release();
                    audioCodec = null;
                }
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        });
    }

    public void release() {
        executor.shutdown();
    }

    private long videoBaseTimestamp = -1L;
    private void encodeVideo(byte[] data, boolean isEnd) {
        ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = videoCodec.getOutputBuffers();
        int index = videoCodec.dequeueInputBuffer(TIME_OUT);
        if (index >= 0) {
            ByteBuffer byteBuffer = inputBuffers[index];
            byteBuffer.clear();
            if (!isEnd) {
                byteBuffer.put(data);
                long time = (System.nanoTime() - videoBaseTimestamp) / 1000;
                videoCodec.queueInputBuffer(index, 0, data.length,
                        time, MediaCodec.BUFFER_FLAG_KEY_FRAME);
            } else {
                videoCodec.queueInputBuffer(index, 0, 0,
                        0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            if (bufferInfo == null) {
                bufferInfo = new MediaCodec.BufferInfo();
            }
            int dequeueOutputBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, TIME_OUT);
            if (dequeueOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (mLock) {
                    videoTrack = mediaMuxer.addTrack(videoCodec.getOutputFormat());
                    Log.d(TAG, "add video track-->" + videoTrack);
                    if (audioTrack >= 0 && videoTrack >= 0) {
                        mediaMuxer.start();
                        muxerStarted = true;
                        Log.d(TAG, "encodeVideo: mux start");
                    }
                }
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

                if (muxerStarted && bufferInfo.size != 0) {
                    Log.d(TAG, "video: pts = " + bufferInfo.presentationTimeUs);
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

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_AUDIO_STEP = 2;
    private static final int MSG_QUIT = 3;

    class AudioEncoder implements Runnable {
        private boolean mIsReady;
        private boolean mIsRecording = true;
        private AudioHandler mHandler;
        private final Object mReadyFence = new Object();
        private long mBaseTimeStamp = -1;

        @Override
        public void run() {
            Log.d(TAG, "audio encoder loop start...");
            Looper.prepare();
            mHandler = new AudioHandler(this);
            synchronized (mReadyFence){
                mIsReady = true;
                mReadyFence.notify();
            }
            Looper.loop();

            // clear flag and release handler.
            synchronized (mReadyFence){
                mIsReady = false;
                mHandler = null;
            }
            Log.d(TAG, "audio encoder loop end...");
        }

        void startRecord() {
            synchronized (mReadyFence){
                if(!mIsReady){
                    try {
                        mReadyFence.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mHandler.sendEmptyMessage(MSG_START_RECORDING);
            }
        }

        void stopRecord() {
            mHandler.sendEmptyMessage(MSG_STOP_RECORDING);
        }

        void handleStartRecord() {
            mBaseTimeStamp = System.nanoTime();
            mHandler.sendEmptyMessage(MSG_AUDIO_STEP);
        }

        void handleAudioStep() {
            if (mIsRecording) {
                audioStep();
                mHandler.sendEmptyMessage(MSG_AUDIO_STEP);
            } else {
                drainEncoder();
                mHandler.sendEmptyMessage(MSG_QUIT);
            }
        }

        private void drainEncoder() {
            while (!audioStep()) {
                if (Logger.DEBUG) {
                    Log.d(TAG, "loop audio step...");
                }
            }
        }

        void handleStopRecord() {
            mIsRecording = false;
        }

        private boolean audioStep() {
            int index = audioCodec.dequeueInputBuffer(0);
            if (index >= 0) {
                final ByteBuffer buffer = getInputBuffer(audioCodec, index);
                buffer.clear();
                int length = audioRecord.read(buffer, audioBufferSize);
                if (length > 0) {
                    if (mBaseTimeStamp != -1) {
                        long nano = System.nanoTime();
                        long time = (nano - mBaseTimeStamp) / 1000;
                        audioCodec.queueInputBuffer(index,
                                0,
                                length,
                                time,
                                mIsRecording ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        );
                    } else {
                        audioCodec.queueInputBuffer(index,
                                0,
                                length,
                                0,
                                mIsRecording ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        );
                    }
                }
            }

            MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
            int outIndex;
            do {
                outIndex = audioCodec.dequeueOutputBuffer(mInfo, 0);
                if (outIndex >= 0) {
                    if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "audio end");
                        audioCodec.releaseOutputBuffer(outIndex, false);
                        return true;
                    }

                    ByteBuffer buffer = getOutputBuffer(audioCodec, outIndex);
                    buffer.position(mInfo.offset);
                    if (muxerStarted && mInfo.presentationTimeUs > 0) {
                        try {
                            if(isStart){
                                Log.d(TAG, "audioStep: pts = " + mInfo.presentationTimeUs);
                                mediaMuxer.writeSampleData(audioTrack, buffer, mInfo);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    audioCodec.releaseOutputBuffer(outIndex, false);
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (mLock) {
                        audioTrack = mediaMuxer.addTrack(audioCodec.getOutputFormat());
                        Log.d(TAG, "add audio track-->" + audioTrack);
                        if (audioTrack >= 0 && videoTrack >= 0) {
                            mediaMuxer.start();
                            muxerStarted = true;
                            Log.d(TAG, "encodeVideo: mux start");
                        }
                    }
                }
            } while (outIndex >= 0);

            return false;
        }

        private ByteBuffer getInputBuffer(MediaCodec codec, int index) {
            return codec.getInputBuffer(index);
        }

        private ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
            return codec.getOutputBuffer(index);
        }
    }

    private static class AudioHandler extends Handler {

        private WeakReference<AudioEncoder> encoderWeakReference;

        AudioHandler(AudioEncoder encoder) {
            encoderWeakReference = new WeakReference<>(encoder);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            AudioEncoder audioEncoder = encoderWeakReference.get();
            if (audioEncoder == null) {
                return;
            }
            switch (what) {
                case MSG_START_RECORDING:
                    Log.d(TAG, "handleMessage: MSG_START_RECORDING");
                    audioEncoder.handleStartRecord();
                    break;
                case MSG_STOP_RECORDING:
                    Log.d(TAG, "handleMessage: MSG_STOP_RECORDING");
                    audioEncoder.handleStopRecord();
                    break;
                case MSG_AUDIO_STEP:
                    audioEncoder.handleAudioStep();
                    break;
                case MSG_QUIT:
                    Looper looper = Looper.myLooper();
                    if (looper != null) {
                        looper.quit();
                    }
                    Log.d(TAG, "handleMessage: MSG_QUIT");
                    break;
            }
        }
    }
}
