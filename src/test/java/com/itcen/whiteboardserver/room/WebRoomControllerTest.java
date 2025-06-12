package com.itcen.whiteboardserver.room;

import com.itcen.whiteboardserver.auth.service.TokenService;
import com.itcen.whiteboardserver.game.constant.GameConstants;
import com.itcen.whiteboardserver.game.dto.request.RoomJoinRequest;
import com.itcen.whiteboardserver.game.dto.response.RoomInfoResponse;
import com.itcen.whiteboardserver.game.entity.RoomParticipation;
import com.itcen.whiteboardserver.game.exception.ErrorResponse;
import com.itcen.whiteboardserver.game.repository.RoomParticipationRepository;
import com.itcen.whiteboardserver.game.repository.RoomRepository;
import com.itcen.whiteboardserver.game.service.RoomService;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.enums.AuthProvider;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.member.enums.ProfileColor;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseCookie;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket을 통한 게임방 참여 기능에 대한 통합 테스트 클래스입니다.
 * STOMP 프로토콜을 사용하여 실제 서버-클라이언트 통신을 테스트합니다.
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebRoomControllerTest {

    /**
     * 테스트 서버의 랜덤 포트 번호
     */
    @LocalServerPort
    private int port;

    /**
     * 회원 정보 조회 및 삭제를 위한 리포지토리
     */
    @Autowired
    private MemberRepository memberRepository;


    /**
     * 방 정보 조회 및 삭제를 위한 리포지토리
     */
    @Autowired
    private RoomRepository roomRepository;

    /**
     * 방 참여자 정보 삭제를 위한 리포지토리
     */
    @Autowired
    private RoomParticipationRepository roomParticipationRepository;

    /**
     * 토큰 발급 및 검증을 위한 서비스
     */
    @Autowired
    private TokenService tokenService;

    /**
     * 룸 서비스
     */
    @Autowired
    private RoomService roomService;

    /**
     * WebSocket STOMP 클라이언트
     */
    private WebSocketStompClient stompClient;
    /**
     * WebSocket 연결 시 사용할 HTTP 헤더
     */
    private final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

    /**
     * WebSocket 서버 URL
     */
    private String wsUrl;

    /**
     * 테스트용 사용자 이메일
     */
    private final String testUserEmail = "test@example.com";

    /**
     * 테스트용 사용자 이메일 목록
     */
    private final List<String> testUserEmailList = new ArrayList<>();

    /**
     * 테스트용 방 코드 목록
     */
    private final List<Long> testRoomCodeList = new ArrayList<>();


    /**
     * 각 테스트 실행 전 필요한 설정을 초기화합니다.
     * - STOMP 클라이언트 설정
     * - 테스트용 사용자 생성
     * - 인증 토큰 발급
     */
    @BeforeEach
    public void setup() {
        // 테스트용 사용자 이메일 목록 초기화
        testUserEmailList.clear();
        // 테스트용 방 코드 목록 초기화
        testRoomCodeList.clear();

        // STOMP 클라이언트 설정
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketTransport transport = new WebSocketTransport(webSocketClient);
        SockJsClient sockJsClient = new SockJsClient(Collections.singletonList(transport));

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        wsUrl = "ws://localhost:" + port + "/ws";

        // 테스트 사용자 준비
        Member testUser = memberRepository.findByEmail(testUserEmail)
                .orElseGet(() -> {
                    Member newMember =
                            Member.builder()
                                    .email(testUserEmail)
                                    .password("<PASSWORD>")
                                    .profileColor(ProfileColor.BABYPINK)
                                    .nickname("Test User")
                                    .memberRole(Collections.singleton(MemberRole.MEMBER))
                                    .provider(AuthProvider.LOCAL)
                                    .build();
                    // 기타 필요한 설정
                    return memberRepository.save(newMember);
                });
        testUserEmailList.add(testUser.getEmail());

        // TokenService를 사용하여 토큰 생성
        // TokenService의 issueTokens 메소드 호출
        ResponseCookie[] cookies = tokenService.issueTokens(testUserEmail, testUser.getNickname(), String.valueOf(testUser.getId()), List.of(MemberRole.MEMBER.name()), testUser.getProfileColor().name());

        // ResponseCookie 에서 Set-Cookie 헤더 문자열로 변환
        String cookieHeader = cookies[0].toString(); // access_token 쿠키

        // 헤더에 access_token 쿠키 추가
        headers.add("Cookie", cookieHeader);
        log.info("테스트용 액세스 토큰 설정: {}", cookieHeader);

    }

    /**
     * 존재하지 않는 방에 참여를 시도할 때 적절한 에러 응답이 오는지 테스트합니다.
     * 1. WebSocket 연결 설정
     * 2. 존재하지 않는 방 코드로 참여 요청
     * 3. 적절한 에러 메시지가 수신되는지 확인
     *
     * @throws InterruptedException STOMP 연결 대기 중 인터럽트 발생 시
     * @throws ExecutionException   STOMP 연결 실행 중 예외 발생 시
     * @throws TimeoutException     STOMP 연결 타임아웃 발생 시
     */
    @Test
    @DisplayName("존재하지 않는 방에 참여 시 에러 응답 확인")
    public void testJoinNonExistingRoomShouldReturnError
    () throws InterruptedException, ExecutionException, TimeoutException {

        Long testRoomCode = 0L;

        // 결과를 저장할 CompletableFuture 객체
        CompletableFuture<RoomInfoResponse> roomInfoResponseFuture = new CompletableFuture<>();
        CompletableFuture<ErrorResponse> errorResponseFuture = new CompletableFuture<>();

        // StompSession 핸들러 설정
        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("연결 성공: {}", session.getSessionId());

                // 방 정보 업데이트를 구독
                session.subscribe("/topic/room/" + testRoomCode, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return RoomInfoResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("방 정보 업데이트 수신: {}", payload);
                        roomInfoResponseFuture.complete((RoomInfoResponse) payload);
                    }
                });

                // 에러 메시지 구독
                session.subscribe("/user/queue/errors", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.error("에러 메시지 수신: {}", payload);
                        errorResponseFuture.complete((ErrorResponse) payload);
                    }
                });

                // 방 참여 메시지 전송
                RoomJoinRequest request = new RoomJoinRequest(testRoomCode);
                session.send("/app/room/join", request);
                log.info("방 참여 요청 전송: {}", request);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.error("STOMP 세션 예외 발생", exception);
                errorResponseFuture.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("WebSocket 전송 오류 발생", exception);
                errorResponseFuture.completeExceptionally(exception);
            }
        };

        // WebSocket 연결 및 STOMP 세션 시작
        StompSession stompSession = stompClient.connectAsync(wsUrl, headers, sessionHandler).get(10, TimeUnit.SECONDS);
        assertNotNull(stompSession);
        log.info("STOMP 세션 연결됨: {}", stompSession.getSessionId());

        try {
            // 두 Future 중 하나가 완료될 때까지 대기
            Object result = CompletableFuture.anyOf(roomInfoResponseFuture, errorResponseFuture)
                    .get(15, TimeUnit.SECONDS);

            // 테스트 로직 변경: 에러 응답을 기대합니다 (방이 존재하지 않으므로)
            if (result instanceof ErrorResponse error) {
                log.info("방 참여 실패 (예상된 결과): {}", error);
                // 에러 메시지가 예상대로 왔는지 확인
                assertNotNull(error.getMessage());
                // 여기서 필요한 추가 검증 가능 (예: 에러 타입 확인 등)
                assertEquals("방을 찾을 수 없습니다.", error.getMessage(), "방이 존재하지 않는다는 에러 메시지가 와야 합니다");
            } else if (result instanceof RoomInfoResponse response) {
                // 방이 존재하지 않으므로 이 경우는 실패해야 함
                fail("방이 존재하지 않아야 하는데 방 정보가 반환되었습니다: " + response);
            }
        } finally {
            // 연결 종료
            if (stompSession.isConnected()) {
                stompSession.disconnect();
                log.info("STOMP 세션 연결 종료");
            }
        }
    }


    /**
     * 방을 생성하고 성공적으로 참여하는 시나리오를 테스트합니다.
     * 1. 방 생성
     * 2. 다른 사용자로 방 참여
     * 3. 방 참여 결과 검증 (방 코드, 참가자 목록 등)
     *
     * @throws InterruptedException STOMP 연결 대기 중 인터럽트 발생 시
     * @throws ExecutionException   STOMP 연결 실행 중 예외 발생 시
     * @throws TimeoutException     STOMP 연결 타임아웃 발생 시
     */
    @Test
    @DisplayName("방 생성 후 성공적으로 참여하는 테스트")
    public void testCreateAndJoinRoom() throws InterruptedException, ExecutionException, TimeoutException {
        // 먼저 방을 생성
        RoomInfoResponse createdRoom = roomService.createRoom(testUserEmail);
        assertNotNull(createdRoom);
        assertNotNull(createdRoom.getRoomCode());

        Long roomCode = createdRoom.getRoomCode();
        testRoomCodeList.add(roomCode);
        log.info("테스트용 방 생성 완료: roomCode={}", roomCode);

        // 결과를 저장할 CompletableFuture 객체
        CompletableFuture<RoomInfoResponse> roomInfoResponseFuture = new CompletableFuture<>();
        CompletableFuture<ErrorResponse> errorResponseFuture = new CompletableFuture<>();

        // 다른 사용자로 생성된 방에 참여
        String joinUserEmail = "join-test@example.com";
        Member joinUser = createTestUser(joinUserEmail, "Join Test User");
        testUserEmailList.add(joinUserEmail);

        // StompSession 핸들러 설정
        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("연결 성공: {}", session.getSessionId());

                // 방 정보 업데이트를 구독
                session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return RoomInfoResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("방 정보 업데이트 수신: {}", payload);
                        roomInfoResponseFuture.complete((RoomInfoResponse) payload);
                    }
                });

                // 에러 메시지 구독
                session.subscribe("/user/queue/errors", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.error("에러 메시지 수신: {}", payload);
                        errorResponseFuture.complete((ErrorResponse) payload);
                    }
                });

                // 방 참여 메시지 전송
                RoomJoinRequest request = new RoomJoinRequest(roomCode);
                session.send("/app/room/join", request);
                log.info("방 참여 요청 전송: {}", request);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.error("STOMP 세션 예외 발생", exception);
                errorResponseFuture.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("WebSocket 전송 오류 발생", exception);
                errorResponseFuture.completeExceptionally(exception);
            }
        };

        // 테스트 사용자의 쿠키 생성
        WebSocketHttpHeaders joinUserHeaders = new WebSocketHttpHeaders();
        ResponseCookie[] cookies = tokenService.issueTokens(joinUserEmail, joinUser.getNickname(),
                String.valueOf(joinUser.getId()), List.of(MemberRole.MEMBER.name()), joinUser.getProfileColor().name());
        joinUserHeaders.add("Cookie", cookies[0].toString());

        // WebSocket 연결 및 STOMP 세션 시작
        StompSession stompSession = stompClient.connectAsync(wsUrl, joinUserHeaders, sessionHandler).get(10, TimeUnit.SECONDS);
        assertNotNull(stompSession);
        log.info("STOMP 세션 연결됨: {}", stompSession.getSessionId());

        try {
            // 두 Future 중 하나가 완료될 때까지 대기
            Object result = CompletableFuture.anyOf(roomInfoResponseFuture, errorResponseFuture)
                    .get(15, TimeUnit.SECONDS);

            // 성공적인 방 참여를 기대함
            if (result instanceof RoomInfoResponse response) {
                log.info("방 참여 성공: {}", response);
                // 방 코드 검증
                assertEquals(roomCode, response.getRoomCode(), "반환된 방 코드가 일치해야 합니다");
                // 참가자 목록에 참여한 사용자가 포함되어 있는지 확인
                boolean joinUserFound = response.getParticipantList().stream()
                        .anyMatch(p -> p.getMemberId().equals(joinUser.getId()));
                assertTrue(joinUserFound, "참가자 목록에 참여한 사용자가 포함되어야 합니다");
                // 참가자 수 확인 (최소 2명: 방장 + 참여자)
                assertTrue(response.getParticipantList().size() >= 2, "참가자 목록에는 최소 2명 이상이 있어야 합니다");
            } else if (result instanceof ErrorResponse error) {
                fail("방 참여가 성공해야 하는데 에러가 발생했습니다: " + error.getMessage());
            }
        } finally {
            // 연결 종료
            if (stompSession.isConnected()) {
                stompSession.disconnect();
                log.info("STOMP 세션 연결 종료");
            }
        }
    }


    /**
     * 방 참여 인원이 최대치일 때 추가 참여 시도 시 에러가 발생하는지 테스트합니다.
     * 1. 방 생성
     * 2. 최대 인원수만큼 사용자를 생성하여 방에 참여
     * 3. 추가 사용자의 참여 시도 시 에러 발생 확인
     *
     * @throws Exception 테스트 중 예외 발생 시
     */
    @Test
    @DisplayName("방 참여 인원 초과 시 에러 응답 확인")
    public void testJoinRoomWhenMaxParticipantsReached() throws Exception {
        // 방 생성
        RoomInfoResponse createdRoom = roomService.createRoom(testUserEmail);
        Long roomCode = createdRoom.getRoomCode();
        testRoomCodeList.add(roomCode);
        log.info("테스트용 방 생성 완료: roomCode={}", roomCode);

        // 최대 인원수만큼 더미 사용자를 생성하여 방에 참여시킴
        int maxParticipants = GameConstants.MAX_PARTICIPANTS;
        for (int i = 1; i < maxParticipants; i++) {
            String dummyEmail = "dummy" + i + "@example.com";
            createTestUser(dummyEmail, "더미사용자" + i);
            testUserEmailList.add(dummyEmail);
            roomService.joinRoom(new RoomJoinRequest(roomCode), dummyEmail);
            log.info("더미 사용자 방 참여 완료: user={}, roomCode={}", dummyEmail, roomCode);
        }

        // 이제 방은 가득 찬 상태. 한 명 더 참여 시도
        String extraUserEmail = "extra@example.com";
        Member extraUser = createTestUser(extraUserEmail, "추가사용자");
        testUserEmailList.add(extraUserEmail);

        // 결과를 저장할 CompletableFuture 객체
        CompletableFuture<RoomInfoResponse> roomInfoResponseFuture = new CompletableFuture<>();
        CompletableFuture<ErrorResponse> errorResponseFuture = new CompletableFuture<>();

        // 테스트 사용자의 쿠키 생성
        WebSocketHttpHeaders extraUserHeaders = new WebSocketHttpHeaders();
        ResponseCookie[] cookies = tokenService.issueTokens(extraUserEmail, extraUser.getNickname(),
                String.valueOf(extraUser.getId()), List.of(MemberRole.MEMBER.name()), extraUser.getProfileColor().name());
        extraUserHeaders.add("Cookie", cookies[0].toString());

        // StompSession 핸들러 설정
        StompSessionHandler sessionHandler = createStompSessionHandler(roomInfoResponseFuture, errorResponseFuture, roomCode);

        // WebSocket 연결 및 STOMP 세션 시작
        StompSession stompSession = stompClient.connectAsync(wsUrl, extraUserHeaders, sessionHandler).get(10, TimeUnit.SECONDS);
        assertNotNull(stompSession);

        try {
            // 두 Future 중 하나가 완료될 때까지 대기
            Object result = CompletableFuture.anyOf(roomInfoResponseFuture, errorResponseFuture)
                    .get(15, TimeUnit.SECONDS);

            // 에러 응답을 기대함 (방이 가득 찼으므로)
            if (result instanceof ErrorResponse error) {
                log.info("방 참여 실패 (예상된 결과): {}", error);
                assertNotNull(error.getMessage());
                assertTrue(error.getMessage().contains("최대 인원"), "최대 인원 초과 관련 에러 메시지가 포함되어야 합니다");
            } else if (result instanceof RoomInfoResponse) {
                fail("방이 가득 차서 참여할 수 없어야 하는데 방 정보가 반환되었습니다");
            }
        } finally {
            if (stompSession.isConnected()) {
                stompSession.disconnect();
            }
        }
    }


    /**
     * 방 참여 후 정상적으로 방을 나가는 기능을 테스트합니다.
     * 1. 방 생성
     * 2. 사용자 방 참여
     * 3. 방 나가기 요청
     * 4. 방 참여자 목록에서 사용자가 제거되었는지 확인
     *
     * @throws Exception 테스트 중 예외 발생 시
     */
    @Test
    @DisplayName("방 참여 후 나가기 테스트")
    public void testJoinAndLeaveRoom() throws Exception {
        // 방 생성
        RoomInfoResponse createdRoom = roomService.createRoom(testUserEmail);
        Long roomCode = createdRoom.getRoomCode();
        testRoomCodeList.add(roomCode);
        log.info("테스트용 방 생성 완료: roomCode={}", roomCode);

        // 참여할 사용자 준비
        String joinUserEmail = "join-leave-test@example.com";
        Member joinUser = createTestUser(joinUserEmail, "참여후퇴장테스트");
        testUserEmailList.add(joinUserEmail);

        // 결과를 저장할 CompletableFuture 객체들
        CompletableFuture<RoomInfoResponse> joinResponseFuture = new CompletableFuture<>();
        CompletableFuture<RoomInfoResponse> leaveResponseFuture = new CompletableFuture<>();
        CompletableFuture<ErrorResponse> errorResponseFuture = new CompletableFuture<>();

        // 테스트 사용자의 쿠키 생성
        WebSocketHttpHeaders joinUserHeaders = new WebSocketHttpHeaders();
        ResponseCookie[] cookies = tokenService.issueTokens(joinUserEmail, joinUser.getNickname(),
                String.valueOf(joinUser.getId()), List.of(MemberRole.MEMBER.name()), joinUser.getProfileColor().name());
        joinUserHeaders.add("Cookie", cookies[0].toString());

        // WebSocket 연결 및 STOMP 세션 시작
        StompSession stompSession = stompClient.connectAsync(wsUrl, joinUserHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("연결 성공: {}", session.getSessionId());

                // 방 정보 업데이트를 구독
                session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return RoomInfoResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("방 정보 업데이트 수신: {}", payload);
                        RoomInfoResponse response = (RoomInfoResponse) payload;

                        // 참여자 목록에 현재 사용자가 있는지 확인
                        boolean containsUser = response.getParticipantList().stream()
                                .anyMatch(p -> p.getMemberId().equals(joinUser.getId()));

                        if (!joinResponseFuture.isDone() && containsUser) {
                            // 첫 번째 업데이트는 참여 응답
                            joinResponseFuture.complete(response);

                            // 참여 성공 후 나가기 요청 전송
                            session.send("/app/room/leave", null);
                            log.info("방 나가기 요청 전송");
                        } else if (joinResponseFuture.isDone() && !leaveResponseFuture.isDone()) {
                            // 두 번째 업데이트는 나가기 응답
                            leaveResponseFuture.complete(response);
                        }
                    }
                });

                // 에러 메시지 구독
                session.subscribe("/user/queue/errors", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.error("에러 메시지 수신: {}", payload);
                        errorResponseFuture.complete((ErrorResponse) payload);
                    }
                });

                // 방 참여 메시지 전송
                RoomJoinRequest request = new RoomJoinRequest(roomCode);
                session.send("/app/room/join", request);
                log.info("방 참여 요청 전송: {}", request);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.error("STOMP 세션 예외 발생", exception);
                errorResponseFuture.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("WebSocket 전송 오류 발생", exception);
                errorResponseFuture.completeExceptionally(exception);
            }
        }).get(10, TimeUnit.SECONDS);

        assertNotNull(stompSession);
        log.info("STOMP 세션 연결됨: {}", stompSession.getSessionId());

        try {
            // 첫 번째: 방 참여 응답 대기
            RoomInfoResponse joinResponse = joinResponseFuture.get(15, TimeUnit.SECONDS);
            log.info("방 참여 성공: {}", joinResponse);

            // 참여 응답 검증
            assertEquals(roomCode, joinResponse.getRoomCode(), "방 코드가 일치해야 합니다");
            boolean joinUserFound = joinResponse.getParticipantList().stream()
                    .anyMatch(p -> p.getMemberId().equals(joinUser.getId()));
            assertTrue(joinUserFound, "참가자 목록에 방금 참여한 사용자가 포함되어야 합니다");

            // 두 번째: 방 나가기 응답 대기
            RoomInfoResponse leaveResponse = leaveResponseFuture.get(15, TimeUnit.SECONDS);
            log.info("방 나가기 성공: {}", leaveResponse);

            // 나가기 응답 검증
            assertEquals(roomCode, leaveResponse.getRoomCode(), "방 코드가 일치해야 합니다");
            boolean userStillInRoom = leaveResponse.getParticipantList().stream()
                    .anyMatch(p -> p.getMemberId().equals(joinUser.getId()));
            assertFalse(userStillInRoom, "참가자 목록에 방금 나간 사용자가 포함되어 있지 않아야 합니다");

        } catch (Exception e) {
            // 에러 발생 시 확인
            if (errorResponseFuture.isDone()) {
                ErrorResponse error = errorResponseFuture.get();
                fail("테스트 중 에러 발생: " + error.getMessage());
            } else {
                fail("테스트 중 예외 발생: " + e.getMessage());
            }
        } finally {
            // 연결 종료
            if (stompSession.isConnected()) {
                stompSession.disconnect();
                log.info("STOMP 세션 연결 종료");
            }
        }
    }


    /**
     * 방장이 방을 나갔을 때 새로운 방장이 자동으로 지정되는지 테스트합니다.
     * 1. 방 생성 (첫 번째 사용자가 방장)
     * 2. 두 번째 사용자를 방에 참여
     * 3. 방장이 방을 나감
     * 4. 두 번째 사용자가 새 방장으로 지정되었는지 확인
     *
     * @throws Exception 테스트 중 예외 발생 시
     */
    @Test
    @DisplayName("방장 퇴장 시 새 방장 지정 테스트")
    public void testHostLeaveAndNewHostAssigned() throws Exception {
        // 방 생성 (첫 번째 사용자가 방장)
        RoomInfoResponse createdRoom = roomService.createRoom(testUserEmail);
        Long roomCode = createdRoom.getRoomCode();
        testRoomCodeList.add(roomCode);
        log.info("테스트용 방 생성 완료: roomCode={}", roomCode);

        // 두 번째 사용자 준비
        String secondUserEmail = "second-user@example.com";
        Member secondUser = createTestUser(secondUserEmail, "두번째사용자");
        testUserEmailList.add(secondUserEmail);

        // 두 번째 사용자를 방에 참여시킴
        roomService.joinRoom(new RoomJoinRequest(roomCode), secondUserEmail);

        // 결과를 저장할 CompletableFuture 객체
        CompletableFuture<RoomInfoResponse> hostLeaveResponseFuture = new CompletableFuture<>();
        CompletableFuture<ErrorResponse> errorResponseFuture = new CompletableFuture<>();

        // 첫 번째 사용자(방장)의 세션 준비
        StompSession hostSession = stompClient.connectAsync(wsUrl, headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("방장 연결 성공: {}", session.getSessionId());

                // 방 정보 업데이트를 구독 (두 번째 사용자를 위해)
                session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return RoomInfoResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("방 정보 업데이트 수신: {}", payload);
                        // 여기서는 아무 것도 하지 않음
                    }
                });

                // 에러 메시지 구독
                session.subscribe("/user/queue/errors", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.error("에러 메시지 수신: {}", payload);
                        errorResponseFuture.complete((ErrorResponse) payload);
                    }
                });
            }
        }).get(10, TimeUnit.SECONDS);

        // 두 번째 사용자의 세션 준비 (새 방장이 될 사용자)
        WebSocketHttpHeaders secondUserHeaders = new WebSocketHttpHeaders();
        ResponseCookie[] cookies = tokenService.issueTokens(secondUserEmail, secondUser.getNickname(),
                String.valueOf(secondUser.getId()), List.of(MemberRole.MEMBER.name()), secondUser.getProfileColor().name());
        secondUserHeaders.add("Cookie", cookies[0].toString());

        StompSession secondUserSession = stompClient.connectAsync(wsUrl, secondUserHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("두 번째 사용자 연결 성공: {}", session.getSessionId());

                // 방 정보 업데이트를 구독
                session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return RoomInfoResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("방 정보 업데이트 수신 (두 번째 사용자): {}", payload);
                        RoomInfoResponse response = (RoomInfoResponse) payload;

                        // 방장이 나간 후의 정보인지 확인
                        boolean originalHostExists = response.getParticipantList().stream()
                                .anyMatch(p -> p.getMemberId().equals(memberRepository.findByEmail(testUserEmail).get().getId()));

                        if (!originalHostExists) {
                            // 원래 방장이 없고, 현재 사용자가 새 방장인지 확인
                            boolean isNewHost = response.getParticipantList().stream()
                                    .anyMatch(p -> p.getMemberId().equals(secondUser.getId()) && p.isHost());

                            if (isNewHost) {
                                hostLeaveResponseFuture.complete(response);
                            }
                        }
                    }
                });

                // 에러 메시지 구독
                session.subscribe("/user/queue/errors", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.error("에러 메시지 수신 (두 번째 사용자): {}", payload);
                        errorResponseFuture.complete((ErrorResponse) payload);
                    }
                });
            }
        }).get(10, TimeUnit.SECONDS);

        try {
            // 잠시 대기하여 모든 구독이 완료되도록 함
            Thread.sleep(1000);

            // 첫 번째 사용자(방장)가 방을 떠남
            hostSession.send("/app/room/leave", null);
            log.info("방장이 방 나가기 요청 전송");

            // 방장 변경 응답 대기
            RoomInfoResponse hostChangeResponse = hostLeaveResponseFuture.get(15, TimeUnit.SECONDS);
            log.info("방장 변경 감지: {}", hostChangeResponse);

            // 검증
            // 1. 원래 방장이 목록에 없어야 함
            boolean originalHostExists = hostChangeResponse.getParticipantList().stream()
                    .anyMatch(p -> p.getMemberId().equals(memberRepository.findByEmail(testUserEmail).get().getId()));
            assertFalse(originalHostExists, "원래 방장이 참가자 목록에 없어야 합니다");

            // 2. 두 번째 사용자가 새 방장이어야 함
            boolean secondUserIsHost = hostChangeResponse.getParticipantList().stream()
                    .anyMatch(p -> p.getMemberId().equals(secondUser.getId()) && p.isHost());
            assertTrue(secondUserIsHost, "두 번째 사용자가 새 방장으로 지정되어야 합니다");

        } finally {
            // 연결 종료
            if (hostSession.isConnected()) {
                hostSession.disconnect();
            }
            if (secondUserSession.isConnected()) {
                secondUserSession.disconnect();
            }
        }
    }


    /**
     * 각 테스트 종료 후 정리 작업을 수행합니다.
     * &#064;Transactional  애노테이션으로 인해 DB 관련 정리는 자동으로 수행됩니다.
     */
    @AfterEach
    public void cleanup() {

        // 테스트에서 생성한 RoomParticipation 정리
        for (Long roomCode : testRoomCodeList) {
            try {
                log.info("테스트 방 참여자 삭제 시작: roomCode={}", roomCode);
                List<RoomParticipation> roomParticipationList = roomParticipationRepository.findByRoomId(roomCode);
                for (RoomParticipation roomParticipation : roomParticipationList) {
                    try {
                        roomParticipationRepository.delete(roomParticipation);
                        log.info("테스트 방 참여자 삭제 완료: roomParticipationId={}", roomParticipation.getId());
                    } catch (Exception e) {
                        log.warn("테스트 방 참여자 삭제 중 오류 발생: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("테스트 방 참여자 삭제 중 오류 발생: {}", e.getMessage());
            }
        }

        // 테스트에서 생성한 방 정리
        for (Long roomCode : testRoomCodeList) {
            try {
                roomRepository.findById(roomCode).ifPresent(room -> {
                    roomRepository.delete(room);
                    log.info("테스트 방 삭제 완료: roomCode={}", roomCode);
                });
            } catch (Exception e) {
                log.warn("테스트 방 삭제 중 오류 발생: {}", e.getMessage());
            }
        }

        // 테스트에서 생성한 사용자 정리
        for (String email : testUserEmailList) {
            try {
                memberRepository.findByEmail(email).ifPresent(member -> {
                    memberRepository.delete(member);
                    log.info("테스트 사용자 삭제 완료: email={}", email);
                });
            } catch (Exception e) {
                log.warn("테스트 사용자 삭제 중 오류 발생: {}", e.getMessage());
            }
        }

    }


    /**
     * StompSessionHandler를 생성하는 헬퍼 메소드입니다.
     * 방 정보 응답과 에러 응답을 처리하는 핸들러를 생성합니다.
     *
     * @param roomInfoResponseFuture 방 정보 응답을 저장할 CompletableFuture
     * @param errorResponseFuture    에러 응답을 저장할 CompletableFuture
     * @param roomCode               연결할 방 코드
     * @return 설정된 StompSessionHandler
     */
    private StompSessionHandler createStompSessionHandler(
            CompletableFuture<RoomInfoResponse> roomInfoResponseFuture,
            CompletableFuture<ErrorResponse> errorResponseFuture,
            Long roomCode) {

        return new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("연결 성공: {}", session.getSessionId());

                // 방 정보 업데이트를 구독
                session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return RoomInfoResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        roomInfoResponseFuture.complete((RoomInfoResponse) payload);
                    }
                });

                // 에러 메시지 구독
                session.subscribe("/user/queue/errors", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errorResponseFuture.complete((ErrorResponse) payload);
                    }
                });

                // 방 참여 메시지 전송
                RoomJoinRequest request = new RoomJoinRequest(roomCode);
                session.send("/app/room/join", request);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorResponseFuture.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorResponseFuture.completeExceptionally(exception);
            }
        };
    }

    /**
     * 테스트용 사용자를 생성하는 헬퍼 메소드입니다.
     * 이미 존재하는 사용자면 해당 사용자를 반환하고, 없으면 새로 생성합니다.
     *
     * @param email    사용자 이메일
     * @param nickname 사용자 닉네임
     * @return 생성된 또는 조회된 사용자 객체
     */
    private Member createTestUser(String email, String nickname) {
        return memberRepository.findByEmail(email)
                .orElseGet(() -> {
                    Member newMember = Member.builder()
                            .email(email)
                            .password("<PASSWORD>")
                            .profileColor(ProfileColor.BABYPINK)
                            .nickname(nickname)
                            .memberRole(Collections.singleton(MemberRole.MEMBER))
                            .provider(AuthProvider.LOCAL)
                            .build();
                    return memberRepository.save(newMember);
                });
    }

}