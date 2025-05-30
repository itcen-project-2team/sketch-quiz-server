package com.itcen.whiteboardserver.game.state;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.TimerTask;

@Slf4j
@RequiredArgsConstructor
public abstract class GameTimerTask extends TimerTask {
    protected final GameState gameState;

    @Override
    public void run() {
        try {
            executeTask();
        } catch (Exception e) {
            log.error("게임 타이머 작업 실행 중 오류 발생: gameId={}", gameState.getGameId(), e);
        }
    }

    protected abstract void executeTask();
}
