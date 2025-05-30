package com.itcen.whiteboardserver.game.state;

import com.itcen.whiteboardserver.game.service.TurnService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NextTurnTask extends GameTimerTask {

    private final TurnService turnService;

    public NextTurnTask(GameState gameState, TurnService turnService) {
        super(gameState);
        this.turnService = turnService;
    }

    @Override
    protected void executeTask() {
        log.debug("다음 턴 시작 타이머 실행: gameId={}", gameState.getGameId());
        if (turnService.startNextTurn(gameState)) {
            log.info("다음 턴 시작 실패, 게임 종료 처리: gameId={}", gameState.getGameId());
            // 여기서는 게임 종료 서비스를 직접 주입받거나 다른 방법으로 처리해야 함
        }
    }
}
