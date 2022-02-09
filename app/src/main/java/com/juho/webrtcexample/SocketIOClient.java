package com.juho.webrtcexample;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.juho.webrtcexample.util.AsyncHttpURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.util.Date;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketIOClient implements AppRTCClient {

    private static final String TAG = "SocketIOClient";

    private Socket mSocket;
    private String roomNumber="1111";
//    private final String serverUrl = "https://dev.roaigen.com:7061";
    private final String serverUrl = "https://mybom-dev.herokuapp.com";
    private String myID = "default myID";
    private String otherUserID = "default otherUserID";
    private String sessionID = "default sessionID";

    private boolean connectionFlag = false;


    //------------------------
    private static final String ROOM_JOIN = "room";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    private enum MessageType { MESSAGE, LEAVE }

    private final Handler handler;
    private boolean initiator;
    private SignalingEvents events;
//    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;

    public SocketIOClient(SignalingEvents events) {
        this.events = events;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        init();
    }

    //--------------------------
    @Override
    public boolean isSocketConnectionComplete(){
        return connectionFlag;
    }
    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                System.err.println("for socketio, in connectToRoom");
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
        String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(TAG, "for socketio, connectToRoomInternal, room: " + connectionUrl);
        roomState = ConnectionState.NEW;
//        wsClient = new WebSocketChannelClient(handler, this);


//        RoomParametersFetcher.RoomParametersFetcherEvents callbacks = new RoomParametersFetcher.RoomParametersFetcherEvents() {
//            @Override
//            public void onSignalingParametersReady(final SignalingParameters params) {
//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        System.err.println("for socketio, in onSignalingParametersReady");
//                        signalingParametersReady(params);
//                    }
//                });
//            }
//
//            @Override
//            public void onSignalingParametersError(String description) {
//                reportError(description);
//            }
//        };
//        new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            sendPostMessage(MessageType.LEAVE, leaveUrl, null);
        }
        roomState = ConnectionState.CLOSED;
//        if (wsClient != null) {
//            wsClient.disconnect(true);
//        }
    }

    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
        String value = connectionParameters.roomUrl + "/" + ROOM_JOIN + "/" + connectionParameters.roomId
                + getQueryString(connectionParameters);
        System.err.println("result on getConnectionUrl: "+value);
        return value;
    }

    private String getMessageUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_MESSAGE + "/" + connectionParameters.roomId
                + "/" + signalingParameters.clientId + getQueryString(connectionParameters);
    }

    private String getLeaveUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_LEAVE + "/" + connectionParameters.roomId + "/"
                + signalingParameters.clientId + getQueryString(connectionParameters);
    }

    private String getQueryString(RoomConnectionParameters connectionParameters) {
        if (connectionParameters.urlParameters != null) {
            return "?" + connectionParameters.urlParameters;
        } else {
            return "";
        }
    }

    // Callback issued when room parameters are extracted. Runs on local
    // looper thread.
//    private void signalingParametersReady(final SignalingParameters signalingParameters) {
    private void signalingParametersReady() {
        Log.e(TAG, "[Socketio] signalingParametersReady");
//        if (connectionParameters.loopback
//                && (!signalingParameters.initiator || signalingParameters.offerSdp != null)) {
//            reportError("Loopback room is busy.");
//            return;
//        }
//        if (!connectionParameters.loopback && !signalingParameters.initiator
//                && signalingParameters.offerSdp == null) {
//            Log.w(TAG, "No offer SDP in room response.");
//        }
//        initiator = signalingParameters.initiator;
//        messageUrl = getMessageUrl(connectionParameters, signalingParameters);
//        leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);
        Log.d(TAG, "Message URL: " + messageUrl);
        Log.d(TAG, "Leave URL: " + leaveUrl);
        roomState = ConnectionState.CONNECTED;

        // Fire connection and signaling parameters events.
//        events.onConnectedToRoom(signalingParameters);
        SignalingParameters signalingParameters = null;
        events.onConnectedToRoom(signalingParameters);

        // Connect and register WebSocket client.
//        wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
//        wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                System.err.println("for socketio, Start sendOfferSdp");
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }
//                JSONObject json = new JSONObject();
//                jsonPut(json, "sdp", sdp.description);
//                jsonPut(json, "type", "offer");
//                sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                //TODO send sdp
                JSONObject description = new JSONObject();
                jsonPut(description, "sdp", sdp.description);
                System.err.println("for socketio, sendOfferSdp description is: "+description);
                jsonPut(description, "type", "offer");

                JSONObject json = new JSONObject();
                jsonPut(json, "to", otherUserID);
                System.err.println("for socketio, sendOfferSdp otherUserID is: "+otherUserID);
                jsonPut(json, "from", myID);
                System.err.println("for socketio, sendOfferSdp myID is: "+myID);
                jsonPut(json, "description", description);
                jsonPut(json, "session_id", sessionID);
                System.err.println("for socketio, sendOfferSdp session_id is: "+sessionID);
                jsonPut(json, "media", "video");

                emit("offer", json);

                if (connectionParameters.loopback) {
                    // In loopback mode rename this offer to answer and route it back.
                    SessionDescription sdpAnswer = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm("answer"), sdp.description);
                    events.onRemoteDescription(sdpAnswer);
                }
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.loopback) {
                    Log.e(TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject description = new JSONObject();
                jsonPut(description, "sdp", sdp.description);
                jsonPut(description, "type", "answer");

                JSONObject json = new JSONObject();
                jsonPut(json, "to", otherUserID);
                System.err.println("for socketio, sendAnswerSdp otherUserID is: "+otherUserID);
                jsonPut(json, "from", myID);
                System.err.println("for socketio, sendAnswerSdp myID is: "+myID);
                jsonPut(json, "description", description);
                jsonPut(json, "session_id", sessionID);
                System.err.println("for socketio, sendAnswerSdp sessionID is: "+sessionID);
//                jsonPut(json, "media", "video");

                emit("answer", json);
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate iceCandidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject candidate = new JSONObject();
//                jsonPut(json, "type", "candidate");
                jsonPut(candidate, "sdpMLineIndex", iceCandidate.sdpMLineIndex);
                jsonPut(candidate, "sdpMid", iceCandidate.sdpMid);
                jsonPut(candidate, "candidate", iceCandidate.sdp);
                //TODO 통화 caller와 taker일 때 동작 구분
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    sendPostMessage(MessageType.MESSAGE, messageUrl, candidate.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(iceCandidate);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    JSONObject json = new JSONObject();
                    jsonPut(json, "to", otherUserID);
                    System.err.println("for socketio, sendLocalIceCandidate otherUserID is: "+otherUserID);
                    jsonPut(json, "from", myID);
                    System.err.println("for socketio, sendLocalIceCandidate myID is: "+myID);
                    jsonPut(json, "candidate", candidate);
                    System.err.println("for socketio, sendLocalIceCandidate candidate is: "+candidate);
                    jsonPut(json, "session_id", sessionID);
                    System.err.println("for socketio, sendLocalIceCandidate sessionID is: "+sessionID);

                    emit("ice-candidate", json);
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);//TODO array로 사용하고 있지 않을 듯, 여러 번 보내는 중?
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
//                    wsClient.send(json.toString());
                    //TODO emit을 해야할지, 구현안해도 될지 확인 필요
                }
            }
        });
    }

    // --------------------------------------------------------------------

    private void init() {
        try {
            System.out.println("[Socketio] Connecting to server: "+serverUrl);

            mSocket = IO.socket(serverUrl);

            mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    System.out.println((new Date().toString())+" [Socketio] Connected");
                    connectionFlag = true;
                }
            });

            mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    System.err.println((new Date().toString())+" [Socketio] Disconnected");
                }
            });

            mSocket.on("room-info", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String payload = args[0].toString();
                    JSONObject payloadJson = null;

                    try {
                        payloadJson = new JSONObject(payload);
                        myID = payloadJson.getString("myID");
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    System.err.println((new Date().toString())+" [Socketio] room-info: "+myID);
                }
            });

            mSocket.on("ice-candidate", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String payload = args[0].toString();
                    System.err.println((new Date().toString())+" [Socketio] ice-candidate");
                    System.err.println((new Date().toString())+" [Socketio] payload is: "+payload);

                    JSONObject remoteCandidate = null;

                    try {
                        remoteCandidate = (JSONObject) new JSONObject(payload).get("candidate");
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    try {
                        //{"to":"1BMmkiLNp8_123QyAAAY","from":"yCThpQyYgGnAnKdSAAAa","candidate":{"candidate":"candidate:718709169 1 udp 2122260223 10.0.0.12 54253 typ host generation 0 ufrag JuMD network-id 1 network-cost 10","sdpMid":"0","sdpMLineIndex":0},"session_id":"yCThpQyYgGnAnKdSAAAa-1BMmkiLNp8_123QyAAAY"}
                        handleNewICECandidateMsg(remoteCandidate);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //상대방 candidate받으면 이쪽 candidate도 보내기
                }
            });

            mSocket.on("answer", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String payload = args[0].toString();
                    System.err.println((new Date().toString())+" [Socketio] answer");
                    System.err.println((new Date().toString())+" [Socketio] payload is: "+payload);
                    JSONObject description = null;
                    String sdp = null;
                    String type = null;

                    try {
                        description = (JSONObject) new JSONObject(payload).get("description");
                        sdp = description.getString("sdp");
                        type = description.getString("type");
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    handleAnswerCall(type, sdp);
                    //상대방이 걸은 통화, 상대의 sdp 대응
                }
            });

            mSocket.on("offer", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String payload = args[0].toString();
                    System.err.println((new Date().toString())+" [Socketio] offer");
                    System.err.println((new Date().toString())+" [Socketio] payload is: "+payload);
                    JSONObject payloadJson = null;
                    JSONObject description = null;
                    String sdp = null;
                    String type = null;
                    String from = null;
                    String session_id = null;

                    try {
                        payloadJson = new JSONObject(payload);
                        description = (JSONObject) payloadJson.get("description");
                        sdp = description.getString("sdp");
                        type = description.getString("type");
                        from = payloadJson.getString("from");
                        session_id = payloadJson.getString("session_id");
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    sessionID = session_id;
                    handleOfferCall(type, sdp, from);
                    signalingParametersReady();//EEE?
                    //이쪽이 거는 통화 sdp 요청
                }
            });

            mSocket.on("other-user", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String otherUser = args[0].toString();
                    otherUserID = otherUser;
                    System.err.println((new Date().toString())+" [Socketio] other-user: "+otherUserID);
                    callUser(otherUserID);
                    //상대방에게 condidate먼저 보내기
                }
            });
            mSocket.on("user-disconnected", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    System.err.println((new Date().toString())+" [Socketio] user-disconnected");
                    events.onChannelClose();
                }
            });

            mSocket.on("user-joined", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    String takerID = args[0].toString();
                    //이쪽에서 걸었을 때 상대방의 참여 이벤트
                    //otherUser.current = userID
                    otherUserID = takerID;
                    System.err.println((new Date().toString())+" [Socketio] user-joined: "+otherUserID);
                }
            });


            mSocket.connect();

            //로그 보려면 서버컴 docker로 보기
            Log.d("SOCKET", "Connection success : " + mSocket.id());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void emitJoinRoom(String roomID){
        System.err.println("[Socketio] roomID: "+roomID);
        String contact = "13131313";//TODO caller(robotID) 현재는 기본값, //현재 주소에서 &contact=?을 가져오거나 plan에서 받기
        String timestamp = "2022-01-27T01:59:08.077Z";

        JSONObject json = new JSONObject();
        jsonPut(json, "roomID", roomID);
        jsonPut(json, "contact", contact);
        jsonPut(json, "timestamp", timestamp);

        System.err.println((new Date().toString())+" [Socketio] emitting join-room: "+json.toString());
        emit("join-room", json);
    }

    public void emit(String eventName, Object message) {
        Log.e(TAG, "for socketio, emitting: "+eventName);
        mSocket.emit(eventName, message);
    }

    public void callUser(String targetUser){
        //TODO add user track
        createPeer(targetUser);
    }

    public void createPeer(String targetUser){

        // TODO get my candidates, from PeerConnectionClient class?
        handleNegotiationNeededEvent(targetUser);
    }

    public void handleNegotiationNeededEvent(String targetUser){

        sessionID = myID+"-"+targetUser;

        //여기 대신에 sendOfferSdp에서 동작
//        String description = "my sdp";//TODO get sdp
    }

    public void handleOfferCall(String type, String sdp, String from){
        //TODO offer가 맞을거 같은데 have-local-offer 오류가 나서 일단 answer로 수정
//        type = "answer";

        System.err.println("[Socketio] handleOfferCall type: "+type);
        System.err.println("[Socketio] handleOfferCall from: "+from);
//        createPeer(from);

        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type), sdp);
        events.onRemoteDescription(sessionDescription);

        //const payload = {
        //                to: message.from,
        //                from: socketRef.current.id,
        //                description: peerRef.current.localDescription,
        //                session_id: sessionID.current
        //            }
        //            socketRef.current.emit("answer", payload);
        //        })
        //여기 대신에 sendAnswerSdp에서 동작
    }

    public void handleAnswerCall(String type, String sdp){
//        SessionDescription sessionDescription = new SessionDescription(
//                SessionDescription.Type.fromCanonicalForm(type), sdp);
//        events.onRemoteDescription(sessionDescription);
    }

    public void handleNewICECandidateMsg(JSONObject remoteCandidate) throws JSONException {
        events.onRemoteIceCandidate(toJavaCandidate(remoteCandidate));
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
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

    private void sendPostMessage(
            final MessageType messageType, final String url, @Nullable final String message) {
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        Log.d(TAG, "C->GAE: " + logInfo);
//        AsyncHttpURLConnection httpConnection =
//                new AsyncHttpURLConnection("POST", url, message, new AsyncHttpURLConnection.AsyncHttpEvents() {
//                    @Override
//                    public void onHttpError(String errorMessage) {
//                        reportError("GAE POST error: " + errorMessage);
//                    }
//
//                    @Override
//                    public void onHttpComplete(String response) {
//                        if (messageType == MessageType.MESSAGE) {
//                            try {
//                                JSONObject roomJson = new JSONObject(response);
//                                String result = roomJson.getString("result");
//                                if (!result.equals("SUCCESS")) {
//                                    reportError("GAE POST error: " + result);
//                                }
//                            } catch (JSONException e) {
//                                reportError("GAE POST JSON error: " + e.toString());
//                            }
//                        }
//                    }
//                });
//        httpConnection.send();
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
        jsonPut(json, "sdpMid", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"));
    }
}