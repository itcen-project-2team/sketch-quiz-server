package com.itcen.whiteboardserver.auth.service;

import com.itcen.whiteboardserver.global.exception.GlobalCommonException;
import com.itcen.whiteboardserver.global.exception.GlobalErrorCode;
import com.itcen.whiteboardserver.member.enums.ProfileColor;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.itcen.whiteboardserver.member.enums.ProfileColor.HOTPINK;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private TokenService tokenService;
    private JwtParser parser;

    private final String username      = "user@example.com";
    private final String nickname      = "nick";
    private final String id            = "123";
    private final List<String> roles   = List.of("MEMBER");
    private final ProfileColor profileColor  = HOTPINK;

    private final Duration ACCESS_TTL  = Duration.ofSeconds(10);
    private final Duration REFRESH_TTL = Duration.ofSeconds(20);

    @BeforeEach
    void setUp() {
        // generate a HS256 key and its Base64 secret
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String secret = Base64.getEncoder().encodeToString(key.getEncoded());
        // build parser for test use
        parser = Jwts.parser()
                .verifyWith(key)
                .build();
        // instantiate system under test
        tokenService = new TokenService(
                redisTemplate,
                secret,
                ACCESS_TTL.getSeconds(),
                REFRESH_TTL.getSeconds()
        );
    }

    @Test
    @DisplayName("issueTokens: Redis에 JTI 저장 후 access/refresh 쿠키 반환")
    void issueTokens_storesJtiAndReturnsCookies() {
        // given
        // mock opsForValue()
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        // when
        ResponseCookie[] cookies = tokenService.issueTokens(
                username, nickname, id, roles, profileColor.toString()
        );

        // then
        // verify Redis에 저장
        then(valueOps).should().set(
                eq("refresh:" + username),
                anyString(),             // JTI
                eq(REFRESH_TTL)
        );
        // 두 개 쿠키(access, refresh) 반환
        assertThat(cookies).hasSize(2);
        assertThat(cookies[0].getName()).isEqualTo("access_token");
        assertThat(cookies[1].getName()).isEqualTo("refresh_token");
    }

    @Test
    @DisplayName("authenticateAccess: valid access token으로 Authentication 생성")
    void authenticateAccess_withValidToken_returnsAuthentication() {
        // given
        // mock opsForValue()
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        ResponseCookie accessCookie = tokenService.issueTokens(
                username, nickname, id, roles, profileColor.toString()
        )[0];
        String accessToken = accessCookie.getValue();

        // when
        Authentication auth = tokenService.authenticateAccess(accessToken);

        // then
        assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        CustomPrincipal p = (CustomPrincipal) auth.getPrincipal();
        assertThat(p.getEmail()).isEqualTo(username);
        assertThat(p.getNickname()).isEqualTo(nickname);
        assertThat(p.getId()).isEqualTo(Long.valueOf(id));
        assertThat(p.getProfileColor()).isEqualTo(profileColor);
        // 권한 문자열 검증
        assertThat(p.getAuthorities())
                .extracting("authority")
                .containsExactlyElementsOf(roles);
    }

    @Test
    @DisplayName("rotateRefresh: null 토큰일 때 REFRESH_TOKEN_EXPIRED 예외")
    void rotateRefresh_nullToken_throws() {
        assertThatThrownBy(() -> tokenService.rotateRefresh(null))
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("rotateRefresh: invalid 토큰일 때 INVALID_REFRESH_TOKEN 예외")
    void rotateRefresh_invalidToken_throws() {
        String bad = "not.a.jwt.token";
        assertThatThrownBy(() -> tokenService.rotateRefresh(bad))
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("rotateRefresh: valid 토큰이면 재발급 및 Redis overwrite")
    void rotateRefresh_validToken_rotatesTokens() {
        // given
        // mock opsForValue()
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        // 최초 발급 & original JTI 파싱
        ResponseCookie[] first = tokenService.issueTokens(
                username, nickname, id, roles, profileColor.toString()
        );
        String firstRefresh = first[1].getValue();
        Jws<Claims> parsed1 = parser.parseSignedClaims(firstRefresh);
        String originalJti = parsed1.getPayload().getId();

        // stub Redis get to return originalJti
        given(valueOps.get("refresh:" + username)).willReturn(originalJti);

        // when
        ResponseCookie[] second = tokenService.rotateRefresh(firstRefresh);

        // then
        // Redis overwrite 호출
        then(valueOps).should().set(
                eq("refresh:" + username),
                not(eq(originalJti)).toString(),   // new JTI
                eq(REFRESH_TTL)
        );
        // 새로운 쿠키 반환 (토큰 값이 바뀌었는지 확인)
        assertThat(second).hasSize(2);
        assertThat(second[0].getValue()).isNotEqualTo(first[0].getValue());
        assertThat(second[1].getValue()).isNotEqualTo(first[1].getValue());
    }

    @Test
    @DisplayName("logout: Redis 키 삭제 및 빈 쿠키 반환")
    void logout_deletesRedisKey_andReturnsClearedCookies() {
        // given
        CustomPrincipal principal = new CustomPrincipal(
                9L, username, nickname, "", Set.of(), profileColor
        );

        // when
        ResponseCookie[] cookies = tokenService.logout(principal);

        // then
        then(redisTemplate).should().delete("refresh:" + username);
        assertThat(cookies).hasSize(2);
        // 쿠키가 만료된 상태(maxAge=0)
        assertThat(cookies[0].getMaxAge().getSeconds()).isZero();
        assertThat(cookies[1].getMaxAge().getSeconds()).isZero();
    }
}