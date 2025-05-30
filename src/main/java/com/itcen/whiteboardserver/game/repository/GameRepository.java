package com.itcen.whiteboardserver.game.repository;

import com.itcen.whiteboardserver.game.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
}

