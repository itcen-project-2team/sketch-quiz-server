package com.itcen.whiteboardserver.game.entity;

import com.itcen.whiteboardserver.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "room")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 방 ID

    @Column(length = 30)
    private String title; // 방 제목

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Member host; // 방장 사용자 ID

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_game_id")
    private Game currentGame;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status; // WAITING, PLAYING, FINISHED

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성 시간

    public enum RoomStatus {
        WAITING, PLAYING, FINISHED
    }
    
    // 방장 변경 메서드
    public void updateHost(Member newHost) {
        this.host = newHost;
    }
    
    // 상태 변경 메서드
    public void updateStatus(RoomStatus newStatus) {
        this.status = newStatus;
    }

    // 현재 게임 변경 메서드
    public void updateCurrentGame(Game game) {
        this.currentGame = game;
    }

}