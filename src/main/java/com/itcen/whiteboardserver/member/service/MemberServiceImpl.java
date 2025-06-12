package com.itcen.whiteboardserver.member.service;

import com.itcen.whiteboardserver.global.exception.GlobalCommonException;
import com.itcen.whiteboardserver.global.exception.GlobalErrorCode;
import com.itcen.whiteboardserver.member.dto.MemberDTO;
import com.itcen.whiteboardserver.member.dto.MemberResponseDTO;
import com.itcen.whiteboardserver.member.dto.NicknameDTO;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import com.itcen.whiteboardserver.member.repository.NicknamesRepository;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final NicknamesRepository nicknamesRepository;

    @Override
    @Transactional(readOnly = true)
    public MemberDTO getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .map(member -> new MemberDTO(member.getId(), member.getNickname(), member.getEmail(), member.getMemberRole(), member.getProfileColor()))
                .orElseThrow(() -> new GlobalCommonException(GlobalErrorCode.MEMBER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public NicknameDTO getRandomNickname() {

        NicknameDTO randomNickname = nicknamesRepository.findRandomNickname();

        if(randomNickname == null) {
            throw new GlobalCommonException(GlobalErrorCode.NICKNAME_NOT_FOUND);
        }

        return randomNickname;
    }


    /* 설명.
     *  초기 사용자를 위한 랜덤 닉네임 설정
    * */
    @Override
    @Transactional
    public NicknameDTO postRandomNickname() {

        /* 설명. 닉네임 repo에서 닉네임 가져오기
         *  1. is_used가 false인 닉네임을 가져온다.
         *  2. 가져온 닉네임을 사용자의 닉네임으로 설정한다.
         *  3. 사용한 닉네임은 is_used를 true로 변경한다.
        * */

        NicknameDTO randomNickname = nicknamesRepository.findRandomNickname();

        if(randomNickname == null) {
            throw new GlobalCommonException(GlobalErrorCode.NICKNAME_NOT_FOUND);
        }


        nicknamesRepository.updateIsUsed(randomNickname.getId(), true);

        return randomNickname;
    }

    /* 설명.
     *  선택한 닉네임으로 바꾸기
    * */
    @Override
    @Transactional
    public MemberResponseDTO patchNickname(CustomPrincipal principal, NicknameDTO nicknameDTO) {

        if (principal == null) {
            throw new GlobalCommonException(GlobalErrorCode.MEMBER_NOT_FOUND);
        }

        /* 설명. 닉네임 repo에서 닉네임 가져오기
         *  2. 가져온 닉네임을 사용자의 닉네임으로 설정한다.
         *  3. 사용한 닉네임은 is_used를 true로 변경한다.
         *  4. 기존 닉네임은 is_used를 false로 변경한다.
        * */
        MemberResponseDTO memberDTO = null;

        if (nicknameDTO != null) {
            Long memberId = principal.getId();

            try {
                // 닉네임을 사용자의 닉네임으로 설정
                memberRepository.updateNickname(memberId, nicknameDTO.getNickname());

                // 사용한 닉네임은 is_used를 true로 변경
                nicknamesRepository.updateIsUsed(nicknameDTO.getId(), true);

                // 기존 닉네임은 is_used를 false로 변경
                nicknamesRepository.updateIsUsedByNickname(principal.getNickname());

                memberDTO = new MemberResponseDTO(
                        memberId,
                        nicknameDTO.getNickname(),
                        principal.getEmail(),
                        principal.getProfileColor()
                );

            } catch (Exception e) {
                // 예외 처리 로직 추가 (예: 로그 기록, 사용자에게 에러 메시지 전달 등)
                log.error("Error updating nickname for member ID: {}", memberId, e);
                throw new GlobalCommonException(GlobalErrorCode.NICKNAME_UPDATE_FAILED);
            }
        }
        return memberDTO;
    }

}
