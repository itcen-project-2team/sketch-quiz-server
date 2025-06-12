package com.itcen.whiteboardserver.auth.service;

import com.itcen.whiteboardserver.global.exception.GlobalCommonException;
import com.itcen.whiteboardserver.global.exception.GlobalErrorCode;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.member.enums.ProfileColor;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TokenService: Best-practice implementation for JWT-based auth
 * - Access & Refresh in HttpOnly SameSite=Lax cookies
 * - Refresh rotation with jti stored in Redis (TTL)
 * - Logout revokes all refresh tokens
 * - Clear separation: generate, validate, authenticate, rotate
 */
@Service
@Slf4j
public class TokenService {

    private final StringRedisTemplate redis;
    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final JwtParser jwtParser;

    public TokenService(StringRedisTemplate redis,
                        @Value("${jwt.secret}") String secret,
                        @Value("${jwt.access-token-validity-seconds}") long accessSeconds,
                        @Value("${jwt.refresh-token-validity-seconds}") long refreshSeconds) {
        this.redis = redis;
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser  = Jwts.parser()
                .verifyWith(key)
                .build();
        this.accessTtl = Duration.ofSeconds(accessSeconds);
        this.refreshTtl = Duration.ofSeconds(refreshSeconds);
    }

    // -------------------------------------
    // 1) 로그인 or OAuth 콜백 이후, 최초 토큰 발급 시 사용
    // -------------------------------------
    /**
     * 최초 로그인 또는 OAuth 콜백 직후에 호출.
     * username, nickname, id, roles 정보를 바탕으로
     * 액세스 토큰과 리프레시 토큰을 생성한 뒤,
     * Redis에 JTI를 저장하고 ResponseCookie 배열을 반환한다.
     *
     * @param username 사용자 식별자(예: 이메일)
     * @param nickname 닉네임
     * @param id       사용자 테이블 PK(문자열 형태)
     * @param roles    권한 목록(예: ["ROLE_USER","ROLE_ADMIN"])
     * @param profileColor 배경 색상
     * @return [0] = access_token 쿠키, [1] = refresh_token 쿠키
     */
    public ResponseCookie[] issueTokens(
            String username,
            String nickname,
            String id,
            List<String> roles,
            String profileColor) {
        // 1) 기존 키 하나만 삭제할 필요 없이, 덮어쓰기
        String setKey = "refresh:" + username;

        // 2) 새 토큰 생성
        String access  = createToken(username, nickname, id, roles, profileColor, accessTtl);
        String refresh = createToken(username, nickname, id, roles, profileColor, refreshTtl);

        // 3) 새 JTI만 저장 (overwrite)
        String jti = jwtParser.parseSignedClaims(refresh).getPayload().getId();

        try {
            redis.opsForValue()
                    .set(setKey, jti, refreshTtl);
        } catch(Exception e) {
            log.error("Error saving refresh token to Redis: {}", e.getMessage());
            throw new GlobalCommonException(GlobalErrorCode.REDIS_ERROR);
        }

        // 4) 쿠키 세팅
        ResponseCookie accessCookie = addCookie("access_token",  access,  accessTtl,  "/");
        ResponseCookie refreshCookie = addCookie("refresh_token", refresh, refreshTtl, "/api/auth/oauth2/refresh");

        return new ResponseCookie[]{ accessCookie, refreshCookie };
    }


    /**
     * Validate access token, return Authentication
     */
    public Authentication authenticateAccess(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token)
                    .getPayload();
            String username = claims.getSubject();

            String id = (String) claims.get("id");
            String nickname = (String) claims.get("nickname");

            ProfileColor profileColor = ProfileColor.valueOf(
                    claims.get("profileColor", String.class)
            );

            @SuppressWarnings("unchecked")
            List<String> rolesAsString = claims.get("roles", List.class);
            Set<MemberRole> rolesEnum = rolesAsString.stream()
                    .map(MemberRole::valueOf)     // ex: "ROLE_ADMIN" → MemberRole.ADMIN
                    .collect(Collectors.toSet());

            /* 설명. customPrincipal 생성 */
            CustomPrincipal principal = new CustomPrincipal(
                    Long.valueOf(id),
                    username,
                    nickname,
                    /* 빈 문자열이어도 무방(패스워드는 이미 검증된 상태) */ "",
                    rolesEnum,
                    profileColor
            );

            return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
        } catch (JwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
            throw new GlobalCommonException(GlobalErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    // -------------------------------------
    // 3) 리프레시 토큰 검증 및 토큰 재발급
    // -------------------------------------
    /**
     * 클라이언트가 보낸 리프레시 토큰을 검증하고,
     * 유효하면 새 액세스/리프레시 토큰 쿠키를 생성하여 반환한다.
     * 검증에 실패하면 GlobalCommonException을 던진다.
     *
     * @param old  클라이언트가 보낸 refresh_token 문자열(JWT)
     * @return [0] = 새 access_token 쿠키, [1] = 새 refresh_token 쿠키
     */
    public ResponseCookie[] rotateRefresh(String old) {
        if (old == null) {
            log.error("Missing refresh token");
            throw new GlobalCommonException(GlobalErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        Jws<Claims> claims;

        try {
            claims = jwtParser.parseSignedClaims(old);
        }catch (JwtException ex){
            log.error("Invalid Refresh token: {}", ex.getMessage());
            throw new GlobalCommonException(GlobalErrorCode.INVALID_REFRESH_TOKEN);
        }

        String username = claims.getPayload().getSubject();
        String id = claims.getPayload().get("id", String.class);
        String nickname = claims.getPayload().get("nickname", String.class);

        String profileColor = claims.getPayload().get("profileColor", String.class);

        String oldJti = claims.getPayload().getId();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.getPayload().get("roles", List.class);
        // 1) Redis에 저장된 값과 비교
        String key      = "refresh:" + username;

        String storedJti;

        try {
             storedJti = redis.opsForValue().get(key);
        }catch(Exception e){
            log.error("Error retrieving refresh token from Redis: {}", e.getMessage());
            throw new GlobalCommonException(GlobalErrorCode.REDIS_ERROR);
        }

        if (!oldJti.equals(storedJti)) {
//            throw new JwtException("Invalid refresh token");
            throw new GlobalCommonException(GlobalErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 재사용 issueTokens
        return issueTokens(username, nickname, id, roles, profileColor);
    }

    /**
     * Logout: revoke all refresh tokens for current user
     */
    public ResponseCookie[] logout(CustomPrincipal principal) {

        // 1) Redis에서 refresh 토큰 삭제 시도
        try {
            if (principal != null) {
                String user = principal.getEmail();
                log.debug("Logging out user: {}", user);
                redis.delete("refresh:" + user);
            }
        } catch (Exception e) {
            // Redis 삭제 과정에서 예외가 나더라도, 로그만 기록하고 흐름은 계속 이어간다.
            log.error("Error deleting refresh token from Redis: {}", e.getMessage());
        }

        // 2) 무조건 쿠키를 삭제 (null 체크 없이), SecurityContext 초기화
        ResponseCookie deleteAccessCookie  = clearCookie("access_token");
        ResponseCookie deleteRefreshCookie = clearCookie("refresh_token");
        SecurityContextHolder.clearContext();

        // 3) 삭제용 쿠키 배열 반환
        return new ResponseCookie[]{ deleteAccessCookie, deleteRefreshCookie };
    }

    // --- internal helpers ---


    private String createToken(String subject, String nickname, String id, List<String> roles,String profileColor, Duration ttl) {
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
//                .claim("roles", String.join(",", roles))
                .claim("roles", roles)
                .claim("nickname", nickname)
                .claim("id", id)
                .claim("profileColor", profileColor)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** HttpOnly, Secure, SameSite=Lax 쿠키 추가 */
    private ResponseCookie addCookie(String name, String value,
                           Duration ttl, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
//                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(ttl)
                .build();
        return cookie;
    }

    private ResponseCookie clearCookie(String name) {
        String path = "/";

        // refresh_token의 경우 생성 시와 동일한 path 사용
        if ("refresh_token".equals(name)) {
            path = "/api/auth/oauth2/refresh";
        }

        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
//                .secure(true)
                .path(path)
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        return cookie;
    }
}
