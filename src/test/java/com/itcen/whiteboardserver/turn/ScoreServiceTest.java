package com.itcen.whiteboardserver.turn;

import com.itcen.whiteboardserver.common.broadcast.Broadcaster;
import com.itcen.whiteboardserver.game.entity.Game;
import com.itcen.whiteboardserver.game.entity.GameParticipation;
import com.itcen.whiteboardserver.game.repository.GameParticipationRepository;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.turn.entitiy.Correct;
import com.itcen.whiteboardserver.turn.entitiy.Turn;
import com.itcen.whiteboardserver.turn.repository.CorrectRepository;
import com.itcen.whiteboardserver.turn.service.ScoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {
    @Mock
    CorrectRepository correctRepository;

    @Mock
    GameParticipationRepository gameParticipationRepository;

    @Mock
    Broadcaster broadcaster;

    @InjectMocks
    ScoreService scoreService;


    @Test
    void 정답자_점수_부여_테스트() {
        // given
        Game game = new Game();

        Member drawer = Member.builder()
                .id(1L).build();
        Member member2 = Member.builder()
                .id(2L).build();
        Member member3 = Member.builder()
                .id(3L).build();

        GameParticipation gp1 = mock(GameParticipation.class);
        GameParticipation gp2 = mock(GameParticipation.class);
        GameParticipation gp3 = mock(GameParticipation.class);

        Turn turn = Turn.builder()
                .game(game)
                .member(drawer)
                .build();

        Correct correct1 = Correct.builder()
                .member(member2).build();
        Correct correct2 = Correct.builder()
                .member(member3).build();

        when(correctRepository.findAllByTurnOrderByCreatedAtDesc(turn)).thenReturn(List.of(correct1, correct2));
        when(gameParticipationRepository.findByGameAndMember(game, member2)).thenReturn(Optional.of(gp2));
        when(gameParticipationRepository.findByGameAndMember(game, member3)).thenReturn(Optional.of(gp3));

        when(gameParticipationRepository.countByGame(game)).thenReturn(3);
        when(gameParticipationRepository.findByGameAndMember(game, drawer)).thenReturn(Optional.of(gp1));

        // when
        scoreService.finalizeTurnScore(turn);

        // then
        verify(gp2).increaseScore(10);
        verify(gp3).increaseScore(20);
        verify(gp1).increaseScore(10);
    }

    @Test
    void 모두_정답_못_맞추는_경우() {
        // given
        Game game = new Game();

        Member drawer = Member.builder()
                .id(1L).build();
        Member member2 = Member.builder()
                .id(2L).build();
        Member member3 = Member.builder()
                .id(3L).build();

        GameParticipation gp1 = mock(GameParticipation.class);
        GameParticipation gp2 = mock(GameParticipation.class);
        GameParticipation gp3 = mock(GameParticipation.class);

        Turn turn = Turn.builder()
                .game(game)
                .member(drawer)
                .build();

        when(correctRepository.findAllByTurnOrderByCreatedAtDesc(turn)).thenReturn(List.of());
        when(gameParticipationRepository.countByGame(game)).thenReturn(3);

        // when
        scoreService.finalizeTurnScore(turn);

        // then
        verify(gp2, never()).increaseScore(anyInt());
        verify(gp3, never()).increaseScore(anyInt());
        verify(gp1, never()).increaseScore(anyInt());
    }

}

