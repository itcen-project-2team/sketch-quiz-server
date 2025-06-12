package com.itcen.whiteboardserver.security.handler;

import com.itcen.whiteboardserver.auth.service.TokenService;
import com.itcen.whiteboardserver.member.dto.MemberDTO;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.member.enums.ProfileColor;
import com.itcen.whiteboardserver.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTests {

    /* 설명. 스텁과 객체를 자동으로 초기화*/
    @Mock MemberService memberService;
    @Mock TokenService   tokenService;
    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OAuth2AuthenticationToken oauthToken;
    @Captor ArgumentCaptor<String> headerName;
    @Captor ArgumentCaptor<String> headerValue;
    @Value("${FRONTEND_REDIRECT_URL}")
    private String frontendRedirectUrl;

    @InjectMocks OAuth2LoginSuccessHandler handler;

    @Test
    @DisplayName("oauth 로그인 성공 시, 쿠키 발급 및 redirect 확인")
    void onAuthenticationSuccess_setsCookies_andRedirects() throws IOException {

        // --- Given ---
        // 1) 토큰 principal이 반환할 이메일 설정
        String email = "u@example.com";
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", email),
                "email"
        );
        given(oauthToken.getPrincipal()).willReturn(principal);

        // 2) MemberService가 반환할 MemberDTO 준비
        MemberDTO m = MemberDTO.builder()
                .id(1L)
                .email("u@example.com")
                .nickname("nick")
                .memberRole(Set.of(MemberRole.MEMBER))
                .profileColor(ProfileColor.HOTPINK)
                .build();
        given(memberService.getMemberByEmail(email)).willReturn(m);

        // 3) TokenService가 반환할 쿠키 배열 준비
        ResponseCookie access  = ResponseCookie.from("access_token",  "A").build();
        ResponseCookie refresh = ResponseCookie.from("refresh_token", "R").build();
        given(tokenService.issueTokens(
                eq(email),
                eq("nick"),
                eq("1"),
                eq(List.of("MEMBER")),
                eq("HOTPINK")
        )).willReturn(new ResponseCookie[]{ access, refresh });


        // --- When ---
        handler.onAuthenticationSuccess(request, response, oauthToken);

        // --- Then ---
        /* 설명. then(mock).should()를 통해 행위가 일어났는지 검증 */
//        then(response).should().addHeader(HttpHeaders.SET_COOKIE, access.toString());
//        then(response).should().addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
//        then(response).should().sendRedirect("/");
        then(response).should(times(2)).addHeader(headerName.capture(), headerValue.capture());
        assertThat(headerName.getAllValues())
                .containsExactly(HttpHeaders.SET_COOKIE, HttpHeaders.SET_COOKIE);
        assertThat(headerValue.getAllValues())
                .anySatisfy(v -> assertThat(v).contains("access_token=A"))
                .anySatisfy(v -> assertThat(v).contains("refresh_token=R"));

        // 마지막엔 redirect 도 놓치지 않고 검증
        then(response).should().sendRedirect(frontendRedirectUrl);
    }
}
