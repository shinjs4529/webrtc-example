/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.juho.webrtcexample;

import android.os.Handler;
import android.os.HandlerThread;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class SocketIOClient implements AppRTCClient {

    private static final String TAG = "SocketIOClient";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    // TODO: Remove handler if socket.io api is async
    private final Handler handler;
    private SignalingEvents events;
    private Socket _client;
    private ConnectionState roomState;

    //region ROAI-SIGNAL
    private RoomConnectionParameters connectionParameters; // Only use roomID
    private String _contact = "DUMMY_CONTACT";
    private String _selfID;
    private SignalingSession signalingSession;

    class SignalingSession extends SignalingParameters {
        public String sessionID;

        public SignalingSession(String clientId, boolean initiator) {
            super(null, initiator, clientId, "", "", null, new ArrayList<>());
            PeerConnection.IceServer[] servers = {
                    PeerConnection.IceServer.builder("stun:stun.stunprotocol.org").createIceServer(),
                    PeerConnection.IceServer.builder("turn:numb.viagenie.ca").setUsername("webrtc@live.com").setPassword("muazkh").createIceServer(),
            };
            iceServers = Arrays.asList(servers);
        }
    }
    //endregion ROAI-SIGNAL

    public SocketIOClient(SignalingEvents events) {
        this.events = events;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters roomParams) {
        this.connectionParameters = roomParams;
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        String connectionUrl = "https://dev.roaigen.com:7061";
        Log.d(TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        try {
            // Socket.IO
            IO.Options options = IO.Options.builder()
                                            .setForceNew(true)
                                            .setTransports(new String[] {"websocket"})
                                            .build();
            _client = IO.socket(connectionUrl, options);
            _client.on(Socket.EVENT_CONNECT, (args) -> {
                JSONObject json = new JSONObject();
                jsonPut(json, "roomID", connectionParameters.roomId);
                jsonPut(json, "contact", _contact);

                _client.emit("join-room", json);

            });
            _client.on(Socket.EVENT_DISCONNECT, (args) -> {
                if (roomState == ConnectionState.CLOSED) {
                    return;
                }
                roomState = ConnectionState.CLOSED;
            });
            _client.on("room-info", (args) -> {
                try {
                    Log.d(TAG, "room-info : " + args[0]);
                    JSONObject json = new JSONObject(args[0].toString());
                    _selfID = json.getString("myID");
                } catch (Exception e) {
                    reportError("room-info : " + e);
                }
            });
            _client.on("user-joined", (args) -> {
                try {
                    Log.d(TAG, "user-joined : " + args[0]);
                    String otherID = args[0].toString();
                    signalingSession = new SignalingSession(otherID, false);
                    events.onConnectedToRoom(signalingSession);
                } catch (Exception e) {
                    reportError("user-joined : " + e);
                }
            });
            _client.on("other-user", (args) -> {
                try {
                    Log.d(TAG, "other-user : " + args[0]);
                    String otherID = args[0].toString();
                    // 다른 유저가 기다리는 경우, 전화를 검
                    signalingSession = new SignalingSession(otherID, true);
                    signalingSession.sessionID = java.util.UUID.randomUUID().toString();
                    events.onConnectedToRoom(signalingSession);
                } catch (Exception e) {
                    reportError("other-user : " + e);
                }
            });
            _client.on("offer", (args) -> {
                if (!signalingSession.initiator) {
                    try {
                        Log.d(TAG, "offer : " + args[0]);
                        JSONObject json = new JSONObject(args[0].toString());
                        signalingSession.sessionID = json.getString("session_id");
                        JSONObject desc = json.getJSONObject("description");
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm("offer"), desc.getString("sdp"));
                        events.onRemoteDescription(sdp);
                    } catch (Exception e) {
                        reportError("offer : " + e);
                    }
                } else {
                    reportError("Received offer for call receiver");
                }
            });
            _client.on("answer", (args) -> {
                if (signalingSession.initiator) {
                    try {
                        Log.d(TAG, "answer : " + args[0]);
                        JSONObject json = new JSONObject(args[0].toString());
                        JSONObject desc = json.getJSONObject("description");
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm("answer"), desc.getString("sdp"));
                        events.onRemoteDescription(sdp);
                    } catch (Exception e) {
                        reportError("answer : " + e);
                    }
                } else {
                    reportError("Received answer for call initiator");
                }
            });
            _client.on("ice-candidate", (args) -> {
                try {
                    Log.d(TAG, "ice-candidate : " + args[0]);
                    JSONObject json = new JSONObject(args[0].toString());
                    JSONObject candidateJSON = json.getJSONObject("candidate");
                    String sdp = candidateJSON.getString("candidate");
                    if(sdp.isEmpty()) {
                        Log.d(TAG, "ice-candidate : Skip empty candidate");
                        return;
                    }
                    IceCandidate candidate = new IceCandidate(
                            candidateJSON.getString("sdpMid"), candidateJSON.getInt("sdpMLineIndex"), sdp);
                    events.onRemoteIceCandidate(candidate);
                } catch (Exception e) {
                    reportError("ice-candidate : " + e);
                }
            });
            // Fire connection and signaling parameters events.
            _client.connect();
            roomState = ConnectionState.CONNECTED;
        }catch (Exception e){
            reportError("connectToRoomInternal : " + e);
        }
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            // No BYE message
        }
        if (roomState == ConnectionState.CLOSED) {
            return;
        }
        roomState = ConnectionState.CLOSED;
        if (_client != null) {
            _client.disconnect();
        }
    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }

                JSONObject descJson = new JSONObject();
                jsonPut(descJson, "sdp", sdp.description);
                jsonPut(descJson, "type", "offer");

                JSONObject json = new JSONObject();
                jsonPut(json, "to", signalingSession.clientId);
                jsonPut(json, "from", _selfID);
                jsonPut(json, "description", descJson);
                jsonPut(json, "session_id", signalingSession.sessionID);
                jsonPut(json, "media", "video");

                _client.emit("offer", json);
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject descJson = new JSONObject();
                jsonPut(descJson, "sdp", sdp.description);
                jsonPut(descJson, "type", "offer");

                JSONObject json = new JSONObject();
                jsonPut(json, "to", signalingSession.clientId);
                jsonPut(json, "from", _selfID);
                jsonPut(json, "description", descJson);
                jsonPut(json, "session_id", signalingSession.sessionID);

                _client.emit("answer", json);
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject candidateJson = new JSONObject();
                jsonPut(candidateJson, "sdpMLineIndex", candidate.sdpMLineIndex);
                jsonPut(candidateJson, "sdpMid", candidate.sdpMid);
                jsonPut(candidateJson, "candidate", candidate.sdp);

                JSONObject json = new JSONObject();
                jsonPut(json, "to", signalingSession.clientId);
                jsonPut(json, "from", _selfID);
                jsonPut(json, "candidate", candidateJson);
                jsonPut(json, "session_id", signalingSession.sessionID);
                _client.emit("ice-candidate", json);
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        // NOT SUPPORTED
    }

    //region: Helper functions
    private void reportError(final String errorMessage) {
        Log.e(TAG, "reportError : " + errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    //endregion Helper functions
}
