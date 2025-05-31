package com.itcen.whiteboardserver.game.service;

import com.itcen.whiteboardserver.game.constant.GameConstants;
import com.itcen.whiteboardserver.game.dto.request.GameStartRequest;
import com.itcen.whiteboardserver.game.dto.response.GameStartedResponse;
import com.itcen.whiteboardserver.game.entity.Game;
import com.itcen.whiteboardserver.game.entity.GameParticipation;
import com.itcen.whiteboardserver.game.entity.Room;
import com.itcen.whiteboardserver.game.entity.RoomParticipation;
import com.itcen.whiteboardserver.game.exception.*;
import com.itcen.whiteboardserver.game.repository.GameParticipationRepository;
import com.itcen.whiteboardserver.game.repository.GameRepository;
import com.itcen.whiteboardserver.game.repository.RoomParticipationRepository;
import com.itcen.whiteboardserver.game.repository.RoomRepository;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final MemberRepository memberRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipationRepository roomParticipationRepository;
    private final GameRepository gameRepository;
    private final GameParticipationRepository gameParticipationRepository;
    private final SimpMessagingTemplate messagingTemplate;


    /**
     * 게임 시작
     */
    @Transactional
    public void startGame(GameStartRequest request, String memberEmail) {
        // 멤버 조회
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new MemberNotFoundException("사용자를 찾을 수 없습니다."));
        Long memberId = member.getId();
        Long roomId = request.getRoomCode();
        log.info("게임 시작 요청: roomId={}, memberId={}", roomId, memberId);

        // 방 정보 조회
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("방을 찾을 수 없습니다."));

        // 방장 확인
        if (!Objects.equals(room.getHost().getId(), memberId)) {
            log.error("게임 시작 실패: 방장만 게임을 시작할 수 있습니다. (roomId={}, hostId={}, requesterId={})",
                    roomId, room.getHost().getId(), memberId);
            throw new UnauthorizedException("방장만 게임을 시작할 수 있습니다.");
        }

        // 방 상태 확인
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            log.error("게임 시작 실패: 대기 중인 방만 게임을 시작할 수 있습니다. (roomId={}, status={})",
                    roomId, room.getStatus());
            throw new InvalidRoomStatusException("대기 중인 방만 게임을 시작할 수 있습니다.");
        }

        // 참가자 수 확인
        List<RoomParticipation> participants = roomParticipationRepository.findByRoomId(roomId);
        int participantCount = participants.size();

        if (participantCount < GameConstants.MIN_PARTICIPANTS) {
            log.error("게임 시작 실패: 최소 {}명의 참가자가 필요합니다. (roomId={}, 현재 참가자 수={})",
                    GameConstants.MIN_PARTICIPANTS, roomId, participantCount);
            throw new InsufficientParticipantsException("게임을 시작하려면 최소 " + GameConstants.MIN_PARTICIPANTS + "명의 참가자가 필요합니다.");
        }

        if (participantCount > GameConstants.MAX_PARTICIPANTS) {
            log.error("게임 시작 실패: 최대 {}명까지만 참가할 수 있습니다. (roomId={}, 현재 참가자 수={})",
                    GameConstants.MAX_PARTICIPANTS, roomId, participantCount);
            throw new TooManyParticipantsException("게임에는 최대 " + GameConstants.MAX_PARTICIPANTS + "명까지만 참가할 수 있습니다.");
        }

        // 게임 생성
        Game game = new Game(
                null,
                null,
                Game.GameStatus.NOT_STARTED
        );
        Game savedGame = gameRepository.save(game);
        log.debug("게임 생성 완료: gameId={}", savedGame.getId());

        // 방 상태 업데이트
        room.updateStatus(Room.RoomStatus.PLAYING);
        room.updateCurrentGame(savedGame);
        roomRepository.save(room);
        log.debug("방 상태 업데이트 완료: roomId={}, status={}", room.getId(), room.getStatus());

        // 게임 참가자 등록
        for (RoomParticipation participant : participants) {
            Member m = participant.getMember();
            GameParticipation gameParticipation = new GameParticipation(
                    null,
                    savedGame,
                    m,
                    0 // 초기 점수는 0
            );
            gameParticipationRepository.save(gameParticipation);
            log.debug("게임 참가자 등록 완료: gameId={}, memberId={}", savedGame.getId(), member.getId());
        }

        // 게임 시작 알림
        GameStartedResponse response = new GameStartedResponse(savedGame.getId(), roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
        log.info("게임 시작 성공: roomId={}, gameId={}, 참가자 수={}", roomId, savedGame.getId(), participantCount);
    }
}