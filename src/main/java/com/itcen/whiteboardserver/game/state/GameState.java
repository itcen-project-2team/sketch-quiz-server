package com.itcen.whiteboardserver.game.state;

import com.itcen.whiteboardserver.game.constant.GameConstants;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Getter
public class GameState {
    private final Long gameId;
    private final Long roomId;
    private final List<Long> playerOrder; // 플레이어 순서
    private int currentPlayerIndex; // 현재 플레이어 인덱스
    private int currentRound; // 현재 라운드 (1~TURNS_PER_PLAYER)
    private Long currentTurnId;
    private LocalDateTime turnEndTime; // 현재 턴 종료 시간
    private final Set<Long> correctPlayers; // 이번 턴에 정답을 맞춘 플레이어
    private int correctCount; // 이번 턴에 정답을 맞춘 플레이어 수

    public GameState(Long gameId, Long roomId, List<Long> playerOrder) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.playerOrder = new ArrayList<>(playerOrder);
        this.currentPlayerIndex = 0;
        this.currentRound = 1;
        this.correctPlayers = new HashSet<>();
    }

    public void plusCorrectCount() {
        this.correctCount++;
    }

    public void changeCurrentTurnId(Long turnId) {
        this.currentTurnId = turnId;
    }

    public void endTurn(LocalDateTime turnEndTime) {
        this.turnEndTime = turnEndTime;
    }

    public boolean isAllPlayersCorrect(int totalPlayers) {
        // 출제자를 제외한 모든 플레이어가 정답을 맞췄는지 확인
        return correctPlayers.size() >= totalPlayers - 1;
    }

    public void resetForNextTurn() {
        correctPlayers.clear();
        correctCount = 0;

        // 다음 플레이어로 이동
        currentPlayerIndex = (currentPlayerIndex + 1) % playerOrder.size();

        // 모든 플레이어가 한 번씩 턴을 가졌으면 라운드 증가
        if (currentPlayerIndex == 0) {
            currentRound++;
        }
    }

    public boolean isGameComplete() {
        return currentRound > GameConstants.TURNS_PER_PLAYER;
    }

    public Long getCurrentDrawerId() {
        return playerOrder.get(currentPlayerIndex);
    }
}