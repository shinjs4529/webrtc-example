# Web-RTC-Example
Web RTC 공부

# Example
https://parkkas.tistory.com/13


# Short Vidio
[![mq1](https://user-images.githubusercontent.com/52353492/109332328-49e8e680-78a1-11eb-94c2-1c65b9ad9083.jpg)](https://youtu.be/yAI7zfQ2Fsk)


###CallActivity
-onCreate
//appRtcClient = new SocketIOClient(this, roomId);->init();
startCall();

----OR----
-onActivityResult
-startCall()

-startCall
appRtcClient.connectToRoom(roomConnectionParameters);

###SocketIOClient
-connectToRoom
connectToRoomInternal()

-connectToRoomInternal
signalingParametersReady(params);//EEEEEEEEEEEEEEEEE
//http 요청의 콜백으로 setRemoteDescription 등을 하는데, 이걸 socketio이벤트의 콜백으로 set하면 될까

-signalingParametersReady
events.onConnectedToRoom(signalingParameters)

###CallActivity
-onConnectedToRoom()
-onConnectedToRoomInternal
peerConnectionClient.createPeerConnection()

###PeerConnectionClient
-createPeerConnection()
createPeerConnectionInternal();
-createPeerConnectionInternal();
PeerConnection.RTCConfiguration rtcConfig =
        new PeerConnection.RTCConfiguration(signalingParameters.iceServers);//EEEEEEEEEEEEEEE line 594


위 과정에서 peerConnectionClient.createPeerConnection가 완료되면,
peerConnectionClient.getLocalDescription()가 동작해야 한다.
-----------------------------------iceServers---------------------------
"iceServers":[{"urls":"https://??", "credential":"4529"}, {"urls":"https://!!", "credential":"7921"}]


