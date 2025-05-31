package com.itcen.whiteboardserver.game.service;

import com.itcen.whiteboardserver.game.constant.GameConstants;
import com.itcen.whiteboardserver.game.dto.request.RoomInfoRequest;
import com.itcen.whiteboardserver.game.dto.request.RoomJoinRequest;
import com.itcen.whiteboardserver.game.dto.response.ParticipantResponse;
import com.itcen.whiteboardserver.game.dto.response.RoomInfoResponse;
import com.itcen.whiteboardserver.game.dto.response.RoomResponse;
import com.itcen.whiteboardserver.game.entity.Room;
import com.itcen.whiteboardserver.game.entity.RoomParticipation;
import com.itcen.whiteboardserver.game.event.RoomHostChangedEvent;
import com.itcen.whiteboardserver.game.event.RoomParticipantChangedEvent;
import com.itcen.whiteboardserver.game.exception.MemberNotFoundException;
import com.itcen.whiteboardserver.game.exception.RoomJoinException;
import com.itcen.whiteboardserver.game.exception.RoomNotFoundException;
import com.itcen.whiteboardserver.game.repository.RoomParticipationRepository;
import com.itcen.whiteboardserver.game.repository.RoomRepository;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomParticipationRepository participationRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher applicationEventPublisher;


    /**
     * 방 생성
     */
    @Transactional
    public RoomResponse createRoom(String memberEmail) {
        log.info("방 생성 요청: memberEmail={}", memberEmail);
        Member host = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new MemberNotFoundException("사용자를 찾을 수 없습니다."));

        // 방 생성
        Room room = new Room(
                null,
                null,
                host,
                null,
                Room.RoomStatus.WAITING,
                null
        );
        Room savedRoom = roomRepository.save(room);
        log.debug("방 생성 완료: roomId={}", savedRoom.getId());

        // 참가자로 등록
        RoomParticipation participation = new RoomParticipation(null, savedRoom, host);
        participationRepository.save(participation);
        log.debug("방장 참가자로 등록 완료: roomId={}, memberId={}", savedRoom.getId(), host.getId());


        // 방 코드 반환
        log.info("방 생성 성공: roomId={}, hostId={}", savedRoom.getId(), host.getId());
        return new RoomResponse(room.getId());
    }

    /**
     * 방 정보 조회
     */
    @Transactional(readOnly = true)
    public RoomInfoResponse getRoomInfoByRoomCode(RoomInfoRequest request) {
        log.info("방 정보 조회 요청: roomCode={}", request.getRoomCode());

        // 방 정보
        Room room = roomRepository.findById(request.getRoomCode())
                .orElseThrow(() -> new RoomNotFoundException("방을 찾을 수 없습니다."));

        // 참여자 정보
        List<RoomParticipation> roomParticipationList = participationRepository.findByRoomId(request.getRoomCode());
        log.debug("방 참여자 조회 완료: roomId={}, 참여자 수={}", room.getId(), roomParticipationList.size());

        List<ParticipantResponse> participantList = roomParticipationList.stream()
                .map(roomParticipation -> {
                    Member member = roomParticipation.getMember();
                    boolean isHost = Objects.equals(member.getId(), room.getHost().getId());
                    return new ParticipantResponse(member.getId(), member.getName(), isHost);
                })
                .toList();

        log.info("방 정보 조회 성공: roomId={}, 참여자 수={}", room.getId(), participantList.size());
        return new RoomInfoResponse(participantList, room.getId());
    }

    /**
     * 방 참여
     */
    @Transactional
    public void joinRoom(RoomJoinRequest request, String memberEmail) {
        log.info("방 참여 요청: roomCode={}", request.getRoomCode());
        // 방
        Long roomCode = request.getRoomCode();
        Room room = roomRepository.findById(roomCode)
                .orElseThrow(() -> new RoomNotFoundException("방을 찾을 수 없습니다."));
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            log.error("방 참여 실패: 참여할 수 없는 방 상태 (roomCode={}, status={})", roomCode, room.getStatus());
            throw new RoomJoinException("참여할 수 없는 방입니다.");
        }

        // 사용자
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new MemberNotFoundException("사용자를 찾을 수 없습니다."));

        // 이미 참여 중인지 확인
        boolean alreadyJoined = participationRepository
                .findByRoomIdAndMemberId(room.getId(), member.getId())
                .isPresent();
        if (alreadyJoined) {
            throw new RoomJoinException("이미 참여중인 방입니다.");
        }

        // 참가자 수 확인 - 최대 인원 체크
        List<RoomParticipation> currentParticipants = participationRepository.findByRoomId(roomCode);
        if (currentParticipants.size() >= GameConstants.MAX_PARTICIPANTS) {
            log.error("방 참여 실패: 최대 참가자 수 초과 (roomId={}, 현재 참가자 수={})",
                    roomCode, currentParticipants.size());
            throw new RoomJoinException("방에 더 이상 참여할 수 없습니다. 최대 인원은 " + GameConstants.MAX_PARTICIPANTS + "명입니다.");
        }

        // 참가자로 등록
        RoomParticipation participation = new RoomParticipation(null, room, member);
        participationRepository.save(participation);
        log.debug("방 참여자 등록 완료: roomId={}, memberId={}", room.getId(), member.getId());

        // 이벤트 발행
        applicationEventPublisher.publishEvent(new RoomParticipantChangedEvent(roomCode));
        log.info("방 참여 성공: roomId={}, memberId={}", room.getId(), member.getId());
    }

    /**
     * 방 떠나기
     */
    @Transactional
    public void leaveRoom(String memberEmail) {
        log.info("방 나가기 요청: memberEmail={}", memberEmail);

        // 사용자
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new MemberNotFoundException("사용자를 찾을 수 없습니다."));
        Long memberId = member.getId();

        // 룸
        List<RoomParticipation> participationList = participationRepository.findByMemberId(memberId);

        if (!participationList.isEmpty()) {
            for (RoomParticipation roomParticipation : participationList) {
                // 룸
                Long roomId = roomParticipation.getRoom().getId();
                // 방 참여 정보 삭제
                participationRepository.delete(roomParticipation);
                log.debug("방 참여 정보 삭제 완료: roomId={}, memberId={}", roomId, memberId);

                // 만약 방장이 나가는 경우, 방의 상태를 변경하거나 다른 사용자를 방장으로 변경하는 로직
                Room room = roomParticipation.getRoom();
                if (Objects.equals(room.getHost().getId(), memberId)) {
                    log.debug("방장이 방을 나가는 경우 처리: roomId={}, hostId={}", roomId, memberId);
                    List<RoomParticipation> remainingParticipants = participationRepository.findByRoomId(room.getId());

                    if (remainingParticipants.isEmpty()) {
                        // 참가자가 없으면 방 상태 변경
                        room.updateStatus(Room.RoomStatus.FINISHED);
                        roomRepository.save(room);
                        log.info("방 상태 변경 완료: roomId={}, status=FINISHED (참가자 없음)", roomId);
                    } else {
                        // 첫 번째 참가자를 새 방장으로 지정
                        Member newHost = remainingParticipants.get(0).getMember();
                        room.updateHost(newHost);
                        roomRepository.save(room);
                        log.info("새 방장 지정 완료: roomId={}, newHostId={}", roomId, newHost.getId());

                        // 참가자 변경 이벤트 발행
                        applicationEventPublisher.publishEvent(new RoomHostChangedEvent(roomId, newHost.getId()));
                    }
                } else {
                    // 일반 참가자가 나가는 경우
                    log.info("일반 참가자 방 나가기 완료: roomId={}, memberId={}", roomId, memberId);
                    applicationEventPublisher.publishEvent(new RoomParticipantChangedEvent(roomId));
                }
            }
        } else {
            log.warn("방 나가기 실패: 방에 참여하고 있지 않음 (memberId={})", memberId);
        }
    }


}