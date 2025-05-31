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
@Table(name = "correct")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Correct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 해당 턴에 정답 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id")
    private Turn turn; // 소속된 턴

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member; // 정답자

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성 시간 (정답 시간)
}