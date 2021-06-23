/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rocky.flutter_plugin;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;


public class VoiceRecorder {

    private static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000, 11025, 22050, 44100};

    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int AMPLITUDE_THRESHOLD = 1500;
    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;

    public static abstract class Callback {


        public void onVoiceStart() {
        }

        public void onVoice(byte[] data, int size) {
        }

        public void onVoiceEnd() {
        }
    }

    private final Callback mCallback;

    private AudioRecord mAudioRecord;

    private Thread mThread;

    private byte[] mBuffer;

    private final Object mLock = new Object();

    private long mLastVoiceHeardMillis = Long.MAX_VALUE;

    private long mVoiceStartedMillis;

    public VoiceRecorder(Callback callback) {
        mCallback = callback;
    }


    public void start() {
        // Stop recording if it is currently ongoing.
        stop();
        // Try to create a new recording session.
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            throw new RuntimeException("Cannot instantiate VoiceRecorder");
        }
        // Start recording.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
            mAudioRecord.startRecording();
        }
        // Start processing the captured audio.
        mThread = new Thread(new ProcessVoice());
        mThread.start();
    }


    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    public void stop() {
        synchronized (mLock) {
            dismiss();
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
            if (mAudioRecord != null) {
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }
            mBuffer = null;
        }
    }


    public void dismiss() {
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }
    }


    public int getSampleRate() {
        if (mAudioRecord != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                return mAudioRecord.getSampleRate();
            }
        }
        return 0;
    }


    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
             int sizeInBytes = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
                sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            }
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, CHANNEL, ENCODING, sizeInBytes);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mBuffer = new byte[sizeInBytes];
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }


    private class ProcessVoice implements Runnable {

        @TargetApi(Build.VERSION_CODES.CUPCAKE)
        @Override
        public void run() {
            while (true) {
                synchronized (mLock) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    final int size = mAudioRecord.read(mBuffer, 0, mBuffer.length);
                    final long now = System.currentTimeMillis();
                    if (isHearingVoice(mBuffer, size)) {
                        if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
                            mVoiceStartedMillis = now;
                            mCallback.onVoiceStart();
                        }
                        mCallback.onVoice(mBuffer, size);
                        mLastVoiceHeardMillis = now;
                        if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                            end();
                        }
                    } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                        mCallback.onVoice(mBuffer, size);
                        if (now - mLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS) {
                            end();
                        }
                    }
                }
            }
        }

        private void end() {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }

        private boolean isHearingVoice(byte[] buffer, int size) {
            for (int i = 0; i < size - 1; i += 2) {
                // The buffer has LINEAR16 in little endian.
                int s = buffer[i + 1];
                if (s < 0) s *= -1;
                s <<= 8;
                s += Math.abs(buffer[i]);
                if (s > AMPLITUDE_THRESHOLD) {
                    return true;
                }
            }
            return false;
        }

    }

}
