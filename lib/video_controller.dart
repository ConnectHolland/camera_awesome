import 'package:flutter/widgets.dart';

import 'camerawesome_plugin.dart';

class VideoController {
  Future<void> recordVideo(String filePath, Orientation? orientation) async {
    // We need to refresh camera before using it
    // audio channel need to be ready
    await CamerawesomePlugin.refresh();

    await CamerawesomePlugin.recordVideo(filePath, orientation);
  }

  Future<void> stopRecordingVideo() async {
    await CamerawesomePlugin.stopRecordingVideo();
  }
}
