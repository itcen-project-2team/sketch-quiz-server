package com.itcen.whiteboardserver.game.entity;

import com.itcen.whiteboardserver.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "turn")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Turn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 턴 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game; // 소속된 게임

    @Column(nullable = false, length = 30)
    private String keyword; // 그림 키워드

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member; // 그림 그리는 사람

    @Column
    private LocalDateTime startTime; // 시작 시간

    @Column
    private LocalDateTime endTime; // 종료 시간

    @Column
    private Boolean turnOver; // 종료 여부
}
