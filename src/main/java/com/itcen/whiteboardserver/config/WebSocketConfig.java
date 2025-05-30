package com.itcen.whiteboardserver.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.itcen.whiteboardserver.game.exception.AuthenticationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        if (request instanceof ServletServerHttpRequest) {
                            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();

                            // HTTP 요청 헤더에서 JWT 토큰 추출
                            String token = extractToken(httpServletRequest);

                            if (token != null) {
                                try {
                                    // JWT 토큰 검증 및 payload에서 subject(sub) 추출
                                    DecodedJWT decodedJWT = verifyToken(token);
                                    String subject = decodedJWT.getSubject();

                                    if (subject != null) {
                                        // 사용자 ID를 세션 속성에 저장
                                        Long memberId = Long.valueOf(subject);
                                        attributes.put("memberId", memberId);

                                        // 사용자별 메시지를 위한 Principal 설정
                                        attributes.put("username", subject);

                                        log.info("핸드쉐이크 과정에서 WebSocket 연결 인증 성공: userId={}", subject);
                                        return true;
                                    }
                                } catch (JWTVerificationException e) {
                                    log.error("핸드쉐이크 과정에서 JWT 토큰 검증 실패", e);
                                    return false;
                                } catch (NumberFormatException e) {
                                    log.error("핸드쉐이크 과정에서 유효하지 않은 사용자 ID 형식", e);
                                    return false;
                                }
                            }

                            log.warn("핸드쉐이크 과정에서 유효한 JWT 토큰이 없음");
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                        // 핸드셰이크 후 처리 (필요한 경우)
                    }

                    private String extractToken(HttpServletRequest request) {
                        // 1. 헤더에서 Authorization 값 추출
                        String bearerToken = request.getHeader("Authorization");

                        // Bearer 토큰이 있으면 반환
                        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                            return bearerToken.substring(7);
                        }

                        // 2. URL 파라미터에서 토큰 추출 시도
                        String paramToken = request.getParameter("token");
                        if (paramToken != null && !paramToken.isEmpty()) {
                            return paramToken;
                        }

                        // 3. 쿠키에서 토큰 추출 시도
                        Cookie[] cookies = request.getCookies();
                        if (cookies != null) {
                            for (Cookie cookie : cookies) {
                                // TODO 추후 토큰 이름 적용
                                if ("token".equals(cookie.getName())) {
                                    return cookie.getValue();
                                }
                            }
                        }

                        // 토큰을 찾지 못한 경우
                        return null;

                    }

                    private DecodedJWT verifyToken(String token) throws JWTVerificationException {
                        // JWT 검증기 생성
                        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
                        JWTVerifier verifier = JWT.require(algorithm).build();

                        // 토큰 검증 및 디코딩
                        return verifier.verify(token);
                    }
                })
                // WebSocket 연결을 위한 Principal(사용자 인증 정보)을 설정
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        final String username = (String) attributes.get("username");
                        return username != null ? () -> username : super.determineUser(request, wsHandler, attributes);
                    }
                })
                .withSockJS();
        registry.setErrorHandler(customErrorHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트에서 구독할 주제 접두사
        registry.enableSimpleBroker("/topic", "/queue");

        // 서버로 메시지를 보낼 때 사용할 접두사
        registry.setApplicationDestinationPrefixes("/app");

        // 사용자별 목적지 접두사
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 이미 인증된 사용자인지 확인
                    if (accessor.getUser() != null) {
                        log.info("소켓연결 과정에서 이미 인증된 사용자: {}", accessor.getUser().getName());
                        return message;
                    }

                    // STOMP 연결 시 헤더에서 Authorization 추출
                    List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
                    String token = null;
                    if (authorizationHeaders != null && !authorizationHeaders.isEmpty()) {
                        String authHeader = authorizationHeaders.get(0);
                        if (authHeader.startsWith("Bearer ")) {
                            token = authHeader.substring(7);
                        }
                    }
                    // 토큰이 없으면 연결 거부
                    if (token == null) {
                        log.error("STOMP 연결 과정에서 Authorization 헤더 없음");
                        throw new AuthenticationException("STOMP 연결 과정에서 Authorization 헤더 없음");
                    }
                    try {
                        // JWT 토큰 검증 및 payload에서 subject(sub) 추출
                        DecodedJWT decodedJWT = verifyToken(token);
                        String subject = decodedJWT.getSubject();
                        if (subject == null) {
                            log.error("STOMP 연결 과정에서 JWT subject 없음");
                            throw new AuthenticationException("STOMP 연결 과정에서 JWT subject 없음");
                        }
                        // 사용자 ID를 세션 속성에 저장
                        Long memberId = Long.valueOf(subject);
                        accessor.setSessionAttributes(Collections.singletonMap("memberId", memberId));
                        accessor.setUser(() -> subject);
                        log.info("STOMP 연결 인증 성공: userId={}", subject);
                    } catch (JWTVerificationException e) {
                        log.error("STOMP 연결 과정에서 JWT 토큰 검증 실패", e);
                        throw new AuthenticationException("STOMP 연결 과정에서 JWT 토큰 검증 실패");
                    } catch (NumberFormatException e) {
                        log.error("STOMP 연결 과정에서 유효하지 않은 사용자 ID 형식", e);
                        throw new AuthenticationException("STOMP 연결 과정에서 유효하지 않은 사용자 ID 형식");
                    }
                }
                return message;
            }

            private DecodedJWT verifyToken(String token) throws JWTVerificationException {
                // JWT 검증기 생성
                Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
                JWTVerifier verifier = JWT.require(algorithm).build();

                // 토큰 검증 및 디코딩
                return verifier.verify(token);
            }

        });
    }

    /**
     * WebSocket 통신에서 STOMP(서브 프로토콜)을 사용하는 동안 발생하는 오류를 처리하기 위한
     * 사용자 정의 {@link StompSubProtocolErrorHandler}를 생성하고 구성합니다.
     * <p>
     * 이 메서드는 클라이언트 메시지 처리 오류를 처리하고, 상세한 오류 메시지를 생성하며,
     * 필수 헤더(예: 'message')를 포함하고 선택적으로 원본 클라이언트 메시지의 'receipt-id' 를
     * 포함하는 적절한 STOMP ERROR 프레임을 구성하는 오류 처리기를 설정합니다.
     *
     * @return STOMP 프로토콜 오류를 처리하도록 구성된 {@link StompSubProtocolErrorHandler} 인스턴스
     */
    @Bean
    public StompSubProtocolErrorHandler customErrorHandler() {
        return new StompSubProtocolErrorHandler() {
            @Override
            public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
                // 1. 에러 메시지 생성
                String errorMessage = ex.getCause().getMessage();

                // 2. ERROR 프레임 헤더 설정
                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
                accessor.setMessage(ex.getMessage()); // 필수 헤더 'message'
                accessor.setLeaveMutable(true); // 메시지 수정 허용

                // 3. 클라이언트 원본 메시지의 receipt-id 포함 (선택)
                if (clientMessage != null) {
                    StompHeaderAccessor clientAccessor = StompHeaderAccessor.wrap(clientMessage);
                    String receiptId = clientAccessor.getReceipt();
                    if (receiptId != null) {
                        accessor.setReceiptId(receiptId); // 클라이언트 요청과 연결
                    }
                }

                // 4. 메시지 본문 생성 (UTF-8 인코딩)
                byte[] payload = errorMessage.getBytes(StandardCharsets.UTF_8);

                // 5. 최종 메시지 조립
                return MessageBuilder.createMessage(
                        payload,
                        accessor.getMessageHeaders()
                );
            }
        };
    }


}