package com.itcen.whiteboardserver.game.repository;

import com.itcen.whiteboardserver.game.entity.Correct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CorrectRepository extends JpaRepository<Correct, Long> {
}
