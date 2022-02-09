실행 순서

###CallActivity
-onCreate
appRtcClient = new SocketIOClient(this, roomId);->init();
startCall();

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

SocketIOClient의 sendOfferSdp와 sendAnswerSdp를 수정하여 emit하도록 하기

signalingParameters.initiator는 통화 받기 동작에서 쓰일 테니 추후에 넣어야 함

[Socket.io] Connected가 먼저 뜨기 때문에 myID등이 설정되지 않음
socketio연결이 완료된 후에 CallActivity를 켜야 함

###핸드쉐이킹 

#	caller		taker		server
1.	join-room
2.					room-info=>caller
3.			join-room
4.					room-info=>taker
-------------------------------------------------------------
5.					other-user=>taker
6.					user-joined=>caller
7.			offer
8.			ice-candidate*N
9.	handleNewICECandidateMsg()
10. 

Failed to execute 'setRemoteDescription' on 'RTCPeerConnection':
Failed to set remote answer sdp:
The order of m-lines in answer doesn't match order in offer. Rejecting answer.
->폰에서 크롬으로 걸때, ICECandidateMsg가 한번 정도 보내져야 하는데 5번 보낸다.
(caller->taker로는 한번 / taker->caller로는 여러번이 맞다)

->크롬이 보낸 ice-candidate리스트를 자신의 candidate목록에 추가한 후,
합쳐진 후보를 응답으로 보내야 한다.
->추가가 되지 않았다.
AddIceCandidate: ICE candidates can't be added without any remote session description.
remote session description이 오면("offer"가 오면),
handleOfferCall에서 session description 처리하고(추가하고?) 나서,

###CallActivity
-onRemoteIceCandidate(candidate)
peerConnectionClient.addRemoteIceCandidate(candidate);

#sdp차이점
1. a=setup:active가 아니라 :actpass다(일단 sdp answer할 때는 active로 수정)

#ice관련에러
1. 
E/basic_packet_socket_factory.cc: (line 54): UDP bind failed with error 13
E/basic_packet_socket_factory.cc: (line 54): UDP bind failed with error 101
2. 
E/stun_port.cc: (line 596): sendto : [0x00000016] Invalid argument
3. 
E/turn_port.cc: (line 848): Port[1c779000:0:1:0:relay:Net[lo:127.0.0.x/8:Loopback:id=1]]: Failed to send TURN message, error: 22
E/turn_port.cc: (line 369): Failed to create TURN client socket

위 에러를 고치기 위해 ice candidate 설정 수정
1. "ice-candidates"를 받으면 addicecandidate 실행
2. 자신의 iceServers를 생성하는 것은 아래 두가지 경우

2_1. taker: 'other-user'가 온 경우
->'offer' emit

2_2. caller: 'offer'가 온 경우
->'answer' emit

----------------------------
#as caller //EEE

0. user-joined -> onConnectedToRoom -> createPeerConnectionInternal -> ??
1. offer
#SocketIOClient
events.onRemoteDescription(sdp)
#CallActivity
-onRemoteDescription
#PeerConnectionClient
peerConnectionClient.setRemoteDescription(desc);---1
->onSetSuccess -> else else ->완료
peerConnectionClient.createAnswer();---2
->onCreateSuccess -> peerConnection.setLocalDescription
->onSetSuccess -> else if -> events.onLocalDescription
#CallActivity
-onLocalDescription
appRtcClient.sendAnswerSdp(desc);

@->
->handleOfferCall()
->goto @1@
->createAnswer() ck
->setLocalDescription() as setRemoteDescription ck?
->6개 candidate 다 받으면
->onSetSuccess
->events.onLocalDescription(localDescription), -> drainCandidates()
->onLocalDescription
->appRtcClient.sendAnswerSdp(desc)
->@
->emit("answer") 

2. ice-candidate*N //key
take iceServer
drainCandidates()
emit answer

@1@
->createPeer() //이에 해당하는거 찾아야함 @2@?
->peer.onicecandidate //받은 ice-candidate로 설정이 완료되면?, 그게 onIceCandidate?
->emit("ice-candidate")
->@

#PeerConnectionClient
=PCObserver
-onIceCandidate //EEE그냥 candidate 추가되는대로 아래가 실행됨
?????->queuedRemoteCandidates로 해결?
events.onIceCandidate(candidate)
#CallActivity
-onIceCandidate
appRtcClient.sendLocalIceCandidate
#SocketIOClient
-sendLocalIceCandidate
emit ice-candidate*N //EEEEE
3. ice-candidate*1 //EEEEE

#as taker
0. other-user
emit offer
emit ice-candidate*3
1. answer

->callUser(userID) ck? onConnectedToRoomInternal
->createPeer(userID) ck? createPeerConnection()
->peer.onicecandidate 
->emit("ice-candidate")
->@
2. ice-candidate
//emit ice-candidate*3
(완료)

##
If UDP is blocked, a TURN server won't help you.





