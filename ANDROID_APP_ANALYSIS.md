# VoipClient 안드로이드 앱 분석 및 수정 사항

## 1. 네트워크 설정 이슈

### 문제점:
- 에뮬레이터에서 `localhost`는 에뮬레이터 자체를 가리킴
- 호스트 머신의 서버에 접근하려면 `10.0.2.2` 사용 필요

### 수정 완료:
- ✅ Constants.kt의 서버 URL들을 `10.0.2.2`로 변경
- ✅ NetworkModule.kt는 이미 올바르게 설정됨
- ✅ network_security_config.xml에 cleartext 트래픽 허용됨

## 2. Socket.IO 연결 이슈

### 문제점:
- GuestSignalingClient가 query 파라미터 방식 사용
- 서버는 auth 객체 방식 기대

### 수정 완료:
- ✅ GuestSignalingClient.kt의 인증 방식을 auth 객체로 변경
- ✅ WebSocket 전용 transport 설정 추가

## 3. API 타입 이슈

### 문제점:
- loginAsGuest API가 `isGuest: true` (boolean) 전송
- Map<String, String>으로는 boolean 전송 불가

### 수정 완료:
- ✅ AuthApi의 loginAsGuest 메서드 파라미터 타입을 Map<String, Any>로 변경

## 4. 테스트 전 확인사항

### 서버 실행 상태:
1. API Gateway (포트 3000)
2. Auth Service (포트 3001)  
3. Signaling Service (포트 3004)
4. TURN/STUN Server (포트 3478)

### 에뮬레이터 설정:
1. 2개의 AVD 실행
2. 각각 다른 포트 사용 (5554, 5556)
3. 네트워크 연결 확인

## 5. 예상되는 동작 흐름

1. **게스트 로그인**
   - 게스트 이름 입력
   - POST /api/auth/guest 호출
   - JWT 토큰 수신 및 저장

2. **Socket.IO 연결**
   - Signaling 서버 연결 (포트 3004)
   - 인증 정보 전달
   - 온라인 상태 업데이트

3. **게스트 목록 표시**
   - guestJoined 이벤트 수신
   - 온라인 게스트 목록 업데이트

4. **통화 시작**
   - WebRTC offer/answer 교환
   - ICE candidate 교환
   - P2P 연결 수립

## 6. 디버깅 팁

### 로그 확인:
```bash
# 안드로이드 로그
adb logcat | grep -E "VoipEx|Socket|WebRTC|Retrofit"

# 서버 로그
docker logs voip-api-gateway -f
docker logs voip-signaling -f
```

### 네트워크 문제 해결:
```bash
# 에뮬레이터 네트워크 상태
adb shell ping 10.0.2.2

# 포트 포워딩 (필요시)
adb forward tcp:3000 tcp:3000
adb forward tcp:3004 tcp:3004
```

## 7. 추가 권장사항

1. **에러 핸들링 강화**
   - 네트워크 오류 시 재시도 로직
   - 연결 실패 시 사용자 친화적 메시지

2. **연결 상태 표시**
   - Socket.IO 연결 상태 UI 표시
   - WebRTC 연결 품질 표시

3. **테스트 자동화**
   - Espresso UI 테스트 추가
   - MockWebServer를 이용한 단위 테스트
