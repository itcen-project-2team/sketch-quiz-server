package com.itcen.whiteboardserver.game.constant;

public class GameConstants {
    // 게임 참가자 제한
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 6;

    // 턴 제한
    public static final int TURN_DURATION_SECONDS = 140; // 2분 20초
    public static final int TURNS_PER_PLAYER = 3; // 각 플레이어당 턴 수

    // 점수
    public static final int MAX_SCORE_PER_TURN = 100; // 첫 번째 맞춘 사람 점수
    public static final int MIN_SCORE_PER_TURN = 50; // 마지막에 맞춘 사람 점수

    // 상수 클래스이므로 인스턴스화 방지
    private GameConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

}
