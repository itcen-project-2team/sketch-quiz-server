package com.itcen.whiteboardserver.turn.service;

import com.itcen.whiteboardserver.common.broadcast.Broadcaster;
import com.itcen.whiteboardserver.common.broadcast.dto.TurnBroadcastDto;
import com.itcen.whiteboardserver.game.entity.Game;
import com.itcen.whiteboardserver.game.entity.GameParticipation;
import com.itcen.whiteboardserver.game.repository.GameParticipationRepository;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponse;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponseType;
import com.itcen.whiteboardserver.turn.dto.response.data.MemberScore;
import com.itcen.whiteboardserver.turn.dto.response.data.TurnQuitData;
import com.itcen.whiteboardserver.turn.entitiy.Correct;
import com.itcen.whiteboardserver.turn.entitiy.Turn;
import com.itcen.whiteboardserver.turn.repository.CorrectRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Transactional
@RequiredArgsConstructor
@Service
public class ScoreService {
    private final CorrectRepository correctRepository;
    private final GameParticipationRepository gameParticipationRepository;
    private final Broadcaster broadcaster;

    public void finishGameWithScore(Game game) {
        broadcastTurnScore(game, TurnResponseType.GAME_FINISH);
    }

    public void finalizeTurnScore(Turn turn) {
        Game game = turn.getGame();

        //정답자 점수
        List<Correct> corrects = correctRepository.findAllByTurnOrderByCreatedAtDesc(turn);
        int score = 10;
        for (Correct correct : corrects) {
            GameParticipation gameParticipation = gameParticipationRepository.findByGameAndMember(game, correct.getMember())
                    .orElseThrow(
                            () -> new RuntimeException("게임, 회원에 해당하는 게임 참가이력이 없습니다.")
                    );

            gameParticipation.increaseScore(score);
            score += 10;
        }

        //출제자 점수
        int participationCnt = gameParticipationRepository.countByGame(game);
        Member drawer = turn.getMember();

        if (participationCnt - 1 == corrects.size()) {
            int drawerScore = participationCnt / 2 * 10;

            GameParticipation gameParticipation = gameParticipationRepository.findByGameAndMember(game, drawer).orElseThrow(
                    () -> new RuntimeException("현재 게임, 출제자에 해당하는 게임 참여가 없습니다.")
            );

            gameParticipation.increaseScore(drawerScore);
        }

        broadcastTurnScore(game, TurnResponseType.FINISH);
    }

    private void broadcastTurnScore(Game game, TurnResponseType type) {
        List<GameParticipation> gameParticipations = gameParticipationRepository.findAllByGame(game);

        List<MemberScore> memberScores = new ArrayList<>();
        for (GameParticipation gameParticipation : gameParticipations) {
            memberScores.add(
                    new MemberScore(gameParticipation.getMember().getId(), gameParticipation.getScore())
            );
        }

        TurnResponse<TurnQuitData> response = new TurnResponse<>(
                type,
                new TurnQuitData(
                        game.getId(),
                        memberScores
                )
        );

        broadcaster.broadcast(
                TurnBroadcastDto.<TurnQuitData>builder()
                        .destination("/topic/game/" + game.getId())
                        .data(response)
                        .build()
        );
    }
}
