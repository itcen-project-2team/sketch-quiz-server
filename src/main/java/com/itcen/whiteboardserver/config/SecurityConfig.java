package com.itcen.whiteboardserver.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.itcen.whiteboardserver.auth.service.CustomOAuth2UserService;
import com.itcen.whiteboardserver.auth.service.CustomOidcUserService;
import com.itcen.whiteboardserver.auth.service.UserDetailsServiceImpl;
import com.itcen.whiteboardserver.config.mvc.SpaCsrfTokenRequestHandler;
import com.itcen.whiteboardserver.global.exception.GlobalErrorCode;
import com.itcen.whiteboardserver.global.exception.GlobalExceptionResponse;
import com.itcen.whiteboardserver.security.filter.JwtAuthenticationFilter;
import com.itcen.whiteboardserver.security.filter.RedisRateLimitingFilter;
import com.itcen.whiteboardserver.security.filter.RequestResponseLoggingFilter;
import com.itcen.whiteboardserver.security.handler.CookieAuthorizationRequestRepository;
import com.itcen.whiteboardserver.security.handler.CustomAuthenticationEntryPoint;
import com.itcen.whiteboardserver.security.handler.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;

/**
 * Spring Security 6.x configuration applying best practices:
 * 1. Access & Refresh tokens stored in HttpOnly cookies (SameSite=Lax)
 * 2. CSRF protection: Double-submit using Non-HttpOnly CSRF cookie + header check
 * 3. Redis TTL-based refresh-token storage
 * 4. Logout revokes Redis keys and clears cookies
 * 5. Rate limiting on refresh endpoint using distributed token bucket (Bucket4j + Redis)
 */
@Configuration
@EnableWebSecurity
@Slf4j
@RequiredArgsConstructor
@Profile("dev")
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RedisRateLimitingFilter redisRateLimitingFilter;
    private final RequestResponseLoggingFilter requestResponseLoggingFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CustomOidcUserService customOidcUserService;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS 설정을 dsl로 연결
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // 가장 먼저 만날 필터(로깅 설정)
                .addFilterBefore(requestResponseLoggingFilter,
                        SecurityContextHolderFilter.class)
                // Rate limiting filter 를 먼저 chaining 하는 것은 비인증 사용자만 하면 된다
                .addFilterAfter(redisRateLimitingFilter, RequestResponseLoggingFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // 랜딩 시 api/member 호출하기 때문에 cookie를 내려줌.
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
//                // JWT auth filter -> 인증 필터보다 먼저 토큰 추출 및 검증을 하여 SecurityContext 설정
//                .addFilterAfter(jwtAuthenticationFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, CsrfFilter.class)
                // OAuth2 Login
                .oauth2Login(oauth2 -> oauth2
                                .authorizationEndpoint(a -> a
                                        .baseUri("/oauth2/authorization")
                                        // 쿠키 저장소: OAuth2 인가 요청(state) 보관
                                        .authorizationRequestRepository(authorizationRequestRepository()
                                        )
                                )
                                .redirectionEndpoint(r ->
                                        r.baseUri("/login/oauth2/code/*"))
                                .userInfoEndpoint(u ->
                                        u.userService(customOAuth2UserService)
                                                // OIDC (openid) 용
                                                .oidcUserService(customOidcUserService)
                                )
                                .successHandler(oAuth2LoginSuccessHandler)

                                .failureHandler((request, response, exception) -> {
                                    // 1) 예외 로그 찍기
                                    log.error("OAuth2 로그인 실패: registrationId={}, uri={}",
                                            request.getParameter("registrationId"),
                                            request.getRequestURI(),
                                            exception
                                    );

                                    sendErrorResponse(response, GlobalErrorCode.OAUTH_UNAUTHORIZED);
                                })
                )
                // Disable HTTP session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/logout",
                                "/api/auth/oauth2/refresh",
                                "/login/oauth2/code/kakao",      // 카카오 OAuth2 콜백 (경로는 실제 리다이렉트 URI에 따라 다를 수 있음)
                                "/oauth2/authorization/kakao"    // 카카오 OAuth2 로그인 시작 (프론트에서 이 경로로 요청)
                        ).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        .anyRequest().authenticated()
                )


                /* 설명. 보호된 api 접근 시*/
                .exceptionHandling(ex -> ex
                                .authenticationEntryPoint(new CustomAuthenticationEntryPoint(GlobalErrorCode.ACCESS_TOKEN_EXPIRED))
                                .accessDeniedHandler(((request, response, accessDeniedException) -> {
                                    log.error("Access Denied: {}", accessDeniedException.getMessage(), accessDeniedException);
                                    sendErrorResponse(response, GlobalErrorCode.ENDPOINT_NOT_FOUND);
                                }))
                );

        return http.build();
    }

    /*
     * CSRF token repository using latest builder for HttpOnly cookie.
     * Cookie name: XSRF-TOKEN, Header name: X-XSRF-TOKEN, SameSite=Lax
     */
    private CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(cookie -> cookie
                        .path("/")              // 모든 경로에 대해 CSRF 쿠키 적용
//                .secure(true)          // HTTPS 환경에서만 전송되도록
                        .sameSite("Lax")       // CSRF 방지 기본 전략
        );
        return repo;
    }

    /* 설명. 인코딩에 있어서 가용성이 좋은 팩토리*/
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * AuthenticationConfiguration 에서 컨텍스트에 등록된 AuthenticationManager를 꺼내 옵니다.
     * WebSecurityConfigurerAdapter 없이도 전역 AuthenticationManager 를 사용할 수 있게 해 줍니다.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            UserDetailsServiceImpl userDetailsService,
            PasswordEncoder passwordEncoder
    ) throws Exception {
        AuthenticationManagerBuilder authBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);

        authBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder);

        return authBuilder.build();
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository(){
        return new CookieAuthorizationRequestRepository();
    }

    private void sendErrorResponse(HttpServletResponse response, GlobalErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // JSON 직렬화: { "code": 40101, "message": "Access Token Expired" }
        GlobalExceptionResponse globalExceptionResponse = GlobalExceptionResponse.of(errorCode);
        String json = objectMapper.writeValueAsString(globalExceptionResponse);
        response.getWriter().write(json);
    }

}
