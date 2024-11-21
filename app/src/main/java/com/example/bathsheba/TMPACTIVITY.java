package com.example.bathsheba;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TMPACTIVITY extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int SCREEN_WIDTH = 1280;
    private static final int SCREEN_HEIGHT = 720;
    private static final int SCREEN_DPI = 300;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private EglBase eglBase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize MediaProjectionManager
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // Request MediaProjection permission
        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(result.getResultCode(), result.getData());
                        startScreenCapture();
                    }
                }
        );

        launcher.launch(mediaProjectionManager.createScreenCaptureIntent());

        // Initialize WebRTC
        initWebRTC();
    }

    private void initWebRTC() {
        // Initialize PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        eglBase = EglBase.create();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        // Create PeerConnection
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new SimplePeerConnectionObserver());
    }

    private void startScreenCapture() {
        imageReader = ImageReader.newInstance(SCREEN_WIDTH, SCREEN_HEIGHT, PixelFormat.RGBA_8888, 2);
        Surface surface = imageReader.getSurface();

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, null, null
        );

        videoSource = peerConnectionFactory.createVideoSource(false);
        videoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource);
        videoTrack.setEnabled(true);

        // Process captured frames and send them to WebRTC
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                processImage(image);
                image.close();
            }
        }, null);

        // Add video track to PeerConnection
        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(videoTrack);
        peerConnection.addStream(mediaStream);
    }

    private void processImage(Image image) {
        // Get image data
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();

        // Extract and encode frame for WebRTC
        VideoFrame.Buffer videoBuffer = new VideoFrame.Buffer() {
            // Implement methods to adapt the frame into WebRTC VideoFrame.Buffer
        };
        VideoFrame frame = new VideoFrame(videoBuffer, 0, System.currentTimeMillis());

        videoSource.getCapturerObserver().onFrameCaptured(frame);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
        if (eglBase != null) {
            eglBase.release();
        }
    }

    private static class SimplePeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
        @Override
        public void onIceConnectionReceivingChange(boolean b) {}
        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
        @Override
        public void onAddStream(MediaStream mediaStream) {}
        @Override
        public void onRemoveStream(MediaStream mediaStream) {}
        @Override
        public void onDataChannel(org.webrtc.DataChannel dataChannel) {}
        @Override
        public void onRenegotiationNeeded() {}
    }
}
