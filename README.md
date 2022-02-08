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

SocketIOClient의 sendOfferSdp와 sendAnswerSdp를 수정하여 emit하도록 하기

signalingParameters.initiator는 통화 받기 동작에서 쓰일 테니 추후에 넣어야 함
Failed to send TURN message, error: 22 //문제없나??

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
->폰에서 크롬으로 걸때, ICECandidateMsg가 한번 정도 받아져야 하는데 5번 받는다.
(caller->taker로는 한번 / taker->caller로는 여러번이 맞는듯)

->크롬이 보낸 ice-candidate리스트를 자신의 candidate목록에 추가한 후,
합쳐진 후보를 응답으로 보내야 한다.
->추가가 되지 않았다.
AddIceCandidate: ICE candidates can't be added without any remote session description.
remote session description이 오면("offer"가 오면),
handleOfferCall에서 session description 처리하고(추가하고?) 나서,
########
CallActivity에서 setRemoteDescription 콜하는거 참조하니 drainCandidates성공?
drainCandidates()하기
1. 현재 drainCandidates()는 콜되지 않음

###CallActivity
-onRemoteIceCandidate(candidate)
peerConnectionClient.addRemoteIceCandidate(candidate);

sdp차이점
1. a=setup:active가 아니라 :actpass다(일단 걸기일때는 active로 수정)

에러
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
caller-side
0. user-joined
1. offer
2. ice-candidate*N
setup iceServer
drainCandidates()
emit answer
emit ice-candidate(type:)//E?
3. ice-candidate*1 //왜 다시 오지??

------------------------------
offerer - actpass
answerer - active/passive
-------------------------------
#Wifi로 연결 시, 팝업과 함께 다음 로그(앱 새로 안켜고, 남아있던 액티비티에서 킴)
E/peer_connection.cc: (line 6752): Called in wrong state: stable (INVALID_STATE)
E/peer_connection.cc: (line 3077): Failed to set remote answer sdp: Called in wrong state: stable
E/PCRTCClient: Peerconnection error: setSDP error: Failed to set remote answer sdp: Called in wrong state: stable
D/PCRTCClient: PC create ANSWER
E/peer_connection.cc: (line 2385): PeerConnection cannot create an answer in a state other than have-remote-offer or have-local-pranswer.
D/PCRTCClient: Set local SDP from OFFER
E/PCRTCClient: Peerconnection error: createSDP error: PeerConnection cannot create an answer in a state other than have-remote-offer or have-local-pranswer.

#또는 팝업과 함께 다음 로그(다른 와이파이)
E/jsep_transport_controller.cc: (line 652): Failed to apply the description for m= section with mid='0': Offerer must use actpass value for setup attribute. (INVALID_PARAMETER)
E/peer_connection.cc: (line 3097): Failed to set remote answer sdp: Failed to apply the description for m= section with mid='0': Offerer must use actpass value for setup attribute.
D/PCRTCClient: PC create ANSWER
E/PCRTCClient: Peerconnection error: setSDP error: Failed to set remote answer sdp: Failed to apply the description for m= section with mid='0': Offerer must use actpass value for setup attribute.
E/peer_connection.cc: (line 2373): CreateAnswer: Session error code: ERROR_CONTENT. Session error description: Failed to apply the description for m= section with mid='0': Offerer must use actpass value for setup attribute..
E/PCRTCClient: Peerconnection error: createSDP error: Session error code: ERROR_CONTENT. Session error description: Failed to apply the description for m= section with mid='0': Offerer must use actpass value for setup attribute..

#또는 다음 로그와 함께 앱이 멈춤(다른 와이파이, 앱 새로 빌드, 창 켜고 기다림/바로 킴)
E/peer_connection.cc: (line 4102): AddIceCandidate: ICE candidates can't be added without any remote session description.
--------------------------------------------------
#LTE로 연결 시, 다음 로그와 함께 잠시 후 멈춤
ICE candidates can't be added without any remote session description.
--------------------------------------------------
##CallActivity line 908에 Sending OFFER 가 왜 OFFER지
--------------------------------------------------
E/peer_connection.cc: (line 6752): Called in wrong state: have-local-offer (INVALID_STATE)
E/peer_connection.cc: (line 3077): Failed to set remote offer sdp: Called in wrong state: have-local-offer
D/PCRTCClient: PC create ANSWER
E/PCRTCClient: Peerconnection error: setSDP error: Failed to set remote offer sdp: Called in wrong state: have-local-offer
E/peer_connection.cc: (line 2385): PeerConnection cannot create an answer in a state other than have-remote-offer or have-local-pranswer.
E/PCRTCClient: Peerconnection error: createSDP error: PeerConnection cannot create an answer in a state other than have-remote-offer or have-local-pranswer.
---------------------------------------------------
createPeerConnectionInternal()보다,
setRemoteDescription()이 먼저 불리는 것이 문제다.

전자는 startCall 이후로 순차적으로 call되고,
후자는 handleOfferCall에 의해 call된다.
===>CallActivity의 onCreate에서 while로 기다리는 동안, startCall이 되지 않으니 handleOfferCall의 Call이 더 빠르다.





