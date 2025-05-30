package com.itcen.whiteboardserver.game.repository;

import com.itcen.whiteboardserver.game.entity.Turn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TurnRepository extends JpaRepository<Turn, Long> {
}
