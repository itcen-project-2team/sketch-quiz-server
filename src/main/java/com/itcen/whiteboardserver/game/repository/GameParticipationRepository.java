package com.itcen.whiteboardserver.game.repository;

import com.itcen.whiteboardserver.game.entity.GameParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameParticipationRepository extends JpaRepository<GameParticipation, Long> {
    List<GameParticipation> findByGameId(Long gameId);
}
