package com.apparence.camerawesome;

import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.apparence.camerawesome.models.CameraCharacteristicsModel;
import com.apparence.camerawesome.sensors.SensorOrientation;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class CameraSetup {

    private Context context;

    private String mCameraId;

    private CameraManager mCameraManager;

    private Activity activity;

    private int currentOrientation = ORIENTATION_UNKNOWN;

    private int sensorOrientation;

    private OrientationEventListener orientationEventListener;

    private boolean facingFront;

    private CameraCharacteristicsModel characteristicsModel;

    private SensorOrientation sensorOrientationListener;

    private final int deviceNaturalOrientation;

    CameraSetup(Context context, Activity activity, SensorOrientation sensorOrientationListener) {
        this.context = context;
        this.activity = activity;
        this.sensorOrientationListener = sensorOrientationListener;
        this.deviceNaturalOrientation = getDeviceNaturalOrientation(activity);
    }

    void chooseCamera(CameraSensor sensor) throws CameraAccessException {
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (mCameraManager == null) {
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "cannot init CameraStateManager");
        }
        facingFront = sensor.equals(CameraSensor.FRONT);
        for (String cameraId : mCameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing == null
                    || (sensor == CameraSensor.FRONT && facing != CameraCharacteristics.LENS_FACING_FRONT)
                    || (sensor == CameraSensor.BACK && facing != CameraCharacteristics.LENS_FACING_BACK)) {
                continue;
            }
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                continue;
            }
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            this.characteristicsModel = new CameraCharacteristicsModel.Builder()
                    .withMaxZoom(characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))
                    .withAvailablePreviewZone(characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))
                    .withAutoFocus(characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES))
                    .withFlash(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                    .withAeCompensationRange(characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE))
                    .withAeCompensationStep(characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP))
                    .build();
            mCameraId = cameraId;
            return;
        }
        if (mCameraId == null) {
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "cannot find sensor");
        }
    }

    public void listenOrientation() {
        if (orientationEventListener != null) {
            return;
        }
        final OrientationEventListener orientationEventListener = new OrientationEventListener(activity.getApplicationContext()) {
            @Override
            public void onOrientationChanged(int i) {
                if (i == ORIENTATION_UNKNOWN) {
                    return;
                }
                currentOrientation = (i + 45) / 90 * 90;
                if (currentOrientation == 360)
                    currentOrientation = 0;
                if (sensorOrientationListener != null)
                    sensorOrientationListener.notify(currentOrientation);
            }
        };
        orientationEventListener.enable();
    }

    /**
     * Returns the natural orientation of the device: Configuration.ORIENTATION_LANDSCAPE or
     * Configuration.ORIENTATION_PORTRAIT.
     * The result should be consistent no matter the orientation of the device
     */
    public static int getDeviceNaturalOrientation(@NonNull final Context context) {
        //based on : http://stackoverflow.com/a/9888357/878126
        final WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Configuration config = context.getResources().getConfiguration();
        final int rotation = windowManager.getDefaultDisplay().getRotation();
        if (((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE)
                || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
                config.orientation == Configuration.ORIENTATION_PORTRAIT))
            return Configuration.ORIENTATION_LANDSCAPE;
        else
            return Configuration.ORIENTATION_PORTRAIT;
    }

    Size[] getOutputSizes() throws CameraAccessException {
        if (mCameraManager == null) {
            throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "cannot init CameraStateManager");
        }
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getOutputSizes(ImageFormat.JPEG);
    }

    /**
     * calculate orientation for exif
     * <p>
     * Computes rotation required to transform from the camera sensor orientation to the device's
     * current orientation in degrees. This value can be used as orientation in the exif data.
     *
     * @see CaptureRequest#JPEG_ORIENTATION
     */
    public int getOrientation(int requestedOrientation) {
        int currentDeviceOrientationDegrees = currentOrientation;

        if (requestedOrientation != Configuration.ORIENTATION_UNDEFINED) {
            // If we want to force portrait and the device natural orientation is landscape, or if
            // we want to force landscape and the device natural orientation is portrait, we want
            // the device to be either in 90 or 270 degrees to get the forced orientation. If that's
            // not the case force it to be 90.
            if ((requestedOrientation == Configuration.ORIENTATION_PORTRAIT && this.deviceNaturalOrientation == Configuration.ORIENTATION_LANDSCAPE ||
                    requestedOrientation == Configuration.ORIENTATION_LANDSCAPE && this.deviceNaturalOrientation == Configuration.ORIENTATION_PORTRAIT)
                    && currentDeviceOrientationDegrees != 90 && currentDeviceOrientationDegrees != 270) {
                currentDeviceOrientationDegrees = 90;
            }
            // If we want to force portrait and the device natural orientation is portrait, or if
            // we want to force landscape and the device natural orientation is landscape, we want
            // the device to be either in 0 or 180 degrees to get the forced orientation. If that's
            // not the case force it to be 0.
            else if ((requestedOrientation == Configuration.ORIENTATION_PORTRAIT && this.deviceNaturalOrientation == Configuration.ORIENTATION_PORTRAIT ||
                    requestedOrientation == Configuration.ORIENTATION_LANDSCAPE && this.deviceNaturalOrientation == Configuration.ORIENTATION_LANDSCAPE)
                    && currentDeviceOrientationDegrees != 0 && currentDeviceOrientationDegrees != 180) {
                currentDeviceOrientationDegrees = 0;
            }
        }

        // Reverse device orientation for front-facing cameras
        final int sensorOrientationOffset =
                (currentDeviceOrientationDegrees == ORIENTATION_UNKNOWN)
                        ? 0
                        : (facingFront) ? -currentDeviceOrientationDegrees : currentDeviceOrientationDegrees;

        // Calculate desired orientation relative to camera orientation to make
        // the image/video upright relative to the device orientation
        return (sensorOrientationOffset + sensorOrientation + 360) % 360;
    }

    /**
     * Used to wrap CameraCharacteristics in a simpler model
     *
     * @return CameraCharacteristics
     */
    public CameraCharacteristicsModel getCharacteristicsModel() {
        return characteristicsModel;
    }

    // --------------------------------------------
    // GETTERS
    // --------------------------------------------

    public String getCameraId() {
        return mCameraId;
    }

    public int getCurrentOrientation() {
        return currentOrientation;
    }

}
