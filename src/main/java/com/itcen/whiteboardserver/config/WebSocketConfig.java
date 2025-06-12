package com.itcen.whiteboardserver.config;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.security.Principal;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtParser jwtParser;
    private final String allowedOrigins;

    public WebSocketConfig(@Value("${jwt.secret}") String secret, @Value("${cors.allowed-origins}") String allowedOrigins) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser()
                .verifyWith(key)
                .build();
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http"+allowedOrigins, "https"+allowedOrigins)
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        if (request instanceof ServletServerHttpRequest) {
                            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();

                            // HTTP 요청 헤더에서 JWT 토큰 추출
                            String token = WebUtils.getCookie(httpServletRequest, "access_token") != null
                                    ? Objects.requireNonNull(WebUtils.getCookie(httpServletRequest, "access_token")).getValue()
                                    : null;

                            if (token != null) {
                                try {
                                    // JWT 토큰 검증 및 payload 에서 subject(sub) 추출
                                    String subject = jwtParser.parseSignedClaims(token).getPayload().getSubject();

                                    if (subject != null) {
                                        // 사용자 email을 세션 속성에 저장
                                        attributes.put("memberId", subject);

                                        // 사용자별 메시지를 위한 Principal 설정
                                        attributes.put("username", subject);

                                        log.info("핸드쉐이크 과정에서 WebSocket 연결 인증 성공: userId={}", subject);
                                        return true;
                                    }
                                } catch (JwtException e) {
                                    log.error("핸드쉐이크 과정에서 JWT 토큰 검증 실패", e);
                                    return false;
                                } catch (NumberFormatException e) {
                                    log.error("핸드쉐이크 과정에서 유효하지 않은 사용자 ID 형식", e);
                                    return false;
                                }
                            }

                            log.warn("핸드쉐이크 과정에서 유효한 JWT 토큰이 없음");
                            return false;
                        }
                        return false;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                        // 핸드셰이크 후 처리 (필요한 경우)
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

}