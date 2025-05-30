package com.itcen.whiteboardserver.game.repository;

import com.itcen.whiteboardserver.game.entity.Game;
import com.itcen.whiteboardserver.game.entity.GameParticipation;
import com.itcen.whiteboardserver.member.domain.aggregate.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameParticipationRepository extends JpaRepository<GameParticipation, Long> {

    Optional<GameParticipation> findByGameAndMember(Game game, Member member);

    List<GameParticipation> findAllByGame(Game game);
}
