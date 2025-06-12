package com.itcen.whiteboardserver.member.service;

import com.itcen.whiteboardserver.global.exception.GlobalCommonException;
import com.itcen.whiteboardserver.global.exception.GlobalErrorCode;
import com.itcen.whiteboardserver.member.dto.MemberDTO;
import com.itcen.whiteboardserver.member.dto.MemberResponseDTO;
import com.itcen.whiteboardserver.member.dto.NicknameDTO;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.member.enums.ProfileColor;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import com.itcen.whiteboardserver.member.repository.NicknamesRepository;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static com.itcen.whiteboardserver.member.enums.AuthProvider.GOOGLE;
import static com.itcen.whiteboardserver.member.enums.ProfileColor.HOTPINK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTests {

    @Mock
    MemberRepository memberRepository;
    @Mock
    NicknamesRepository nicknamesRepository;

    @InjectMocks
    MemberServiceImpl memberService;

    @Test
    @DisplayName("getMemberByEmail: 이메일로 조회하면 MemberDTO 반환")
    void getMemberByEmail_whenFound_returnsDto() {
        // --- Given ---
        String email = "test@example.com";

        Member member = new Member(
                1L,
                "당당한 풍뎅이",
                "test@example.com",
                "default",
                HOTPINK,
                LocalDateTime.now(),
                LocalDateTime.now(),
                Set.of(MemberRole.MEMBER),
                GOOGLE,
                "123"
        );

        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));

        // --- When ---
        MemberDTO memberDTO = memberService.getMemberByEmail(email);

        // --- Then ---
        assertThat(memberDTO).isNotNull();
        assertThat(memberDTO.getId()).isEqualTo(1L);
        assertThat(memberDTO.getEmail()).isEqualTo("test@example.com");
        assertThat(memberDTO.getNickname()).isEqualTo("당당한 풍뎅이");
        assertThat(memberDTO.getProfileColor()).isEqualTo(HOTPINK);
        assertThat(memberDTO.getMemberRole()).isEqualTo(Set.of(MemberRole.MEMBER));
    }

    @Test
    @DisplayName("getMemberByEmail: 존재하지 않으면 MEMBER_NOT_FOUND 예외")
    void getMemberByEmail_whenNotFound_throws(){
        // --- Given ---
        given(memberRepository.findByEmail(anyString())).willReturn(Optional.empty());

        // --- When / Then ---
        assertThatThrownBy(() -> memberService.getMemberByEmail("aa@example.com"))
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.MEMBER_NOT_FOUND);
    }

    //--- getRandomNickname ---------------------------------------------------

    @Test
    @DisplayName("getRandomNickname: 닉네임 있으면 반환")
    void getRandomNickname_whenExists_returnsDto() {
        // given
        NicknameDTO nick = new NicknameDTO(2L, "달콤이");
        given(nicknamesRepository.findRandomNickname())
                .willReturn(nick);

        // when
        NicknameDTO result = memberService.getRandomNickname();

        // then
        assertThat(result).isSameAs(nick);
    }

    @Test
    @DisplayName("getRandomNickname: 없으면 NICKNAME_NOT_FOUND 예외")
    void getRandomNickname_whenMissing_throws() {
        // given
        given(nicknamesRepository.findRandomNickname())
                .willReturn(null);

        // when/then
        assertThatThrownBy(() -> memberService.getRandomNickname())
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.NICKNAME_NOT_FOUND);
    }

    //--- postRandomNickname ---------------------------------------------------

    @Test
    @DisplayName("postRandomNickname: 존재하는 닉네임이면 is_used 변경 후 반환")
    void postRandomNickname_whenExists_marksUsedAndReturns() {
        // given
        NicknameDTO nick = new NicknameDTO(3L, "사자왕");
        given(nicknamesRepository.findRandomNickname())
                .willReturn(nick);

        // when
        NicknameDTO result = memberService.postRandomNickname();

        // then
        then(nicknamesRepository)
                .should().updateIsUsed(3L, true);
        assertThat(result).isSameAs(nick);
    }

    @Test
    @DisplayName("postRandomNickname: 없으면 NICKNAME_NOT_FOUND 예외")
    void postRandomNickname_whenMissing_throws() {
        // given
        given(nicknamesRepository.findRandomNickname())
                .willReturn(null);

        // when/then
        assertThatThrownBy(() -> memberService.postRandomNickname())
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.NICKNAME_NOT_FOUND);
    }

    //--- patchNickname -------------------------------------------------------

    @Test
    @DisplayName("patchNickname: 올바른 principal 과 nicknameDTO 주어지면 업데이트 후 DTO 반환")
    void patchNickname_withValidInput_updatesAndReturns() {
        // given
        CustomPrincipal principal =
                new CustomPrincipal(10L, "x@y.com", "zzz", "default", Set.of(MemberRole.MEMBER), HOTPINK);

        NicknameDTO dto = new NicknameDTO(7L, "newNick");

        // no stubbing needed for repository updates (void)

        // when
        MemberResponseDTO result = memberService.patchNickname(principal, dto);

        // then
        then(memberRepository)
                .should().updateNickname(10L, "newNick");
        then(nicknamesRepository)
                .should().updateIsUsed(7L, true);
        then(nicknamesRepository)
                .should().updateIsUsedByNickname("zzz");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getEmail()).isEqualTo("x@y.com");
        assertThat(result.getNickname()).isEqualTo("newNick");
        assertThat(result.getProfileColor()).isEqualTo(HOTPINK);
    }

    @Test
    @DisplayName("patchNickname: principal 이 null 이면 MEMBER_NOT_FOUND 예외")
    void patchNickname_withNullPrincipal_throwsMemberNotFound() {
        // given
        NicknameDTO dto = new NicknameDTO(9L, "어차피");

        // when/then
        assertThatThrownBy(() -> memberService.patchNickname(null, dto))
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("patchNickname: repository 에러 시 NICKNAME_UPDATE_FAILED 예외")
    void patchNickname_whenRepoThrows_throwsNicknameUpdateFailed() {
        // given
        CustomPrincipal principal =
                new CustomPrincipal(10L, "x@y.com", "zzz", "default",Set.of(MemberRole.MEMBER), HOTPINK);
        NicknameDTO dto = new NicknameDTO(8L, "다람쥐");

        doThrow(new RuntimeException("db error"))
                .when(memberRepository).updateNickname(6L, "다람쥐");

        // when/then
        assertThatThrownBy(() -> memberService.patchNickname(principal, dto))
                .isInstanceOf(GlobalCommonException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.NICKNAME_UPDATE_FAILED);
    }

    @Test
    @DisplayName("patchNickname: nicknameDTO 가 null 이면 null 반환")
    void patchNickname_withNullDto_returnsNull() {
        // given
        CustomPrincipal principal =
                new CustomPrincipal(10L, "x@y.com", "zzz", "default",Set.of(MemberRole.MEMBER), HOTPINK);

        // when
        MemberResponseDTO result = memberService.patchNickname(principal, null);

        // then
        assertThat(result).isNull();
    }
}