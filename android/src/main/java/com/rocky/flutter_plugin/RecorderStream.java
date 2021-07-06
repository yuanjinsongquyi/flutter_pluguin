package com.rocky.flutter_plugin;

import java.util.Arrays;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** FlutterPlugin */
public class RecorderStream implements FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  MethodChannel channel;
  VoiceRecorder mVoiceRecorder;
  private Handler uiThreadHandler = new Handler(Looper.getMainLooper());
  VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

    @Override
    public void onVoiceStart() {

    }

    @Override
    public void onVoice(final byte[] data, final int size) {
      uiThreadHandler.post(new Runnable() {
                             @Override
                             public void run() {
                               channel.invokeMethod("addEvent", Arrays.copyOfRange(data, 0, size));
                             }
                           }
                           );

    }

    @Override
    public void onVoiceEnd() {

    }

  };
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_plugin");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("startRecorder")) {
      if (mVoiceRecorder != null) {
        mVoiceRecorder.stop();
      }
      mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
      mVoiceRecorder.start();
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("endRecorder")) {
      if (mVoiceRecorder != null) {
        mVoiceRecorder.stop();
        mVoiceRecorder = null;
      }
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
}
