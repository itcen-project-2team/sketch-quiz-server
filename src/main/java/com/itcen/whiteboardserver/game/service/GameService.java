package com.itcen.whiteboardserver.game.service;

import com.itcen.whiteboardserver.game.constant.GameConstants;
import com.itcen.whiteboardserver.game.dto.request.GameStartRequest;
import com.itcen.whiteboardserver.game.dto.request.GuessRequest;
import com.itcen.whiteboardserver.game.dto.response.CorrectGuessResponse;
import com.itcen.whiteboardserver.game.dto.response.GameStatusResponse;
import com.itcen.whiteboardserver.game.entity.*;
import com.itcen.whiteboardserver.game.exception.*;
import com.itcen.whiteboardserver.game.repository.*;
import com.itcen.whiteboardserver.game.state.GameState;
import com.itcen.whiteboardserver.member.domain.aggregate.entity.Member;
import com.itcen.whiteboardserver.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final RoomRepository roomRepository;
    private final RoomParticipationRepository roomParticipationRepository;
    private final GameRepository gameRepository;
    private final GameParticipationRepository gameParticipationRepository;
    private final TurnRepository turnRepository;
    private final CorrectRepository correctRepository;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TurnService turnService;


    // 현재 활성화된 게임의 상태를 관리
    private final Map<Long, GameState> activeGames = new ConcurrentHashMap<>();


    /**
     * 게임 시작
     */
    @Transactional
    public void startGame(GameStartRequest request, Long memberId) {
        Long roomId = request.getRoomId();
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
                Game.GameStatus.IN_PROGRESS
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
            Member member = participant.getMember();
            GameParticipation gameParticipation = new GameParticipation(
                    null,
                    savedGame,
                    member,
                    0 // 초기 점수는 0
            );
            gameParticipationRepository.save(gameParticipation);
            log.debug("게임 참가자 등록 완료: gameId={}, memberId={}", savedGame.getId(), member.getId());
        }

        // 게임 참가자 순서 결정 (무작위)
        List<Long> playerOrder = participants.stream()
                .map(p -> p.getMember().getId())
                .collect(Collectors.toList());
        Collections.shuffle(playerOrder);

        // 게임 상태 초기화
        GameState gameState = new GameState(game.getId(), room.getId(), playerOrder);
        activeGames.put(game.getId(), gameState);

        // 첫 번째 턴 시작
        if (turnService.startNextTurn(gameState)) {
            endGame(gameState.getGameId());
        }

        // 게임 시작 알림
        messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/game-started",
                new GameStatusResponse(game.getId(), room.getId(), playerOrder));

        log.info("게임 시작 성공: roomId={}, gameId={}, 참가자 수={}", roomId, savedGame.getId(), participantCount);
    }


    @Transactional
    public void processGuess(GuessRequest request, Long memberId) {
        // 턴 정보 조회
        Turn turn = turnRepository.findById(request.getTurnId())
                .orElseThrow(() -> new IllegalArgumentException("턴을 찾을 수 없습니다."));

        // 이미 종료된 턴이면 무시
        if (turn.getTurnOver()) {
            return;
        }

        // 게임 상태 조회
        GameState gameState = activeGames.get(turn.getGame().getId());
        if (gameState == null) {
            throw new IllegalStateException("게임 상태를 찾을 수 없습니다.");
        }

        // 그림 그리는 사람은 추측할 수 없음
        if (memberId.equals(gameState.getCurrentDrawerId())) {
            return;
        }

        // 이미 정답을 맞춘 플레이어면 무시
        if (gameState.getCorrectPlayers().contains(memberId)) {
            return;
        }

        // 정답 확인
        if (isCorrectGuess(request.getGuess(), turn.getKeyword())) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

            // 정답 기록 저장
            Correct correct = new Correct(
                    null,
                    turn,
                    member,
                    null
            );
            correctRepository.save(correct);

            // 점수 계산 및 업데이트
            int scoreForThisGuess = calculateScore(gameState.getCorrectCount());
            GameParticipation participation = gameParticipationRepository.findByGameAndMember(turn.getGame(), member)
                    .orElseThrow(() -> new IllegalStateException("게임 참가 정보를 찾을 수 없습니다."));
            participation.addScore(scoreForThisGuess);
            gameParticipationRepository.save(participation);

            // 게임 상태 업데이트
            gameState.getCorrectPlayers().add(memberId);
            gameState.plusCorrectCount();

            // 정답 알림
            messagingTemplate.convertAndSend("/topic/room/" + gameState.getRoomId() + "/correct-guess",
                    new CorrectGuessResponse(memberId, member.getName(), participation.getScore())
            );

            // 모든 플레이어가 정답을 맞췄는지 확인
            List<GameParticipation> participants = gameParticipationRepository.findAllByGame(turn.getGame());
            if (gameState.isAllPlayersCorrect(participants.size())) {
                endCurrentTurn(gameState, turn);
            }
        }
    }

    // 턴 시간이 만료된 게임을 확인
    @Scheduled(fixedRate = 5000) // 5초마다 체크
    public void checkExpiredTurns() {
        LocalDateTime now = LocalDateTime.now();

        for (GameState gameState : activeGames.values()) {
            if (gameState.getTurnEndTime() != null && now.isAfter(gameState.getTurnEndTime())) {
                // 턴 종료 시간이 지났으면 턴 종료
                Turn turn = turnRepository.findById(gameState.getCurrentTurnId())
                        .orElseThrow(() -> new IllegalStateException("턴을 찾을 수 없습니다."));

                if (!turn.getTurnOver()) {
                    endCurrentTurn(gameState, turn);
                }
            }
        }
    }




    @Transactional
    public void endGame(Long gameId) {
        // 게임 상태 조회
        GameState gameState = activeGames.get(gameId);
        if (gameState == null) {
            throw new IllegalStateException("게임 상태를 찾을 수 없습니다.");
        }

        // 게임 정보 조회
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalStateException("게임을 찾을 수 없습니다."));

        // 게임 상태 업데이트
        game.changeStatus(Game.GameStatus.ENDED);
        gameRepository.save(game);

        // 최종 점수 정보 조회
        List<GameParticipation> finalScores = gameParticipationRepository.findAllByGame(game);

        // 게임 결과 알림
        messagingTemplate.convertAndSend("/topic/room/" + gameState.getRoomId() + "/game-end",
                Map.of("gameId", gameId, "scores", finalScores));

        // 게임 상태 정보 삭제
        activeGames.remove(gameId);
    }

    // 정답 확인
    private boolean isCorrectGuess(String guess, String keyword) {
        return guess.trim().equalsIgnoreCase(keyword.trim());
    }

    // 점수 계산 (등수에 따라 점수 차등 부여)
    private int calculateScore(int correctIndex) {

        int maxScore = GameConstants.MAX_SCORE_PER_TURN;
        int minScore = GameConstants.MIN_SCORE_PER_TURN;

        // 첫 번째로 맞춘 사람은 최대 점수, 마지막으로 맞춘 사람은 최소 점수
        // 그 사이는 선형적으로 감소
        if (correctIndex == 0) {
            return maxScore;
        }

        // 참가자 수에 따라 점수 감소폭을 조정해야 하지만 여기서는 간단하게 구현
        return Math.max(minScore, maxScore - (correctIndex * 10));
    }


}