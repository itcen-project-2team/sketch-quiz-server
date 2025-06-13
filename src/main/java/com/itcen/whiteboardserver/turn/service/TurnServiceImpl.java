package com.itcen.whiteboardserver.turn.service;

import com.itcen.whiteboardserver.common.broadcast.Broadcaster;
import com.itcen.whiteboardserver.common.broadcast.dto.TurnBroadcastDto;
import com.itcen.whiteboardserver.common.broadcast.dto.TurnUnicastDto;
import com.itcen.whiteboardserver.game.entity.Game;
import com.itcen.whiteboardserver.game.entity.GameParticipation;
import com.itcen.whiteboardserver.game.entity.Room;
import com.itcen.whiteboardserver.game.repository.GameParticipationRepository;
import com.itcen.whiteboardserver.game.repository.GameRepository;
import com.itcen.whiteboardserver.game.repository.RoomRepository;
import com.itcen.whiteboardserver.game.session.GameSession;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponse;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponseType;
import com.itcen.whiteboardserver.turn.dto.response.data.*;
import com.itcen.whiteboardserver.turn.entitiy.Correct;
import com.itcen.whiteboardserver.turn.entitiy.Turn;
import com.itcen.whiteboardserver.turn.mapper.TurnMapper;
import com.itcen.whiteboardserver.turn.repository.CorrectRepository;
import com.itcen.whiteboardserver.turn.repository.TurnRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Transactional
@RequiredArgsConstructor
@Service
public class TurnServiceImpl implements TurnService {
    final ScoreService scoreService;
    final MemberRepository memberRepository;
    final GameRepository gameRepository;
    final TurnRepository turnRepository;
    final CorrectRepository correctRepository;
    final RoomRepository roomRepository;
    final GameParticipationRepository gameParticipationRepository;
    final Broadcaster broadcaster;
    final GameSession gameSession;
    final PlatformTransactionManager transactionManager;
    final ApplicationContext applicationContext;
    final int TURN_SECONDS = 90;
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @Override
    public void startTurn(Long gameId) {
        //TODO: 현재 사용자가 게임 중이면 게임 시작하지 못하도록 함
        Game game = getGameByGameId(gameId);

        if (!gameSession.canGoNextTurn(game.getId())) {
            quitGame(game);

            return;
        }

        if (game.getCurrentTurn() != null && !game.getCurrentTurn().getIsTurnOver()) {
            throw new RuntimeException("현재 게임의 턴이 끝나지 않았습니다.");
        }

        Turn turn = createTurn(game);
        turnRepository.save(turn);

        game.changeTurn(turn);
        gameRepository.save(game);

        turnRepository.flush();
        gameRepository.flush();

        scheduler.schedule(() -> {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(
                    status -> {
                        broadcastTurnInfo(turn.getId());
                        sendDrawInfoToDrawer(turn.getId());

                        return null;
                    }
            );
        }, 500, TimeUnit.MILLISECONDS);

        scheduleTurnOver(turn.getId());
    }

    @Override
    public void correct(Long gameId, Long memberId) {
        Game game = getGameByGameId(gameId);
        Turn turn = game.getCurrentTurn();

        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new RuntimeException("해당하는 member가 존재하지 않습니다.")
        );

        Correct correct = Correct.builder()
                .turn(turn)
                .member(member)
                .build();

        correctRepository.save(correct);
        broadcastCorrect(
                new CorrectData(
                        memberId,
                        turn.getId(),
                        game.getId()
                )
        );
    }

    @Override
    public boolean isAlreadyCorrect(Long gameId, Long memberId) {
        Game game = getGameByGameId(gameId);
        Turn turn = game.getCurrentTurn();
        Member member = getMemberByMemberId(memberId);

        return correctRepository.existsByTurnAndMember(turn, member);
    }

    @Override
    public boolean turnOverIfPossible(Long gameId) {
        Game game = getGameByGameId(gameId);
        Turn turn = game.getCurrentTurn();

        if (turn.getIsTurnOver()) {
            throw new RuntimeException("이미 끝난 턴입니다.");
        }

        List<Correct> corrects = correctRepository.findAllByTurn(turn);
        List<GameParticipation> participations = gameParticipationRepository.findAllByGame(game);

        if (participations.size() - 1 != corrects.size()) {
            return false;
        }

        doTurnOver(turn.getId());
        return true;
    }

    private void scheduleTurnOver(Long turnId) {
        scheduler.schedule(() -> {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(status -> {
                doTurnOver(turnId);

                return null;
            });
        }, TURN_SECONDS, TimeUnit.SECONDS);
    }

    private void quitGame(Game game) {
        scoreService.finishGameWithScore(game);

        gameSession.removeSession(game.getId());

        game.thisTurnDown();
        game.quitGame();

        Room room = roomRepository.findByCurrentGame(game).orElseThrow(
                () -> new RuntimeException("현재 game에 해당하는 방이 없습니다.")
        );

        room.updateStatus(Room.RoomStatus.FINISHED);

        gameRepository.save(game);
        roomRepository.save(room);
    }

    private void broadcastCorrect(CorrectData correctData) {
        TurnResponse<CorrectData> response = new TurnResponse(
                TurnResponseType.CORRECT,
                correctData
        );

        broadcaster.broadcast(
                TurnBroadcastDto.<CorrectData>builder()
                        .destination("/topic/game" + correctData.gameId())
                        .data(response)
                        .build()
        );
    }

    private void doTurnOver(Long turnId) {
        try {
            Turn turn = turnRepository.findById(turnId).orElseThrow(
                    () -> new RuntimeException("현재 turnId에 해당하는 턴이 존재하지 않습니다.")
            );
            Game game = turn.getGame();

            if (turn.getIsTurnOver()) {
                throw new RuntimeException("이미 끝난 턴입니다.");
            }

            if (!game.getCurrentTurn().equals(turn)) {
                throw new RuntimeException("현재 게임의 턴과 해당 턴이 동일하지 않습니다.");
            }

            scoreService.finalizeTurnScore(turn);

            game.thisTurnDown();
            gameRepository.save(game);
            turn.doTurnOver();
            turnRepository.save(turn);

            gameRepository.flush();
            turnRepository.flush();

            applicationContext.getBean(TurnService.class).startTurn(game.getId());
        } catch (OptimisticLockingFailureException e) {
            throw new RuntimeException("낙관적 lock 적용");
        }
    }

    private void broadcastTurnInfo(Long turnId) {
        Turn turn = getTurnByTurnId(turnId);
        Game game = turn.getGame();
        TurnResponse<TurnData> response = TurnMapper.turnToTurnDataResponse(turn);

        broadcaster.broadcast(
                TurnBroadcastDto.<TurnData>builder()
                        .destination("/topic/game/" + game.getId())
                        .data(response)
                        .build()
        );
    }

    private void sendDrawInfoToDrawer(Long turnId) {
        Turn turn = getTurnByTurnId(turnId);
        Game game = turn.getGame();

        TurnResponse<DrawerData> response = new TurnResponse(
                TurnResponseType.DRAWER,
                new DrawerData(
                        turn.getQuizWord(),
                        turn.getId()
                )
        );

        broadcaster.unicast(
                TurnUnicastDto.<DrawerData>builder()
                        .email(turn.getMember().getEmail())
                        .destination("/topic/game/" + game.getId())
                        .data(response)
                        .build()
        );
    }

    private Turn createTurn(Game game) {
        Long drawerId = gameSession.goNextTurnAndGetDrawer(game.getId());
        Member drawer = getMemberByMemberId(drawerId);
        String quizWord = gameSession.getNowTurnQuizWord(game.getId());
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusSeconds(TURN_SECONDS);

        return Turn.builder()
                .game(game)
                .member(drawer)
                .quizWord(quizWord)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    private Member getMemberByMemberId(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(
                () -> new RuntimeException("현재 memberId의 회원이 없습니다.")
        );
    }

    private Game getGameByGameId(Long gameId) {
        return gameRepository.findById(gameId).orElseThrow(
                () -> new RuntimeException("현재 gameId의 게임이 없습니다.")
        );
    }

    private Turn getTurnByTurnId(Long turnId) {
        return turnRepository.findById(turnId).orElseThrow(
                () -> new RuntimeException("현재 turnId의 턴이 없습니다.")
        );
    }
}
