package com.itcen.whiteboardserver.game.service;

import com.itcen.whiteboardserver.game.constant.GameConstants;
import com.itcen.whiteboardserver.game.dto.response.TurnStartResponse;
import com.itcen.whiteboardserver.game.entity.Game;
import com.itcen.whiteboardserver.game.entity.Turn;
import com.itcen.whiteboardserver.game.repository.*;
import com.itcen.whiteboardserver.game.state.GameEndTask;
import com.itcen.whiteboardserver.game.state.GameState;
import com.itcen.whiteboardserver.game.state.NextTurnTask;
import com.itcen.whiteboardserver.member.domain.aggregate.entity.Member;
import com.itcen.whiteboardserver.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnService {

    private final RoomRepository roomRepository;
    private final RoomParticipationRepository roomParticipationRepository;
    private final GameRepository gameRepository;
    private final GameParticipationRepository gameParticipationRepository;
    private final TurnRepository turnRepository;
    private final CorrectRepository correctRepository;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;


    // 게임이 끝났다면 true 반환
    public boolean startNextTurn(GameState gameState) {
        if (gameState.isGameComplete()) {
            return true;
        }

        // 현재 턴의 그림 그리는 사람 (drawer) 결정
        Long drawerId = gameState.getCurrentDrawerId();
        Member drawer = memberRepository.findById(drawerId)
                .orElseThrow(() -> new IllegalStateException("그림 그리는 사람을 찾을 수 없습니다."));

        // 게임 정보 가져오기
        Game game = gameRepository.findById(gameState.getGameId())
                .orElseThrow(() -> new IllegalStateException("게임을 찾을 수 없습니다."));

        // 키워드 선택
        String keyword = selectRandomKeyword();

        // 턴 생성
        Turn turn = new Turn(
                null,
                game,
                keyword,
                drawer,
                LocalDateTime.now(),
                null,
                false
        );
        turn = turnRepository.save(turn);

        // 게임의 현재 턴 업데이트
        game.changeCurrentTurnId(turn);
        gameRepository.save(game);

        // 게임 상태 업데이트
        gameState.changeCurrentTurnId(turn.getId());
        gameState.endTurn(LocalDateTime.now().plusSeconds(GameConstants.TURN_DURATION_SECONDS));

        // 턴 시작 알림 - 모든 참가자에게
        TurnStartResponse baseResponse = new TurnStartResponse(
                turn.getId(),
                drawer.getId(),
                drawer.getName(),
                null, // 키워드는 그림 그리는 사람만 볼 수 있음
                GameConstants.TURN_DURATION_SECONDS
        );
        messagingTemplate.convertAndSend("/topic/room/" + gameState.getRoomId() + "/turn-start", baseResponse);

        // 키워드 알림 - 그림 그리는 사람에게만
        TurnStartResponse drawerResponse = new TurnStartResponse(
                turn.getId(),
                drawer.getId(),
                drawer.getName(),
                keyword,
                GameConstants.TURN_DURATION_SECONDS
        );
        messagingTemplate.convertAndSendToUser(
                drawerId.toString(),
                "/queue/game/keyword",
                drawerResponse
        );

        return false;
    }


    public void endCurrentTurn(GameState gameState, Turn turn) {
        // 턴 종료 처리
        turn.endTurn(LocalDateTime.now(), true);
        turnRepository.save(turn);

        // 턴 종료 알림
        messagingTemplate.convertAndSend("/topic/room/" + gameState.getRoomId() + "/turn-end",
                Map.of("turnId", turn.getId(), "keyword", turn.getKeyword()));

        // 다음 턴 준비
        gameState.resetForNextTurn();

        // 게임이 끝났는지 확인
        Timer timer = new Timer();
        if (gameState.isGameComplete()) {
            // 3초 후에 게임 종료 처리 (클라이언트가 턴 종료 메시지를 처리할 시간을 줌)
            timer.schedule(new GameEndTask(gameState, this), 3000);
        } else {
            // 3초 후에 다음 턴 시작 (클라이언트가 턴 종료 메시지를 처리할 시간을 줌)
            timer.schedule(new NextTurnTask(gameState, this), 3000);
        }

    }


    // 랜덤 키워드 선택
    private String selectRandomKeyword() {
        List<String> keywords = Arrays.asList(
                "사과", "바나나", "고양이", "강아지", "자동차", "비행기", "컴퓨터", "책상",
                "의자", "전화기", "시계", "모자", "신발", "바다", "산", "나무", "꽃",
                "태양", "달", "별", "구름", "비", "눈", "자전거", "버스", "기차"
        );
        return keywords.get(new Random().nextInt(keywords.size()));
    }


}
