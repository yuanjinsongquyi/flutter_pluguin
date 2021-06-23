
import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

class RecorderStreamPlugin {
  static const MethodChannel _channel =
      const MethodChannel('flutter_plugin');
  static StreamController<Uint8List> controller = StreamController<Uint8List>();
  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
  static Stream<Uint8List> getAudioEvent() {
    _channel.setMethodCallHandler((call) {
      switch (call.method){
        case 'addEvent':
          Uint8List event = call.arguments as Uint8List;
          controller.add(event);
          return Future.value('');
        default:
          print('Unknowm method ${call.method}');
          throw MissingPluginException();
      }
    });
    return controller.stream.asBroadcastStream();
  }

  static Future<void> startRecorder() async {
    await _channel.invokeMethod('startRecorder');
  }
  static Future<void> endRecorder() async {
    await _channel.invokeMethod('endRecorder');
  }
}
