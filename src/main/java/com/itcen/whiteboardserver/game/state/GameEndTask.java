package com.itcen.whiteboardserver.game.state;

import com.itcen.whiteboardserver.game.service.GameService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GameEndTask extends GameTimerTask {

    private final GameService gameService;

    public GameEndTask(GameState gameState, GameService gameService) {
        super(gameState);
        this.gameService = gameService;
    }

    @Override
    protected void executeTask() {
        log.debug("게임 종료 타이머 실행: gameId={}", gameState.getGameId());
        gameService.endGame(gameState.getGameId());
    }
}
