package com.itcen.whiteboardserver.game.entity;

import com.itcen.whiteboardserver.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "game_participation",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"game_id", "member_id"})
        },
        indexes = {
                @Index(columnList = "game_id, member_id")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameParticipation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 게임 참여 결과 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game; // 게임

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member; // 사용자

    private Integer score; // 점수

    public void increaseScore(int score) {
        this.score += score;
    }
}