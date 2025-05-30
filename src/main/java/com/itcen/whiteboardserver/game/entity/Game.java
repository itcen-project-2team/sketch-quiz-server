package com.itcen.whiteboardserver.game.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.  IDENTITY)
    private Long id; // 게임 ID

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_turn_id")
    private Turn currentTurnId; // 현재 턴

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    public enum GameStatus {
        NOT_STARTED, IN_PROGRESS, ENDED
    }

    public void changeStatus(GameStatus status) {
        this.status = status;
    }

    public void changeCurrentTurnId(Turn currentTurnId) {
        this.currentTurnId = currentTurnId;
    }

}
