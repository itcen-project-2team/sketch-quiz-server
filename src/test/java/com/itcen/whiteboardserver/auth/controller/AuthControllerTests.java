package com.itcen.whiteboardserver.auth.controller;

import com.itcen.whiteboardserver.auth.service.TokenService;
import com.itcen.whiteboardserver.member.dto.MemberResponseDTO;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static com.itcen.whiteboardserver.member.enums.ProfileColor.HOTPINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    TokenService tokenService;
    @Captor
    ArgumentCaptor<String> headerName;
    @Captor
    ArgumentCaptor<String> headerValue;

    @InjectMocks
    AuthController authController;

    @Test
    @DisplayName("refresh_token이 있으면, 검증 후 200 ok와 새 access,refresh token 발급")
    void refresh_WithRefreshToken_returnsAccessTokenAndRefreshToken(){
        // --- Given ---
        ResponseCookie refresh = ResponseCookie.from("refresh_token", "R").build();

        ResponseCookie access2  = ResponseCookie.from("access_token",  "A2").build();
        ResponseCookie refresh2 = ResponseCookie.from("refresh_token", "R2").build();

        given(tokenService.rotateRefresh(refresh.toString())).willReturn(new ResponseCookie[]{ access2, refresh2});

        // --- When ---
        ResponseEntity<Void> response = authController.refresh(refresh.toString());

        // --- Then ---
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // ResponseEntity 에 담긴 헤더에서 Set-Cookie 값을 꺼냅니다.
        HttpHeaders headers = response.getHeaders();
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);

        // 두 개의 Set-Cookie 헤더가 정확히 access2, refresh2 순서로 담겼는지 검증
        assertThat(setCookieHeaders)
                .containsOnly(access2.toString(), refresh2.toString());
    }

    @Test
    @DisplayName("refresh_token이 있으면, 검증 후 200 ok와 새 access, refresh 무효화 쿠키 발급")
    void logout_WithRefreshToken_returnsAccessTokenAndRefreshToken(){
        // --- Given ---
        CustomPrincipal customPrincipal = new CustomPrincipal(
                1L,
                "user@example.com",
                "별명",
                "password",
                Set.of(MemberRole.MEMBER),
                HOTPINK
        );

        ResponseCookie access2  = ResponseCookie.from("access_token",  "A2").build();
        ResponseCookie refresh2 = ResponseCookie.from("refresh_token", "R2").build();

        given(tokenService.logout(customPrincipal)).willReturn(new ResponseCookie[]{ access2, refresh2});

        // --- When ---
        ResponseEntity<Void> response = authController.logout(customPrincipal);

        // --- Then ---
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // ResponseEntity 에 담긴 헤더에서 Set-Cookie 값을 꺼냅니다.
        HttpHeaders headers = response.getHeaders();
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);

        // 두 개의 Set-Cookie 헤더가 정확히 access2, refresh2 순서로 담겼는지 검증
        assertThat(setCookieHeaders)
                .containsOnly(access2.toString(), refresh2.toString());
    }
}