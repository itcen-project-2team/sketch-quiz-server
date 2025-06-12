package com.itcen.whiteboardserver.member.controller;

import com.itcen.whiteboardserver.global.exception.GlobalCommonException;
import com.itcen.whiteboardserver.global.exception.GlobalErrorCode;
import com.itcen.whiteboardserver.member.dto.MemberResponseDTO;
import com.itcen.whiteboardserver.member.dto.NicknameDTO;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.member.enums.ProfileColor;
import com.itcen.whiteboardserver.member.service.MemberService;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static com.itcen.whiteboardserver.member.enums.ProfileColor.HOTPINK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberControllerTests {

    @Mock
    MemberService memberService;

    @InjectMocks
    MemberController memberController;

    @Test
    @DisplayName("인증된 Principal이 있으면 200 ok와 MemberResponseDTO 반환")
    void getMember_withValidPrincipal_returnsMemberResponse() {
        // --- Given ---
        CustomPrincipal customPrincipal = new CustomPrincipal(
                1L,
                "user@example.com",
                "별명",
                "password",
                Set.of(MemberRole.MEMBER),
                HOTPINK
        );

        // --- When ---
        ResponseEntity<MemberResponseDTO> response = memberController.getMember(customPrincipal);

        // --- Then ---
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MemberResponseDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(1L);
        assertThat(body.getEmail()).isEqualTo("user@example.com");
        assertThat(body.getNickname()).isEqualTo("별명");
        assertThat(body.getProfileColor()).isEqualTo(HOTPINK);
    }

    @Test
    @DisplayName("Principal이 null 이면 MEMBER_NOT_FOUND 예외 발생")
    void getMember_withNullPrincipal_throwsGlobalCommonException() {
        // --- When / Then ---
        assertThatThrownBy(() -> memberController.getMember(null))
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("닉네임을 골랐을 시, 닉네임과 principal을 전송하여 200 ok 및 MemberResponseDTO 반환")
    void patchChangeNickname_withPrincipalAndNicknameDTO_returnsMemberResponse() {
        // --- Given ---
        CustomPrincipal customPrincipal = new CustomPrincipal(
                1L,
                "user@example.com",
                "별명",
                "password",
                Set.of(MemberRole.MEMBER),
                HOTPINK
        );

        NicknameDTO nicknameDTO = new NicknameDTO(
                1L,
                "별명1"
        );

        MemberResponseDTO updated = MemberResponseDTO.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("별명1")
                .profileColor(HOTPINK)
                .build();

        given(memberService.patchNickname(customPrincipal, nicknameDTO)).willReturn(updated);

        // --- When ---
        ResponseEntity<MemberResponseDTO> response = memberController.patchChangeNickname(nicknameDTO, customPrincipal);

        // --- Then ---
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MemberResponseDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(1L);
        assertThat(body.getEmail()).isEqualTo("user@example.com");
        assertThat(body.getNickname()).isEqualTo("별명1");
        assertThat(body.getProfileColor()).isEqualTo(HOTPINK);
    }

    @Test
    void getRandomNickname_returnsNicknameDTO() {
        // --- Given ---
        NicknameDTO nicknameDTO = new NicknameDTO(
                1L,
                "별명1"
        );

        given(memberService.getRandomNickname()).willReturn(nicknameDTO);

        // --- When ---
        ResponseEntity<NicknameDTO> response = memberController.getRandomNickname();

        // --- Then ---
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        NicknameDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(1L);
        assertThat(body.getNickname()).isEqualTo("별명1");
    }
}