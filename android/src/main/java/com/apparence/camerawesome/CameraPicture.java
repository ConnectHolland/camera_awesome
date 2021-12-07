package com.apparence.camerawesome;

import static com.apparence.camerawesome.CameraPictureStates.STATE_RELEASE_FOCUS;
import static com.apparence.camerawesome.CameraPictureStates.STATE_REQUEST_PHOTO_AFTER_FOCUS;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.apparence.camerawesome.models.CameraCharacteristicsModel;
import com.apparence.camerawesome.models.FlashMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraPicture implements CameraSession.OnCaptureSession, CameraSettingsManager.CameraSettingsHandler {

    private static String TAG = CameraPicture.class.getName();

    private static final int RECORDER_VIDEO_BITRATE = 10_000_000;
    private static final int RECORDER_VIDEO_FRAME_RATE = 30;

    private final CameraSession mCameraSession;

    private CameraCharacteristicsModel mCameraCharacteristics;

    private CameraDevice mCameraDevice;

    private ImageReader pictureImageReader;

    private Size photoSize;

    private Size videoSize;

    private boolean autoFocus;

    private CaptureRequest.Builder takePhotoRequestBuilder;

    private int orientation;

    private FlashMode flashMode;

    private MediaRecorder recorder;

    private Surface recorderSurface;

    private Context context;

    private CameraPreview cameraPreview;

    // Defaults to false because permission check doesn't include the RECORD_AUDIO permission
    // because it's optional so it's up to the user of this package to request that permission
    // and enable recording of audio.
    private boolean enableAudio = false;

    public CameraPicture(Context context, CameraPreview cameraPreview, CameraSession cameraSession, final CameraCharacteristicsModel cameraCharacteristics) {
        this.context = context;
        this.cameraPreview = cameraPreview;
        mCameraSession = cameraSession;
        mCameraCharacteristics = cameraCharacteristics;
        flashMode = FlashMode.NONE;
        setAutoFocus(true);
    }

    /**
     * captureSize size of photo to use (must be in the available set of size) use CameraSetup to get all
     *
     * @param width
     * @param height
     */
    public void setPhotoSize(int width, int height) {
        this.photoSize = new Size(width, height);
        refresh();
    }

    public void setVideoSize(int width, int height) {
        this.videoSize = new Size(width, height);
        refresh();
    }

    public Size getPhotoSize() {
        return photoSize;
    }

    public void refresh() {
        setAutoFocus(this.autoFocus);

        if (photoSize != null) {
            pictureImageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 2);
            mCameraSession.addPictureSurface(pictureImageReader.getSurface());
        }

        if (videoSize != null) {
            if (recorderSurface == null) {
                // Get a persistent Surface from MediaCodec, don't forget to release when done
                recorderSurface = MediaCodec.createPersistentInputSurface();
            }

            // Prepare and release a dummy MediaRecorder with our surface. Required to allocate an
            // appropriately sized buffer before passing the Surface as the output target to the capture
            // session.
            MediaRecorder dummyRecorder = createRecorder(recorderSurface, createDummyFile("mp4").getAbsolutePath());
            try {
                dummyRecorder.prepare();
            } catch (IOException e) {
                // Throw unchecked exception instead of checked.
                throw new RuntimeException("Prepare media recorder failed: " + e.getMessage());
            }
            dummyRecorder.release();

            mCameraSession.addRecorderSurface(recorderSurface);
        }
    }

    public void recordVideo(final CameraDevice cameraDevice, final String filePath, final int orientation) throws CameraAccessException, IOException {
        final File file = new File(filePath);
        if (file.exists()) {
            Log.e(TAG, "recordVideo : PATH NOT FOUND");
            return;
        }
        if (videoSize == null) {
            Log.e(TAG, "recordVideo : NO SIZE SET");
            return;
        }

        CaptureRequest.Builder recordVideoRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        recordVideoRequestBuilder.addTarget(cameraPreview.getPreviewSurface());
        recordVideoRequestBuilder.addTarget(recorderSurface);

        // Sets FPS for all targets
        recordVideoRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(RECORDER_VIDEO_FRAME_RATE, RECORDER_VIDEO_FRAME_RATE));

        // Start recording repeating requests, which will stop the ongoing preview
        // repeating requests without having to explicitly call `session.stopRepeating`
        mCameraSession.getCaptureSession().setRepeatingRequest(recordVideoRequestBuilder.build(), null, null);

        if (recorder != null) {
            recorder.release();
        }
        recorder = createRecorder(recorderSurface, filePath);

        recorder.setOrientationHint(orientation);
        recorder.prepare();
        recorder.start();
    }

    public void stopRecording() throws IllegalStateException {
        recorder.stop();
    }

    /**
     * Takes a picture from the current device
     *
     * @param cameraDevice     the cameraDevice that
     * @param filePath         the path where to save the picture
     * @param orientation      orientation to use to save the image
     * @param onResultListener fires on success / failure
     * @throws CameraAccessException if camera is not available
     */
    public void takePicture(final CameraDevice cameraDevice, final String filePath, final int orientation, final OnImageResult onResultListener) throws CameraAccessException {
        final File file = new File(filePath);
        this.mCameraDevice = cameraDevice;
        this.orientation = orientation;
        if (file.exists()) {
            Log.e(TAG, "takePicture : PATH NOT FOUND");
            return;
        }
        if (photoSize == null) {
            Log.e(TAG, "takePicture : NO SIZE SET");
            return;
        }
        if (mCameraSession.getCaptureSession() == null) {
            Log.e(TAG, "takePicture: mCameraSession.getCaptureSession() is null");
            return;
        }
        pictureImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try (Image image = reader.acquireNextImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    CameraPicture.this.writeToFile(buffer, file);
                    onResultListener.onSuccess();
                } catch (IOException e) {
                    onResultListener.onFailure("IOError");
                }
            }
        }, null);
        if (autoFocus) {
            mCameraSession.setState(CameraPictureStates.STATE_REQUEST_FOCUS);
        } else {
            captureStillPicture();
        }
    }

    public void setFlashMode(FlashMode flashMode) {
        if (!mCameraCharacteristics.hasFlashAvailable()) {
            return;
        }
        this.flashMode = flashMode;
    }

    public void setCameraCharacteristics(CameraCharacteristicsModel mCameraCharacteristics) {
        this.mCameraCharacteristics = mCameraCharacteristics;
    }

    public void setAutoFocus(boolean autoFocus) {
        this.autoFocus = autoFocus && mCameraCharacteristics.hasAutoFocus();
    }

    public void setRecordAudioEnabled(boolean enableAudio) {
        this.enableAudio = enableAudio;
    }

    public void dispose() {
        if (pictureImageReader != null) {
            pictureImageReader.close();
            pictureImageReader = null;
        }
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        if (recorderSurface != null) {
            recorderSurface.release();
            recorderSurface = null;
        }
    }

    // ---------------------------------------------------
    // PRIVATES
    // ---------------------------------------------------

    private void captureStillPicture() throws CameraAccessException {
        takePhotoRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        takePhotoRequestBuilder.addTarget(pictureImageReader.getSurface());
        switch (flashMode) {
            case NONE:
                takePhotoRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                takePhotoRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case ON:
                takePhotoRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                break;
            case AUTO:
                takePhotoRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            case ALWAYS:
                takePhotoRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                takePhotoRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                break;
        }
        takePhotoRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCameraSession.getZoomArea());
        takePhotoRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);
        mCameraSession.getCaptureSession().stopRepeating();
        mCameraSession.getCaptureSession().capture(takePhotoRequestBuilder.build(), mCaptureCallback, null);
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (mCameraSession.getState() != null && mCameraSession.getState().equals(STATE_REQUEST_PHOTO_AFTER_FOCUS)) {
                mCameraSession.setState(STATE_RELEASE_FOCUS);
            } else {
                mCameraSession.setState(CameraPictureStates.STATE_RESTART_PREVIEW_REQUEST);
            }
        }
    };

    private void refreshFocus() {
        final CaptureRequest.Builder captureBuilder;
        try {
            captureBuilder = mCameraSession.getCameraDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(pictureImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (flashMode == FlashMode.AUTO) {
                takePhotoRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
            }
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    mCameraSession.setState(STATE_RELEASE_FOCUS);
                }
            };
            mCameraSession.getCaptureSession().stopRepeating();
            mCameraSession.getCaptureSession().abortCaptures();
            mCameraSession.getCaptureSession().capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "refreshFocus: ", e);
            e.printStackTrace();
        }
    }

    private void writeToFile(ByteBuffer buffer, File file) throws IOException {
        // outputstream is autoclosed by the try
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            while (0 < buffer.remaining()) {
                outputStream.getChannel().write(buffer);
            }
        }
    }

    private File createDummyFile(String extension) {
        return new File(context.getFilesDir(), "dummy." + extension);
    }

    private MediaRecorder createRecorder(Surface surface, String filePath) {
        MediaRecorder mediaRecorder = new MediaRecorder();
        if (enableAudio) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(filePath);
        mediaRecorder.setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE);
        mediaRecorder.setVideoFrameRate(RECORDER_VIDEO_FRAME_RATE);
        mediaRecorder.setVideoSize(this.videoSize.getWidth(), this.videoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (enableAudio) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }
        mediaRecorder.setInputSurface(surface);

        return mediaRecorder;
    }

    // --------------------------------------------------
    // CameraSession.OnCaptureSession
    // --------------------------------------------------

    @Override
    public void onConfigured(@NonNull CameraCaptureSession session) {
        this.mCameraSession.setCaptureSession(session);
    }

    @Override
    public void onConfigureFailed() {
        this.mCameraSession.setCaptureSession(null);
    }

    @Override
    public void onStateChanged(CameraPictureStates state) {
        if (state == null) {
            return;
        }
        try {
            switch (state) {
                case STATE_REQUEST_PHOTO_AFTER_FOCUS:
                    captureStillPicture();
                    break;
                case STATE_READY_AFTER_FOCUS:
                    refreshFocus();
                    break;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "onStateChanged: ", e);
        }
    }

    // ------------------------------------------------------
    // CameraSettingsManager.CameraSettingsHandler
    // ------------------------------------------------------
    @Override
    public void refreshConfiguration(CameraSettingsManager.CameraSettings settings) {
        takePhotoRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, settings.manualBrightness);
    }

    // --------------------------------------------------
    // OnImageResult interface
    // --------------------------------------------------

    public interface OnImageResult {

        void onSuccess();

        void onFailure(String error);
    }

}
