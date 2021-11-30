package com.apparence.camerawesome;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.PluginRegistry;

public class CameraPermissions {

    private static final String TAG = CameraPermissions.class.getName();

    // RECORD_AUDIO permission is not included because it's not required to be able to record a video.
    private static final String[] permissions = new String[]{CAMERA};

    private boolean permissionGranted = false;


    public String[] checkPermissions(Activity activity) {
        if (activity == null) {
            throw new RuntimeException("NULL_ACTIVITY");
        }
        List<String> permissionsToAsk = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToAsk.add(permission);
            }
        }
        this.permissionGranted = permissionsToAsk.size() == 0;
        return permissionsToAsk.toArray(new String[0]);
    }

    public boolean hasPermissionGranted() {
        return permissionGranted;
    }
}
