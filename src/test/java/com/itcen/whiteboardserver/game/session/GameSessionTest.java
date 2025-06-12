package com.itcen.whiteboardserver.game.session;

import com.itcen.whiteboardserver.game.session.state.GameState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class GameSessionTest {
    private GameSession gameSession;
    private GameState gameState;
    private final Long gameId = 1L;
    private final int size = 4;


    @BeforeEach
    void init() {
        gameSession = new GameSession();

        //작성자
        List<Long> participants = new ArrayList<>();
        List<String> keywords = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            participants.add((long) i);
            for (int j = 0; j < 2; j++) {
                keywords.add("keyword");
            }
        }

        gameState = GameState.createGameState(participants, keywords);
    }

    @Test
    public void 게임_생성시_시작_안됨() {
        gameSession.createSession(gameId, gameState);
        Assertions.assertFalse(gameSession.isGamePlaying(gameId));
    }

    @Test
    public void 게임_턴_넘기면_게임_진행중_상태가_됨() {
        gameSession.createSession(gameId, gameState);
        gameSession.canGoNextTurn(gameId);

        Assertions.assertFalse(gameSession.isGamePlaying(gameId));
    }

    @Test
    public void 한_게임에_게임세션_여러번_생성() {
        gameSession.createSession(gameId, gameState);
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> {
                    gameSession.createSession(gameId, gameState);
                }
        );
    }

    @Test
    public void 참가자_확인() {
        gameSession.createSession(gameId, gameState);

        Long i = 0L;
        for (; i < size; i++) {
            Assertions.assertTrue(gameSession.isThisMemberParticipant(gameId, i));
        }

        Assertions.assertFalse(gameSession.isThisMemberParticipant(gameId, i));
    }

    @Test
    public void 다음_턴_넘어가는지_확인() {
        gameSession.createSession(gameId, gameState);
        Long drawer = gameSession.goNextTurnAndGetDrawer(gameId);

        Assertions.assertEquals(0, drawer);
        Assertions.assertTrue(gameSession.isThisDrawer(gameId, 0L));
    }
}
