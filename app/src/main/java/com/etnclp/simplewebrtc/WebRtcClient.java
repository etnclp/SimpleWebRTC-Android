package com.etnclp.simplewebrtc;

/*
  WebRtcClient.java
  SimpleWebRTC

  Created by Erdi T. on 19.01.2018.
  Copyright © 2018 Mirana Software. All rights reserved.
 */

import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;

import com.etnclp.simplewebrtc.WebRTCUtil.Command;
import com.etnclp.simplewebrtc.WebRTCUtil.RtcListener;

public class WebRtcClient implements SdpObserver, PeerConnection.Observer {

    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private static WebRtcClient mInstance;

    private Socket socket;
    private RtcListener mListener;
    private PeerConnectionFactory factory;
    private MediaStream localStream;
    private PeerConnection pc;

    private String mClientId = null;
    private String mSid = null;

    synchronized static WebRtcClient getInstance() {
        if (mInstance == null) {
            mInstance = new WebRtcClient();
        }
        return mInstance;
    }

    private WebRtcClient() {}

    void setWebRTCListener(RtcListener listener) {
        mListener = listener;

        PeerConnectionFactory.initializeAndroidGlobals(mListener, true, true, true, VideoRendererGui.getEGLContext());
        factory = new PeerConnectionFactory();
        localStream = createLocalStream();
    }

    void start(final String roomName) {
        final HashMap<String, Command> commandMap = new HashMap<>();
        commandMap.put("offer", new CreateAnswerCommand());
        commandMap.put("answer", new SetRemoteSDPCommand());
        commandMap.put("candidate", new AddIceCandidateCommand());

        try {
            socket = IO.socket("https://sandbox.simplewebrtc.com");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                joinRoom(roomName);
            }
        });

        socket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String type = data.getString("type");
                    if (commandMap.containsKey(type)) {
                        commandMap.get(type).execute(data);
                    } else {
                        Log.e(TAG, "Unknown message type: " + type);
                    }
                } catch (JSONException | NullPointerException e) {
                    //e.printStackTrace();
                }
            }
        });

        socket.on("remove", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                for (Object arg : args) {
                    try {
                        JSONObject object = (JSONObject) arg;
                        String id = object.getString("id");
                        if (id.equals(mClientId)) {
                            Log.e(TAG, "Client remove: " + id);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        socket.on("stunservers", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                for (Object arg : args) {
                    try {
                        JSONArray array = (JSONArray) arg;
                        JSONObject object = array.getJSONObject(0);
                        String urls = object.getString("urls");
                        WebRTCUtil.stunServer = new PeerConnection.IceServer(urls);
                        Log.e(TAG, "Stun server set");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        socket.on("turnservers", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                for (Object arg : args) {
                    try {
                        JSONArray array = (JSONArray) arg;
                        JSONObject object = array.getJSONObject(0);
                        String urls = object.getString("urls");
                        String username = object.getString("username");
                        String credential = object.getString("credential");
                        WebRTCUtil.turnServer = new PeerConnection.IceServer(urls, username, credential);
                        Log.e(TAG, "Turn server set");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        socket.connect();
    }

    private void joinRoom(String roomName) {
        Log.e(TAG, "joinRoom: ");
        socket.emit("join", roomName, new Ack() {
            @Override
            public void call(Object... args) {
                for (Object arg : args) {
                    try {
                        JSONObject object = (JSONObject) arg;
                        JSONObject clients = object.getJSONObject("clients");
                        Iterator<String> keys = clients.keys();

                        if (keys.hasNext()) {
                            mClientId = keys.next();
                            createOffer();
                        }
                    } catch (JSONException | NullPointerException e) {
                        e.printStackTrace();
                    }

                }

            }
        });
    }

    private PeerConnection getPeer() {
        if (pc == null) {
            pc = factory.createPeerConnection(WebRTCUtil.iceServers(), WebRTCUtil.peerConnectionConstraints(), this);
            if (localStream != null) {
                pc.addStream(localStream);
            } else {
                pc.addStream(createLocalStream());
            }
        }
        return pc;
    }

    private MediaStream createLocalStream() {
        VideoSource videoSource = factory.createVideoSource(getVideoCapturer(), WebRTCUtil.videoConstraints());
        AudioSource audioSource = factory.createAudioSource(WebRTCUtil.audioConstraints());

        MediaStream localMS = factory.createLocalMediaStream("MyStream");
        localMS.addTrack(factory.createVideoTrack("MyVideo", videoSource));
        localMS.addTrack(factory.createAudioTrack("MyAudio", audioSource));
        mListener.onLocalStream(localMS);
        return localMS;
    }

    private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }

    /**
     *
     */

    private class Sender {

        private JSONObject mObject;

        Sender() {
            mObject = new JSONObject();
        }

        private Sender put(String name, Object value) {
            try {
                mObject.put(name, value);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return this;
        }

        private void send() {
            Log.d(TAG, "message send: " + mObject.toString());
            socket.emit("message", mObject);
        }
    }

    /**
     * Commands
     */

    private void createOffer() {
        Log.d(TAG, "CreateOffer");
        getPeer().createOffer(this, WebRTCUtil.offerConstraints());
    }

    private class CreateAnswerCommand implements Command {
        public void execute(JSONObject data) throws JSONException {
            Log.d(TAG, "CreateAnswerCommand");
            mClientId = data.getString("from");
            mSid = data.getString("sid");
            JSONObject payload = data.getJSONObject("payload");

            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            getPeer().setRemoteDescription(WebRtcClient.this, sdp);
            getPeer().createAnswer(WebRtcClient.this, WebRTCUtil.answerConstraints());
        }
    }

    private class SetRemoteSDPCommand implements Command {
        public void execute(JSONObject data) throws JSONException {
            Log.d(TAG, "SetRemoteSDPCommand");
            JSONObject payload = data.getJSONObject("payload");
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            getPeer().setRemoteDescription(WebRtcClient.this, sdp);
        }
    }

    private class AddIceCandidateCommand implements Command {
        public void execute(JSONObject data) throws JSONException {
            Log.d(TAG, "AddIceCandidateCommand");
            JSONObject payload = data.getJSONObject("payload");
            JSONObject candidate = payload.getJSONObject("candidate");
            getPeer().addIceCandidate(new IceCandidate(
                    candidate.getString("sdpMid"),
                    candidate.getInt("sdpMLineIndex"),
                    candidate.getString("candidate")
            ));
        }
    }

    /**
     * This method sends message to server.
     */

    private void sendMessage(String type, JSONObject payload) {
        new Sender()
                .put("type", type)
                .put("sid", (mSid != null) ? mSid : System.currentTimeMillis() + "")
                .put("to", mClientId)
                .put("roomType", "video")
                .put("payload", payload)
                .send();
    }

    /**
     * SDP Observer
     */

    @Override
    public void onCreateSuccess(final SessionDescription sdp) {
        pc.setLocalDescription(WebRtcClient.this, sdp);
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", sdp.type.canonicalForm());
            payload.put("sdp", sdp.description);
            sendMessage(sdp.type.canonicalForm(), payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSetSuccess() {
    }

    @Override
    public void onCreateFailure(String s) {
    }

    @Override
    public void onSetFailure(String s) {
    }

    /**
     * PeerConnection Observer
     */

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sdpMLineIndex", candidate.sdpMLineIndex);
            payload.put("sdpMid", candidate.sdpMid);
            payload.put("candidate", candidate.sdp);

            sendMessage("candidate", new JSONObject().put("candidate", payload));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(TAG, "onAddStream " + mediaStream.label());
        mListener.onAddRemoteStream(mediaStream);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(TAG, "onRemoveStream " + mediaStream.label());
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
    }

    @Override
    public void onRenegotiationNeeded() {

    }

}