package com.itcen.whiteboardserver.chatting.service;

import com.itcen.whiteboardserver.chatting.dto.ChattingRequest;
import com.itcen.whiteboardserver.common.broadcast.Broadcaster;
import com.itcen.whiteboardserver.common.broadcast.dto.TurnBroadcastDto;
import com.itcen.whiteboardserver.game.session.GameSession;
import com.itcen.whiteboardserver.member.entity.Member;
import com.itcen.whiteboardserver.member.repository.MemberRepository;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponse;
import com.itcen.whiteboardserver.turn.dto.response.TurnResponseType;
import com.itcen.whiteboardserver.turn.dto.response.data.ChatData;
import com.itcen.whiteboardserver.turn.service.TurnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChattingServiceImpl implements ChattingService {
    private final GameSession gameSession;
    private final MemberRepository memberRepository;
    private final TurnService turnService;
    private final Broadcaster broadcaster;

    @Override
    public void chat(ChattingRequest chattingRequest) {
        Member member = memberRepository.findByEmail(chattingRequest.email()).orElseThrow(
                () -> new RuntimeException("해당하는 이메일의 회원이 존재하지 않습니다.")
        );

        if (!gameSession.isGamePlaying(chattingRequest.gameId())) {
            broadcastChat(chattingRequest.gameId(), chattingRequest.message(), member);
            return;
        }

        if (!gameSession.isThisMemberParticipant(chattingRequest.gameId(), member.getId())) {
            throw new RuntimeException("채팅 요청한 회원이 게임 참가자가 아닙니다.");
        }

        if (gameSession.isThisDrawer(chattingRequest.gameId(), member.getId())) {
            throw new RuntimeException("채팅 요청한 회원이 출제자이기 때문에 채팅이 금지됩니다.");
        }

        if (turnService.isAlreadyCorrect(chattingRequest.gameId(), member.getId())) {
            throw new RuntimeException("채팅 요청한 회원이 이미 정답을 맞춰 채팅이 금지됩니다.");
        }

        String answer = gameSession.getNowTurnQuizWord(chattingRequest.gameId());
        String message = chattingRequest.message();
        System.out.println(answer + ", " + message);
        if (answer.replaceAll(" ", "").equals(message.replaceAll(" ", ""))) {
            message = member.getNickname() + "님이 정답을 맞추셨습니다.";

            turnService.correct(chattingRequest.gameId(), member.getId());
        }

        broadcastChat(chattingRequest.gameId(), message, member);
        turnService.turnOverIfPossible(chattingRequest.gameId());
    }

    private void broadcastChat(Long gameId, String message, Member member) {
        TurnResponse<ChatData> response = new TurnResponse(
                TurnResponseType.CHAT,
                new ChatData(
                        message,
                        member.getId(),
                        member.getNickname()
                )
        );

        broadcaster.broadcast(
                TurnBroadcastDto.<ChatData>builder()
                        .destination("/topic/game/" + gameId + "/chat")
                        .data(response)
                        .build()
        );
    }
}
