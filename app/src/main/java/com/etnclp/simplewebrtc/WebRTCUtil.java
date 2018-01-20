package com.etnclp.simplewebrtc;

/*
  WebRTCUtil.java
  SimpleWebRTC

  Created by Erdi T. on 19.01.2018.
  Copyright © 2018 Mirana Software. All rights reserved.
 */

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

import java.util.LinkedList;

class WebRTCUtil {

    static PeerConnection.IceServer stunServer;

    static PeerConnection.IceServer turnServer;

    static LinkedList<PeerConnection.IceServer> iceServers() {
        LinkedList<PeerConnection.IceServer> servers = new LinkedList<>();
        if (stunServer != null) servers.add(stunServer);
        if (turnServer != null) servers.add(turnServer);
        return servers;
    }

    static MediaConstraints videoConstraints() {
        MediaConstraints videoConstraints = new MediaConstraints();
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(480)));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(640)));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(30)));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(30)));
        return videoConstraints;
    }

    static MediaConstraints audioConstraints() {
        return new MediaConstraints();
    }

    static MediaConstraints offerConstraints() {
        return new MediaConstraints();
    }

    static MediaConstraints answerConstraints() {
        return new MediaConstraints();
    }

    static MediaConstraints peerConnectionConstraints() {
        return new MediaConstraints();
    }

    public interface RtcListener {

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream);

    }

    public interface Command {
        void execute(JSONObject data) throws JSONException;
    }

}
