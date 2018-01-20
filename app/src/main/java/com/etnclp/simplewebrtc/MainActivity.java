package com.etnclp.simplewebrtc;

/*
  MainActivity.java
  SimpleWebRTC

  Created by Erdi T. on 19.01.2018.
  Copyright © 2018 Mirana Software. All rights reserved.
 */

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import com.etnclp.simplewebrtc.WebRTCUtil.RtcListener;

public class MainActivity extends Activity implements RtcListener {

    private MediaStream remoteStream;
    private MediaStream localStream;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks remoteRender;
    private VideoRenderer.Callbacks localRender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vsv = findViewById(R.id.remote);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
            }
        });

        remoteRender = VideoRendererGui.create(0, 0, 100, 50, VideoRendererGui.ScalingType.SCALE_ASPECT_BALANCED, true);
        localRender = VideoRendererGui.create(0, 50, 100, 50, VideoRendererGui.ScalingType.SCALE_ASPECT_BALANCED, true);

        String roomName = "2f36f758-0db1-4691-a4b6-e30cdc5dd11d";
        WebRtcClient client = WebRtcClient.getInstance();
        client.setWebRTCListener(this);
        client.start(roomName);
    }

    public void setRemoteStream(MediaStream remoteStream) {
        this.remoteStream = remoteStream;
        this.remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
    }

    public void setLocalStream(MediaStream localStream) {
        this.localStream = localStream;
        this.localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
    }

    /**
     * Lifecycle
     */

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
    }

    /**
     * RtcListener
     */

    @Override
    public void onLocalStream(MediaStream localStream) {
        setLocalStream(localStream);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream) {
        setRemoteStream(remoteStream);
    }

}
