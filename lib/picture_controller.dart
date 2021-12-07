import 'package:flutter/widgets.dart';

import 'camerawesome_plugin.dart';

class PictureController {
  Future<void> takePicture(String filePath, Orientation? orientation) async {
    await CamerawesomePlugin.takePhoto(filePath, orientation);
  }
}
