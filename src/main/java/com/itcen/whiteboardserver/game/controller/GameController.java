package com.itcen.whiteboardserver.game.controller;

import com.itcen.whiteboardserver.game.dto.request.GameStartRequest;
import com.itcen.whiteboardserver.game.dto.request.GuessRequest;
import com.itcen.whiteboardserver.game.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;


@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    // WebSocket 엔드포인트 - 게임 시작 요청 처리
    @MessageMapping("/game/start")
    public void startGame(@Valid GameStartRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long memberId = Long.valueOf(headerAccessor.getUser().getName());
        gameService.startGame(request, memberId);
    }

    // // WebSocket 엔드포인트 - 정답 추측 요청
    @MessageMapping("/game/guess")
    public void processGuess(@Valid GuessRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long memberId = Long.valueOf(headerAccessor.getUser().getName());
        gameService.processGuess(request, memberId);
    }


}
