package com.itcen.whiteboardserver.game.constant;

public class GameConstants {
    // 게임 참가자 제한
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 6;

    // 상수 클래스이므로 인스턴스화 방지
    private GameConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

}
